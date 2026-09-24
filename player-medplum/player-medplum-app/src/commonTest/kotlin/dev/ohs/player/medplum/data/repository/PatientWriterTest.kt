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

import dev.ohs.fhir.model.r4.Patient
import dev.ohs.player.medplum.util.FhirJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * These edits read the stored Patient, change one field and write the whole resource back. Upload
 * is a PUT, so anything the round-trip drops is DELETED on the server -- which makes "the fields we
 * did not touch are still there" the property that actually matters here, more than the field we
 * did touch.
 */
class PatientWriterTest {

  private val json = FhirJson.instance

  private fun child(): Patient =
    json.decodeFromString(
      Patient.serializer(),
      """
      {
        "resourceType": "Patient",
        "id": "child-1",
        "active": true,
        "identifier": [
          {"system": "https://medplum.player.ohs.dev/child-id", "value": "EIR-0001"}
        ],
        "name": [{"family": "Kamau", "given": ["Zawadi"]}],
        "gender": "female",
        "birthDate": "2026-07-01",
        "telecom": [{"system": "phone", "value": "+254720118934"}],
        "managingOrganization": {"reference": "Organization/facility-1"}
      }
      """,
    )

  private suspend fun writerOver(
    patient: Patient
  ): Pair<PatientWriter, InMemorySampleFhirRepository> {
    val repository = InMemorySampleFhirRepository()
    repository.upsert(patient)
    return PatientWriter(repository) to repository
  }

  private suspend fun stored(repository: InMemorySampleFhirRepository): Patient =
    repository.get("Patient", "child-1") as Patient

  @Test
  fun deactivatingFlipsActiveAndKeepsEverythingElse() = runTest {
    val (writer, repository) = writerOver(child())

    writer.setActive("child-1", false)

    val saved = stored(repository)
    assertEquals(false, saved.active?.value)
    assertEquals("EIR-0001", saved.identifier.firstOrNull()?.value?.value)
    assertEquals("Kamau", saved.name.firstOrNull()?.family?.value)
    assertEquals("+254720118934", saved.telecom.firstOrNull()?.value?.value)
    assertEquals("2026-07-01", saved.birthDate?.value.toString())
    assertEquals(
      "Organization/facility-1",
      saved.managingOrganization?.reference?.value,
      "losing the facility would orphan the child from its register",
    )
  }

  @Test
  fun reactivatingIsPossible() = runTest {
    val (writer, repository) = writerOver(child())

    writer.setActive("child-1", false)
    writer.setActive("child-1", true)

    assertEquals(true, stored(repository).active?.value)
  }

  @Test
  fun reportingDeceasedAlsoDeactivates() = runTest {
    // Not tidiness: an active deceased child keeps the forecaster generating overdue vaccines for
    // them, which is the worst thing this register could put in front of a health worker.
    val (writer, repository) = writerOver(child())

    writer.markDeceased("child-1", "2026-09-14")

    val saved = stored(repository)
    assertEquals(false, saved.active?.value)
    val deceased = saved.deceased
    assertTrue(deceased is Patient.Deceased.DateTime, "expected deceasedDateTime, got $deceased")
    assertEquals("2026-09-14", deceased.value.value.toString())
    assertEquals("Kamau", saved.name.firstOrNull()?.family?.value, "the record itself must survive")
  }

  @Test
  fun anUnparseableDateOfDeathIsRejectedRatherThanStored() = runTest {
    // The old JSON-splicing implementation wrote whatever string it was handed straight into
    // deceasedDateTime, so a malformed date became a malformed resource that only failed later, on
    // the server, during upload. Parsing up front turns that into a caller error here.
    val (writer, repository) = writerOver(child())

    val failure = runCatching { writer.markDeceased("child-1", "14/09/2026") }.exceptionOrNull()

    assertTrue(failure != null, "a malformed date must not reach the record")
    assertEquals(null, stored(repository).deceased, "and must leave the child unchanged")
  }

  @Test
  fun editingAChildThatIsNotThereFailsLoudly() = runTest {
    val (writer, _) = writerOver(child())

    val failure = runCatching { writer.setActive("no-such-child", false) }.exceptionOrNull()

    assertTrue(failure != null, "a silent no-op would look like a successful edit in the UI")
  }
}
