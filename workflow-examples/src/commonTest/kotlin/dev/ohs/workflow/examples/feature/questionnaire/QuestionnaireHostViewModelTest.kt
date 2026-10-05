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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class QuestionnaireHostViewModelTest {

  @Test
  fun failedReferralKeepsTheAssessmentOnScreen() = runTest {
    val patients = InMemorySampleFhirRepository().apply { upsert(Patient(id = "child-1")) }
    val workflow = InMemoryWorkflowRepository()
    val service =
      QuestionnaireService(
        patients,
        ProtocolService(workflow, { FhirOperator(workflow, resolver = BundledProtocols.load()) }),
      )
    val viewModel =
      QuestionnaireHostViewModel(
        QuestionnaireIds.ICCM_SICK_CHILD,
        QuestionnaireLaunchContext("child-1", UserContext(AppRole.CHW, "p1", "o1", null)),
        service,
      )
    viewModel.uiState.first { it is QuestionnaireHostUiState.Ready }
    fun answer(linkId: String, value: Value) =
      QuestionnaireResponse.Item(
        linkId = FhirString(value = linkId),
        answer = listOf(QuestionnaireResponse.Item.Answer(value = value)),
      )
    viewModel.onSubmit(
      QuestionnaireResponse(
        status = Enumeration(value = QuestionnaireResponse.QuestionnaireResponseStatus.Completed),
        item =
          listOf(
            answer("age-months", Value.Integer(Integer(value = 14))),
            answer("convulsions", Value.Boolean(FhirBoolean(value = true))),
          ),
      )
    )
    viewModel.uiState.first { it is QuestionnaireHostUiState.Submitted }
    workflow.resources.clear()

    viewModel.confirmReferral().join()

    val state = assertIs<QuestionnaireHostUiState.Submitted>(viewModel.uiState.value)
    assertNotNull(state.result.assessment)
    assertNotNull(state.referralError)
  }
}
