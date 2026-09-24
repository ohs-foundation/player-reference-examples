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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import dev.ohs.player.client.registry.ViewRegistry
import dev.ohs.player.client.registry.registerComponent
import dev.ohs.player.client.registry.registerLayout
import dev.ohs.player.generated.config.AdverseEventItemConfig
import dev.ohs.player.generated.config.ContactItemConfig
import dev.ohs.player.generated.config.GrowthItemConfig
import dev.ohs.player.generated.config.ImmunizationItemConfig
import dev.ohs.player.generated.config.ImmunizationRecommendationItemConfig
import dev.ohs.player.generated.config.PatientHeaderConfig
import dev.ohs.player.generated.config.SectionCardConfig
import dev.ohs.player.generated.state.PatientAdverseEventState
import dev.ohs.player.generated.state.PatientContactState
import dev.ohs.player.generated.state.PatientGrowthState
import dev.ohs.player.generated.state.PatientImmunizationRecommendationState
import dev.ohs.player.generated.state.PatientImmunizationState
import dev.ohs.player.generated.state.PatientSummaryState
import dev.ohs.player.generated.viewtype.ViewTypeCS
import dev.ohs.player.medplum.feature.component.common.SectionCardLayoutRenderer

/**
 * Sections a child's profile can show. This is an immunization register, so the set is deliberately
 * narrow: what the child is due for, what they have had, how they are growing, and who to call.
 * Allergies, medications and conditions were removed with the household register — nothing in this
 * app records them and nothing syncs them down, so the sections could only ever render empty.
 */
fun ViewRegistry.registerPatientProfile() {
  registerComponent<PatientSummaryState, PatientHeaderConfig>(
    ViewTypeCS.PatientHeader,
    PatientHeaderRenderer(),
    PatientHeaderConfig(),
  )
  registerComponent<PatientImmunizationRecommendationState, ImmunizationRecommendationItemConfig>(
    ViewTypeCS.ImmunizationRecommendationItem,
    ImmunizationRecommendationItemRenderer(),
    ImmunizationRecommendationItemConfig(),
  )
  registerLayout<PatientImmunizationRecommendationState>(
    ViewTypeCS.SectionCard,
    SectionCardLayoutRenderer(
      title = "Immunisations due",
      icon = Icons.Default.Notifications,
      config = SectionCardConfig(collapsible = false),
    ),
  )
  registerComponent<PatientImmunizationState, ImmunizationItemConfig>(
    ViewTypeCS.ImmunizationItem,
    ImmunizationItemRenderer(),
    ImmunizationItemConfig(),
  )
  registerLayout<PatientImmunizationState>(
    ViewTypeCS.SectionCard,
    SectionCardLayoutRenderer(
      title = "Immunisations given",
      icon = Icons.Default.CheckCircle,
      config = SectionCardConfig(collapsible = true),
    ),
  )
  registerComponent<PatientGrowthState, GrowthItemConfig>(
    ViewTypeCS.GrowthItem,
    GrowthItemRenderer(),
    GrowthItemConfig(),
  )
  registerLayout<PatientGrowthState>(
    ViewTypeCS.SectionCard,
    SectionCardLayoutRenderer(
      title = "Growth monitoring",
      icon = Icons.Default.Favorite,
      config = SectionCardConfig(collapsible = true),
    ),
  )
  registerComponent<PatientAdverseEventState, AdverseEventItemConfig>(
    ViewTypeCS.AdverseEventItem,
    AdverseEventItemRenderer(),
    AdverseEventItemConfig(),
  )
  registerLayout<PatientAdverseEventState>(
    ViewTypeCS.SectionCard,
    SectionCardLayoutRenderer(
      title = "Adverse events",
      icon = Icons.Default.Warning,
      config = SectionCardConfig(collapsible = true),
    ),
  )
  registerComponent<PatientContactState, ContactItemConfig>(
    ViewTypeCS.ContactItem,
    ContactItemRenderer(),
    ContactItemConfig(),
  )
  registerLayout<PatientContactState>(
    ViewTypeCS.SectionCard,
    SectionCardLayoutRenderer(
      title = "Caregiver",
      icon = Icons.Default.Person,
      config = SectionCardConfig(collapsible = false),
    ),
  )
}
