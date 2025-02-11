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
package org.projectnessie.quarkus.cli;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static org.projectnessie.versioned.storage.common.logic.Logics.commitLogic;
import static org.projectnessie.versioned.storage.common.logic.Logics.referenceLogic;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Collectors;
import org.projectnessie.nessie.relocated.protobuf.ByteString;
import org.projectnessie.versioned.GetNamedRefsParams;
import org.projectnessie.versioned.GetNamedRefsParams.RetrieveOptions;
import org.projectnessie.versioned.ReferenceInfo;
import org.projectnessie.versioned.persist.adapter.RepoDescription;
import org.projectnessie.versioned.storage.common.logic.CommitLogic;
import org.projectnessie.versioned.storage.common.logic.InternalRef;
import org.projectnessie.versioned.storage.common.logic.ReferenceLogic;
import org.projectnessie.versioned.storage.common.objtypes.CommitObj;
import org.projectnessie.versioned.storage.common.persist.Reference;
import picocli.CommandLine.Command;

@Command(
    name = "info",
    mixinStandardHelpOptions = true,
    description = "Nessie repository information")
public class NessieInfo extends BaseCommand {

  @Override
  protected Integer callWithDatabaseAdapter() throws Exception {
    warnOnInMemory();

    ReferenceInfo<ByteString> refInfo =
        databaseAdapter.namedRef(
            serverConfig.getDefaultBranch(),
            GetNamedRefsParams.builder()
                .branchRetrieveOptions(RetrieveOptions.COMMIT_META)
                .tagRetrieveOptions(RetrieveOptions.COMMIT_META)
                .build());

    RepoDescription repoDesc = databaseAdapter.fetchRepositoryDescription();

    spec.commandLine()
        .getOut()
        .printf(
            "%n"
                //
                + "No-ancestor hash:                  %s%n"
                + "Default branch head commit ID:     %s%n"
                + "Default branch commit count:       %s%n"
                + "Repository description version:    %d%n"
                + "Repository description properties: %s%n"
                + "%n"
                + "From configuration:%n"
                + "-------------------%n"
                + "Version-store type:                %s%n"
                + "Default branch:                    %s%n",
            databaseAdapter.noAncestorHash().asString(),
            refInfo.getHash().asString(),
            refInfo.getCommitSeq(),
            repoDesc.getRepoVersion(),
            repoDesc.getProperties().entrySet().stream()
                .map(e -> String.format("%-30s = %s", e.getKey(), e.getValue()))
                .collect(Collectors.joining("\n                                   ")),
            versionStoreConfig.getVersionStoreType(),
            serverConfig.getDefaultBranch());

    return 0;
  }

  @Override
  protected Integer callWithPersist() throws Exception {
    warnOnInMemory();

    ReferenceLogic referenceLogic = referenceLogic(persist);
    Reference defaultBranch =
        referenceLogic.getReference("refs/heads/" + serverConfig.getDefaultBranch());

    CommitLogic commitLogic = commitLogic(persist);
    CommitObj headCommit = commitLogic.fetchCommit(defaultBranch.pointer());

    Reference refRepo = persist.fetchReference(InternalRef.REF_REPO.name());
    CommitObj repoCommit = refRepo != null ? commitLogic.fetchCommit(refRepo.pointer()) : null;

    spec.commandLine()
        .getOut()
        .printf(
            "%n"
                //
                + "Repository created:                %s%n"
                + "Default branch head commit ID:     %s%n"
                + "Default branch commit count:       %s%n"
                + "%n"
                + "From configuration:%n"
                + "-------------------%n"
                + "Version-store type:                %s%n"
                + "Default branch:                    %s%n"
                + "Parent commit IDs per commit:      %s%n",
            repoCommit != null
                ? LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(MICROSECONDS.toMillis(repoCommit.created())),
                    ZoneId.systemDefault())
                : "???",
            defaultBranch.pointer(),
            headCommit != null ? headCommit.seq() : 0,
            versionStoreConfig.getVersionStoreType(),
            serverConfig.getDefaultBranch(),
            persist.config().parentsPerCommit());

    return 0;
  }
}
