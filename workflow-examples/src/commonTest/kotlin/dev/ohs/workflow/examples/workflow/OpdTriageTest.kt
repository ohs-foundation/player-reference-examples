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
package dev.ohs.workflow.examples.workflow

import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.ServiceRequest
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Task
import dev.ohs.fhir.workflow.FhirOperator
import dev.ohs.workflow.examples.util.FhirJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class OpdTriageTest {
  private val patient = Patient(id = "patient-1")

  private suspend fun priorityFor(
    vitals: List<Observation>,
    referrals: List<ServiceRequest>,
  ): String? {
    val carePlan =
      FhirOperator(InMemoryWorkflowRepository(), resolver = BundledProtocols.load())
        .generateCarePlan(
          planDefinitionCanonical = "http://ohs.dev/fhir/PlanDefinition/opd-triage",
          subject = patient,
          variables = mapOf("vitals" to collection(vitals), "referrals" to collection(referrals)),
          today = LocalDate(2026, 10, 5),
        )
    return carePlan.contained.filterIsInstance<Task>().single().priority?.value?.code
  }

  private fun collection(resources: List<Resource>) =
    Bundle(
      type = Enumeration(value = Bundle.BundleType.Collection),
      entry = resources.map { Bundle.Entry(resource = it) },
    )

  private fun spo2(percent: Int) =
    FhirJson.instance.decodeFromString(
      Observation.serializer(),
      """
        {"resourceType": "Observation", "status": "final",
         "code": {"coding": [{"system": "http://loinc.org", "code": "59408-5"}]},
         "valueQuantity": {"value": $percent, "unit": "%"}}
      """,
    )

  private fun referral(priority: ServiceRequest.RequestPriority) =
    ServiceRequest(
      status = Enumeration(value = ServiceRequest.RequestStatus.Active),
      intent = Enumeration(value = ServiceRequest.RequestIntent.Order),
      priority = Enumeration(value = priority),
      subject = Reference(reference = FhirString(value = "Patient/patient-1")),
    )

  @Test
  fun lowOxygenIsSeenFirst() = runTest {
    assertEquals("stat", priorityFor(listOf(spo2(88)), emptyList()))
  }

  @Test
  fun urgentReferralIsSeenFirst() = runTest {
    assertEquals(
      "stat",
      priorityFor(listOf(spo2(97)), listOf(referral(ServiceRequest.RequestPriority.Urgent))),
    )
  }

  @Test
  fun routineReferralIsUrgent() = runTest {
    assertEquals(
      "urgent",
      priorityFor(listOf(spo2(97)), listOf(referral(ServiceRequest.RequestPriority.Routine))),
    )
  }

  @Test
  fun walkInIsRoutine() = runTest {
    assertEquals("routine", priorityFor(listOf(spo2(97)), emptyList()))
  }

  @Test
  fun missingVitalsIsRoutine() = runTest {
    assertEquals("routine", priorityFor(emptyList(), emptyList()))
  }
}
