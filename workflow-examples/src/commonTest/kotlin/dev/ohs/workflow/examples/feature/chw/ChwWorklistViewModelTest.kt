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
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Task
import dev.ohs.workflow.examples.auth.AppRole
import dev.ohs.workflow.examples.auth.UserContext
import dev.ohs.workflow.examples.data.repository.InMemorySampleFhirRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class ChwWorklistViewModelTest {

  @Test
  fun aClosedFollowUpLeavesTheList() = runTest {
    val repository = InMemorySampleFhirRepository()
    repository.upsert(Patient(id = "child-1"))
    repository.upsert(
      Task(
        id = "t1",
        status = Enumeration(value = Task.TaskStatus.Requested),
        intent = Enumeration(value = Task.TaskIntent.Proposal),
        code = CodeableConcept(coding = listOf(Coding(code = Code(value = FOLLOW_UP_TASK)))),
        `for` = Reference(reference = FhirString(value = "Patient/child-1")),
        owner = Reference(reference = FhirString(value = "Practitioner/p1")),
        authoredOn = DateTime(value = FhirDateTime.fromString("2026-10-05")),
      )
    )
    val viewModel = ChwWorklistViewModel(UserContext(AppRole.CHW, "p1", "o1", null), repository)
    assertEquals(listOf("t1"), viewModel.followUps.first { it != null }!!.map { it.id })

    repository.upsert(
      (repository.get("Task", "t1") as Task).copy(
        status = Enumeration(value = Task.TaskStatus.Completed)
      )
    )

    assertEquals(emptyList(), viewModel.followUps.first { it?.isEmpty() == true })
  }
}
