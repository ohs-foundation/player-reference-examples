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
package dev.ohs.player.medplum.data.repository

import dev.ohs.fhir.model.r4.ContactPoint
import dev.ohs.fhir.model.r4.Organization
import dev.ohs.fhir.model.r4.Practitioner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The signed-in practitioner and the facility they work at, as synced FHIR resources. */
data class PractitionerProfile(
  val name: String? = null,
  val email: String? = null,
  val practitionerId: String? = null,
  val facilityId: String? = null,
  val facilityName: String? = null,
  val facilityIdentifier: String? = null,
  val caseload: Int = 0,
)

/**
 * Reads the signed-in practitioner's own record.
 *
 * The identity is not guessed from the user's name or email — it comes from the OIDC `profile`
 * claim, which Medplum returns as `Practitioner/<id>`. The facility is the single [Organization] in
 * the local database: the AccessPolicy scopes Organization to the practitioner's own facility, so
 * whatever synced down IS where they work.
 */
class PractitionerRepository(private val fhirRepository: FhirRepository) {

  fun observeProfile(practitionerReference: String?): Flow<PractitionerProfile> =
    fhirRepository.revision.map { profile(practitionerReference) }

  suspend fun profile(practitionerReference: String?): PractitionerProfile {
    val practitionerId = practitionerReference?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    val practitioner =
      practitionerId?.let { fhirRepository.get("Practitioner", it) as? Practitioner }
    val facility = fhirRepository.all("Organization").filterIsInstance<Organization>().firstOrNull()

    return PractitionerProfile(
      name =
        practitioner?.name?.firstOrNull()?.let { humanName ->
          (humanName.given.mapNotNull { it.value } + listOfNotNull(humanName.family?.value))
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
        },
      email =
        practitioner
          ?.telecom
          ?.firstOrNull { it.system?.value == ContactPoint.ContactPointSystem.Email }
          ?.value
          ?.value,
      practitionerId = practitioner?.id ?: practitionerId,
      facilityId = facility?.id,
      facilityName = facility?.name?.value,
      facilityIdentifier = facility?.identifier?.firstOrNull()?.value?.value,
      caseload = fhirRepository.countPatients(),
    )
  }
}
