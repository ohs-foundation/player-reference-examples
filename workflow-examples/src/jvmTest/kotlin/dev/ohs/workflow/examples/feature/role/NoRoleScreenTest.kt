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
package dev.ohs.workflow.examples.feature.role

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NoRoleScreenTest {

  @Test
  fun noRole_explainsAndSignsOut() = runComposeUiTest {
    var signedOut = false
    setContent {
      MaterialTheme {
        NoRoleScreen(message = null, onRetry = null, onSignOut = { signedOut = true })
      }
    }

    onNodeWithText("No role assigned").assertExists()
    assertTrue(onAllNodesWithText("Retry").fetchSemanticsNodes().isEmpty())
    onNodeWithText("Sign out").performClick()
    assertTrue(signedOut)
  }

  @Test
  fun failure_showsTheMessageAndRetries() = runComposeUiTest {
    var retries = 0
    setContent {
      MaterialTheme { NoRoleScreen(message = "offline", onRetry = { retries++ }, onSignOut = {}) }
    }

    onNodeWithText("offline").assertExists()
    onNodeWithText("Retry").performClick()
    assertEquals(1, retries)
  }
}
