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
package org.projectnessie.versioned.storage.common.exceptions;

import javax.annotation.Nonnull;
import org.projectnessie.versioned.storage.common.persist.Reference;

public class RefConditionFailedException extends RefException {

  public RefConditionFailedException(@Nonnull @jakarta.annotation.Nonnull Reference reference) {
    super(reference, "Reference " + reference.name() + " pointer expectation failed");
  }

  @Nonnull
  @jakarta.annotation.Nonnull
  @Override
  public Reference reference() {
    //noinspection DataFlowIssue
    return super.reference();
  }
}
