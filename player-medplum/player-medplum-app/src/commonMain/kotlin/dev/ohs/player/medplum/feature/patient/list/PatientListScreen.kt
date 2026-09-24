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
package dev.ohs.player.medplum.feature.patient.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ohs.player.client.layout.VerticalListRenderer
import dev.ohs.player.client.scaffold.ListScaffold
import dev.ohs.player.generated.state.PatientSummaryState
import dev.ohs.player.generated.viewtype.ViewTypeCS
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import player_medplum.player_medplum_app.generated.resources.Res
import player_medplum.player_medplum_app.generated.resources.home_open_navigation_menu
import player_medplum.player_medplum_app.generated.resources.patient_list_clear_search
import player_medplum.player_medplum_app.generated.resources.patient_list_empty
import player_medplum.player_medplum_app.generated.resources.patient_list_no_matches
import player_medplum.player_medplum_app.generated.resources.patient_list_register
import player_medplum.player_medplum_app.generated.resources.patient_list_search_hint
import player_medplum.player_medplum_app.generated.resources.patient_list_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientListScreen(
  onPatientClick: (String) -> Unit,
  onRegisterChild: (() -> Unit)? = null,
  onOpenNavigation: (() -> Unit)? = null,
) {
  val viewModel: PatientListViewModel = koinViewModel()
  val patients by viewModel.patients.collectAsStateWithLifecycle()
  val query by viewModel.query.collectAsStateWithLifecycle()

  val loaded = patients

  Box(modifier = Modifier.fillMaxSize()) {
    ListScaffold<PatientSummaryState>(
      items = loaded.orEmpty(),
      onItemClick = { onPatientClick(it.patientId ?: "") },
      key = { it.patientId ?: it.hashCode().toString() },
    ) {
      component(ViewTypeCS.PatientCard)
      layout(VerticalListRenderer.VIEW_TYPE)
      topBar {
        // A plain Row rather than a TopAppBar: the Material app bar reserves a fixed 64dp plus
        // its own insets, which on a register that also carries a search field below wastes a
        // band of blue above and under the title.
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.primary)) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            onOpenNavigation?.let { open ->
              IconButton(onClick = open) {
                Icon(
                  Icons.Filled.Menu,
                  contentDescription = stringResource(Res.string.home_open_navigation_menu),
                  tint = MaterialTheme.colorScheme.onPrimary,
                )
              }
            }
            Text(
              text = stringResource(Res.string.patient_list_title),
              modifier = Modifier.padding(start = if (onOpenNavigation == null) 12.dp else 0.dp),
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onPrimary,
            )
          }
          PatientSearchField(
            query = query,
            onQueryChange = viewModel::onQueryChange,
            modifier =
              Modifier.fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
          )
        }
      }
      emptyState {
        when {
          loaded == null -> CircularProgressIndicator()
          query.isBlank() -> Text(stringResource(Res.string.patient_list_empty))
          else -> Text(stringResource(Res.string.patient_list_no_matches, query))
        }
      }
    }

    onRegisterChild?.let { register ->
      ExtendedFloatingActionButton(
        onClick = register,
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        // Fully rounded -- deliberately not the Material default's 16dp corners.
        shape = RoundedCornerShape(percent = 50),
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = {
          Text(
            text = stringResource(Res.string.patient_list_register).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
          )
        },
      )
    }
  }
}

/**
 * Search sits in the app bar, on the primary ground, so it reads as part of the register header
 * rather than as the first row of the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatientSearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  OutlinedTextField(
    value = query,
    onValueChange = onQueryChange,
    modifier = modifier,
    singleLine = true,
    shape = RoundedCornerShape(8.dp),
    placeholder = { Text(stringResource(Res.string.patient_list_search_hint)) },
    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
    trailingIcon = {
      if (query.isNotEmpty()) {
        IconButton(onClick = { onQueryChange("") }) {
          Icon(
            Icons.Filled.Close,
            contentDescription = stringResource(Res.string.patient_list_clear_search),
          )
        }
      }
    },
    keyboardOptions =
      androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
    colors =
      OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
      ),
  )
}
