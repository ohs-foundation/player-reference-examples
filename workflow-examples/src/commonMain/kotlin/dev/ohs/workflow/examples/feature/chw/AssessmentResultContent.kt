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
package dev.ohs.workflow.examples.feature.chw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.workflow.examples.workflow.AssessmentResult
import org.jetbrains.compose.resources.stringResource
import player_reference_examples.workflow_examples.generated.resources.Res
import player_reference_examples.workflow_examples.generated.resources.assessment_confirm_referral
import player_reference_examples.workflow_examples.generated.resources.assessment_done
import player_reference_examples.workflow_examples.generated.resources.assessment_follow_up
import player_reference_examples.workflow_examples.generated.resources.assessment_home_care
import player_reference_examples.workflow_examples.generated.resources.assessment_refer
import player_reference_examples.workflow_examples.generated.resources.assessment_referral_sent

/**
 * What the iCCM protocol decided, with the one action the CHW takes on it: sending the referral.
 */
@Composable
fun AssessmentResultContent(
  assessment: AssessmentResult,
  referralSent: Boolean,
  onConfirmReferral: () -> Unit,
  onDone: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    val decisions =
      listOfNotNull(assessment.referral?.let { stringResource(Res.string.assessment_refer) }) +
        assessment.medications.mapNotNull { it.medicineName() } +
        listOfNotNull(assessment.followUp?.let { stringResource(Res.string.assessment_follow_up) })
    if (decisions.isEmpty()) {
      Text(
        stringResource(Res.string.assessment_home_care),
        style = MaterialTheme.typography.bodyLarge,
      )
    }
    decisions.forEach { ListItem(headlineContent = { Text(it) }) }
    when {
      assessment.referral != null && referralSent ->
        Text(
          stringResource(Res.string.assessment_referral_sent),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      assessment.referral != null ->
        Button(onClick = onConfirmReferral, modifier = Modifier.fillMaxWidth()) {
          Text(stringResource(Res.string.assessment_confirm_referral))
        }
    }
    OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
      Text(stringResource(Res.string.assessment_done))
    }
  }
}

private fun MedicationRequest.medicineName(): String? =
  (medication as? MedicationRequest.Medication.CodeableConcept)?.value?.text?.value
