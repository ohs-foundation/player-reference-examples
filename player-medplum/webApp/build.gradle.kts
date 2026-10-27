/*
 * Copyright 2026 Open Health Stack Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  // Kept even though main.kt uses no Compose API: this module owns the executable, and the Compose
  // plugin is what packages :player-medplum-app's composeResources (including the files/configs
  // and files/states JSON read at runtime via Res.readBytes) into the web distribution.
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  id("spotless-conventions")
}

kotlin {
  js {
    browser()
    binaries.executable()
  }

  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    browser()
    binaries.executable()
  }

  sourceSets {
    webMain.dependencies {
      implementation(project(":player-medplum-app"))
      implementation(libs.compose.ui)
      // :engine's WebWorkerSQLiteDriver worker (androidx.sqlite:sqlite-web) is loaded via
      // `new Worker(new URL("sqlite-wasm-worker/worker.js", import.meta.url), { type: "module" })`
      // -- a bare npm specifier. A local file: npm dependency does not propagate to consumers, so
      // the vendored module has to be declared by whichever module owns the webpack build: this
      // one.
      implementation(
        npm(
          "sqlite-wasm-worker",
          layout.projectDirectory.dir("src/webMain/npm/sqlite-wasm-worker").asFile,
        )
      )
    }
  }
}
