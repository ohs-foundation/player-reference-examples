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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ohs.workflow.examples.auth.UserContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import player_reference_examples.workflow_examples.generated.resources.Res
import player_reference_examples.workflow_examples.generated.resources.queue_cancel
import player_reference_examples.workflow_examples.generated.resources.queue_complete
import player_reference_examples.workflow_examples.generated.resources.queue_empty
import player_reference_examples.workflow_examples.generated.resources.queue_outcome
import player_reference_examples.workflow_examples.generated.resources.queue_priority_routine
import player_reference_examples.workflow_examples.generated.resources.queue_priority_stat
import player_reference_examples.workflow_examples.generated.resources.queue_priority_urgent
import player_reference_examples.workflow_examples.generated.resources.queue_referral

@Composable
fun QueueScreen(context: UserContext) {
  val viewModel = koinViewModel<QueueViewModel> { parametersOf(context) }
  val items by viewModel.queue.collectAsStateWithLifecycle()
  val error by viewModel.error.collectAsStateWithLifecycle()
  Column {
    error?.let {
      Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
    }
    QueueContent(items, onComplete = { taskId, outcome -> viewModel.complete(taskId, outcome) })
  }
}

/** The waiting patients; tapping one opens the consult, which closes with a recorded outcome. */
@Composable
fun QueueContent(items: List<QueueItem>?, onComplete: (taskId: String, outcome: String) -> Unit) {
  var open by remember { mutableStateOf<QueueItem?>(null) }
  when {
    items == null ->
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    items.isEmpty() ->
      Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
          stringResource(Res.string.queue_empty),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    else ->
      LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.taskId }) { item ->
          ListItem(
            modifier = Modifier.clickable { open = item },
            headlineContent = { Text(item.patientName) },
            supportingContent = { Text(item.reason) },
            trailingContent = {
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.referred) {
                  AssistChip(
                    onClick = {},
                    label = { Text(stringResource(Res.string.queue_referral)) },
                  )
                }
                AssistChip(onClick = {}, label = { Text(item.priorityLabel()) })
              }
            },
          )
          HorizontalDivider()
        }
      }
  }
  open?.let { item ->
    var outcome by remember(item.taskId) { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { open = null },
      title = { Text(item.patientName) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(item.reason)
          if (item.vitals.isNotBlank()) Text(item.vitals)
          if (item.referred) Text(stringResource(Res.string.queue_referral))
          OutlinedTextField(
            value = outcome,
            onValueChange = { outcome = it },
            label = { Text(stringResource(Res.string.queue_outcome)) },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            onComplete(item.taskId, outcome)
            open = null
          },
          enabled = outcome.isNotBlank(),
        ) {
          Text(stringResource(Res.string.queue_complete))
        }
      },
      dismissButton = {
        TextButton(onClick = { open = null }) { Text(stringResource(Res.string.queue_cancel)) }
      },
    )
  }
}

@Composable
private fun QueueItem.priorityLabel(): String =
  stringResource(
    when (priority) {
      "stat" -> Res.string.queue_priority_stat
      "urgent" -> Res.string.queue_priority_urgent
      else -> Res.string.queue_priority_routine
    }
  )
