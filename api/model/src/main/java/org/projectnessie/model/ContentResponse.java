/*
 * Copyright (C) 2020 Dremio
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
package org.projectnessie.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableContentResponse.class)
@JsonDeserialize(as = ImmutableContentResponse.class)
public interface ContentResponse {

  static ImmutableContentResponse.Builder builder() {
    return ImmutableContentResponse.builder();
  }

  @NotNull
  @jakarta.validation.constraints.NotNull
  @Value.Parameter(order = 1)
  Content getContent();

  /**
   * The effective reference (for example a branch or tag) including the commit ID from which the
   * entries were fetched.
   */
  @Nullable
  @jakarta.annotation.Nullable
  @Value.Parameter(order = 2)
  Reference getEffectiveReference();

  static ContentResponse of(Content content, Reference effectiveReference) {
    return ImmutableContentResponse.of(content, effectiveReference);
  }
}
