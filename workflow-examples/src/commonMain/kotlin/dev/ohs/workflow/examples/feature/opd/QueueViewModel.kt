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
package dev.ohs.workflow.examples.feature.opd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ohs.fhir.model.r4.Encounter
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Task
import dev.ohs.workflow.examples.auth.UserContext
import dev.ohs.workflow.examples.data.repository.FhirRepository
import dev.ohs.workflow.examples.workflow.ProtocolService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QueueViewModel(
  private val context: UserContext,
  private val repository: FhirRepository,
  private val protocols: ProtocolService,
) : ViewModel() {

  val queue: StateFlow<List<QueueItem>?> =
    repository.revision
      .map {
        consultQueue(
          tasks = repository.all("Task").filterIsInstance<Task>(),
          patients = repository.all("Patient").filterIsInstance<Patient>(),
          encounters = repository.all("Encounter").filterIsInstance<Encounter>(),
          observations = repository.all("Observation").filterIsInstance<Observation>(),
          organizationId = context.organizationId,
        )
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  fun complete(taskId: String, outcome: String): Job =
    viewModelScope.launch {
      val task = repository.get("Task", taskId) as? Task ?: return@launch
      protocols.completeConsult(task, outcome)
    }
}
