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
package dev.ohs.player.medplum

import dev.ohs.player.client.layout.GridListRenderer
import dev.ohs.player.client.layout.HorizontalListRenderer
import dev.ohs.player.client.layout.VerticalListRenderer
import dev.ohs.player.client.registry.componentRenderer
import dev.ohs.player.client.registry.layoutRenderer
import dev.ohs.player.generated.state.PatientContactState
import dev.ohs.player.generated.state.PatientGrowthState
import dev.ohs.player.generated.state.PatientImmunizationRecommendationState
import dev.ohs.player.generated.state.PatientImmunizationState
import dev.ohs.player.generated.state.PatientSummaryState
import dev.ohs.player.generated.viewtype.ViewTypeCS
import kotlin.test.Test

class AppViewRegistryTest {

  @Test
  fun allRequiredRenderersAreRegistered() {
    val registry = buildAppViewRegistry()

    // Child register — component + every layout
    registry.componentRenderer<PatientSummaryState>(ViewTypeCS.PatientCard)
    registry.layoutRenderer<PatientSummaryState>(VerticalListRenderer.VIEW_TYPE)
    registry.layoutRenderer<PatientSummaryState>(HorizontalListRenderer.VIEW_TYPE)
    registry.layoutRenderer<PatientSummaryState>(GridListRenderer.VIEW_TYPE)

    // Child profile — every section the profile can render
    registry.componentRenderer<PatientSummaryState>(ViewTypeCS.PatientHeader)
    registry.componentRenderer<PatientImmunizationRecommendationState>(
      ViewTypeCS.ImmunizationRecommendationItem
    )
    registry.componentRenderer<PatientImmunizationState>(ViewTypeCS.ImmunizationItem)
    registry.componentRenderer<PatientGrowthState>(ViewTypeCS.GrowthItem)
    registry.componentRenderer<PatientContactState>(ViewTypeCS.ContactItem)

    // Every profile section is wrapped in a section card; a missing layout renders nothing.
    registry.layoutRenderer<PatientImmunizationRecommendationState>(ViewTypeCS.SectionCard)
    registry.layoutRenderer<PatientImmunizationState>(ViewTypeCS.SectionCard)
    registry.layoutRenderer<PatientGrowthState>(ViewTypeCS.SectionCard)
    registry.layoutRenderer<PatientContactState>(ViewTypeCS.SectionCard)
  }
}
