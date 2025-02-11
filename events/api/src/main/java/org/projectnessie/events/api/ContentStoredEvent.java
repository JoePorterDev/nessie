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
import org.immutables.value.Value;

/**
 * Event that is emitted when a content is stored. This event corresponds to a PUT operation in a
 * commit, merge or transplant. This event is emitted after the content has been stored.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableContentStoredEvent.class)
@JsonDeserialize(as = ImmutableContentStoredEvent.class)
public interface ContentStoredEvent extends ContentEvent {
  @Override
  @Value.Default
  default EventType getType() {
    return EventType.CONTENT_STORED;
  }

  /** The content that was stored. */
  Content getContent();
}
