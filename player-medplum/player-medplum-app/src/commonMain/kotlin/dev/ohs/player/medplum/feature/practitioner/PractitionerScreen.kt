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
package dev.ohs.player.medplum.feature.practitioner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ohs.player.medplum.data.repository.PractitionerProfile
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import player_medplum.player_medplum_app.generated.resources.Res
import player_medplum.player_medplum_app.generated.resources.name_unknown
import player_medplum.player_medplum_app.generated.resources.practitioner_back
import player_medplum.player_medplum_app.generated.resources.practitioner_caseload
import player_medplum.player_medplum_app.generated.resources.practitioner_label_email
import player_medplum.player_medplum_app.generated.resources.practitioner_label_facility
import player_medplum.player_medplum_app.generated.resources.practitioner_label_facility_id
import player_medplum.player_medplum_app.generated.resources.practitioner_label_id
import player_medplum.player_medplum_app.generated.resources.practitioner_label_name
import player_medplum.player_medplum_app.generated.resources.practitioner_label_username
import player_medplum.player_medplum_app.generated.resources.practitioner_section_facility
import player_medplum.player_medplum_app.generated.resources.practitioner_section_you
import player_medplum.player_medplum_app.generated.resources.practitioner_title
import player_medplum.player_medplum_app.generated.resources.practitioner_unavailable

/**
 * Who is signed in, and where they work — read from the synced Practitioner and Organization rather
 * than from the token, so what a health worker sees here is the same record the facility sees.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PractitionerScreen(onBack: () -> Unit) {
  val viewModel: PractitionerViewModel = koinViewModel()
  val profile by viewModel.profile.collectAsStateWithLifecycle()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(Res.string.practitioner_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(Res.string.practitioner_back),
              tint = MaterialTheme.colorScheme.onPrimary,
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
          ),
      )
    }
  ) { padding ->
    val current = profile
    if (current == null) {
      Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
      return@Scaffold
    }

    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      Identity(current, viewModel.username)

      if (current.practitionerId == null) {
        Text(
          text = stringResource(Res.string.practitioner_unavailable),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return@Column
      }

      DetailCard(
        title = stringResource(Res.string.practitioner_section_you),
        rows =
          listOfNotNull(
            current.name?.let { stringResource(Res.string.practitioner_label_name) to it },
            current.email?.let { stringResource(Res.string.practitioner_label_email) to it },
            viewModel.username
              .takeIf { it.isNotBlank() }
              ?.let { stringResource(Res.string.practitioner_label_username) to it },
            current.practitionerId?.let { stringResource(Res.string.practitioner_label_id) to it },
          ),
      )
      DetailCard(
        title = stringResource(Res.string.practitioner_section_facility),
        rows =
          listOfNotNull(
            current.facilityName?.let {
              stringResource(Res.string.practitioner_label_facility) to it
            },
            current.facilityIdentifier?.let {
              stringResource(Res.string.practitioner_label_facility_id) to it
            },
          ),
      )
      Text(
        text = stringResource(Res.string.practitioner_caseload, current.caseload.toString()),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun Identity(profile: PractitionerProfile, username: String) {
  val displayName =
    profile.name ?: username.takeIf { it.isNotBlank() } ?: stringResource(Res.string.name_unknown)
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onPrimary,
        fontWeight = FontWeight.Bold,
      )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = displayName,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
      )
      profile.facilityName?.let {
        Text(
          text = it,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun DetailCard(title: String, rows: List<Pair<String, String>>) {
  if (rows.isEmpty()) return
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = title.uppercase(),
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surface)
          .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      rows.forEachIndexed { index, (label, value) ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Text(
            text = label,
            modifier = Modifier.weight(0.4f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = value,
            modifier = Modifier.weight(0.6f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
          )
        }
      }
    }
  }
}
