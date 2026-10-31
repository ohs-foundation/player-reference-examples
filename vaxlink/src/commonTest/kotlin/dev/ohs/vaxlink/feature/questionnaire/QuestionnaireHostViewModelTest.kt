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

import androidx.lifecycle.viewModelScope
import dev.ohs.fhir.engine.sync.SyncJobStatus
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.QuestionnaireResponse.Item.Answer.Value
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.vaxlink.auth.AppRole
import dev.ohs.vaxlink.auth.UserContext
import dev.ohs.vaxlink.data.repository.InMemorySampleFhirRepository
import dev.ohs.vaxlink.data.sync.FakeSyncManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class QuestionnaireHostViewModelTest {

  private val patients = InMemorySampleFhirRepository()

  private suspend fun TestScope.submitRegistration(
    sync: FakeSyncManager,
    scope: CoroutineScope,
    user: UserContext? = UserContext(AppRole.CHW, "p1", "o1", null),
    leaveRightAway: Boolean = false,
  ): QuestionnaireHostViewModel {
    val viewModel =
      QuestionnaireHostViewModel(
        QuestionnaireIds.PATIENT_REGISTRATION,
        QuestionnaireLaunchContext(user = user),
        QuestionnaireService(patients),
        sync,
        scope,
      )
    viewModel.uiState.first { it is QuestionnaireHostUiState.Ready }
    viewModel.onSubmit(
      QuestionnaireResponse(
        status = Enumeration(value = QuestionnaireResponse.QuestionnaireResponseStatus.Completed),
        item =
          listOf(
            QuestionnaireResponse.Item(
              linkId = FhirString(value = "given"),
              answer =
                listOf(
                  QuestionnaireResponse.Item.Answer(
                    value = Value.String(FhirString(value = "Chanthala"))
                  )
                ),
            )
          ),
      )
    )
    if (leaveRightAway) viewModel.viewModelScope.cancel()
    viewModel.uiState.first { it !is QuestionnaireHostUiState.Submitting }
    advanceUntilIdle()
    return viewModel
  }

  @Test
  fun submittingUploadsRightAway() = runTest {
    val uploaded = CompletableDeferred<Unit>()
    val sync = FakeSyncManager { SyncJobStatus.Succeeded().also { uploaded.complete(Unit) } }

    submitRegistration(sync, backgroundScope)

    uploaded.await()
    assertEquals(1, sync.syncNowCount)
  }

  @Test
  fun anOfflineUploadIsIgnored() = runTest {
    val attempted = CompletableDeferred<Unit>()
    val sync = FakeSyncManager {
      attempted.complete(Unit)
      error("Unable to resolve host")
    }

    val viewModel = submitRegistration(sync, backgroundScope)

    attempted.await()
    assertIs<QuestionnaireHostUiState.Submitted>(viewModel.uiState.value)
  }

  @Test
  fun leavingTheFormDoesNotCutTheSubmitShort() = runTest {
    submitRegistration(FakeSyncManager(), backgroundScope, leaveRightAway = true)

    assertTrue(
      patients.all("Patient").filterIsInstance<Patient>().any {
        it.name.firstOrNull()?.given?.firstOrNull()?.value == "Chanthala"
      }
    )
  }

  @Test
  fun aFailedSubmitKeepsTheFormAndSaysWhy() = runTest {
    val viewModel = submitRegistration(FakeSyncManager(), backgroundScope, user = null)

    val state = assertIs<QuestionnaireHostUiState.Ready>(viewModel.uiState.value)
    assertEquals("This questionnaire needs a signed-in user with a role.", state.error)
  }
}
