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
package dev.ohs.workflow.examples.feature.questionnaire

import dev.ohs.fhir.engine.FhirEngine
import dev.ohs.fhir.engine.FhirEngineConfiguration
import dev.ohs.fhir.engine.FhirEngineProvider
import dev.ohs.fhir.engine.search.Search
import dev.ohs.fhir.model.r4.Boolean as FhirBoolean
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Integer
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.QuestionnaireResponse.Item.Answer.Value
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.ServiceRequest
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.terminologies.ResourceType
import dev.ohs.fhir.workflow.FhirOperator
import dev.ohs.fhir.workflow.WorkflowRepository
import dev.ohs.workflow.examples.auth.AppRole
import dev.ohs.workflow.examples.auth.UserContext
import dev.ohs.workflow.examples.data.repository.FhirEngineRepository
import dev.ohs.workflow.examples.workflow.BundledProtocols
import dev.ohs.workflow.examples.workflow.EngineTransactor
import dev.ohs.workflow.examples.workflow.EngineWorkflowRepository
import dev.ohs.workflow.examples.workflow.ProtocolService
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlinx.coroutines.test.runTest

class AtomicSubmitTest {
  private lateinit var engine: FhirEngine

  @BeforeTest
  fun setUp() = runTest {
    if (FhirEngineProvider.isNotInitialized()) {
      FhirEngineProvider.init(
        FhirEngineConfiguration(
          storageDirectory = Files.createTempDirectory("atomic-submit-test").toString()
        )
      )
    }
    engine = FhirEngineProvider.getInstance()
    engine.clearDatabase()
    engine.create(Patient(id = "child-1"))
  }

  @Test
  fun aSubmitThatFailsHalfwayStoresNothing() = runTest {
    val workflow = EngineWorkflowRepository(engine)
    val flaky =
      object : WorkflowRepository by workflow {
        override suspend fun create(resource: Resource): String {
          if (
            resource is ServiceRequest &&
              resource.intent.value == ServiceRequest.RequestIntent.Order
          ) {
            error("Disk full")
          }
          return workflow.create(resource)
        }
      }
    val service =
      QuestionnaireService(
        FhirEngineRepository(engine),
        ProtocolService(flaky, { FhirOperator(flaky, resolver = BundledProtocols.load()) }),
        EngineTransactor(engine),
      )

    assertFails {
      service.submit(
        service.getQuestionnaire(QuestionnaireIds.ICCM_SICK_CHILD),
        QuestionnaireResponse(
          status = Enumeration(value = QuestionnaireResponse.QuestionnaireResponseStatus.Completed),
          item =
            listOf(
              answer("age-months", Value.Integer(Integer(value = 14))),
              answer("convulsions", Value.Boolean(FhirBoolean(value = true))),
            ),
        ),
        QuestionnaireLaunchContext("child-1", UserContext(AppRole.CHW, "p1", "o1", null)),
      )
    }

    assertEquals(0, engine.search<Resource>(Search(ResourceType.QuestionnaireResponse)).size)
    assertEquals(0, engine.search<Resource>(Search(ResourceType.ServiceRequest)).size)
  }

  private fun answer(linkId: String, value: Value) =
    QuestionnaireResponse.Item(
      linkId = FhirString(value = linkId),
      answer = listOf(QuestionnaireResponse.Item.Answer(value = value)),
    )
}
