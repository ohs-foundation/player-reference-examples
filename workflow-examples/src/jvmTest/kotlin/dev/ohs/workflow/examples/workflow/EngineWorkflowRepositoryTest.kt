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

import dev.ohs.fhir.engine.FhirEngine
import dev.ohs.fhir.engine.FhirEngineConfiguration
import dev.ohs.fhir.engine.FhirEngineProvider
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.ServiceRequest
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.workflow.examples.data.DataChangeSignal
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class EngineWorkflowRepositoryTest {
  private lateinit var fhirEngine: FhirEngine

  @BeforeTest
  fun setUp() = runTest {
    if (FhirEngineProvider.isNotInitialized()) {
      FhirEngineProvider.init(
        FhirEngineConfiguration(
          storageDirectory = Files.createTempDirectory("engine-workflow-repository-test").toString()
        )
      )
    }
    fhirEngine = FhirEngineProvider.getInstance()
    fhirEngine.clearDatabase()
  }

  private fun referral(status: ServiceRequest.RequestStatus) =
    ServiceRequest(
      id = "sr-1",
      status = Enumeration(value = status),
      intent = Enumeration(value = ServiceRequest.RequestIntent.Proposal),
      subject = Reference(reference = FhirString(value = "Patient/p1")),
    )

  @Test
  fun createdResourceCanBeReadSearchedAndUpdated() = runTest {
    val repository = EngineWorkflowRepository(fhirEngine)
    val revisionBefore = DataChangeSignal.revision.value

    assertEquals("sr-1", repository.create(referral(ServiceRequest.RequestStatus.Active)))
    repository.update(referral(ServiceRequest.RequestStatus.Completed))

    val read = repository.read("ServiceRequest", "sr-1") as ServiceRequest
    assertEquals(ServiceRequest.RequestStatus.Completed, read.status.value)
    assertEquals(
      listOf("sr-1"),
      repository.searchByReferenceParam("ServiceRequest", "subject", "Patient/p1").map { it.id },
    )
    assertTrue(DataChangeSignal.revision.value >= revisionBefore + 2)
  }

  @Test
  fun missingResourceReadsAsNull() = runTest {
    assertNull(EngineWorkflowRepository(fhirEngine).read("ServiceRequest", "nope"))
  }
}
