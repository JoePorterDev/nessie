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
package org.projectnessie.services.rest;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import org.projectnessie.services.config.ServerConfig;
import org.projectnessie.services.impl.ConfigApiImpl;
import org.projectnessie.versioned.VersionStore;

@ApplicationScoped
@jakarta.enterprise.context.ApplicationScoped
public class RestConfigService extends ConfigApiImpl {
  // Mandated by CDI 2.0
  public RestConfigService() {
    this(null, null);
  }

  @Inject
  @jakarta.inject.Inject
  public RestConfigService(ServerConfig config, VersionStore store) {
    super(config, store);
  }
}
