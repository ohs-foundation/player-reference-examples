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
package dev.ohs.workflow.examples.feature.opd

import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.DateTime
import dev.ohs.fhir.model.r4.Encounter
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.HumanName
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Task
import dev.ohs.fhir.model.r4.Uri
import dev.ohs.workflow.examples.util.FhirJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.TimeZone

class ConsultQueueTest {
  private fun ref(value: String) = Reference(reference = FhirString(value = value))

  private fun patient(id: String, given: String) =
    Patient(id = id, name = listOf(HumanName(given = listOf(FhirString(value = given)))))

  private fun consult(
    id: String,
    patientId: String,
    priority: Task.RequestPriority,
    authored: String,
    owner: String = "Organization/o1",
    status: Task.TaskStatus = Task.TaskStatus.Requested,
    referral: Boolean = false,
  ) =
    Task(
      id = id,
      status = Enumeration(value = status),
      intent = Enumeration(value = Task.TaskIntent.Proposal),
      priority = Enumeration(value = priority),
      code =
        CodeableConcept(
          coding =
            listOf(
              Coding(
                system = Uri(value = "http://ohs.dev/fhir/CodeSystem/task-type"),
                code = Code(value = "opd-consult"),
              )
            )
        ),
      `for` = ref("Patient/$patientId"),
      owner = ref(owner),
      encounter = ref("Encounter/e-$id"),
      focus = if (referral) ref("ServiceRequest/sr-$id") else null,
      authoredOn = DateTime(value = FhirDateTime.fromString(authored)),
    )

  private fun encounter(id: String, reason: String) =
    Encounter(
      id = id,
      status = Enumeration(value = Encounter.EncounterStatus.Arrived),
      `class` = Coding(code = Code(value = "AMB")),
      reasonCode = listOf(CodeableConcept(text = FhirString(value = reason))),
    )

  private val spo2 =
    FhirJson.instance.decodeFromString(
      Observation.serializer(),
      """
        {"resourceType": "Observation", "id": "o1", "status": "final",
         "code": {"coding": [{"system": "http://loinc.org", "code": "59408-5", "display": "Oxygen saturation"}]},
         "encounter": {"reference": "Encounter/e-t2"},
         "valueQuantity": {"value": 88, "unit": "%"}}
      """,
    )

  @Test
  fun queueIsTheFacilitysOpenConsultsMostUrgentFirst() {
    val queue =
      consultQueue(
        tasks =
          listOf(
            consult("t1", "a", Task.RequestPriority.Routine, "2026-10-05T08:00:00Z"),
            consult("t2", "b", Task.RequestPriority.Stat, "2026-10-05T09:00:00Z", referral = true),
            consult("t3", "c", Task.RequestPriority.Urgent, "2026-10-05T08:30:00Z"),
            consult("t4", "a", Task.RequestPriority.Routine, "2026-10-05T07:00:00Z"),
            consult(
              "t5",
              "a",
              Task.RequestPriority.Stat,
              "2026-10-05T07:00:00Z",
              owner = "Organization/o2",
            ),
            consult(
              "t6",
              "a",
              Task.RequestPriority.Stat,
              "2026-10-05T07:00:00Z",
              status = Task.TaskStatus.Completed,
            ),
          ),
        patients = listOf(patient("a", "Grace"), patient("b", "Amina"), patient("c", "Peter")),
        encounters = listOf(encounter("e-t2", "Fast breathing")),
        observations = listOf(spo2),
        organizationId = "o1",
        timeZone = TimeZone.UTC,
      )

    assertEquals(listOf("t2", "t3", "t4", "t1"), queue.map { it.taskId })
    assertEquals(listOf(1, 2, 3, 4), queue.map { it.position })
    assertEquals(listOf("09:00", "08:30", "07:00", "08:00"), queue.map { it.arrivedAt })
    assertEquals(
      QueueItem(
        "t2",
        "b",
        "Amina",
        "Fast breathing",
        "stat",
        referred = true,
        vitals = "Oxygen saturation 88 %",
        position = 1,
        arrivedAt = "09:00",
      ),
      queue.first(),
    )
  }
}
