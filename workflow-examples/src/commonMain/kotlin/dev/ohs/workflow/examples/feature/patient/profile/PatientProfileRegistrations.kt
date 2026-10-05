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
package dev.ohs.workflow.examples.feature.patient.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.vector.ImageVector
import dev.ohs.player.client.registry.ViewRegistry
import dev.ohs.player.client.registry.registerComponent
import dev.ohs.player.client.registry.registerLayout
import dev.ohs.player.client.renderer.ComponentRenderer
import dev.ohs.player.generated.config.CareItemConfig
import dev.ohs.player.generated.config.PatientHeaderConfig
import dev.ohs.player.generated.config.SectionCardConfig
import dev.ohs.player.generated.state.PatientFacilityVisitState
import dev.ohs.player.generated.state.PatientFollowUpState
import dev.ohs.player.generated.state.PatientReferralOutcomeState
import dev.ohs.player.generated.state.PatientReferralState
import dev.ohs.player.generated.state.PatientSummaryState
import dev.ohs.player.generated.state.PatientTreatmentState
import dev.ohs.player.generated.state.PatientVitalState
import dev.ohs.player.generated.viewtype.ViewTypeCS
import dev.ohs.workflow.examples.feature.component.common.SectionCardLayoutRenderer

fun ViewRegistry.registerPatientProfile() {
  registerComponent<PatientSummaryState, PatientHeaderConfig>(
    ViewTypeCS.PatientHeader,
    PatientHeaderRenderer(),
    PatientHeaderConfig(),
  )
  careSection<PatientReferralState>(
    "Referrals",
    Icons.AutoMirrored.Filled.Send,
    ReferralItemRenderer(),
  )
  careSection<PatientReferralOutcomeState>(
    "Facility outcome",
    Icons.Filled.CheckCircle,
    ReferralOutcomeItemRenderer(),
  )
  careSection<PatientTreatmentState>("Treatment", Icons.Filled.Favorite, TreatmentItemRenderer())
  careSection<PatientFollowUpState>("Follow-ups", Icons.Filled.DateRange, FollowUpItemRenderer())
  careSection<PatientFacilityVisitState>(
    "Facility visits",
    Icons.Filled.Home,
    FacilityVisitItemRenderer(),
  )
  careSection<PatientVitalState>("Vitals", Icons.Filled.Info, VitalItemRenderer())
}

private inline fun <reified T : Any> ViewRegistry.careSection(
  title: String,
  icon: ImageVector,
  renderer: ComponentRenderer<T, CareItemConfig>,
) {
  registerComponent<T, CareItemConfig>(ViewTypeCS.CareItem, renderer, CareItemConfig())
  registerLayout<T>(
    ViewTypeCS.SectionCard,
    SectionCardLayoutRenderer(title, icon, config = SectionCardConfig(collapsible = true)),
  )
}
