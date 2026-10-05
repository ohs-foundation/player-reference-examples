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
import dev.ohs.fhir.model.r4.Date
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.FhirDate
import dev.ohs.fhir.model.r4.Integer
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.QuestionnaireResponse.Item.Answer.Value
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.terminologies.AdministrativeGender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AnswersTest {
  private val response =
    QuestionnaireResponse(
      status = Enumeration(value = QuestionnaireResponse.QuestionnaireResponseStatus.Completed),
      item =
        listOf(
          item("given", Value.String(FhirString(value = "Amina"))),
          item("family", Value.String(FhirString(value = "Otieno"))),
          item("gender", Value.Coding(Coding(code = Code(value = "female")))),
          item("birth-date", Value.Date(Date(value = FhirDate.fromString("2025-08-01")))),
          item(
            "fever",
            Value.Boolean(FhirBoolean(value = true)),
            children = listOf(item("fever-days", Value.Integer(Integer(value = 2)))),
          ),
        ),
    )

  @Test
  fun readsTypedAnswersIncludingNestedItems() {
    assertEquals(true, response.boolean("fever"))
    assertEquals(2, response.integer("fever-days"))
    assertEquals("female", response.code("gender"))
    assertNull(response.boolean("cough"))
  }

  @Test
  fun buildsPatientManagedByTheUsersOrganization() {
    val patient = registrationPatient(response, organizationId = "org-1")

    assertEquals("Organization/org-1", patient.managingOrganization?.reference?.value)
    assertEquals("Amina", patient.name.single().given.single().value)
    assertEquals("Otieno", patient.name.single().family?.value)
    assertEquals(AdministrativeGender.Female, patient.gender?.value)
    assertEquals(FhirDate.fromString("2025-08-01"), patient.birthDate?.value)
    assertNotNull(patient.id)
  }

  private fun item(
    linkId: String,
    value: Value,
    children: List<QuestionnaireResponse.Item> = emptyList(),
  ) =
    QuestionnaireResponse.Item(
      linkId = FhirString(value = linkId),
      answer = listOf(QuestionnaireResponse.Item.Answer(value = value)),
      item = children,
    )
}
