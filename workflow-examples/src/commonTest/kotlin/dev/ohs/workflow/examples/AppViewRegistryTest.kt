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
package dev.ohs.workflow.examples

import dev.ohs.player.client.layout.GridListRenderer
import dev.ohs.player.client.layout.HorizontalListRenderer
import dev.ohs.player.client.layout.VerticalListRenderer
import dev.ohs.player.client.registry.componentRenderer
import dev.ohs.player.client.registry.layoutRenderer
import dev.ohs.player.generated.state.PatientFacilityVisitState
import dev.ohs.player.generated.state.PatientFollowUpState
import dev.ohs.player.generated.state.PatientReferralOutcomeState
import dev.ohs.player.generated.state.PatientReferralState
import dev.ohs.player.generated.state.PatientSummaryState
import dev.ohs.player.generated.state.PatientTreatmentState
import dev.ohs.player.generated.state.PatientVitalState
import dev.ohs.player.generated.viewtype.ViewTypeCS
import kotlin.test.Test

class AppViewRegistryTest {

  @Test
  fun allRequiredRenderersAreRegistered() {
    val registry = buildAppViewRegistry()

    // Patient list — component + every layout
    registry.componentRenderer<PatientSummaryState>(ViewTypeCS.PatientCard)
    registry.layoutRenderer<PatientSummaryState>(VerticalListRenderer.VIEW_TYPE)
    registry.layoutRenderer<PatientSummaryState>(HorizontalListRenderer.VIEW_TYPE)
    registry.layoutRenderer<PatientSummaryState>(GridListRenderer.VIEW_TYPE)

    // Patient profile header
    registry.componentRenderer<PatientSummaryState>(ViewTypeCS.PatientHeader)

    // Care record sections
    registry.componentRenderer<PatientReferralState>(ViewTypeCS.CareItem)
    registry.layoutRenderer<PatientReferralState>(ViewTypeCS.SectionCard)
    registry.componentRenderer<PatientReferralOutcomeState>(ViewTypeCS.CareItem)
    registry.componentRenderer<PatientTreatmentState>(ViewTypeCS.CareItem)
    registry.componentRenderer<PatientFollowUpState>(ViewTypeCS.CareItem)
    registry.componentRenderer<PatientFacilityVisitState>(ViewTypeCS.CareItem)
    registry.componentRenderer<PatientVitalState>(ViewTypeCS.CareItem)
    registry.layoutRenderer<PatientVitalState>(ViewTypeCS.SectionCard)
  }
}
