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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import player_medplum.player_medplum_app.generated.resources.Res
import player_medplum.player_medplum_app.generated.resources.patient_profile_cancel
import player_medplum.player_medplum_app.generated.resources.patient_profile_deceased_body
import player_medplum.player_medplum_app.generated.resources.patient_profile_deceased_confirm
import player_medplum.player_medplum_app.generated.resources.patient_profile_deceased_date
import player_medplum.player_medplum_app.generated.resources.patient_profile_deceased_title
import player_medplum.player_medplum_app.generated.resources.patient_profile_status_body_activate
import player_medplum.player_medplum_app.generated.resources.patient_profile_status_body_deactivate
import player_medplum.player_medplum_app.generated.resources.patient_profile_status_confirm_activate
import player_medplum.player_medplum_app.generated.resources.patient_profile_status_confirm_deactivate
import player_medplum.player_medplum_app.generated.resources.patient_profile_status_title_activate
import player_medplum.player_medplum_app.generated.resources.patient_profile_status_title_deactivate

/** Today as `YYYY-MM-DD`. ISO dates compare correctly as plain strings, so no date library. */
internal fun todayIsoDate(): String = Clock.System.now().toString().substringBefore('T')

internal fun epochMillisToIsoDate(millis: Long): String =
  Instant.fromEpochMilliseconds(millis).toString().substringBefore('T')

/**
 * Confirms activating or deactivating a child.
 *
 * Deactivating is reversible and nothing is deleted, so this is a plain confirm rather than a
 * type-to-confirm -- but it does change what the register shows and stops forecasting, so it is not
 * a bare toggle either.
 */
@Composable
fun ChangeStatusDialog(
  patientName: String,
  isActive: Boolean,
  onConfirm: (Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        stringResource(
          if (isActive) Res.string.patient_profile_status_title_deactivate
          else Res.string.patient_profile_status_title_activate,
          patientName,
        )
      )
    },
    text = {
      Text(
        stringResource(
          if (isActive) Res.string.patient_profile_status_body_deactivate
          else Res.string.patient_profile_status_body_activate
        )
      )
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(!isActive) }) {
        Text(
          stringResource(
            if (isActive) Res.string.patient_profile_status_confirm_deactivate
            else Res.string.patient_profile_status_confirm_activate
          )
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.patient_profile_cancel)) }
    },
  )
}

/**
 * Captures the date of death.
 *
 * A fuller death report would also ask for the PLACE of death, which base R4 cannot hold on a
 * Patient -- see [dev.ohs.player.medplum.data.repository.PatientWriter]. Asking for an answer there
 * is nowhere to put would be worse than not asking.
 *
 * The date is a picker rather than a text field, and future dates are not selectable at all -- a
 * death cannot be reported before it happens, and blocking it in the picker is better than
 * validating it afterwards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDeceasedDialog(
  patientName: String,
  onConfirm: (date: String) -> Unit,
  onDismiss: () -> Unit,
) {
  var date by remember { mutableStateOf<String?>(null) }
  var pickingDate by remember { mutableStateOf(false) }

  if (pickingDate) {
    val today = remember { todayIsoDate() }
    val pickerState =
      rememberDatePickerState(
        selectableDates =
          object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): kotlin.Boolean =
              epochMillisToIsoDate(utcTimeMillis) <= today
          }
      )
    DatePickerDialog(
      onDismissRequest = { pickingDate = false },
      confirmButton = {
        TextButton(
          enabled = pickerState.selectedDateMillis != null,
          onClick = {
            pickerState.selectedDateMillis?.let { date = epochMillisToIsoDate(it) }
            pickingDate = false
          },
        ) {
          Text(stringResource(Res.string.patient_profile_deceased_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { pickingDate = false }) {
          Text(stringResource(Res.string.patient_profile_cancel))
        }
      },
    ) {
      DatePicker(state = pickerState)
    }
    return
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.patient_profile_deceased_title, patientName)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(Res.string.patient_profile_deceased_body))

        OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
          Text(date ?: stringResource(Res.string.patient_profile_deceased_date))
        }
      }
    },
    confirmButton = {
      TextButton(enabled = date != null, onClick = { date?.let { onConfirm(it) } }) {
        Text(stringResource(Res.string.patient_profile_deceased_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.patient_profile_cancel)) }
    },
  )
}
