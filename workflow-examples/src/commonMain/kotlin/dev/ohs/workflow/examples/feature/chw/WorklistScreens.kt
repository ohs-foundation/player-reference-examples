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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ohs.workflow.examples.auth.UserContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import player_reference_examples.workflow_examples.generated.resources.Res
import player_reference_examples.workflow_examples.generated.resources.follow_ups_empty
import player_reference_examples.workflow_examples.generated.resources.referrals_empty
import player_reference_examples.workflow_examples.generated.resources.work_due
import player_reference_examples.workflow_examples.generated.resources.work_seen_at_facility
import player_reference_examples.workflow_examples.generated.resources.work_waiting_at_facility

@Composable
fun FollowUpsScreen(context: UserContext, onStartVisit: (WorkItem) -> Unit) {
  val viewModel = koinViewModel<ChwWorklistViewModel> { parametersOf(context) }
  val items by viewModel.followUps.collectAsStateWithLifecycle()
  WorklistContent(items, stringResource(Res.string.follow_ups_empty), onStartVisit)
}

@Composable
fun ReferralsScreen(context: UserContext, onPatientClick: (String) -> Unit) {
  val viewModel = koinViewModel<ChwWorklistViewModel> { parametersOf(context) }
  val items by viewModel.referrals.collectAsStateWithLifecycle()
  WorklistContent(
    items,
    stringResource(Res.string.referrals_empty),
    onItemClick = { onPatientClick(it.patientId) },
  )
}

/** A plain worklist: one row per item with its patient, status and an optional action. */
@Composable
fun WorklistContent(
  items: List<WorkItem>?,
  empty: String,
  onItemClick: (WorkItem) -> Unit,
  action: @Composable (WorkItem) -> Unit = {},
) {
  when {
    items == null ->
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    items.isEmpty() ->
      Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    else ->
      LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { item ->
          ListItem(
            modifier = Modifier.clickable { onItemClick(item) },
            headlineContent = { Text(item.patientName) },
            supportingContent = {
              Column {
                Text(item.statusLabel())
                item.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
              }
            },
            trailingContent = { action(item) },
          )
          HorizontalDivider()
        }
      }
  }
}

@Composable
private fun WorkItem.statusLabel(): String =
  when (status) {
    WorkStatus.Due -> stringResource(Res.string.work_due, date)
    WorkStatus.WaitingAtFacility -> stringResource(Res.string.work_waiting_at_facility)
    WorkStatus.SeenAtFacility -> stringResource(Res.string.work_seen_at_facility)
  }
