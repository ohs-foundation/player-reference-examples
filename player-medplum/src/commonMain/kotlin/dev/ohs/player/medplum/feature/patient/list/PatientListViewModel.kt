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
package dev.ohs.player.medplum.feature.patient.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ohs.player.generated.state.PatientSummaryState
import dev.ohs.player.medplum.data.repository.PatientRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PatientListViewModel(private val patientRepository: PatientRepository) : ViewModel() {
  private val _patients = MutableStateFlow<List<PatientSummaryState>?>(null)
  val patients: StateFlow<List<PatientSummaryState>?> = _patients.asStateFlow()

  private val _query = MutableStateFlow("")
  val query: StateFlow<String> = _query.asStateFlow()

  init {
    viewModelScope.launch {
      _query
        // Re-querying on every keystroke would run a search per character. A short pause is
        // imperceptible while typing and cuts the work to roughly one query per word.
        .debounce(SEARCH_DEBOUNCE_MS)
        .flatMapLatest { patientRepository.observePatients(it) }
        .collect { _patients.value = it }
    }
  }

  fun onQueryChange(value: String) {
    _query.value = value
  }

  private companion object {
    const val SEARCH_DEBOUNCE_MS = 250L
  }
}
