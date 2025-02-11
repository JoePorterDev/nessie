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
package org.projectnessie.client.http.v1api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TestHttpGetDiff {

  @Test
  public void testDiffPagingDenied() {
    assertThatThrownBy(() -> new HttpGetDiff(null).pageToken("token"))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Diff pagination is not supported in API v1.");

    assertThatThrownBy(() -> new HttpGetDiff(null).maxRecords(1))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Diff pagination is not supported in API v1.");
  }
}
