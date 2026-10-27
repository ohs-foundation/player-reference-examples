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
package dev.ohs.player.medplum.feature.patient.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ohs.player.generated.state.PatientImmunizationRecommendationState
import dev.ohs.player.medplum.data.repository.ImmunizationWriter
import dev.ohs.player.medplum.data.repository.PatientRepository
import dev.ohs.player.medplum.data.repository.PatientWriter
import dev.ohs.player.medplum.data.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PatientProfileViewModel(
  private val patientId: String,
  patientRepository: PatientRepository,
  private val immunizationWriter: ImmunizationWriter,
  private val patientWriter: PatientWriter,
  private val syncManager: SyncManager,
) : ViewModel() {
  private val _uiState = MutableStateFlow<ProfileUiState?>(null)
  val uiState: StateFlow<ProfileUiState?> = _uiState.asStateFlow()

  /**
   * Doses recorded in this session but not yet reflected in the forecast.
   *
   * The forecast is recomputed by a bot on the SERVER, so a dose recorded here does not change the
   * local ImmunizationRecommendation until the next download sync. Without this the row would sit
   * there still saying "overdue" after being tapped, which reads as a failure. The set is discarded
   * once the refreshed forecast arrives and drops the row on its own.
   */
  private val _justAdministered = MutableStateFlow<Set<String>>(emptySet())
  val justAdministered: StateFlow<Set<String>> = _justAdministered.asStateFlow()

  init {
    viewModelScope.launch {
      patientRepository.observePatientProfile(patientId).collect { _uiState.value = it }
    }
  }

  /**
   * One-shot message for the snackbar -- "Marked inactive", or a failure.
   *
   * These actions produce no visible change until the profile re-queries, and a death or a status
   * change is exactly the kind of edit a health worker needs confirmed rather than inferred.
   */
  private val _actionResult = MutableStateFlow<ProfileActionResult?>(null)
  val actionResult: StateFlow<ProfileActionResult?> = _actionResult.asStateFlow()

  fun clearActionResult() {
    _actionResult.value = null
  }

  fun setActive(active: Boolean) {
    runAction(
      if (active) ProfileActionResult.MarkedActive else ProfileActionResult.MarkedInactive
    ) {
      patientWriter.setActive(patientId, active)
    }
  }

  fun reportDeceased(date: String) {
    runAction(ProfileActionResult.RecordedDeceased) { patientWriter.markDeceased(patientId, date) }
  }

  /**
   * Writes locally, reports, then pushes. The write is what matters: if the sync fails the change
   * is still queued in the engine and goes up with the next one, so it is not reported as a
   * failure.
   */
  private fun runAction(success: ProfileActionResult, write: suspend () -> Unit) {
    viewModelScope.launch {
      runCatching { write() }
        .onSuccess {
          _actionResult.value = success
          runCatching { syncManager.syncNow() }
        }
        .onFailure { _actionResult.value = ProfileActionResult.Failed }
    }
  }

  fun administer(dose: PatientImmunizationRecommendationState) {
    val key = doseKey(dose)
    if (key in _justAdministered.value) {
      return
    }
    _justAdministered.value = _justAdministered.value + key
    viewModelScope.launch {
      immunizationWriter.recordAdministered(patientId, dose)
      // Push it up, then pull the regenerated forecast back down. Best effort: if the sync
      // fails the Immunization is still queued locally and goes up on the next one.
      syncManager.syncNow()
    }
  }

  /** What just happened, for the snackbar. */
  enum class ProfileActionResult {
    MarkedActive,
    MarkedInactive,
    RecordedDeceased,
    Failed,
  }

  companion object {
    fun doseKey(dose: PatientImmunizationRecommendationState): String =
      "${dose.series.orEmpty()}#${dose.doseNumber ?: 0}"
  }
}
