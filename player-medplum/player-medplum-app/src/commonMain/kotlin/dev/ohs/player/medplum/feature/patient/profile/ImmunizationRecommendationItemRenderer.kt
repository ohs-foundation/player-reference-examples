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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ohs.player.client.renderer.ComponentRenderer
import dev.ohs.player.client.renderer.RenderOptions
import dev.ohs.player.generated.config.ImmunizationRecommendationItemConfig
import dev.ohs.player.generated.state.PatientImmunizationRecommendationState
import org.jetbrains.compose.resources.stringResource
import player_medplum.player_medplum_app.generated.resources.Res
import player_medplum.player_medplum_app.generated.resources.immunization_due_on
import player_medplum.player_medplum_app.generated.resources.immunization_overdue_since

/**
 * One forecast row, rendered as a due-task pill: a full-width tappable bar reading "＋ Measles 1 due
 * 8 Aug 2026". The whole row is the target, so there is no small button to aim at — this is used
 * one-handed, often outdoors, on a low-end device.
 *
 * The status shown here is computed SERVER-SIDE by the `eir-forecaster` Medplum bot and arrives on
 * the ImmunizationRecommendation. There is deliberately no schedule logic on the device — this
 * renderer only decides colours and whether to offer the action.
 */
class ImmunizationRecommendationItemRenderer :
  ComponentRenderer<PatientImmunizationRecommendationState, ImmunizationRecommendationItemConfig> {

  @Composable
  override fun Render(
    item: PatientImmunizationRecommendationState,
    config: ImmunizationRecommendationItemConfig,
    options: RenderOptions,
  ) {
    val status = item.forecastStatus?.lowercase()

    // A child's forecast covers the whole schedule, including doses years away. Showing all of
    // them buries the two that matter today, so future doses are hidden by default.
    if (config.hideNotDue != false && (status == "not-due" || status == "complete")) {
      return
    }

    val isOverdue = status == "overdue"
    // Overdue is the one state that must read differently at arm's length, so it gets the error
    // palette; everything else stays in the calm blue of a routine task.
    val container =
      if (isOverdue) MaterialTheme.colorScheme.errorContainer
      else MaterialTheme.colorScheme.secondaryContainer
    val content =
      if (isOverdue) MaterialTheme.colorScheme.onErrorContainer
      else MaterialTheme.colorScheme.onSecondaryContainer

    val dose = item.doseLabel ?: item.vaccineDisplay ?: item.series.orEmpty()
    val date = item.dueDate?.toString()?.take(10)
    val label =
      when {
        !config.showDueDate.let { it != false } || date == null -> dose
        isOverdue -> stringResource(Res.string.immunization_overdue_since, dose, date)
        else -> stringResource(Res.string.immunization_due_on, dose, date)
      }

    // One tap records it. No dialog and no fields: the dose being administered is already fully
    // identified by the forecast (vaccine code, series and dose number all come from the
    // recommendation), and the date is today by definition.
    val onClick = options.onClick
    Row(
      modifier =
        options.modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(container)
          .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
          .padding(horizontal = 12.dp, vertical = 14.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (onClick != null) {
        Icon(
          Icons.Filled.Add,
          contentDescription = null,
          tint = content,
          modifier = Modifier.size(18.dp),
        )
      }
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = content,
      )
    }
  }
}
