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
package dev.ohs.workflow.examples.feature.questionnaire

import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Questionnaire as QuestionnaireR4
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.ServiceRequest
import dev.ohs.workflow.examples.auth.UserContext
import dev.ohs.workflow.examples.data.repository.FhirRepository
import dev.ohs.workflow.examples.util.FhirJson
import dev.ohs.workflow.examples.workflow.AssessmentResult
import dev.ohs.workflow.examples.workflow.ProtocolService
import dev.ohs.workflow.examples.workflow.registrationPatient
import org.jetbrains.compose.resources.ExperimentalResourceApi
import player_reference_examples.workflow_examples.generated.resources.Res

/** Caller-supplied identifiers describing why/for-whom a questionnaire was launched. */
data class QuestionnaireLaunchContext(val patientId: String? = null, val user: UserContext? = null)

/** Outcome of submitting a QuestionnaireResponse, ready for the UI to render. */
data class QuestionnaireSubmissionResult(
  val successMessage: String,
  val assessment: AssessmentResult? = null,
)

object QuestionnaireIds {
  const val PATIENT_REGISTRATION = "patient-registration"
  const val ICCM_SICK_CHILD = "iccm-sick-child"
  const val OPD_CHECK_IN = "opd-check-in"
}

/** Bundled Questionnaire JSON, keyed by the id it should be read under. */
private val BUNDLED_QUESTIONNAIRE_PATHS: Map<String, String> =
  mapOf(
    QuestionnaireIds.PATIENT_REGISTRATION to
      "files/protocols/Questionnaire-PatientRegistration.json",
    QuestionnaireIds.ICCM_SICK_CHILD to "files/protocols/Questionnaire-IccmSickChild.json",
    QuestionnaireIds.OPD_CHECK_IN to "files/protocols/Questionnaire-OpdCheckIn.json",
  )

/** Reads bundled Questionnaires and persists what their responses produce via [FhirRepository]. */
class QuestionnaireService(
  private val repository: FhirRepository,
  private val protocols: ProtocolService,
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
  ): QuestionnaireSubmissionResult =
    when (questionnaire.id) {
      QuestionnaireIds.PATIENT_REGISTRATION -> {
        repository.upsert(registrationPatient(response, launchContext.user().organizationId))
        QuestionnaireSubmissionResult("Patient registered.")
      }
      QuestionnaireIds.ICCM_SICK_CHILD -> {
        val assessment =
          protocols.assessSickChild(patient(launchContext), response, launchContext.user())
        QuestionnaireSubmissionResult("Assessment saved.", assessment)
      }
      QuestionnaireIds.OPD_CHECK_IN -> {
        val consult = protocols.checkIn(patient(launchContext), response, launchContext.user())
        QuestionnaireSubmissionResult(
          "Queued for consultation: ${consult.priority?.value?.code ?: "routine"}"
        )
      }
      else -> error("No submission handling is defined for questionnaire '${questionnaire.id}'.")
    }

  suspend fun confirmReferral(proposal: ServiceRequest, launchContext: QuestionnaireLaunchContext) {
    protocols.confirmReferral(proposal, launchContext.user())
  }

  private suspend fun patient(launchContext: QuestionnaireLaunchContext): Patient =
    launchContext.patientId?.let { repository.get("Patient", it) } as? Patient
      ?: error("This questionnaire needs a patient.")
}

private fun QuestionnaireLaunchContext.user(): UserContext =
  user ?: error("This questionnaire needs a signed-in user with a role.")
