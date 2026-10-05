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

import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.DateTime
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.HumanName
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.ServiceRequest
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Task
import dev.ohs.fhir.model.r4.Uri
import kotlin.test.Test
import kotlin.test.assertEquals

class ChwWorklistTest {
  private val amina =
    Patient(
      id = "child-1",
      name =
        listOf(
          HumanName(
            given = listOf(FhirString(value = "Amina")),
            family = FhirString(value = "Otieno"),
          )
        ),
    )
  private val patients = mapOf("child-1" to amina)

  private fun ref(value: String) = Reference(reference = FhirString(value = value))

  private fun on(date: String) = DateTime(value = FhirDateTime.fromString(date))

  private fun followUp(id: String, owner: String, status: Task.TaskStatus, authored: String) =
    Task(
      id = id,
      status = Enumeration(value = status),
      intent = Enumeration(value = Task.TaskIntent.Proposal),
      code =
        CodeableConcept(
          coding =
            listOf(
              Coding(
                system = Uri(value = "http://ohs.dev/fhir/CodeSystem/task-type"),
                code = Code(value = "iccm-follow-up"),
              )
            )
        ),
      `for` = ref("Patient/child-1"),
      owner = ref(owner),
      authoredOn = on(authored),
    )

  private fun referral(
    id: String,
    intent: ServiceRequest.RequestIntent,
    status: ServiceRequest.RequestStatus,
  ) =
    ServiceRequest(
      id = id,
      status = Enumeration(value = status),
      intent = Enumeration(value = intent),
      subject = ref("Patient/child-1"),
      requester = ref("Practitioner/p1"),
      authoredOn = on("2026-10-05"),
    )

  @Test
  fun followUpsAreTheChwsOpenTasksDueThreeDaysAfterTheVisit() {
    val items =
      followUps(
        listOf(
          followUp("t1", "Practitioner/p1", Task.TaskStatus.Requested, "2026-10-05"),
          followUp("t2", "Practitioner/p1", Task.TaskStatus.Completed, "2026-10-01"),
          followUp("t3", "Practitioner/p2", Task.TaskStatus.Requested, "2026-10-05"),
        ),
        patients,
        practitionerId = "p1",
      )

    assertEquals(
      listOf(WorkItem("t1", "child-1", "Amina Otieno", "2026-10-08", WorkStatus.Due)),
      items,
    )
  }

  @Test
  fun referralsShowWhetherTheFacilityHasSeenTheChild() {
    val items =
      referrals(
        listOf(
          referral(
            "sr-proposal",
            ServiceRequest.RequestIntent.Proposal,
            ServiceRequest.RequestStatus.Completed,
          ),
          referral(
            "sr-open",
            ServiceRequest.RequestIntent.Order,
            ServiceRequest.RequestStatus.Active,
          ),
          referral(
            "sr-done",
            ServiceRequest.RequestIntent.Order,
            ServiceRequest.RequestStatus.Completed,
          ),
        ),
        patients,
        practitionerId = "p1",
      )

    assertEquals(
      mapOf("sr-open" to WorkStatus.WaitingAtFacility, "sr-done" to WorkStatus.SeenAtFacility),
      items.associate { it.id to it.status },
    )
  }
}
