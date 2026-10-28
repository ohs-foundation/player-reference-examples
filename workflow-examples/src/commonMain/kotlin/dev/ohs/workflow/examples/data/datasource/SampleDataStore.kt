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
package dev.ohs.workflow.examples.data.datasource

import dev.ohs.fhir.model.r4.Encounter
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Procedure
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.ServiceRequest
import dev.ohs.fhir.model.r4.Task
import dev.ohs.player.client.model.SearchResult
import dev.ohs.workflow.examples.data.repository.FhirRepository
import dev.ohs.workflow.examples.feature.chw.FOLLOW_UP_TASK
import dev.ohs.workflow.examples.util.idOf
import dev.ohs.workflow.examples.workflow.REFERRAL_CODE

/**
 * Patient list rows: each Patient plus its referral orders (newest first) and follow-up tasks (open
 * first). The row's joins read the first of each, mirroring a search with `_revinclude` sorted by
 * the server. Each table is read once and grouped by patient.
 */
suspend fun patientSummarySearchResults(repository: FhirRepository): List<SearchResult<Resource>> {
  val referrals = referralOrdersByPatient(repository)
  val followUps = followUpsByPatient(repository)
  return repository.all("Patient").filterIsInstance<Patient>().map {
    summary(it, referrals[it.id].orEmpty(), followUps[it.id].orEmpty())
  }
}

/** One patient's list row; see [patientSummarySearchResults]. */
suspend fun patientSummarySearchResult(
  patientId: String,
  repository: FhirRepository,
): SearchResult<Resource>? {
  val patient = repository.get("Patient", patientId) as? Patient ?: return null
  return summary(
    patient,
    referralOrdersByPatient(repository)[patientId].orEmpty(),
    followUpsByPatient(repository)[patientId].orEmpty(),
  )
}

private fun summary(patient: Patient, referrals: List<ServiceRequest>, followUps: List<Task>) =
  SearchResult<Resource>(
    resource = patient,
    revIncluded =
      mapOf(
        ("ServiceRequest" to "subject") to
          referrals.sortedByDescending { it.authoredOn?.value?.toString() },
        ("Task" to "subject") to
          followUps.sortedWith(
            compareBy<Task> { it.status.value != Task.TaskStatus.Requested }
              .thenByDescending { it.authoredOn?.value?.toString() }
          ),
      ),
  )

private suspend fun referralOrdersByPatient(repository: FhirRepository) =
  repository
    .all("ServiceRequest")
    .filterIsInstance<ServiceRequest>()
    .filter { it.isReferralOrder() }
    .groupBy { it.subject.idOf("Patient") }

private suspend fun followUpsByPatient(repository: FhirRepository) =
  repository
    .all("Task")
    .filterIsInstance<Task>()
    .filter { it.isFollowUp() }
    .groupBy { it.`for`.idOf("Patient") }

/** Patient profile: root = Patient, with every care resource that references the patient. */
suspend fun patientProfileSearchResult(
  patientId: String,
  repository: FhirRepository,
): SearchResult<Resource>? {
  val patient = repository.get("Patient", patientId) as? Patient ?: return null
  suspend fun revIncluded(type: String, subject: (Resource) -> Reference?) =
    (type to "subject") to
      repository.all(type).filter { subject(it).idOf("Patient") == patientId }.newestFirst()
  return SearchResult(
    resource = patient,
    revIncluded =
      mapOf(
        revIncluded("ServiceRequest") { (it as ServiceRequest).subject },
        revIncluded("Procedure") { (it as Procedure).subject },
        revIncluded("MedicationRequest") { (it as MedicationRequest).subject },
        revIncluded("Task") { (it as Task).`for` },
        revIncluded("Encounter") { (it as Encounter).subject },
        revIncluded("Observation") { (it as Observation).subject },
      ),
  )
}

private fun ServiceRequest.isReferralOrder() =
  intent.value == ServiceRequest.RequestIntent.Order &&
    code?.coding?.any { it.code?.value == REFERRAL_CODE } == true

private fun Task.isFollowUp() = code?.coding?.any { it.code?.value == FOLLOW_UP_TASK } == true

private fun List<Resource>.newestFirst() = sortedByDescending {
  when (it) {
    is ServiceRequest -> it.authoredOn?.value
    is MedicationRequest -> it.authoredOn?.value
    is Task -> it.authoredOn?.value
    is Encounter -> it.period?.start?.value
    is Observation -> (it.effective as? Observation.Effective.DateTime)?.value?.value
    else -> null
  }?.toString() ?: it.meta?.lastUpdated?.value?.toString()
}
