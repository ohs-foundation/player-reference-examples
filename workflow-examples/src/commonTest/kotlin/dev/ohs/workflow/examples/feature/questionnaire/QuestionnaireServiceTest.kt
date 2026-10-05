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

import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.QuestionnaireResponse.Item.Answer.Value
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.workflow.examples.data.repository.InMemorySampleFhirRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class QuestionnaireServiceTest {
  private val repository = InMemorySampleFhirRepository()
  private val service = QuestionnaireService(repository)

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

    service.submit(questionnaire, response, QuestionnaireLaunchContext(organizationId = "org-1"))

    val patient = repository.all("Patient").single() as Patient
    assertEquals("Brian", patient.name.single().given.single().value)
    assertEquals("Organization/org-1", patient.managingOrganization?.reference?.value)
  }
}
