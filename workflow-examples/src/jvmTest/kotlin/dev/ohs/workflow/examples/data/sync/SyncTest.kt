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

import dev.ohs.fhir.engine.FhirEngine
import dev.ohs.fhir.engine.FhirEngineConfiguration
import dev.ohs.fhir.engine.FhirEngineProvider
import dev.ohs.fhir.engine.sync.AcceptLocalConflictResolver
import dev.ohs.fhir.engine.sync.ConflictResolver
import dev.ohs.fhir.engine.sync.CurrentSyncJobStatus
import dev.ohs.fhir.engine.sync.DownloadWorkManager
import dev.ohs.fhir.engine.sync.FhirSyncTask
import dev.ohs.fhir.engine.sync.download.DownloadRequest
import dev.ohs.fhir.engine.sync.upload.HttpCreateMethod
import dev.ohs.fhir.engine.sync.upload.HttpUpdateMethod
import dev.ohs.fhir.engine.sync.upload.UploadStrategy
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.terminologies.ResourceType
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

/**
 * A no-op [FhirSyncTask] whose download step optionally blocks on [gate] before completing empty
 * (no requests, no local changes) — real enough for `runSync` to reach `SyncJobStatus.Succeeded`
 * without ever touching the network. Matches the shape kotlin-fhir-engine's own
 * `DownloaderImplTest.TestDownloadWorkManager` uses.
 */
private class TestFhirSyncTask(private val gate: CompletableDeferred<Unit>? = null) : FhirSyncTask {
  override fun getFhirEngine(): FhirEngine = FhirEngineProvider.getInstance()

  override fun getDownloadWorkManager(): DownloadWorkManager =
    object : DownloadWorkManager {
      private var requested = false

      override suspend fun getNextRequest(): DownloadRequest? {
        if (requested) return null
        requested = true
        gate?.await()
        return null
      }

      override suspend fun getSummaryRequestUrls(): Map<ResourceType, String> = emptyMap()

      override suspend fun processResponse(response: Resource): Collection<Resource> = emptyList()
    }

  override fun getConflictResolver(): ConflictResolver = AcceptLocalConflictResolver

  override fun getUploadStrategy(): UploadStrategy =
    UploadStrategy.forBundleRequest(
      methodForCreate = HttpCreateMethod.PUT,
      methodForUpdate = HttpUpdateMethod.PATCH,
      squash = true,
      bundleSize = 500,
    )
}

class SyncTest {

  @BeforeTest
  fun setUp() = runTest {
    // Matches the established pattern in FhirEngineRepositoryTest: FhirEngineProvider is a
    // process-wide singleton that throws if initialized twice, so guard it and reset state
    // between tests instead of re-initializing.
    if (FhirEngineProvider.isNotInitialized()) {
      FhirEngineProvider.init(
        FhirEngineConfiguration(
          storageDirectory = Files.createTempDirectory("sync-test").toString()
        )
      )
    }
    FhirEngineProvider.getInstance().clearDatabase()
  }

  @Test
  fun simultaneousRequestsShareOneSync() = runBlocking {
    val gate = CompletableDeferred<Unit>()
    var tasks = 0
    val original = Sync.beforeAttempt
    Sync.beforeAttempt = {}
    try {
      List(20) {
          async(Dispatchers.Default) {
            Sync.oneTimeSync(
              taskFactory = {
                tasks++
                TestFhirSyncTask(gate)
              }
            )
          }
        }
        .awaitAll()
        .first()
        .first { it is CurrentSyncJobStatus.Running }
    } finally {
      Sync.cancelOneTimeSync<TestFhirSyncTask>()
      Sync.beforeAttempt = original
    }

    assertEquals(1, tasks)
  }

  @Test
  fun aFinishedSyncNeverAnswersTheNextRequest() = runBlocking {
    var attempts = 0
    val original = Sync.beforeAttempt
    Sync.beforeAttempt = {
      attempts++
      error("offline")
    }
    val perSync =
      try {
        Sync.oneTimeSync(taskFactory = { TestFhirSyncTask() }).first {
          it is CurrentSyncJobStatus.Failed
        }
        val first = attempts
        Sync.oneTimeSync(taskFactory = { TestFhirSyncTask() }).first {
          it is CurrentSyncJobStatus.Failed
        }
        first to attempts - first
      } finally {
        Sync.beforeAttempt = original
      }

    assertTrue(perSync.first > 0)
    assertEquals(perSync.first, perSync.second)
  }

  @Test
  fun simultaneousPeriodicStartsRunOneLoop() = runBlocking {
    var cycles = 0
    try {
      List(20) {
          async(Dispatchers.Default) {
            Sync.periodicSync(
              taskFactory = {
                cycles++
                TestFhirSyncTask(CompletableDeferred())
              },
              repeatInterval = 1.minutes,
            )
          }
        }
        .awaitAll()
      delay(2_000)
    } finally {
      Sync.cancelPeriodicSync<TestFhirSyncTask>()
    }

    assertEquals(1, cycles)
  }

  @Test
  fun cancelOneTimeSync_whileRunning_emitsCancelled() = runTest {
    val gate = CompletableDeferred<Unit>()
    val statusFlow = Sync.oneTimeSync(taskFactory = { TestFhirSyncTask(gate) })

    // Let the sync actually start (past Enqueued) before cancelling.
    statusFlow.first { it is CurrentSyncJobStatus.Running }

    Sync.cancelOneTimeSync<TestFhirSyncTask>()

    val terminal = statusFlow.first { it !is CurrentSyncJobStatus.Running }
    assertIs<CurrentSyncJobStatus.Cancelled>(terminal)
  }
}
