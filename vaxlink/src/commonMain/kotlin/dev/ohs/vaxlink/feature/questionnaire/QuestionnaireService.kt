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
package dev.ohs.vaxlink.feature.questionnaire

import dev.ohs.fhir.model.r4.Questionnaire as QuestionnaireR4
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.vaxlink.auth.UserContext
import dev.ohs.vaxlink.data.repository.FhirRepository
import dev.ohs.vaxlink.generated.resources.Res
import dev.ohs.vaxlink.util.FhirJson
import dev.ohs.vaxlink.workflow.Transactor
import dev.ohs.vaxlink.workflow.registrationPatient
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Caller-supplied identifiers describing why/for-whom a questionnaire was launched. */
data class QuestionnaireLaunchContext(
  val patientId: String? = null,
  val user: UserContext? = null,
  val taskId: String? = null,
)

/** Outcome of submitting a QuestionnaireResponse, ready for the UI to render. */
data class QuestionnaireSubmissionResult(val successMessage: String)

object QuestionnaireIds {
  /** Newborn registration (NE-2 adapts the form: name, sex, date of birth, guardian). */
  const val PATIENT_REGISTRATION = "patient-registration"
}

/** Bundled Questionnaire JSON, keyed by the id it should be read under. */
private val BUNDLED_QUESTIONNAIRE_PATHS: Map<String, String> =
  mapOf(
    QuestionnaireIds.PATIENT_REGISTRATION to
      "files/protocols/Questionnaire-PatientRegistration.json"
  )

/** Reads bundled Questionnaires and persists what their responses produce via [FhirRepository]. */
class QuestionnaireService(
  private val repository: FhirRepository,
  private val transactor: Transactor = Transactor { it() },
) {

  private val fhirJson = FhirJson.instance

  /** Reads a Questionnaire from the bundled config files. */
  @OptIn(ExperimentalResourceApi::class)
  suspend fun getQuestionnaire(id: String): QuestionnaireR4 {
    val path =
      BUNDLED_QUESTIONNAIRE_PATHS[id]
        ?: error("Questionnaire '$id' was not found in the app config.")
    val json = Res.readBytes(path).decodeToString()
    return fhirJson.decodeFromString(QuestionnaireR4.serializer(), json).copy(id = id)
  }

  fun toJson(questionnaire: QuestionnaireR4): String =
    fhirJson.encodeToString(QuestionnaireR4.serializer(), questionnaire)

  suspend fun submit(
    questionnaire: QuestionnaireR4,
    response: QuestionnaireResponse,
    launchContext: QuestionnaireLaunchContext,
  ): QuestionnaireSubmissionResult {
    var result: QuestionnaireSubmissionResult? = null
    transactor.atomically { result = record(questionnaire, response, launchContext) }
    return result!!
  }

  /**
   * Extracts the response's resources and stores them. One branch per bundled questionnaire; NE-2
   * (scan certificate, refer) and NE-3 (verify, confirm dose) add theirs here.
   */
  private suspend fun record(
    questionnaire: QuestionnaireR4,
    response: QuestionnaireResponse,
    launchContext: QuestionnaireLaunchContext,
  ): QuestionnaireSubmissionResult =
    when (questionnaire.id) {
      QuestionnaireIds.PATIENT_REGISTRATION -> {
        repository.upsert(registrationPatient(response, launchContext.user().organizationId))
        QuestionnaireSubmissionResult("Patient registered.")
      }
      else -> error("No submission handling is defined for questionnaire '${questionnaire.id}'.")
    }
}

private fun QuestionnaireLaunchContext.user(): UserContext =
  user ?: error("This questionnaire needs a signed-in user with a role.")
