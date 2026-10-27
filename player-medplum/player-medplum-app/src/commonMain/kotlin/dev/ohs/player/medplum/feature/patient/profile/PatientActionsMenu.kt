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
package dev.ohs.player.medplum.feature.patient.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import player_medplum.player_medplum_app.generated.resources.Res
import player_medplum.player_medplum_app.generated.resources.patient_profile_already_deceased
import player_medplum.player_medplum_app.generated.resources.patient_profile_change_status
import player_medplum.player_medplum_app.generated.resources.patient_profile_more_actions
import player_medplum.player_medplum_app.generated.resources.patient_profile_no_doses_recorded
import player_medplum.player_medplum_app.generated.resources.patient_profile_report_aefi
import player_medplum.player_medplum_app.generated.resources.patient_profile_report_deceased

/**
 * Overflow menu in the profile's app bar.
 *
 * These three are record-level corrections rather than routine care, which is why they are behind
 * an overflow and not next to "Record growth" -- the FAB stays the one obvious thing to do.
 *
 * Disabled-with-a-reason rather than hidden, in both cases here: a hidden item reads as a missing
 * feature, a disabled one reads as "not applicable yet", and only the second is the truth.
 *
 * [hasRecordedDoses] gates the AEFI form because its first question is WHICH dose caused the
 * reaction, and those options are the child's own Immunizations. With none recorded the form opens
 * on an unanswerable required question.
 */
@Composable
fun PatientActionsMenu(
  isDeceased: Boolean,
  hasRecordedDoses: Boolean,
  onChangeStatus: () -> Unit,
  onReportDeceased: () -> Unit,
  onReportAdverseEvent: () -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }

  IconButton(onClick = { expanded = true }) {
    Icon(
      Icons.Filled.MoreVert,
      contentDescription = stringResource(Res.string.patient_profile_more_actions),
      tint = MaterialTheme.colorScheme.onPrimary,
    )
  }
  DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
    DropdownMenuItem(
      text = { Text(stringResource(Res.string.patient_profile_change_status)) },
      onClick = {
        expanded = false
        onChangeStatus()
      },
    )
    DropdownMenuItem(
      text = { Text(stringResource(Res.string.patient_profile_report_deceased)) },
      enabled = !isDeceased,
      onClick = {
        expanded = false
        onReportDeceased()
      },
    )
    if (isDeceased) {
      DropdownMenuItem(
        text = {
          Text(
            text = stringResource(Res.string.patient_profile_already_deceased),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
        enabled = false,
        onClick = {},
      )
    }
    DropdownMenuItem(
      text = { Text(stringResource(Res.string.patient_profile_report_aefi)) },
      enabled = hasRecordedDoses,
      onClick = {
        expanded = false
        onReportAdverseEvent()
      },
    )
    if (!hasRecordedDoses) {
      DropdownMenuItem(
        text = {
          Text(
            text = stringResource(Res.string.patient_profile_no_doses_recorded),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
        enabled = false,
        onClick = {},
      )
    }
  }
}
