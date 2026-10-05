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

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class QueueContentTest {

  @Test
  fun completingAConsultSendsTheOutcome() = runComposeUiTest {
    var completed: Pair<String, String>? = null
    setContent {
      MaterialTheme {
        QueueContent(
          items =
            listOf(
              QueueItem(
                "t1",
                "b",
                "Amina Otieno",
                "Fast breathing",
                "stat",
                true,
                "Oxygen saturation 88 %",
              )
            ),
          onComplete = { taskId, outcome -> completed = taskId to outcome },
        )
      }
    }

    onNodeWithText("Community referral").assertExists()
    onNodeWithText("Amina Otieno").performClick()
    onNodeWithText("Oxygen saturation 88 %").assertExists()
    onNode(hasSetTextAction()).performTextInput("Admitted for oxygen")
    onNodeWithText("Complete").performClick()

    assertEquals("t1" to "Admitted for oxygen", completed)
  }

  @Test
  fun emptyQueueSaysSo() = runComposeUiTest {
    setContent { MaterialTheme { QueueContent(items = emptyList(), onComplete = { _, _ -> }) } }

    onNodeWithText("No one is waiting").assertExists()
  }
}
