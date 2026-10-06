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
package dev.ohs.workflow.examples.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DataChangeSignalTest {

  @Test
  fun concurrentChangesAreAllCounted() = runBlocking {
    val before = DataChangeSignal.revision.value

    List(8) { launch(Dispatchers.Default) { repeat(10_000) { DataChangeSignal.notifyChanged() } } }
      .forEach { it.join() }

    assertEquals(before + 80_000, DataChangeSignal.revision.value)
  }
}
