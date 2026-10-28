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

import dev.ohs.fhir.model.r4.Boolean as FhirBoolean
import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.QuestionnaireResponse.Item.Answer.Value
import dev.ohs.fhir.model.r4.RequestGroup
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.workflow.FhirOperator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class IccmFollowUpVisitTest {

  private suspend fun actionsFor(condition: String, dangerSign: Boolean): Set<String> {
    val response =
      QuestionnaireResponse(
        status = Enumeration(value = QuestionnaireResponse.QuestionnaireResponseStatus.Completed),
        item =
          listOf(
            QuestionnaireResponse.Item(
              linkId = FhirString(value = "condition"),
              answer =
                listOf(
                  QuestionnaireResponse.Item.Answer(
                    value = Value.Coding(Coding(code = Code(value = condition)))
                  )
                ),
            ),
            QuestionnaireResponse.Item(
              linkId = FhirString(value = "danger-sign"),
              answer =
                listOf(
                  QuestionnaireResponse.Item.Answer(
                    value = Value.Boolean(FhirBoolean(value = dangerSign))
                  )
                ),
            ),
          ),
      )
    return FhirOperator(InMemoryWorkflowRepository(), resolver = BundledProtocols.load())
      .generateCarePlan(
        planDefinitionCanonical = "http://ohs.dev/fhir/PlanDefinition/iccm-follow-up-visit",
        subject = Patient(id = "child-1"),
        variables = mapOf("visit" to response.asCollection()),
        today = LocalDate(2026, 10, 8),
      )
      .contained
      .filterNot { it is RequestGroup }
      .mapNotNull { it.id }
      .toSet()
  }

  @Test
  fun betterClosesTheLoop() = runTest { assertEquals(emptySet(), actionsFor("better", false)) }

  @Test
  fun sameOnDayThreeIsReferred() = runTest {
    assertEquals(setOf("refer"), actionsFor("same", false))
  }

  @Test fun worseIsReferred() = runTest { assertEquals(setOf("refer"), actionsFor("worse", false)) }

  @Test
  fun betterButADangerSignIsReferred() = runTest {
    assertEquals(setOf("refer"), actionsFor("better", true))
  }
}
