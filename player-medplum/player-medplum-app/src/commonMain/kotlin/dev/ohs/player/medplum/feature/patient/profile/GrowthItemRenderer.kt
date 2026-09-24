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
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import dev.ohs.player.client.renderer.ComponentRenderer
import dev.ohs.player.client.renderer.RenderOptions
import dev.ohs.player.generated.config.GrowthItemConfig
import dev.ohs.player.generated.state.PatientGrowthState
import dev.ohs.player.medplum.feature.component.common.StatusChipData
import dev.ohs.player.medplum.feature.component.common.StatusRow
import org.jetbrains.compose.resources.stringResource
import player_medplum.player_medplum_app.generated.resources.Res
import player_medplum.player_medplum_app.generated.resources.growth_measured
import player_medplum.player_medplum_app.generated.resources.growth_unknown

/**
 * One growth measurement: the measurement name on the left, the reading as a chip on the right.
 *
 * The reading is shown verbatim as recorded. This app deliberately does not compute or display a
 * z-score or a percentile — that needs the WHO reference tables and clinical sign-off, and a wrong
 * growth classification is worse than none.
 */
class GrowthItemRenderer : ComponentRenderer<PatientGrowthState, GrowthItemConfig> {
  @Composable
  override fun Render(item: PatientGrowthState, config: GrowthItemConfig, options: RenderOptions) {
    val reading =
      item.measurementValue?.let { value ->
        listOfNotNull(formatReading(value), item.measurementUnit).joinToString(" ")
      }
    StatusRow(
      title = item.measurementName ?: stringResource(Res.string.growth_unknown),
      modifier = options.modifier,
      subtitle =
        if (config.showDate != false)
          item.measurementDate?.toString()?.take(10)?.let {
            stringResource(Res.string.growth_measured, it)
          }
        else null,
      accentColor = MaterialTheme.colorScheme.tertiary,
      status =
        reading?.let {
          StatusChipData(
            it,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
          )
        },
    )
  }
}

/**
 * Renders a measurement the way a health worker writes it: "8.4", not "8.400" and certainly not
 * "8.4E+0".
 *
 * `BigDecimal.toString()` on this arbitrary-precision type is scientific notation — 14 kg comes
 * back as "1.4E+1" — so the expanded form is the one to use.
 */
internal fun formatReading(value: BigDecimal): String {
  val expanded = value.toStringExpanded()
  return if (expanded.contains('.')) expanded.trimEnd('0').trimEnd('.') else expanded
}
