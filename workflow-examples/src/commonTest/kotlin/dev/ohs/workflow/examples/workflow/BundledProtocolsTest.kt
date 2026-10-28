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

import dev.ohs.fhir.model.r4.ActivityDefinition
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.PlanDefinition
import dev.ohs.fhir.model.r4.Uri
import dev.ohs.fhir.model.r4.terminologies.PublicationStatus
import dev.ohs.fhir.model.r4.terminologies.ResourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class BundledProtocolsTest {
  private val plan =
    PlanDefinition(
      id = "pd",
      url = Uri(value = "http://ohs.dev/fhir/PlanDefinition/pd"),
      status = Enumeration(value = PublicationStatus.Active),
    )
  private val activity =
    ActivityDefinition(
      id = "ad",
      url = Uri(value = "http://ohs.dev/fhir/ActivityDefinition/ad"),
      status = Enumeration(value = PublicationStatus.Active),
    )
  private val protocols = BundledProtocols(listOf(plan, activity))

  @Test
  fun resolvesByCanonicalIgnoringTheVersion() = runTest {
    assertEquals(
      plan,
      protocols.resolve(ResourceType.PlanDefinition, "http://ohs.dev/fhir/PlanDefinition/pd|1.0"),
    )
    assertEquals(
      activity,
      protocols.resolve(
        ResourceType.ActivityDefinition,
        "http://ohs.dev/fhir/ActivityDefinition/ad",
      ),
    )
  }

  @Test
  fun wrongTypeOrUnknownCanonicalIsNull() = runTest {
    assertNull(
      protocols.resolve(ResourceType.ActivityDefinition, "http://ohs.dev/fhir/PlanDefinition/pd")
    )
    assertNull(
      protocols.resolve(ResourceType.PlanDefinition, "http://ohs.dev/fhir/PlanDefinition/x")
    )
  }
}
