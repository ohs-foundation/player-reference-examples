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

import dev.ohs.fhir.model.r4.Encounter
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Task
import dev.ohs.workflow.examples.util.displayName
import dev.ohs.workflow.examples.util.idOf

const val CONSULT_TASK = "opd-consult"
private val PRIORITY_ORDER = listOf("stat", "asap", "urgent", "routine")

/** One waiting patient in the clinician's queue. */
data class QueueItem(
  val taskId: String,
  val patientId: String,
  val patientName: String,
  val reason: String,
  val priority: String,
  val referred: Boolean,
  val vitals: String,
)

/** The facility's open consults, most urgent first, then in order of arrival. */
fun consultQueue(
  tasks: List<Task>,
  patients: List<Patient>,
  encounters: List<Encounter>,
  observations: List<Observation>,
  organizationId: String,
): List<QueueItem> {
  val patientsById = patients.associateBy { it.id }
  val encountersById = encounters.associateBy { it.id }
  return tasks
    .filter {
      it.owner.idOf("Organization") == organizationId &&
        it.status.value == Task.TaskStatus.Requested &&
        it.code?.coding?.any { coding -> coding.code?.value == CONSULT_TASK } == true
    }
    .sortedWith(
      compareBy(
        { PRIORITY_ORDER.indexOf(it.priority?.value?.code ?: "routine") },
        { it.authoredOn?.value?.toString() },
      )
    )
    .mapNotNull { task ->
      val patientId = task.`for`.idOf("Patient") ?: return@mapNotNull null
      val encounterId = task.encounter.idOf("Encounter")
      QueueItem(
        taskId = task.id!!,
        patientId = patientId,
        patientName = patientsById[patientId].displayName(),
        reason = encountersById[encounterId]?.reasonCode?.firstOrNull()?.text?.value.orEmpty(),
        priority = task.priority?.value?.code ?: "routine",
        referred = task.focus != null,
        vitals =
          observations
            .filter { encounterId != null && it.encounter.idOf("Encounter") == encounterId }
            .mapNotNull { it.summary() }
            .joinToString(", "),
      )
    }
}

private fun Observation.summary(): String? {
  val quantity = (value as? Observation.Value.Quantity)?.value ?: return null
  val name = code.coding.firstOrNull()?.display?.value ?: code.text?.value ?: return null
  return "$name ${quantity.value?.value} ${quantity.unit?.value.orEmpty()}".trim()
}
