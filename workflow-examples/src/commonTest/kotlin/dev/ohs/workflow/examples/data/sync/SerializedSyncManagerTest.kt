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

import dev.ohs.fhir.engine.sync.SyncJobStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class SerializedSyncManagerTest {

  @Test
  fun requestsDuringASyncShareOneMoreSyncAfterIt() = runTest {
    val gates = ArrayDeque<CompletableDeferred<SyncJobStatus>>()
    val delegate = FakeSyncManager {
      CompletableDeferred<SyncJobStatus>().also(gates::addLast).await()
    }
    val sync = delegate.serialized()

    val running = async { sync.syncNow() }
    runCurrent()
    val second = async { sync.syncNow() }
    val third = async { sync.syncNow() }
    runCurrent()
    gates.removeFirst().complete(SyncJobStatus.Failed())
    runCurrent()
    gates.removeFirst().complete(SyncJobStatus.Succeeded())

    assertEquals(SyncJobStatus.Failed::class, running.await()::class)
    assertEquals(SyncJobStatus.Succeeded::class, second.await()::class)
    assertEquals(SyncJobStatus.Succeeded::class, third.await()::class)
    assertEquals(2, delegate.syncNowCount)
  }

  @Test
  fun anIdleRequestStartsItsOwnSync() = runTest {
    val delegate = FakeSyncManager()
    val sync = delegate.serialized()

    sync.syncNow()
    sync.syncNow()

    assertEquals(2, delegate.syncNowCount)
  }
}
