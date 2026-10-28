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

import dev.ohs.fhir.model.r4.DateTime
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.ServiceRequest
import dev.ohs.fhir.model.r4.Task
import dev.ohs.workflow.examples.util.displayName
import dev.ohs.workflow.examples.util.idOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

const val FOLLOW_UP_TASK = "iccm-follow-up"
private const val FOLLOW_UP_AFTER_DAYS = 3

enum class WorkStatus {
  Due,
  WaitingAtFacility,
  SeenAtFacility,
}

/** One row of a worklist: whose it is, what date matters, and where it stands. */
data class WorkItem(
  val id: String,
  val patientId: String,
  val patientName: String,
  val date: String,
  val status: WorkStatus,
  val detail: String? = null,
)

/** The CHW's open iCCM follow-up visits, soonest first. */
fun followUps(tasks: List<Task>, patients: Map<String, Patient>, practitionerId: String) =
  tasks
    .filter {
      it.owner.isTo("Practitioner/$practitionerId") &&
        it.status.value == Task.TaskStatus.Requested &&
        it.code?.coding?.any { coding -> coding.code?.value == FOLLOW_UP_TASK } == true
    }
    .mapNotNull { task ->
      val patientId = task.`for`.idOf("Patient") ?: return@mapNotNull null
      val due =
        task.restriction?.period?.end.date()
          ?: task.authoredOn.date()?.plus(FOLLOW_UP_AFTER_DAYS, DateTimeUnit.DAY)
      WorkItem(
        task.id!!,
        patientId,
        patients[patientId].displayName(),
        "$due",
        WorkStatus.Due,
        task.description?.value,
      )
    }
    .sortedBy { it.date }

/** Referrals the CHW sent, newest first, showing whether the facility has seen the child yet. */
fun referrals(
  requests: List<ServiceRequest>,
  patients: Map<String, Patient>,
  practitionerId: String,
) =
  requests
    .filter {
      it.intent.value == ServiceRequest.RequestIntent.Order &&
        it.requester.isTo("Practitioner/$practitionerId")
    }
    .mapNotNull { request ->
      val patientId = request.subject.idOf("Patient") ?: return@mapNotNull null
      val status =
        when (request.status.value) {
          ServiceRequest.RequestStatus.Active -> WorkStatus.WaitingAtFacility
          ServiceRequest.RequestStatus.Completed -> WorkStatus.SeenAtFacility
          else -> return@mapNotNull null
        }
      WorkItem(
        request.id!!,
        patientId,
        patients[patientId].displayName(),
        "${request.authoredOn.date()}",
        status,
        request.priority?.value?.code?.replaceFirstChar { it.uppercaseChar() },
      )
    }
    .sortedByDescending { it.date }

private fun Reference?.isTo(reference: String) = this?.reference?.value == reference

private fun DateTime?.date(): LocalDate? =
  when (val value = this?.value) {
    is FhirDateTime.Date -> value.date
    is FhirDateTime.DateTime -> value.dateTime.date
    else -> null
  }
