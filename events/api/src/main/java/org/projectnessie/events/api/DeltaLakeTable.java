/*
 * Copyright (C) 2023 Dremio
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
package org.projectnessie.events.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import org.immutables.value.Value;

/** A {@link Content} object that represents a Delta Lake table. */
@Value.Immutable
@JsonSerialize(as = ImmutableDeltaLakeTable.class)
@JsonDeserialize(as = ImmutableDeltaLakeTable.class)
public interface DeltaLakeTable extends Content {

  @Override
  @Value.Default
  default ContentType getType() {
    return ContentType.DELTA_LAKE_TABLE;
  }

  List<String> getMetadataLocationHistory();

  List<String> getCheckpointLocationHistory();

  String getLastCheckpoint();
}
