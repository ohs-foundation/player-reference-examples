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
package dev.ohs.workflow.examples.data.repository

import dev.ohs.fhir.model.r4.Resource
import dev.ohs.workflow.examples.util.FhirJson
import dev.ohs.workflow.examples.util.calendarDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class PatientCareRecordTest {
  private val repository = InMemorySampleFhirRepository()
  private val patients = PatientRepository(repository)

  private suspend fun store(vararg json: String) =
    json.forEach {
      repository.upsert(FhirJson.instance.decodeFromString(Resource.serializer(), it))
    }

  private suspend fun alfie() =
    store(
      """{"resourceType":"Patient","id":"alfie","name":[{"given":["Alfie"],"family":"Sintamei"}]}""",
      """{"resourceType":"ServiceRequest","id":"old","status":"completed","intent":"order","priority":"urgent",
         "code":{"coding":[{"system":"http://snomed.info/sct","code":"3457005"}]},
         "subject":{"reference":"Patient/alfie"},"authoredOn":"2026-09-01T08:00:00Z"}""",
      """{"resourceType":"ServiceRequest","id":"new","status":"active","intent":"order","priority":"urgent",
         "code":{"coding":[{"system":"http://snomed.info/sct","code":"3457005"}]},
         "subject":{"reference":"Patient/alfie"},"authoredOn":"2026-10-05T08:00:00Z"}""",
      """{"resourceType":"ServiceRequest","id":"proposal","status":"completed","intent":"proposal",
         "code":{"coding":[{"system":"http://snomed.info/sct","code":"3457005"}]},
         "subject":{"reference":"Patient/alfie"},"authoredOn":"2026-10-05T08:00:00Z"}""",
      """{"resourceType":"MedicationRequest","id":"amox","status":"active","intent":"proposal",
         "medicationCodeableConcept":{"text":"Amoxicillin 250 mg dispersible"},
         "subject":{"reference":"Patient/alfie"},"authoredOn":"2026-09-01T08:00:00Z",
         "dosageInstruction":[{"text":"Twice daily for 5 days"}]}""",
      """{"resourceType":"Task","id":"fu","status":"requested","intent":"proposal",
         "code":{"coding":[{"system":"http://ohs.dev/fhir/CodeSystem/task-type","code":"iccm-follow-up"}]},
         "description":"Follow up after facility visit: Discharged","for":{"reference":"Patient/alfie"},
         "focus":{"reference":"ServiceRequest/old"},"restriction":{"period":{"end":"2026-09-03"}}}""",
      """{"resourceType":"Procedure","id":"proc","status":"completed","subject":{"reference":"Patient/alfie"},
         "basedOn":[{"reference":"ServiceRequest/old"}],"outcome":{"text":"Discharged"}}""",
      """{"resourceType":"Encounter","id":"enc","status":"finished","class":{"code":"AMB"},
         "subject":{"reference":"Patient/alfie"},"reasonCode":[{"text":"Fast breathing"}],
         "period":{"start":"2026-09-02T09:00:00Z"}}""",
      """{"resourceType":"Observation","id":"spo2","status":"final",
         "code":{"coding":[{"system":"http://loinc.org","code":"59408-5","display":"Oxygen saturation"}]},
         "subject":{"reference":"Patient/alfie"},"effectiveDateTime":"2026-09-02T09:05:00Z",
         "valueQuantity":{"value":95,"unit":"%"}}""",
    )

  @Test
  fun listRowCarriesTheLatestReferralAndOpenFollowUp() = runTest {
    alfie()

    val row = patients.getPatients().single()

    assertEquals("new" to "active", row.referralId to row.referralStatus)
    assertEquals("requested" to "ServiceRequest/old", row.followUpStatus to row.followUpFocus)
  }

  @Test
  fun profileShowsEveryCareActivity() = runTest {
    alfie()

    val profile = patients.getPatientProfile("alfie")

    assertEquals(
      listOf("2026-10-05", "2026-09-01"),
      profile.referrals.map { it.referredOn.calendarDate() },
    )
    assertEquals(listOf("Discharged"), profile.outcomes.map { it.outcomeText })
    assertEquals(listOf("Amoxicillin 250 mg dispersible"), profile.treatments.map { it.medicine })
    assertEquals(listOf("2026-09-03"), profile.followUps.map { it.dueOn.calendarDate() })
    assertEquals(listOf("Fast breathing"), profile.visits.map { it.visitReason })
    assertEquals(
      listOf("Oxygen saturation 95 %"),
      profile.vitals.map { "${it.vitalName} ${it.vitalValue} ${it.vitalUnit}" },
    )
  }

  @Test
  fun theRegisterReadsEachTableOncePerRefresh() = runTest {
    store(
      """{"resourceType":"Patient","id":"a"}""",
      """{"resourceType":"Patient","id":"b"}""",
      """{"resourceType":"Patient","id":"c"}""",
    )
    val reads = mutableMapOf<String, Int>()
    val counting =
      object : FhirRepository by repository {
        override suspend fun all(resourceType: String): List<Resource> {
          reads[resourceType] = (reads[resourceType] ?: 0) + 1
          return repository.all(resourceType)
        }
      }

    assertEquals(3, PatientRepository(counting).getPatients().size)
    assertEquals(mapOf("Patient" to 1, "ServiceRequest" to 1, "Task" to 1), reads.toMap())
  }
}
