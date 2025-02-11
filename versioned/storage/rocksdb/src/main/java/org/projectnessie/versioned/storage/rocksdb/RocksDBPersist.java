/*
 * Copyright (C) 2022 Dremio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.projectnessie.versioned.storage.rocksdb;

import static com.google.common.base.Preconditions.checkArgument;
import static org.projectnessie.versioned.storage.common.persist.Reference.reference;
import static org.projectnessie.versioned.storage.serialize.ProtoSerialization.deserializeObj;
import static org.projectnessie.versioned.storage.serialize.ProtoSerialization.deserializeObjId;
import static org.projectnessie.versioned.storage.serialize.ProtoSerialization.deserializeReference;
import static org.projectnessie.versioned.storage.serialize.ProtoSerialization.serializeObj;
import static org.projectnessie.versioned.storage.serialize.ProtoSerialization.serializeReference;

import com.google.common.collect.AbstractIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import org.projectnessie.nessie.relocated.protobuf.ByteString;
import org.projectnessie.versioned.storage.common.config.StoreConfig;
import org.projectnessie.versioned.storage.common.exceptions.ObjNotFoundException;
import org.projectnessie.versioned.storage.common.exceptions.ObjTooLargeException;
import org.projectnessie.versioned.storage.common.exceptions.RefAlreadyExistsException;
import org.projectnessie.versioned.storage.common.exceptions.RefConditionFailedException;
import org.projectnessie.versioned.storage.common.exceptions.RefNotFoundException;
import org.projectnessie.versioned.storage.common.persist.CloseableIterator;
import org.projectnessie.versioned.storage.common.persist.Obj;
import org.projectnessie.versioned.storage.common.persist.ObjId;
import org.projectnessie.versioned.storage.common.persist.ObjType;
import org.projectnessie.versioned.storage.common.persist.Persist;
import org.projectnessie.versioned.storage.common.persist.Reference;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.TransactionDB;

class RocksDBPersist implements Persist {

  private final RocksDBBackend backend;
  private final RocksDBRepo repo;
  private final StoreConfig config;

  private final ByteString keyPrefix;

  RocksDBPersist(RocksDBBackend backend, RocksDBRepo repo, StoreConfig config) {
    this.backend = backend;
    this.repo = repo;
    this.config = config;
    this.keyPrefix = ByteString.copyFromUtf8(config.repositoryId() + ':');
  }

  private byte[] dbKey(ByteString key) {
    return keyPrefix.concat(key).toByteArray();
  }

  private byte[] dbKey(String key) {
    return dbKey(ByteString.copyFromUtf8(key));
  }

  private byte[] dbKey(ObjId id) {
    return dbKey(id.asBytes());
  }

  private RuntimeException rocksDbException(RocksDBException e) {
    throw new RuntimeException("Unhandled RocksDB exception", e);
  }

  @Nonnull
  @jakarta.annotation.Nonnull
  @Override
  public String name() {
    return RocksDBBackendFactory.NAME;
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public StoreConfig config() {
    return config;
  }

  @Override
  public Reference fetchReference(@Nonnull @jakarta.annotation.Nonnull String name) {
    try {
      RocksDBBackend v = backend;
      TransactionDB db = v.db();
      ColumnFamilyHandle cf = v.refs();
      byte[] key = dbKey(name);

      byte[] reference = db.get(cf, key);
      return deserializeReference(reference);
    } catch (RocksDBException e) {
      throw rocksDbException(e);
    }
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public Reference[] fetchReferences(@Nonnull @jakarta.annotation.Nonnull String[] names) {
    try {
      RocksDBBackend v = backend;
      TransactionDB db = v.db();
      ColumnFamilyHandle cf = v.refs();

      int num = names.length;
      Reference[] r = new Reference[num];
      List<ColumnFamilyHandle> handles = new ArrayList<>(num);
      List<byte[]> keys = new ArrayList<>(num);
      for (String name : names) {
        if (name != null) {
          handles.add(cf);
          keys.add(dbKey(name));
        }
      }

      if (!keys.isEmpty()) {
        List<byte[]> dbResult = db.multiGetAsList(handles, keys);

        for (int i = 0, ri = 0; i < num; i++) {
          String name = names[i];
          if (name != null) {
            byte[] reference = dbResult.get(ri++);
            r[i] = deserializeReference(reference);
          }
        }
      }

      return r;
    } catch (RocksDBException e) {
      throw rocksDbException(e);
    }
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public Reference addReference(@Nonnull @jakarta.annotation.Nonnull Reference reference)
      throws RefAlreadyExistsException {
    checkArgument(!reference.deleted(), "Deleted references must not be added");

    Lock l = repo.referencesLock(reference.name());
    try {
      RocksDBBackend b = backend;
      TransactionDB db = b.db();
      ColumnFamilyHandle cf = b.refs();
      byte[] key = dbKey(reference.name());

      byte[] existing = db.get(cf, key);
      if (existing != null) {
        throw new RefAlreadyExistsException(deserializeReference(existing));
      }

      db.put(cf, key, serializeReference(reference));

      return reference;
    } catch (RocksDBException e) {
      throw rocksDbException(e);
    } finally {
      l.unlock();
    }
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public Reference markReferenceAsDeleted(@Nonnull @jakarta.annotation.Nonnull Reference reference)
      throws RefNotFoundException, RefConditionFailedException {
    Lock l = repo.referencesLock(reference.name());
    try {
      RocksDBBackend b = backend;
      TransactionDB db = b.db();
      ColumnFamilyHandle cf = b.refs();
      byte[] key = dbKey(reference.name());

      checkReference(reference, db, cf, key, false);

      Reference asDeleted = reference(reference.name(), reference.pointer(), true);
      db.put(cf, key, serializeReference(asDeleted));
      return asDeleted;
    } catch (RocksDBException e) {
      throw rocksDbException(e);
    } finally {
      l.unlock();
    }
  }

  private static void checkReference(
      Reference reference,
      TransactionDB db,
      ColumnFamilyHandle cf,
      byte[] key,
      boolean expectDeleted)
      throws RocksDBException, RefNotFoundException, RefConditionFailedException {
    byte[] existing = db.get(cf, key);
    if (existing == null) {
      throw new RefNotFoundException(reference);
    }

    Reference ref = deserializeReference(existing);
    if (ref.deleted() != expectDeleted || !ref.pointer().equals(reference.pointer())) {
      throw new RefConditionFailedException(ref);
    }
  }

  @Override
  public void purgeReference(@Nonnull @jakarta.annotation.Nonnull Reference reference)
      throws RefNotFoundException, RefConditionFailedException {
    Lock l = repo.referencesLock(reference.name());
    try {
      RocksDBBackend b = backend;
      TransactionDB db = b.db();
      ColumnFamilyHandle cf = b.refs();
      byte[] key = dbKey(reference.name());

      checkReference(reference, db, cf, key, true);

      db.delete(cf, key);
    } catch (RocksDBException e) {
      throw rocksDbException(e);
    } finally {
      l.unlock();
    }
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public Reference updateReferencePointer(
      @Nonnull @jakarta.annotation.Nonnull Reference reference,
      @Nonnull @jakarta.annotation.Nonnull ObjId newPointer)
      throws RefNotFoundException, RefConditionFailedException {
    Lock l = repo.referencesLock(reference.name());
    try {
      RocksDBBackend b = backend;
      TransactionDB db = b.db();
      ColumnFamilyHandle cf = b.refs();
      byte[] key = dbKey(reference.name());

      checkReference(reference, db, cf, key, false);

      Reference updated = reference(reference.name(), newPointer, false);

      db.put(cf, key, serializeReference(updated));
      return updated;
    } catch (RocksDBException e) {
      throw rocksDbException(e);
    } finally {
      l.unlock();
    }
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public Obj fetchObj(@Nonnull @jakarta.annotation.Nonnull ObjId id) throws ObjNotFoundException {
    try {
      RocksDBBackend b = backend;
      TransactionDB db = b.db();
      ColumnFamilyHandle cf = b.objs();
      byte[] key = dbKey(id);

      byte[] obj = db.get(cf, key);
      if (obj == null) {
        throw new ObjNotFoundException(id);
      }
      return deserializeObj(id, obj);
    } catch (RocksDBException e) {
      throw rocksDbException(e);
    }
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public <T extends Obj> T fetchTypedObj(
      @Nonnull @jakarta.annotation.Nonnull ObjId id, ObjType type, Class<T> typeClass)
      throws ObjNotFoundException {
    Obj obj = fetchObj(id);
    if (obj.type() != type) {
      throw new ObjNotFoundException(id);
    }
    @SuppressWarnings("unchecked")
    T r = (T) obj;
    return r;
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public ObjType fetchObjType(@Nonnull @jakarta.annotation.Nonnull ObjId id)
      throws ObjNotFoundException {
    return fetchObj(id).type();
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public Obj[] fetchObjs(@Nonnull @jakarta.annotation.Nonnull ObjId[] ids)
      throws ObjNotFoundException {
    try {
      RocksDBBackend b = backend;
      TransactionDB db = b.db();
      ColumnFamilyHandle cf = b.objs();

      int num = ids.length;
      Obj[] r = new Obj[num];
      List<ColumnFamilyHandle> handles = new ArrayList<>(num);
      List<byte[]> keys = new ArrayList<>(num);
      for (ObjId id : ids) {
        if (id != null) {
          handles.add(cf);
          keys.add(dbKey(id));
        }
      }

      if (!keys.isEmpty()) {
        List<ObjId> notFound = null;
        List<byte[]> dbResult = db.multiGetAsList(handles, keys);
        for (int i = 0, ri = 0; i < num; i++) {
          ObjId id = ids[i];
          if (id != null) {
            byte[] obj = dbResult.get(ri++);
            if (obj == null) {
              if (notFound == null) {
                notFound = new ArrayList<>();
              }
              notFound.add(id);
            } else {
              r[i] = deserializeObj(id, obj);
            }
          }
        }
        if (notFound != null) {
          throw new ObjNotFoundException(notFound);
        }
      }

      return r;
    } catch (RocksDBException e) {
      throw rocksDbException(e);
    }
  }

  @Override
  public boolean storeObj(
      @Nonnull @jakarta.annotation.Nonnull Obj obj, boolean ignoreSoftSizeRestrictions)
      throws ObjTooLargeException {
    checkArgument(obj.id() != null, "Obj to store must have a non-null ID");

    Lock l = repo.objLock(obj.id());
    try {
      RocksDBBackend b = backend;
      TransactionDB db = b.db();
      ColumnFamilyHandle cf = b.objs();
      byte[] key = dbKey(obj.id());

      byte[] existing = db.get(cf, key);
      if (existing != null) {
        return false;
      }

      int incrementalIndexSizeLimit =
          ignoreSoftSizeRestrictions ? Integer.MAX_VALUE : effectiveIncrementalIndexSizeLimit();
      int indexSizeLimit =
          ignoreSoftSizeRestrictions ? Integer.MAX_VALUE : effectiveIndexSegmentSizeLimit();
      byte[] serialized = serializeObj(obj, incrementalIndexSizeLimit, indexSizeLimit);

      db.put(cf, key, serialized);
      return true;
    } catch (RocksDBException e) {
      throw rocksDbException(e);
    } finally {
      l.unlock();
    }
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public boolean[] storeObjs(@Nonnull @jakarta.annotation.Nonnull Obj[] objs)
      throws ObjTooLargeException {
    boolean[] r = new boolean[objs.length];
    for (int i = 0; i < objs.length; i++) {
      Obj o = objs[i];
      if (o != null) {
        r[i] = storeObj(o, false);
      }
    }
    return r;
  }

  @Override
  public void deleteObj(@Nonnull @jakarta.annotation.Nonnull ObjId id) {
    Lock l = repo.objLock(id);
    try {
      RocksDBBackend b = backend;
      TransactionDB db = b.db();
      ColumnFamilyHandle cf = b.objs();
      byte[] key = dbKey(id);

      db.delete(cf, key);
    } catch (RocksDBException e) {
      throw rocksDbException(e);
    } finally {
      l.unlock();
    }
  }

  @Override
  public void deleteObjs(@Nonnull @jakarta.annotation.Nonnull ObjId[] ids) {
    for (ObjId id : ids) {
      deleteObj(id);
    }
  }

  @Override
  public void upsertObj(@Nonnull @jakarta.annotation.Nonnull Obj obj) throws ObjTooLargeException {
    ObjId id = obj.id();
    checkArgument(id != null, "Obj to store must have a non-null ID");

    Lock l = repo.objLock(obj.id());
    try {
      RocksDBBackend b = backend;
      TransactionDB db = b.db();
      ColumnFamilyHandle cf = b.objs();
      byte[] key = dbKey(id);

      byte[] serialized =
          serializeObj(obj, effectiveIncrementalIndexSizeLimit(), effectiveIndexSegmentSizeLimit());

      db.put(cf, key, serialized);
    } catch (RocksDBException e) {
      throw rocksDbException(e);
    } finally {
      l.unlock();
    }
  }

  @Override
  public void upsertObjs(@Nonnull @jakarta.annotation.Nonnull Obj[] objs)
      throws ObjTooLargeException {
    for (Obj obj : objs) {
      upsertObj(obj);
    }
  }

  @Override
  public void erase() {
    // erase() does not use any lock, it's use is rare, taking the risk of having a corrupted,
    // erased repo

    RocksDBBackend b = backend;
    TransactionDB db = b.db();

    b.all()
        .forEach(
            cf -> {
              try (RocksIterator iter = db.newIterator(cf)) {
                List<ByteString> deletes = new ArrayList<>();
                for (iter.seekToFirst(); iter.isValid(); iter.next()) {
                  ByteString key = ByteString.copyFrom(iter.key());
                  if (key.startsWith(keyPrefix)) {
                    deletes.add(key);
                  }
                }
                deletes.forEach(
                    key -> {
                      try {
                        db.delete(cf, key.toByteArray());
                      } catch (RocksDBException e) {
                        throw rocksDbException(e);
                      }
                    });
              }
            });
  }

  @Nonnull
  @jakarta.annotation.Nonnull
  @Override
  public CloseableIterator<Obj> scanAllObjects(
      @Nonnull @jakarta.annotation.Nonnull Set<ObjType> returnedObjTypes) {
    return new ScanAllObjectsIterator(returnedObjTypes::contains);
  }

  private class ScanAllObjectsIterator extends AbstractIterator<Obj>
      implements CloseableIterator<Obj> {

    private final Predicate<ObjType> filter;

    private final TransactionDB db;
    private final ColumnFamilyHandle cf;
    private final RocksIterator iter;
    private boolean first = true;
    private byte[] lastKey;

    ScanAllObjectsIterator(Predicate<ObjType> filter) {
      this.filter = filter;

      RocksDBBackend b = backend;
      db = b.db();
      cf = b.objs();
      iter = db.newIterator(b.objs());
      iter.seekToFirst();
    }

    @Override
    protected Obj computeNext() {
      while (true) {
        if (!iter.isValid()) {
          return endOfData();
        }

        if (first) {
          first = false;
        } else {
          iter.next();
        }

        byte[] k = iter.key();
        if (lastKey != null && Arrays.equals(lastKey, k)) {
          // RocksDB sometimes tends to return the same key twice
          continue;
        }
        lastKey = k;

        ByteString key = ByteString.copyFrom(k);
        if (!key.startsWith(keyPrefix)) {
          continue;
        }

        byte[] obj;
        try {
          obj = db.get(cf, k);
        } catch (RocksDBException e) {
          throw rocksDbException(e);
        }
        if (obj == null) {
          continue;
        }

        ObjId id = deserializeObjId(key.substring(keyPrefix.size()));
        Obj o = deserializeObj(id, obj);

        if (filter.test(o.type())) {
          return o;
        }
      }
    }

    @Override
    public void close() {
      iter.close();
    }
  }
}
