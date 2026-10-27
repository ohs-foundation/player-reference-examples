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

import dev.ohs.fhir.model.r4.Resource
import dev.ohs.player.medplum.data.datasource.patientProfileSearchResult
import dev.ohs.player.medplum.util.FhirJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The profile query used to read every Immunization in the database and filter in memory. These
 * tests pin the contract that replaced it: each section is fetched by the reference search
 * parameter that actually points back at the patient, and one child's record never contains another
 * child's rows.
 */
class PatientProfileScopeTest {

  // suspend, not runBlocking: this is commonTest, and kotlinx.coroutines.runBlocking does not exist
  // on Kotlin/JS or Wasm. Every caller is already inside runTest.
  private suspend fun repositoryWithTwoChildren(): InMemorySampleFhirRepository {
    val json = FhirJson.instance
    val repository = InMemorySampleFhirRepository()
    val resources =
      listOf(
        """{"resourceType":"Patient","id":"c1","name":[{"family":"Otieno","given":["Mercy"]}]}""",
        """{"resourceType":"Patient","id":"c2","name":[{"family":"Kiprop","given":["Fatuma"]}]}""",
        // Each of these hangs off the patient by a DIFFERENT search parameter; getting one wrong
        // silently empties that section rather than failing.
        """{"resourceType":"Immunization","id":"i1","status":"completed",
            "vaccineCode":{"text":"BCG"},"patient":{"reference":"Patient/c1"},
            "occurrenceDateTime":"2026-01-02T00:00:00Z"}""",
        """{"resourceType":"Immunization","id":"i2","status":"completed",
            "vaccineCode":{"text":"OPV 1"},"patient":{"reference":"Patient/c2"},
            "occurrenceDateTime":"2026-01-02T00:00:00Z"}""",
        """{"resourceType":"RelatedPerson","id":"r1","patient":{"reference":"Patient/c1"},
            "name":[{"family":"Otieno","given":["Esther"]}],
            "relationship":[{"coding":[{"code":"MTH","display":"mother"}]}]}""",
        """{"resourceType":"Observation","id":"o1","status":"final",
            "code":{"coding":[{"system":"http://loinc.org","code":"29463-7"}],"text":"Body weight"},
            "subject":{"reference":"Patient/c1"},"effectiveDateTime":"2026-02-01T00:00:00Z",
            "valueQuantity":{"value":7.2,"unit":"kg"}}""",
      )
    resources.forEach { repository.upsert(json.decodeFromString(Resource.serializer(), it)) }
    return repository
  }

  @Test
  fun profileIncludesEverySectionForTheRequestedChild() = runTest {
    val result = patientProfileSearchResult("c1", repositoryWithTwoChildren())

    assertNotNull(result)
    val included = result.revIncluded.orEmpty()
    // The caregiver is a RelatedPerson, not Patient.contact -- reading the wrong one is why the
    // caregiver section rendered empty against real Medplum data.
    assertTrue(included.keys.contains("RelatedPerson" to "patient"), "caregiver missing")
    assertTrue(included.keys.contains("Immunization" to "patient"), "immunizations missing")
    // Growth measurements hang off `subject`, not `patient`.
    assertTrue(included.keys.contains("Observation" to "subject"), "growth missing")
  }

  @Test
  fun profileExcludesAnotherChildsRecords() = runTest {
    val result = patientProfileSearchResult("c1", repositoryWithTwoChildren())

    assertNotNull(result)
    val immunizations = result.revIncluded.orEmpty()["Immunization" to "patient"].orEmpty()
    assertEquals(listOf("i1"), immunizations.map { it.id })
  }

  @Test
  fun unknownChildHasNoProfile() = runTest {
    assertEquals(null, patientProfileSearchResult("nope", repositoryWithTwoChildren()))
  }

  @Test
  fun searchNarrowsTheRegisterAndBlankReturnsEveryone() = runTest {
    val repository = repositoryWithTwoChildren()

    assertEquals(2, repository.searchPatients().size)
    assertEquals(2, repository.countPatients())
    assertEquals(listOf("c2"), repository.searchPatients("Fatuma").map { it.id })
  }
}
