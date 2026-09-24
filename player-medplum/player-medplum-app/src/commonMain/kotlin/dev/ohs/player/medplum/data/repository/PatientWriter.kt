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

import dev.ohs.fhir.model.r4.Boolean as FhirBoolean
import dev.ohs.fhir.model.r4.DateTime as FhirDateTimeElement
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.Patient

/**
 * Edits to an existing child's demographic record: register status, and death.
 *
 * Both touch only fields that exist in base R4 -- `Patient.active` and `Patient.deceasedDateTime`.
 * A fuller death report would also capture the PLACE of death; R4 has nowhere on Patient to put it
 * and recording it would mean either an extension or an invented Observation code, so it is not
 * collected at all rather than stored somewhere a standard R4 consumer would never look.
 *
 * Both read the stored Patient and return a [Patient.copy], which preserves every other field --
 * identifiers, names, telecom, the facility account stamped by the server. That matters because the
 * upload is a PUT: anything dropped here is deleted on the server. Rebuilding a Patient from the
 * summary state the UI holds would do exactly that.
 */
class PatientWriter(private val fhirRepository: FhirRepository) {

  /** Flips `Patient.active`. Inactive is how this register retires a record; nothing is deleted. */
  suspend fun setActive(patientId: String, active: Boolean) {
    val patient = load(patientId)
    fhirRepository.upsert(patient.copy(active = FhirBoolean(value = active)))
  }

  /**
   * Records a death: `deceasedDateTime` plus `active = false`.
   *
   * Deactivating is not merely tidy -- it takes the child off the due list. Leaving them active
   * would keep the forecaster generating overdue vaccines for a child who has died, which is the
   * single worst thing this register could show a health worker.
   *
   * [deceasedDateTime] must be a FHIR date or dateTime with no fractional seconds; see
   * [ImmunizationWriter.isoSeconds].
   */
  suspend fun markDeceased(patientId: String, deceasedDateTime: String) {
    val parsed =
      FhirDateTime.fromString(deceasedDateTime)
        ?: error("'$deceasedDateTime' is not a FHIR date or dateTime")
    val patient = load(patientId)
    fhirRepository.upsert(
      patient.copy(
        deceased = Patient.Deceased.DateTime(FhirDateTimeElement(value = parsed)),
        active = FhirBoolean(value = false),
      )
    )
  }

  private suspend fun load(patientId: String): Patient =
    fhirRepository.get("Patient", patientId) as? Patient
      ?: error("Patient/$patientId is not in the local database")
}
