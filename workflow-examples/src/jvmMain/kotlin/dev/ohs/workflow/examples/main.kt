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
package dev.ohs.workflow.examples

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.ohs.fhir.engine.FhirEngine
import dev.ohs.fhir.engine.FhirEngineConfiguration
import dev.ohs.fhir.engine.FhirEngineProvider
import dev.ohs.fhir.engine.NetworkConfiguration
import dev.ohs.fhir.engine.ServerConfiguration
import dev.ohs.fhir.engine.sync.remote.HttpLogger
import dev.ohs.workflow.examples.auth.FhirBearerAuthenticator
import dev.ohs.workflow.examples.auth.GeneratedAuthConfig
import dev.ohs.workflow.examples.data.di.initKoin
import dev.ohs.workflow.examples.data.sync.ForegroundSyncManager
import dev.ohs.workflow.examples.data.sync.SYNC_TIMEOUT_DURATION
import dev.ohs.workflow.examples.data.sync.SyncManager
import dev.ohs.workflow.examples.data.sync.serialized
import org.jetbrains.compose.resources.painterResource
import org.koin.dsl.module
import player_reference_examples.workflow_examples.generated.resources.Res
import player_reference_examples.workflow_examples.generated.resources.app_logo

fun main() = application {
  FhirEngineProvider.init(
    FhirEngineConfiguration(
      storageDirectory = desktopStorageDirectory.absolutePath,
      serverConfiguration =
        ServerConfiguration(
          baseUrl = GeneratedAuthConfig.FHIR_BASE_URL,
          networkConfiguration =
            NetworkConfiguration(
              connectionTimeOut = SYNC_TIMEOUT_DURATION,
              readTimeOut = SYNC_TIMEOUT_DURATION,
              writeTimeOut = SYNC_TIMEOUT_DURATION,
            ),
          httpLogger = HttpLogger(level = HttpLogger.Level.HEADERS),
          authenticator = FhirBearerAuthenticator,
        ),
    )
  )
  initKoin(
    module {
      single<FhirEngine> { FhirEngineProvider.getInstance() }
      single<SyncManager> { ForegroundSyncManager().serialized() }
    }
  )
  Window(
    onCloseRequest = ::exitApplication,
    title = "Workflow Examples",
    icon = painterResource(Res.drawable.app_logo),
  ) {
    App()
  }
}
