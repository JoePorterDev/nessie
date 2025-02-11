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
package org.projectnessie.versioned.storage.telemetry;

import java.io.Closeable;

interface Traced extends Closeable {

  static String tagName(String tag) {
    return "nessie.persist." + tag;
  }

  @Override
  void close();

  void event(String eventName);

  Traced attribute(String tag, String value);

  Traced attribute(String tag, boolean value);

  Traced attribute(String tag, int value);

  Traced attribute(String tag, long value);

  RuntimeException unhandledError(RuntimeException e);
}
