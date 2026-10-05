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
package dev.ohs.workflow.examples.auth

import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.PractitionerRole
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Uri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserContextTest {

  @Test
  fun firstRoleWithAKnownCodeWins() {
    val context =
      userContextOf("p1", listOf(role("pharmacist", "o0"), role("nurse", "o1", location = "l1")))

    assertEquals(UserContext(AppRole.NURSE, "p1", "o1", "l1"), context)
  }

  @Test
  fun roleWithoutAKnownCodeIsNoContext() {
    assertNull(userContextOf("p1", listOf(role(null, "o1"))))
  }

  @Test
  fun roleWithoutAnOrganizationIsSkipped() {
    assertNull(userContextOf("p1", listOf(role("chw", organization = null))))
  }

  @Test
  fun codeFromAnotherSystemIsIgnored() {
    assertNull(userContextOf("p1", listOf(role("chw", "o1", system = "http://example.org/roles"))))
  }

  private fun role(
    code: String?,
    organization: String?,
    location: String? = null,
    system: String = APP_ROLE_SYSTEM,
  ) =
    PractitionerRole(
      code =
        listOfNotNull(code).map {
          CodeableConcept(
            coding = listOf(Coding(system = Uri(value = system), code = Code(value = it)))
          )
        },
      organization =
        organization?.let { Reference(reference = FhirString(value = "Organization/$it")) },
      location =
        listOfNotNull(location).map { Reference(reference = FhirString(value = "Location/$it")) },
    )
}
