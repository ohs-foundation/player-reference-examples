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
package dev.ohs.workflow.examples.feature.chw

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WorklistContentTest {

  @Test
  fun rowsShowStatusAndOpenThePatient() = runComposeUiTest {
    var opened: String? = null
    setContent {
      MaterialTheme {
        WorklistContent(
          items =
            listOf(
              WorkItem("sr-1", "child-1", "Amina Otieno", "2026-10-05", WorkStatus.SeenAtFacility)
            ),
          empty = "No referrals",
          onPatientClick = { opened = it },
        )
      }
    }

    onNodeWithText("Seen at facility").assertExists()
    onNodeWithText("Amina Otieno").performClick()
    assertEquals("child-1", opened)
  }

  @Test
  fun emptyListSaysSo() = runComposeUiTest {
    setContent {
      MaterialTheme {
        WorklistContent(items = emptyList(), empty = "No follow-ups due", onPatientClick = {})
      }
    }

    onNodeWithText("No follow-ups due").assertExists()
  }

  @Test
  fun rowActionIsOffered() = runComposeUiTest {
    var done: String? = null
    setContent {
      MaterialTheme {
        WorklistContent(
          items = listOf(WorkItem("t1", "child-1", "Amina Otieno", "2026-10-08", WorkStatus.Due)),
          empty = "",
          onPatientClick = {},
          action = { item -> TextButton(onClick = { done = item.id }) { Text("Done") } },
        )
      }
    }

    onNodeWithText("Done").performClick()
    assertEquals("t1", done)
  }
}
