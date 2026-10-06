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
import dev.ohs.fhir.engine.sync.SyncJobStatus
import dev.ohs.fhir.model.r4.Encounter
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Task
import dev.ohs.workflow.examples.auth.UserContext
import dev.ohs.workflow.examples.data.repository.FhirRepository
import dev.ohs.workflow.examples.data.sync.SyncManager
import dev.ohs.workflow.examples.workflow.ProtocolService
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class QueueViewModel(
  private val context: UserContext,
  private val repository: FhirRepository,
  private val protocols: ProtocolService,
  private val syncManager: SyncManager,
  private val now: () -> Instant = { Clock.System.now() },
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
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

  private var refreshing = false

  private val _pulling = MutableStateFlow(false)
  /** True while a refresh the user pulled for is running; automatic refreshes stay silent. */
  val pulling: StateFlow<Boolean> = _pulling.asStateFlow()

  private val _updatedAt = MutableStateFlow<String?>(null)
  /** Local time of the last sync that brought the queue up to date, e.g. "09:14". */
  val updatedAt: StateFlow<String?> = _updatedAt.asStateFlow()

  /** Pulls check-ins from other devices; overlapping calls share the one in flight. */
  fun refresh(pulled: Boolean = false): Job {
    if (pulled) _pulling.value = true
    if (refreshing) return Job().apply { complete() }
    refreshing = true
    return viewModelScope.launch {
      try {
        if (syncManager.syncNow() is SyncJobStatus.Succeeded) {
          val local = now().toLocalDateTime(timeZone)
          _updatedAt.value =
            "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
        }
      } finally {
        refreshing = false
        _pulling.value = false
      }
    }
  }

  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  fun complete(taskId: String, outcome: String): Job =
    viewModelScope.launch {
      val task = repository.get("Task", taskId) as? Task ?: return@launch
      _error.value =
        try {
          protocols.completeConsult(task, outcome)
          null
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          e.message ?: "Could not complete the consult."
        }
    }
}
