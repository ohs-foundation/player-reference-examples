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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ohs.player.client.renderer.ComponentRenderer
import dev.ohs.player.client.renderer.RenderOptions
import dev.ohs.player.generated.config.PatientHeaderConfig
import dev.ohs.player.generated.state.PatientSummaryState
import dev.ohs.player.medplum.feature.component.common.StatusChip
import dev.ohs.player.medplum.feature.patient.list.calculateAge
import org.jetbrains.compose.resources.stringResource
import player_medplum.player_medplum_app.generated.resources.Res
import player_medplum.player_medplum_app.generated.resources.label_age_plain
import player_medplum.player_medplum_app.generated.resources.label_dob
import player_medplum.player_medplum_app.generated.resources.label_id
import player_medplum.player_medplum_app.generated.resources.label_sex
import player_medplum.player_medplum_app.generated.resources.name_unknown

class PatientHeaderRenderer : ComponentRenderer<PatientSummaryState, PatientHeaderConfig> {
  @Composable
  override fun Render(
    item: PatientSummaryState,
    config: PatientHeaderConfig,
    options: RenderOptions,
  ) {
    PatientHeaderCard(patient = item, config = config, modifier = options.modifier)
  }
}

/**
 * Name, then the identifying facts in a labelled strip.
 *
 * Every fact carries its own label rather than being run together in one dotted line, because on a
 * child record "9mo" and "23 Aug 2025" are easy to confuse with each other at a glance.
 */
@Composable
fun PatientHeaderCard(
  patient: PatientSummaryState,
  config: PatientHeaderConfig = PatientHeaderConfig(),
  modifier: Modifier = Modifier,
) {
  val fullName =
    listOfNotNull(patient.givenName, patient.familyName).joinToString(" ").ifBlank {
      stringResource(Res.string.name_unknown)
    }

  val facts = buildList {
    if (config.showGender != false) {
      patient.gender?.let {
        add(stringResource(Res.string.label_sex) to it.replaceFirstChar { c -> c.uppercaseChar() })
      }
    }
    calculateAge(patient.birthDate?.toString())?.let {
      add(stringResource(Res.string.label_age_plain) to it)
    }
    patient.birthDate?.toString()?.let { add(stringResource(Res.string.label_dob) to it) }
    if (config.showMrn != false) {
      patient.mrn?.let { add(stringResource(Res.string.label_id) to it) }
    }
  }

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
      Text(
        text = fullName,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
      )
      if (config.showStatus != false) {
        StatusChip(isActive = patient.active ?: false)
      }
    }
    if (facts.isNotEmpty()) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        facts.forEach { (label, value) ->
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = value,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium,
            )
          }
        }
      }
    }
  }
}
