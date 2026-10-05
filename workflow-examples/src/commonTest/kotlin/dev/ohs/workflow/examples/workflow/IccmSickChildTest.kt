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
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Integer
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

class IccmSickChildTest {
  private val child = Patient(id = "child-1")

  private suspend fun actionsFor(vararg answers: Pair<String, Value>): Set<String> {
    val response =
      QuestionnaireResponse(
        status = Enumeration(value = QuestionnaireResponse.QuestionnaireResponseStatus.Completed),
        item =
          answers.map { (linkId, value) ->
            QuestionnaireResponse.Item(
              linkId = FhirString(value = linkId),
              answer = listOf(QuestionnaireResponse.Item.Answer(value = value)),
            )
          },
      )
    val carePlan =
      FhirOperator(InMemoryWorkflowRepository(), resolver = BundledProtocols.load())
        .generateCarePlan(
          planDefinitionCanonical = "http://ohs.dev/fhir/PlanDefinition/iccm-sick-child",
          subject = child,
          variables = mapOf("assessment" to response.asCollection()),
          today = LocalDate(2026, 10, 5),
        )
    return carePlan.contained.filterNot { it is RequestGroup }.mapNotNull { it.id }.toSet()
  }

  private fun yes(linkId: String) = linkId to Value.Boolean(FhirBoolean(value = true))

  private fun no(linkId: String) = linkId to Value.Boolean(FhirBoolean(value = false))

  private fun number(linkId: String, n: Int) = linkId to Value.Integer(Integer(value = n))

  private fun rdt(code: String) = "rdt-result" to Value.Coding(Coding(code = Code(value = code)))

  private val noDangerSigns =
    arrayOf(
      no("unable-to-drink"),
      no("vomits-everything"),
      no("convulsions"),
      no("lethargic"),
      no("chest-indrawing"),
    )

  @Test
  fun dangerSignRefers() = runTest {
    assertEquals(
      setOf("refer"),
      actionsFor(number("age-months", 20), yes("chest-indrawing"), no("fever"), no("cough")),
    )
  }

  @Test
  fun dangerSignWithMalariaRefersWithoutHomeTreatment() = runTest {
    assertEquals(
      setOf("refer"),
      actionsFor(
        number("age-months", 20),
        yes("convulsions"),
        yes("fever"),
        rdt("positive"),
        no("cough"),
      ),
    )
  }

  @Test
  fun malariaIsTreatedAndFollowedUp() = runTest {
    assertEquals(
      setOf("treat-malaria", "follow-up"),
      actionsFor(
        number("age-months", 20),
        *noDangerSigns,
        yes("fever"),
        rdt("positive"),
        no("cough"),
      ),
    )
  }

  @Test
  fun feverWithNegativeTestNeedsNothing() = runTest {
    assertEquals(
      emptySet(),
      actionsFor(
        number("age-months", 20),
        *noDangerSigns,
        yes("fever"),
        rdt("negative"),
        no("cough"),
      ),
    )
  }

  @Test
  fun feverWithoutATestResultNeedsNothing() = runTest {
    assertEquals(
      emptySet(),
      actionsFor(number("age-months", 20), *noDangerSigns, yes("fever"), no("cough")),
    )
  }

  @Test
  fun fastBreathingInfantGetsAmoxicillin() = runTest {
    assertEquals(
      setOf("treat-pneumonia", "follow-up"),
      actionsFor(
        number("age-months", 6),
        *noDangerSigns,
        no("fever"),
        yes("cough"),
        number("respiratory-rate", 52),
      ),
    )
  }

  @Test
  fun infantJustBelowTheThresholdNeedsNothing() = runTest {
    assertEquals(
      emptySet(),
      actionsFor(
        number("age-months", 6),
        *noDangerSigns,
        no("fever"),
        yes("cough"),
        number("respiratory-rate", 49),
      ),
    )
  }

  @Test
  fun fastBreathingChildGetsAmoxicillin() = runTest {
    assertEquals(
      setOf("treat-pneumonia", "follow-up"),
      actionsFor(
        number("age-months", 30),
        *noDangerSigns,
        no("fever"),
        yes("cough"),
        number("respiratory-rate", 40),
      ),
    )
  }

  @Test
  fun wellChildNeedsNothing() = runTest {
    assertEquals(
      emptySet(),
      actionsFor(number("age-months", 30), *noDangerSigns, no("fever"), no("cough")),
    )
  }
}

internal fun QuestionnaireResponse.asCollection() =
  Bundle(
    type = Enumeration(value = Bundle.BundleType.Collection),
    entry = listOf(Bundle.Entry(resource = this)),
  )
