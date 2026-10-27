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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dev.ohs.player.client.renderer.ComponentRenderer
import dev.ohs.player.client.renderer.RenderOptions
import dev.ohs.player.generated.config.AdverseEventItemConfig
import dev.ohs.player.generated.state.PatientAdverseEventState
import dev.ohs.player.medplum.feature.component.common.StatusChipData
import dev.ohs.player.medplum.feature.component.common.StatusRow
import org.jetbrains.compose.resources.stringResource
import player_medplum.player_medplum_app.generated.resources.Res
import player_medplum.player_medplum_app.generated.resources.adverse_event_after_vaccine
import player_medplum.player_medplum_app.generated.resources.adverse_event_detail_separator
import player_medplum.player_medplum_app.generated.resources.adverse_event_serious
import player_medplum.player_medplum_app.generated.resources.adverse_event_severity_mild
import player_medplum.player_medplum_app.generated.resources.adverse_event_severity_moderate
import player_medplum.player_medplum_app.generated.resources.adverse_event_severity_severe
import player_medplum.player_medplum_app.generated.resources.adverse_event_started
import player_medplum.player_medplum_app.generated.resources.adverse_event_unknown

/**
 * One reported AEFI: the reaction on the left, how bad it was as a chip on the right.
 *
 * Severity drives the chip colour, and a SERIOUS event overrides it to the error colour whatever
 * its severity says. The two are different questions in the WHO form -- "severe" is how bad the
 * reaction felt, "serious" is whether it was life-threatening, hospitalising or disabling -- and a
 * serious event is the one that has to catch the eye from across the card.
 *
 * The subtitle names the DOSE the reaction followed, which is the whole point of an AEFI report. It
 * reaches this row through a join in the view map: `suspectEntity.instance` is stored as a
 * reference, the view strips it to a bare id, and the join map indexes the child's Immunizations by
 * id to turn that back into a vaccine name. Nothing here re-queries.
 *
 * The parts are assembled left to right by importance -- which vaccine, when it started, how it
 * ended -- and any part that is missing is simply left out rather than rendered as a gap.
 */
class AdverseEventItemRenderer :
  ComponentRenderer<PatientAdverseEventState, AdverseEventItemConfig> {
  @Composable
  override fun Render(
    item: PatientAdverseEventState,
    config: AdverseEventItemConfig,
    options: RenderOptions,
  ) {
    val isSerious = item.adverseEventSeriousness.equals("Serious", ignoreCase = true)

    val severityLabel =
      when (item.adverseEventSeverity?.lowercase()) {
        "mild" -> stringResource(Res.string.adverse_event_severity_mild)
        "moderate" -> stringResource(Res.string.adverse_event_severity_moderate)
        "severe" -> stringResource(Res.string.adverse_event_severity_severe)
        else -> null
      }
    val seriousLabel = stringResource(Res.string.adverse_event_serious)
    val chipLabel =
      when {
        isSerious && severityLabel != null -> "$severityLabel · $seriousLabel"
        isSerious -> seriousLabel
        else -> severityLabel
      }

    val container =
      when {
        isSerious -> MaterialTheme.colorScheme.errorContainer
        item.adverseEventSeverity.equals("severe", ignoreCase = true) ->
          MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
      }
    val content =
      if (container == MaterialTheme.colorScheme.errorContainer)
        MaterialTheme.colorScheme.onErrorContainer
      else MaterialTheme.colorScheme.onSecondaryContainer

    val startedOn = item.adverseEventDate?.toString()?.take(10).takeIf { config.showDate != false }
    val subtitle =
      listOfNotNull(
          item.vaccineName?.let { stringResource(Res.string.adverse_event_after_vaccine, it) },
          startedOn?.let { stringResource(Res.string.adverse_event_started, it) },
          item.adverseEventOutcome,
        )
        .joinToString(stringResource(Res.string.adverse_event_detail_separator))
        .takeIf { it.isNotEmpty() }

    StatusRow(
      title = item.adverseEventReaction ?: stringResource(Res.string.adverse_event_unknown),
      modifier = options.modifier,
      subtitle = subtitle,
      accentColor =
        if (isSerious) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
      status = chipLabel?.let { StatusChipData(it, container, content) },
    )
  }
}
