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
package dev.ohs.player.medplum.data.datasource

import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.player.client.model.SearchResult
import dev.ohs.player.medplum.data.repository.FhirRepository

/**
 * Clinical resource types shown on a child's profile, paired with the search parameter that points
 * each one back at the patient. Adding a section to the profile is an entry here plus a
 * ViewDefinition — no new query code.
 *
 * Every type here must also be downloaded by `SYNC_RESOURCE_PARAMS`, or the section renders from
 * whatever this device happened to write and stays empty everywhere else. `ProfileSyncCoverageTest`
 * enforces that pairing.
 */
internal val PROFILE_REV_INCLUDES =
  listOf(
    "RelatedPerson" to "patient",
    "Immunization" to "patient",
    "ImmunizationRecommendation" to "patient",
    "Observation" to "subject",
    "AdverseEvent" to "subject",
  )

/**
 * Patient list rows, newest activity first, optionally narrowed by [query].
 *
 * One indexed search returns the Patient resources themselves. It deliberately does not return ids
 * for the caller to re-fetch one by one: that reads every patient twice and is the shape that made
 * the register slow.
 */
suspend fun patientListSearchResults(
  repository: FhirRepository,
  query: String? = null,
): List<SearchResult<Resource>> =
  repository.searchPatients(query).map { patient -> SearchResult(resource = patient) }

/**
 * Patient profile: root = Patient, clinical resources in revIncluded. Mirrors a real `GET
 * /Patient/{id}/$everything` response, so every section extractor runs against this one result.
 *
 * Each include is an indexed reference lookup returning only this child's rows. The earlier version
 * read every Immunization and ImmunizationRecommendation in the database and filtered in memory,
 * which is what made opening a profile take seconds on a low-end device.
 */
suspend fun patientProfileSearchResult(
  patientId: String,
  repository: FhirRepository,
): SearchResult<Resource>? {
  val patient = repository.get("Patient", patientId) as? Patient ?: return null
  val reference = "Patient/$patientId"
  val revIncluded =
    PROFILE_REV_INCLUDES.mapNotNull { (resourceType, searchParam) ->
        repository
          .referencing(resourceType, searchParam, reference)
          .takeIf { it.isNotEmpty() }
          ?.let { (resourceType to searchParam) to it }
      }
      .toMap()

  return SearchResult(
    resource = patient,
    included = mapOf("patient" to listOf(patient)),
    revIncluded = revIncluded.ifEmpty { null },
  )
}
