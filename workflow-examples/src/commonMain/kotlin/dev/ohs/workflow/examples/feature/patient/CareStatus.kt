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
package dev.ohs.workflow.examples.feature.patient

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dev.ohs.player.generated.state.PatientSummaryState
import dev.ohs.workflow.examples.feature.component.common.Chip
import org.jetbrains.compose.resources.stringResource
import player_reference_examples.workflow_examples.generated.resources.Res
import player_reference_examples.workflow_examples.generated.resources.care_status_follow_up_due
import player_reference_examples.workflow_examples.generated.resources.care_status_referred
import player_reference_examples.workflow_examples.generated.resources.work_seen_at_facility

/** Where a patient stands in the referral loop, as shown on their list row and header. */
enum class CareStatus {
  Referred,
  SeenAtFacility,
  FollowUpDue,
}

/**
 * An open follow-up comes first; otherwise the latest referral decides, and a completed referral
 * whose follow-up is already done means the loop is closed.
 */
fun PatientSummaryState.careStatus(): CareStatus? =
  when {
    followUpStatus == "requested" -> CareStatus.FollowUpDue
    referralStatus == "active" -> CareStatus.Referred
    referralStatus == "completed" && followUpFocus != "ServiceRequest/$referralId" ->
      CareStatus.SeenAtFacility
    else -> null
  }

@Composable
fun CareStatusChip(status: CareStatus) {
  val (label, color) =
    when (status) {
      CareStatus.Referred ->
        stringResource(Res.string.care_status_referred) to MaterialTheme.colorScheme.error
      CareStatus.SeenAtFacility ->
        stringResource(Res.string.work_seen_at_facility) to MaterialTheme.colorScheme.primary
      CareStatus.FollowUpDue ->
        stringResource(Res.string.care_status_follow_up_due) to MaterialTheme.colorScheme.tertiary
    }
  Chip(label = label, containerColor = color.copy(alpha = 0.12f), contentColor = color)
}
