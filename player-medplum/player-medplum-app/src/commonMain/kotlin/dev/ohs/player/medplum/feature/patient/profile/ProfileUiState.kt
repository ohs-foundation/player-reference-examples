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

import dev.ohs.player.generated.state.PatientAdverseEventState
import dev.ohs.player.generated.state.PatientContactState
import dev.ohs.player.generated.state.PatientGrowthState
import dev.ohs.player.generated.state.PatientImmunizationRecommendationState
import dev.ohs.player.generated.state.PatientImmunizationState
import dev.ohs.player.generated.state.PatientSummaryState

data class ProfileUiState(
  val patient: PatientSummaryState? = null,
  val immunizations: List<PatientImmunizationState> = emptyList(),
  val recommendations: List<PatientImmunizationRecommendationState> = emptyList(),
  val growth: List<PatientGrowthState> = emptyList(),
  val adverseEvents: List<PatientAdverseEventState> = emptyList(),
  val contacts: List<PatientContactState> = emptyList(),
)
