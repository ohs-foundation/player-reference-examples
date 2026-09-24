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
package dev.ohs.player.medplum.feature.questionnaire

import dev.ohs.fhir.engine.sync.SyncJobStatus
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.player.medplum.auth.PendingAuth
import dev.ohs.player.medplum.auth.Session
import dev.ohs.player.medplum.auth.SessionStore
import dev.ohs.player.medplum.data.repository.InMemorySampleFhirRepository
import dev.ohs.player.medplum.data.repository.PractitionerRepository
import dev.ohs.player.medplum.data.sync.FakeSyncManager
import dev.ohs.player.medplum.util.FhirJson
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * A record that only ever reaches the local database is invisible to the facility until the next
 * periodic sync — up to 15 minutes later, and never at all if the app is closed first. Registering
 * a child is exactly the moment a health worker expects the record to have gone somewhere, so
 * submitting a form has to push immediately, the way administering a dose already does.
 */
/** Signed out: no practitioner, no facility. Enough for what these tests assert. */
private class FakeSessionStore : SessionStore {
  override val session: StateFlow<Session?> = MutableStateFlow(null)

  override suspend fun load(): Session? = null

  override suspend fun save(session: Session) = Unit

  override suspend fun clear() = Unit

  override suspend fun savePending(pending: PendingAuth) = Unit

  override suspend fun takePending(): PendingAuth? = null
}

class QuestionnaireHostViewModelTest {

  private val json = FhirJson.instance

  /**
   * `viewModelScope` dispatches on `Dispatchers.Main`, which is NOT `runTest`'s scheduler unless it
   * is substituted here — without this, `advanceUntilIdle()` returns while the ViewModel's
   * coroutines are still pending and every assertion reads the initial state.
   */
  private val testScope = TestScope()

  @BeforeTest
  fun installTestMainDispatcher() {
    Dispatchers.setMain(StandardTestDispatcher(testScope.testScheduler))
  }

  @AfterTest
  fun restoreMainDispatcher() {
    Dispatchers.resetMain()
  }

  private fun viewModel(
    questionnaireId: String,
    syncManager: FakeSyncManager,
  ): QuestionnaireHostViewModel {
    val repository = InMemorySampleFhirRepository()
    return QuestionnaireHostViewModel(
      questionnaireId = questionnaireId,
      launchContext = QuestionnaireLaunchContext(patientId = "child-1"),
      questionnaireService = QuestionnaireService(repository),
      syncManager = syncManager,
      sessionStore = FakeSessionStore(),
      practitionerRepository = PractitionerRepository(repository),
    )
  }

  private fun growthResponse(): QuestionnaireResponse =
    json.decodeFromString(
      QuestionnaireResponse.serializer(),
      """
      {
        "resourceType": "QuestionnaireResponse",
        "status": "completed",
        "item": [
          {"linkId": "patient-id", "answer": [{"valueString": "child-1"}]},
          {"linkId": "growth-date", "answer": [{"valueDateTime": "2026-09-21T10:00:00Z"}]},
          {"linkId": "weight", "answer": [{"valueDecimal": 12.4}]}
        ]
      }
      """,
    )

  @Test
  fun onSubmit_whenExtractionSucceeds_pushesToTheServer() =
    testScope.runTest {
      val syncManager = FakeSyncManager()
      val viewModel = viewModel(QuestionnaireIds.GROWTH_MONITORING, syncManager)
      advanceUntilIdle()

      viewModel.onSubmit(growthResponse())
      advanceUntilIdle()

      assertIs<QuestionnaireHostUiState.Submitted>(viewModel.uiState.value)
      assertEquals(1, syncManager.syncNowCount)
    }

  /**
   * The resources are already committed locally, so a failed push is not a failed submission —
   * showing an error here would tell the health worker to re-enter a child who is already saved.
   */
  @Test
  fun onSubmit_whenThePushFails_stillReportsSuccess() =
    testScope.runTest {
      val syncManager = FakeSyncManager { SyncJobStatus.Failed() }
      val viewModel = viewModel(QuestionnaireIds.GROWTH_MONITORING, syncManager)
      advanceUntilIdle()

      viewModel.onSubmit(growthResponse())
      advanceUntilIdle()

      assertIs<QuestionnaireHostUiState.Submitted>(viewModel.uiState.value)
      assertEquals(1, syncManager.syncNowCount)
    }

  @Test
  fun onSubmit_whenThePushThrows_stillReportsSuccess() =
    testScope.runTest {
      val syncManager = FakeSyncManager { throw RuntimeException("network down") }
      val viewModel = viewModel(QuestionnaireIds.GROWTH_MONITORING, syncManager)
      advanceUntilIdle()

      viewModel.onSubmit(growthResponse())
      advanceUntilIdle()

      assertIs<QuestionnaireHostUiState.Submitted>(viewModel.uiState.value)
      assertEquals(1, syncManager.syncNowCount)
    }
}
