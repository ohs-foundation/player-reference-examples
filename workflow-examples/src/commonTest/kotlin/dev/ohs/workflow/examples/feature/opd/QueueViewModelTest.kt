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

import dev.ohs.fhir.engine.sync.SyncJobStatus
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Task
import dev.ohs.fhir.workflow.FhirOperator
import dev.ohs.workflow.examples.auth.AppRole
import dev.ohs.workflow.examples.auth.UserContext
import dev.ohs.workflow.examples.data.repository.InMemorySampleFhirRepository
import dev.ohs.workflow.examples.data.sync.FakeSyncManager
import dev.ohs.workflow.examples.workflow.BundledProtocols
import dev.ohs.workflow.examples.workflow.InMemoryWorkflowRepository
import dev.ohs.workflow.examples.workflow.ProtocolService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone

class QueueViewModelTest {
  private val workflow = InMemoryWorkflowRepository()
  private val repository = InMemorySampleFhirRepository()

  private fun viewModel(sync: FakeSyncManager) =
    QueueViewModel(
      UserContext(AppRole.CLINICIAN, "p1", "o1", "l1"),
      repository,
      ProtocolService(workflow, { FhirOperator(workflow, resolver = BundledProtocols.load()) }),
      sync,
      now = { Instant.parse("2026-10-06T07:05:00Z") },
      timeZone = TimeZone.UTC,
    )

  @Test
  fun refreshSyncsAndStampsTheTime() = runTest {
    val sync = FakeSyncManager()
    val viewModel = viewModel(sync)

    viewModel.refresh().join()

    assertEquals(1, sync.syncNowCount)
    assertEquals("07:05", viewModel.updatedAt.value)
  }

  @Test
  fun completingAConsultUploadsRightAway() = runTest {
    val sync = FakeSyncManager()
    val viewModel = viewModel(sync)
    repository.upsert(
      Task(
        id = "t1",
        status = Enumeration(value = Task.TaskStatus.Requested),
        intent = Enumeration(value = Task.TaskIntent.Order),
        `for` = Reference(reference = FhirString(value = "Patient/a")),
      )
    )

    viewModel.complete("t1", "Treated").join()
    advanceUntilIdle()

    assertNull(viewModel.error.value)
    assertEquals(1, sync.syncNowCount)
  }

  @Test
  fun anOfflineRefreshIsIgnored() = runTest {
    val viewModel = viewModel(FakeSyncManager { error("Unable to resolve host") })

    viewModel.refresh().join()

    assertNull(viewModel.updatedAt.value)
  }

  @Test
  fun onlyAPullShowsTheSpinner() = runTest {
    val release = CompletableDeferred<SyncJobStatus>()
    val viewModel = viewModel(FakeSyncManager { release.await() })

    val automatic = viewModel.refresh()
    runCurrent()
    assertFalse(viewModel.pulling.value)
    release.complete(SyncJobStatus.Succeeded())
    automatic.join()

    val pull = viewModel.refresh(pulled = true)
    assertTrue(viewModel.pulling.value)
    pull.join()
    assertFalse(viewModel.pulling.value)
  }
}
