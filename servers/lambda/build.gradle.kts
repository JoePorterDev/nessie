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

import io.quarkus.gradle.tasks.QuarkusBuild

plugins {
  `java-library`
  `maven-publish`
  signing
  alias(libs.plugins.quarkus)
  `nessie-conventions`
}

extra["maven.name"] = "Nessie - Lambda Function"

val quarkusRunner by
  configurations.creating {
    description = "Used to reference the generated runner-jar (either fast-jar or uber-jar)"
  }

dependencies {
  implementation(project(":nessie-quarkus")) { exclude("io.quarkus", "quarkus-smallrye-openapi") }

  implementation(enforcedPlatform(libs.quarkus.bom))
  implementation("io.quarkus:quarkus-amazon-lambda")
  implementation("io.quarkus:quarkus-amazon-lambda-http")

  implementation(platform(libs.awssdk.bom))
  implementation(libs.awssdk.apache.client)
  implementation(libs.awssdk.apache.client) { exclude("commons-logging", "commons-logging") }
  implementation(libs.awssdk.netty.nio.client)
  implementation(libs.awssdk.url.connection.client)

  testImplementation("io.quarkus:quarkus-test-amazon-lambda")
  testImplementation("io.quarkus:quarkus-jacoco")

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.bundles.junit.testing)
}

buildForJava11()

val useNative = project.hasProperty("native")

quarkus {
  quarkusBuildProperties.put("quarkus.package.type", quarkusPackageType())
  quarkusBuildProperties.put(
    "quarkus.native.builder-image",
    libs.versions.quarkusNativeBuilderImage.get()
  )
  quarkusBuildProperties.put("quarkus.native.container-build", useNative.toString())
}

val quarkusBuild by
  tasks.getting(QuarkusBuild::class) {
    outputs.doNotCacheIf("Do not add huge cache artifacts to build cache") { true }
    inputs.property("final.name", quarkus.finalName())
    inputs.properties(quarkus.quarkusBuildProperties.get())
  }

tasks.withType<Test>().configureEach {
  enabled = false // TODO project.hasProperty("native")  -- verify that tests work

  jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
  systemProperty("native.image.path", quarkusBuild.nativeRunner)

  val testHeapSize: String? by project
  minHeapSize = if (testHeapSize != null) testHeapSize as String else "256m"
  maxHeapSize = if (testHeapSize != null) testHeapSize as String else "1024m"
}

artifacts {
  add(
    quarkusRunner.name,
    if (quarkusFatJar()) quarkusBuild.runnerJar else quarkusBuild.fastJar.resolve("quarkus-run.jar")
  ) {
    builtBy(quarkusBuild)
  }
}

listOf("javadoc", "sourcesJar").forEach { name ->
  tasks.named(name) { dependsOn(tasks.named("compileQuarkusGeneratedSourcesJava")) }
}

listOf("checkstyleTest", "compileTestJava").forEach { name ->
  tasks.named(name) { dependsOn(tasks.named("compileQuarkusTestGeneratedSourcesJava")) }
}

// TODO build zip file
