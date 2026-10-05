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
package dev.ohs.workflow.examples.data.sync

import dev.ohs.fhir.model.r4.terminologies.ResourceType
import dev.ohs.workflow.examples.auth.AppRole
import dev.ohs.workflow.examples.auth.UserContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SyncConfigResolverTest {
  private val context = UserContext(AppRole.CHW, "p1", "o1", locationId = null)

  @Test
  fun fillsPlaceholdersAndTurnsIdsIntoAnIdParam() {
    val params =
      SyncConfigResolver.resolve(
        SyncConfig(
          listOf(
            SyncResource("Task", mapOf("owner" to "Practitioner/{practitioner}")),
            SyncResource("Practitioner", ids = listOf("{practitioner}", "p2")),
            SyncResource("Patient"),
          )
        ),
        context,
      )

    assertEquals(mapOf("owner" to "Practitioner/p1"), params[ResourceType.Task])
    assertEquals(mapOf("_id" to "p1,p2"), params[ResourceType.Practitioner])
    assertEquals(emptyMap(), params[ResourceType.Patient])
  }

  @Test
  fun entryNeedingAValueTheUserLacksIsSkipped() {
    val params =
      SyncConfigResolver.resolve(
        SyncConfig(listOf(SyncResource("Encounter", mapOf("location" to "Location/{location}")))),
        context,
      )

    assertFalse(ResourceType.Encounter in params)
  }

  @Test
  fun unknownPlaceholderFails() {
    assertFailsWith<IllegalArgumentException> {
      SyncConfigResolver.resolve(
        SyncConfig(listOf(SyncResource("Task", mapOf("owner" to "{team}")))),
        context,
      )
    }
  }

  @Test
  fun duplicateTypeFails() {
    assertFailsWith<IllegalArgumentException> {
      SyncConfigResolver.resolve(
        SyncConfig(listOf(SyncResource("Patient"), SyncResource("Patient"))),
        context,
      )
    }
  }
}
