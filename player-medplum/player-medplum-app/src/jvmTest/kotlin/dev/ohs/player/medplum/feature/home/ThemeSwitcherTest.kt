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
package dev.ohs.player.medplum.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.ohs.player.medplum.data.settings.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ThemeSwitcherTest {

  @Test
  fun tappingAnOption_reportsIt() = runComposeUiTest {
    val chosen = mutableListOf<ThemePreference>()
    setContent {
      MaterialTheme {
        ThemeSwitcher(selected = ThemePreference.System, onSelect = { chosen.add(it) })
      }
    }

    onNodeWithText("Dark").performClick()
    onNodeWithText("Light").performClick()

    assertEquals(listOf(ThemePreference.Dark, ThemePreference.Light), chosen)
  }

  @Test
  fun allThreeOptionsAreOffered() = runComposeUiTest {
    // "System" has to be one of them -- it is the default, and without it someone who picked Dark
    // can never hand control back to the OS.
    setContent { MaterialTheme { ThemeSwitcher(selected = ThemePreference.Dark, onSelect = {}) } }

    listOf("System", "Light", "Dark").forEach { onNodeWithText(it).assertExists() }
  }

  @Test
  fun theCurrentChoiceIsMarkedSelected() = runComposeUiTest {
    // Screen readers announce selection state from this, and it is what draws the filled segment.
    setContent { MaterialTheme { ThemeSwitcher(selected = ThemePreference.Light, onSelect = {}) } }

    onNodeWithText("Light").assertIsSelected()
  }
}
