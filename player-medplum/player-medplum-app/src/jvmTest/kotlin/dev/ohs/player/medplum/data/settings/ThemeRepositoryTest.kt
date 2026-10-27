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
package dev.ohs.player.medplum.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.ohs.fhir.engine.sync.createDataStore
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class ThemeRepositoryTest {

  private val dir = File(System.getProperty("java.io.tmpdir"), "eir-theme-${System.nanoTime()}")

  private fun store(name: String): DataStore<Preferences> {
    dir.mkdirs()
    return createDataStore { File(dir, name).absolutePath }
  }

  @AfterTest
  fun cleanUp() {
    dir.deleteRecursively()
  }

  @Test
  fun defaultsToSystem_whenNothingHasBeenChosen() = runTest {
    val repository = ThemeRepository(store("fresh.preferences_pb"))
    assertEquals(ThemePreference.System, repository.persisted())
  }

  @Test
  fun survivesRestart() = runTest {
    // One DataStore per path, as the app has -- a second ThemeRepository over the same store is
    // what a relaunch looks like.
    val dataStore = store("persist.preferences_pb")
    // join(), not a sleep: select() persists on a background scope and hands back the Job.
    ThemeRepository(dataStore).select(ThemePreference.Dark).join()

    val afterRestart = ThemeRepository(dataStore)
    assertEquals(
      ThemePreference.Dark,
      afterRestart.persisted(),
      "the drawer choice has to outlive the process",
    )
  }

  @Test
  fun unknownStoredValue_fallsBackToSystem() = runTest {
    // A build that drops an enum entry, or a hand-edited preferences file, must not crash the app
    // on launch -- there is no UI to recover from that.
    val dataStore = store("corrupt.preferences_pb")
    dataStore.edit { it[stringPreferencesKey("theme_preference")] = "Solarized" }

    assertEquals(ThemePreference.System, ThemeRepository(dataStore).persisted())
  }
}
