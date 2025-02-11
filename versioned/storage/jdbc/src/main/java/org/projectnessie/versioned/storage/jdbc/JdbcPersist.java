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
package org.projectnessie.versioned.storage.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import javax.annotation.Nonnull;
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
import org.projectnessie.versioned.storage.common.persist.Reference;

class JdbcPersist extends AbstractJdbcPersist {

  private final JdbcBackend backend;

  JdbcPersist(JdbcBackend backend, StoreConfig config) {
    super(backend.databaseSpecific(), config);
    this.backend = backend;
  }

  @FunctionalInterface
  interface SQLRunnable<R> {
    R run(Connection conn) throws SQLException;
  }

  @FunctionalInterface
  interface SQLRunnableException<R, E extends Exception> {
    R run(Connection conn) throws SQLException, E;
  }

  @FunctionalInterface
  interface SQLRunnableExceptions<R, E1 extends Exception, E2 extends Exception> {
    R run(Connection conn) throws SQLException, E1, E2;
  }

  @FunctionalInterface
  interface SQLRunnableVoid {
    void run(Connection conn) throws SQLException;
  }

  private void withConnectionVoid(SQLRunnableVoid runnable) {
    withConnection(
        false,
        conn -> {
          runnable.run(conn);
          return null;
        });
  }

  private <R> R withConnection(boolean readOnly, SQLRunnable<R> runnable) {
    try (Connection conn = backend.borrowConnection()) {
      boolean ok = false;
      R r;
      try {
        r = runnable.run(conn);
        ok = true;
      } finally {
        if (!readOnly) {
          if (ok) {
            conn.commit();
          } else {
            conn.rollback();
          }
        }
      }
      return r;
    } catch (SQLException e) {
      throw unhandledSQLException(e);
    }
  }

  private <R, E extends Exception> R withConnectionException(
      boolean readOnly, SQLRunnableException<R, E> runnable) throws E {
    try (Connection conn = backend.borrowConnection()) {
      boolean ok = false;
      R r;
      try {
        r = runnable.run(conn);
        ok = true;
      } finally {
        if (!readOnly) {
          if (ok) {
            conn.commit();
          } else {
            conn.rollback();
          }
        }
      }
      return r;
    } catch (SQLException e) {
      throw unhandledSQLException(e);
    }
  }

  private <R, E1 extends Exception, E2 extends Exception> R withConnectionExceptions(
      SQLRunnableExceptions<R, E1, E2> runnable) throws E1, E2 {
    try (Connection conn = backend.borrowConnection()) {
      boolean ok = false;
      R r;
      try {
        r = runnable.run(conn);
        ok = true;
      } finally {
        if (ok) {
          conn.commit();
        } else {
          conn.rollback();
        }
      }
      return r;
    } catch (SQLException e) {
      throw unhandledSQLException(e);
    }
  }

  @Override
  public Reference fetchReference(@Nonnull @jakarta.annotation.Nonnull String name) {
    return withConnection(true, conn -> super.findReference(conn, name));
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public Reference[] fetchReferences(@Nonnull @jakarta.annotation.Nonnull String[] names) {
    return withConnection(true, conn -> super.findReferences(conn, names));
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public Reference addReference(@Nonnull @jakarta.annotation.Nonnull Reference reference)
      throws RefAlreadyExistsException {
    return withConnectionException(false, conn -> super.addReference(conn, reference));
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public Reference markReferenceAsDeleted(@Nonnull @jakarta.annotation.Nonnull Reference reference)
      throws RefNotFoundException, RefConditionFailedException {
    return withConnectionExceptions(
        (SQLRunnableExceptions<Reference, RefNotFoundException, RefConditionFailedException>)
            conn -> super.markReferenceAsDeleted(conn, reference));
  }

  @Override
  public void purgeReference(@Nonnull @jakarta.annotation.Nonnull Reference reference)
      throws RefNotFoundException, RefConditionFailedException {
    withConnectionExceptions(
        (SQLRunnableExceptions<Reference, RefNotFoundException, RefConditionFailedException>)
            conn -> {
              super.purgeReference(conn, reference);
              return null;
            });
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public Reference updateReferencePointer(
      @Nonnull @jakarta.annotation.Nonnull Reference reference,
      @Nonnull @jakarta.annotation.Nonnull ObjId newPointer)
      throws RefNotFoundException, RefConditionFailedException {
    return withConnectionExceptions(
        (SQLRunnableExceptions<Reference, RefNotFoundException, RefConditionFailedException>)
            conn -> super.updateReferencePointer(conn, reference, newPointer));
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public Obj fetchObj(@Nonnull @jakarta.annotation.Nonnull ObjId id) throws ObjNotFoundException {
    return withConnectionException(true, conn -> super.fetchObj(conn, id));
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public <T extends Obj> T fetchTypedObj(
      @Nonnull @jakarta.annotation.Nonnull ObjId id, ObjType type, Class<T> typeClass)
      throws ObjNotFoundException {
    return withConnectionException(true, conn -> super.fetchTypedObj(conn, id, type, typeClass));
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public ObjType fetchObjType(@Nonnull @jakarta.annotation.Nonnull ObjId id)
      throws ObjNotFoundException {
    return withConnectionException(true, conn -> super.fetchObjType(conn, id));
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public Obj[] fetchObjs(@Nonnull @jakarta.annotation.Nonnull ObjId[] ids)
      throws ObjNotFoundException {
    return withConnectionException(true, conn -> super.fetchObjs(conn, ids));
  }

  @Override
  public boolean storeObj(
      @Nonnull @jakarta.annotation.Nonnull Obj obj, boolean ignoreSoftSizeRestrictions)
      throws ObjTooLargeException {
    return withConnectionException(
        false, conn -> super.storeObj(conn, obj, ignoreSoftSizeRestrictions));
  }

  @Override
  @Nonnull
  @jakarta.annotation.Nonnull
  public boolean[] storeObjs(@Nonnull @jakarta.annotation.Nonnull Obj[] objs)
      throws ObjTooLargeException {
    return withConnectionException(false, conn -> super.storeObjs(conn, objs));
  }

  @Override
  public void deleteObj(@Nonnull @jakarta.annotation.Nonnull ObjId id) {
    withConnectionVoid(conn -> super.deleteObj(conn, id));
  }

  @Override
  public void deleteObjs(@Nonnull @jakarta.annotation.Nonnull ObjId[] ids) {
    withConnectionVoid(conn -> super.deleteObjs(conn, ids));
  }

  @Override
  public void upsertObj(@Nonnull @jakarta.annotation.Nonnull Obj obj) throws ObjTooLargeException {
    withConnectionException(false, conn -> super.updateObj(conn, obj));
  }

  @Override
  public void upsertObjs(@Nonnull @jakarta.annotation.Nonnull Obj[] objs)
      throws ObjTooLargeException {
    withConnectionException(false, conn -> super.updateObjs(conn, objs));
  }

  @Override
  public void erase() {
    withConnectionVoid(super::erase);
  }

  @Nonnull
  @jakarta.annotation.Nonnull
  @Override
  public CloseableIterator<Obj> scanAllObjects(
      @Nonnull @jakarta.annotation.Nonnull Set<ObjType> returnedObjTypes) {
    try {
      return super.scanAllObjects(backend.borrowConnection(), returnedObjTypes);
    } catch (SQLException e) {
      throw unhandledSQLException(e);
    }
  }
}
