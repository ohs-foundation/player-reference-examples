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
package dev.ohs.workflow.examples.feature.chw

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.ServiceRequest
import dev.ohs.fhir.model.r4.Task
import dev.ohs.workflow.examples.auth.UserContext
import dev.ohs.workflow.examples.data.repository.FhirRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ChwWorklistViewModel(
  private val context: UserContext,
  private val repository: FhirRepository,
) : ViewModel() {

  val followUps: StateFlow<List<WorkItem>?> =
    repository.revision
      .map {
        followUps(
          repository.all("Task").filterIsInstance<Task>(),
          patients(),
          context.practitionerId,
        )
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val referrals: StateFlow<List<WorkItem>?> =
    repository.revision
      .map {
        referrals(
          repository.all("ServiceRequest").filterIsInstance<ServiceRequest>(),
          patients(),
          context.practitionerId,
        )
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  private suspend fun patients(): Map<String, Patient> =
    repository.all("Patient").filterIsInstance<Patient>().associateBy { it.id.orEmpty() }
}
