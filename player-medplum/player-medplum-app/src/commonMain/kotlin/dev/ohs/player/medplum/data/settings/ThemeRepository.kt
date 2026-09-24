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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * What the user picked in the navigation drawer. [System] follows the host's light/dark setting.
 */
enum class ThemePreference {
  System,
  Light,
  Dark,
}

/**
 * The chosen theme, persisted and shared app-wide.
 *
 * This is a Koin `single` rather than a ViewModel on purpose. Both the drawer (which writes) and
 * `App()` (which reads, to build the colour scheme) need it, and they sit on opposite sides of the
 * NavHost -- so `koinViewModel()` would hand them two different instances backed by two different
 * ViewModelStoreOwners, and every toggle would have to round-trip through disk before the app above
 * the NavHost noticed. One singleton holding one StateFlow makes the switch instant and the write a
 * side effect.
 *
 * [select] is optimistic -- it moves the StateFlow first and writes afterwards, because a theme
 * toggle that lags a disk round-trip behind the tap feels broken.
 */
class ThemeRepository(
  private val dataStore: DataStore<Preferences>,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

  private val _preference = MutableStateFlow(ThemePreference.System)
  val preference: StateFlow<ThemePreference> = _preference.asStateFlow()

  init {
    // Keep following the store after the first read: another window on desktop, or a restored
    // backup on Android, can change it underneath us.
    scope.launch { dataStore.data.collect { _preference.value = it.decode() } }
  }

  /**
   * The persisted choice, read straight from disk.
   *
   * [preference] starts at [ThemePreference.System] and is corrected a moment later, once DataStore
   * has read the file -- in the app that happens behind the existing startup spinner, so nothing
   * flashes. Anything that must see the real stored value without racing that read uses this.
   */
  suspend fun persisted(): ThemePreference = dataStore.data.first().decode()

  /**
   * Optimistic: the UI recolours on this frame, and the write lands in the background.
   *
   * Returns the write's [Job] so a test can await it. Callers in the UI ignore it -- there is
   * nothing useful to do if persisting a theme fails, and the choice already took effect.
   */
  fun select(preference: ThemePreference): Job {
    _preference.value = preference
    return scope.launch { dataStore.edit { it[THEME_KEY] = preference.name } }
  }

  // An unknown or absent value means "never chosen" -- fall back to System rather than throwing,
  // so a build that drops an enum entry cannot brick the app on launch.
  private fun Preferences.decode(): ThemePreference =
    this[THEME_KEY]?.let { name -> ThemePreference.entries.firstOrNull { it.name == name } }
      ?: ThemePreference.System

  private companion object {
    val THEME_KEY = stringPreferencesKey("theme_preference")
  }
}
