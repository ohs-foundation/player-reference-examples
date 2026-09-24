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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.player.medplum.auth.SessionStore
import dev.ohs.player.medplum.data.repository.PractitionerRepository
import dev.ohs.player.medplum.data.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Screen-level state holder for hosting a single questionnaire. All FHIR work is delegated to
 * [QuestionnaireService].
 */
internal class QuestionnaireHostViewModel(
  private val questionnaireId: String,
  private val launchContext: QuestionnaireLaunchContext,
  private val questionnaireService: QuestionnaireService,
  private val syncManager: SyncManager,
  private val sessionStore: SessionStore,
  private val practitionerRepository: PractitionerRepository,
) : ViewModel() {

  private val _uiState =
    MutableStateFlow<QuestionnaireHostUiState>(QuestionnaireHostUiState.Loading)
  val uiState: StateFlow<QuestionnaireHostUiState> = _uiState.asStateFlow()

  /**
   * The questionnaire as PREPARED for this launch (placeholders substituted), which is what both
   * the form and the extraction must run against.
   */
  private var preparedQuestionnaireJson: String? = null

  init {
    load()
  }

  fun load() {
    _uiState.value = QuestionnaireHostUiState.Loading
    viewModelScope.launch {
      runCatching {
          val questionnaire = questionnaireService.getQuestionnaire(questionnaireId)
          val prepared =
            questionnaireService.prepareForLaunch(questionnaire, launchContext.withSignedInUser())
          preparedQuestionnaireJson = prepared
          prepared to questionnaire.title?.value
        }
        .onSuccess { (json, title) -> _uiState.value = QuestionnaireHostUiState.Ready(json, title) }
        .onFailure { throwable ->
          _uiState.value =
            QuestionnaireHostUiState.Error(throwable.message ?: "Failed to load questionnaire.")
        }
    }
  }

  fun onSubmit(response: QuestionnaireResponse) {
    val questionnaire = preparedQuestionnaireJson ?: return
    val current = _uiState.value as? QuestionnaireHostUiState.Ready ?: return

    _uiState.value = QuestionnaireHostUiState.Submitting(current.questionnaireJson, current.title)

    viewModelScope.launch {
      runCatching { questionnaireService.submit(questionnaire, response) }
        .onSuccess { result ->
          _uiState.value = QuestionnaireHostUiState.Submitted(result)
          pushToServer()
        }
        .onFailure { throwable ->
          _uiState.value =
            QuestionnaireHostUiState.Error(throwable.message ?: "Failed to submit questionnaire.")
        }
    }
  }

  /**
   * Sends what was just extracted up to Medplum, without making the health worker wait for it.
   *
   * The success state is already shown before this runs, so a slow or absent network never blocks
   * the form from closing. Failure is deliberately swallowed: the resources are already committed
   * to the local engine and stay queued, so the 15-minute periodic sync carries them up later.
   *
   * Cancellation when this screen closes is safe on Android, where [SyncManager.syncNow] only
   * *observes* a WorkManager job it enqueued — the upload itself outlives this ViewModel. On the
   * in-process platforms (JVM/web) sync runs in a scheduler that likewise isn't tied to this scope.
   */
  private suspend fun pushToServer() {
    runCatching { syncManager.syncNow() }
  }

  /**
   * Adds who is registering and where they work.
   *
   * These are session facts rather than route arguments, so the screen never passes them. The
   * practitioner comes from the OIDC `profile` claim — never guessed from a display name — and the
   * facility is the single Organization the AccessPolicy allowed to sync down, which is by
   * construction the one the signed-in practitioner belongs to.
   *
   * Either can legitimately be absent: a session restored before the first sync has no Organization
   * yet. That is not an error here. [QuestionnaireService] drops whatever depended on the missing
   * value, so the child is still registered — just without that reference.
   */
  private suspend fun QuestionnaireLaunchContext.withSignedInUser(): QuestionnaireLaunchContext {
    val profile = practitionerRepository.profile(sessionStore.session.value?.user?.profileReference)
    return copy(practitionerId = profile.practitionerId, organizationId = profile.facilityId)
  }
}
