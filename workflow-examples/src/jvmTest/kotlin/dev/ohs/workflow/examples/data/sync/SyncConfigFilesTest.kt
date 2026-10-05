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
package dev.ohs.workflow.examples.data.sync

import dev.ohs.workflow.examples.auth.AppRole
import dev.ohs.workflow.examples.auth.UserContext
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/** Types and params the org-scoped gateway allows; anything else is a 403 at sync time. */
private val GATEWAY_ALLOWED_TYPES =
  setOf(
    "Patient",
    "Encounter",
    "Observation",
    "QuestionnaireResponse",
    "ServiceRequest",
    "MedicationRequest",
    "Task",
    "CarePlan",
    "Procedure",
    "PractitionerRole",
    "Location",
    "Organization",
    "Practitioner",
    "Questionnaire",
  )
private val GATEWAY_STRIPPED_PARAMS =
  setOf("_include", "_revinclude", "_filter", "_contained", "_containedType")

class SyncConfigFilesTest {

  @Test
  fun everyRoleConfigUsesOnlyWhatTheGatewayAllows() {
    AppRole.entries.forEach { role ->
      val config =
        Json.decodeFromString<SyncConfig>(
          File("src/commonMain/composeResources/files/sync/${role.code}.json").readText()
        )
      config.resources.forEach {
        assertTrue(it.type in GATEWAY_ALLOWED_TYPES, "${role.code}: ${it.type}")
        assertTrue(
          it.params.keys.none(GATEWAY_STRIPPED_PARAMS::contains),
          "${role.code}: ${it.params}",
        )
      }
      SyncConfigResolver.resolve(config, UserContext(role, "p", "o", "l"))
    }
  }
}
