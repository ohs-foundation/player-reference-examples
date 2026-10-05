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

import dev.ohs.fhir.model.r4.Boolean as FhirBoolean
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Integer
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.QuestionnaireResponse.Item.Answer.Value
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.workflow.FhirOperator
import dev.ohs.workflow.examples.auth.AppRole
import dev.ohs.workflow.examples.auth.UserContext
import dev.ohs.workflow.examples.data.repository.InMemorySampleFhirRepository
import dev.ohs.workflow.examples.workflow.BundledProtocols
import dev.ohs.workflow.examples.workflow.InMemoryWorkflowRepository
import dev.ohs.workflow.examples.workflow.ProtocolService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class QuestionnaireServiceTest {
  private val repository = InMemorySampleFhirRepository()
  private val user = UserContext(AppRole.CHW, "p1", "org-1", null)
  private val service =
    QuestionnaireService(
      repository,
      InMemoryWorkflowRepository().let { workflow ->
        ProtocolService(workflow, { FhirOperator(workflow, resolver = BundledProtocols.load()) })
      },
    )

  @Test
  fun registrationQuestionnaireIsBundled() = runTest {
    val questionnaire = service.getQuestionnaire(QuestionnaireIds.PATIENT_REGISTRATION)

    assertEquals(
      listOf("given", "family", "gender", "birth-date"),
      questionnaire.item.map { it.linkId.value },
    )
  }

  @Test
  fun submittingRegistrationStoresThePatient() = runTest {
    val questionnaire = service.getQuestionnaire(QuestionnaireIds.PATIENT_REGISTRATION)
    val response =
      QuestionnaireResponse(
        status = Enumeration(value = QuestionnaireResponse.QuestionnaireResponseStatus.Completed),
        item =
          listOf(
            QuestionnaireResponse.Item(
              linkId = FhirString(value = "given"),
              answer =
                listOf(
                  QuestionnaireResponse.Item.Answer(
                    value = Value.String(FhirString(value = "Brian"))
                  )
                ),
            )
          ),
      )

    service.submit(questionnaire, response, QuestionnaireLaunchContext(user = user))

    val patient = repository.all("Patient").single() as Patient
    assertEquals("Brian", patient.name.single().given.single().value)
    assertEquals("Organization/org-1", patient.managingOrganization?.reference?.value)
  }

  @Test
  fun submittingASickChildAssessmentRunsTheProtocol() = runTest {
    repository.upsert(Patient(id = "child-1"))
    val questionnaire = service.getQuestionnaire(QuestionnaireIds.ICCM_SICK_CHILD)
    fun answer(linkId: String, value: Value) =
      QuestionnaireResponse.Item(
        linkId = FhirString(value = linkId),
        answer = listOf(QuestionnaireResponse.Item.Answer(value = value)),
      )
    val response =
      QuestionnaireResponse(
        status = Enumeration(value = QuestionnaireResponse.QuestionnaireResponseStatus.Completed),
        item =
          listOf(
            answer("age-months", Value.Integer(Integer(value = 14))),
            answer("chest-indrawing", Value.Boolean(FhirBoolean(value = true))),
            answer("fever", Value.Boolean(FhirBoolean(value = false))),
            answer("cough", Value.Boolean(FhirBoolean(value = false))),
          ),
      )

    val result =
      service.submit(
        questionnaire,
        response,
        QuestionnaireLaunchContext(patientId = "child-1", user = user),
      )

    val assessment = assertNotNull(result.assessment)
    assertNotNull(assessment.referral)
    assertNull(assessment.followUp)
  }

  @Test
  fun checkingInQueuesThePatientByTriagePriority() = runTest {
    repository.upsert(Patient(id = "patient-1"))
    val questionnaire = service.getQuestionnaire(QuestionnaireIds.OPD_CHECK_IN)
    val response =
      QuestionnaireResponse(
        status = Enumeration(value = QuestionnaireResponse.QuestionnaireResponseStatus.Completed),
        item =
          listOf(
            QuestionnaireResponse.Item(
              linkId = FhirString(value = "spo2"),
              answer =
                listOf(
                  QuestionnaireResponse.Item.Answer(value = Value.Integer(Integer(value = 86)))
                ),
            )
          ),
      )

    val result =
      service.submit(
        questionnaire,
        response,
        QuestionnaireLaunchContext(
          patientId = "patient-1",
          user = UserContext(AppRole.NURSE, "p2", "org-1", "loc-1"),
        ),
      )

    assertEquals("Queued for consultation: stat", result.successMessage)
  }
}
