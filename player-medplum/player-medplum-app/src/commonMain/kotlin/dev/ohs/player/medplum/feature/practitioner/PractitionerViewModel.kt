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
package dev.ohs.player.medplum.feature.practitioner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ohs.player.medplum.auth.SessionStore
import dev.ohs.player.medplum.data.repository.PractitionerProfile
import dev.ohs.player.medplum.data.repository.PractitionerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class PractitionerViewModel(
  private val sessionStore: SessionStore,
  private val practitionerRepository: PractitionerRepository,
) : ViewModel() {

  private val _profile = MutableStateFlow<PractitionerProfile?>(null)
  val profile: StateFlow<PractitionerProfile?> = _profile.asStateFlow()

  /** Username comes straight from the token; it has no FHIR resource to live on. */
  val username: String = sessionStore.session.value?.user?.username.orEmpty()

  init {
    val reference = sessionStore.session.value?.user?.profileReference
    viewModelScope.launch {
      practitionerRepository.observeProfile(reference).collect { _profile.value = it }
    }
  }
}
