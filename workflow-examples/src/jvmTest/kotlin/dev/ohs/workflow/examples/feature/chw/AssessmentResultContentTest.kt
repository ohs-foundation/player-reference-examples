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

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.ServiceRequest
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.workflow.examples.workflow.AssessmentResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AssessmentResultContentTest {
  private val referral =
    ServiceRequest(
      status = Enumeration(value = ServiceRequest.RequestStatus.Active),
      intent = Enumeration(value = ServiceRequest.RequestIntent.Proposal),
      subject = Reference(reference = FhirString(value = "Patient/child-1")),
    )
  private val amoxicillin =
    MedicationRequest(
      status = Enumeration(value = MedicationRequest.MedicationrequestStatus.Active),
      intent = Enumeration(value = MedicationRequest.MedicationRequestIntent.Proposal),
      medication =
        MedicationRequest.Medication.CodeableConcept(
          CodeableConcept(text = FhirString(value = "Amoxicillin 250 mg dispersible"))
        ),
      subject = Reference(reference = FhirString(value = "Patient/child-1")),
    )

  @Test
  fun referralIsConfirmedFromTheResult() = runComposeUiTest {
    var confirmed = 0
    setContent {
      MaterialTheme {
        AssessmentResultContent(
          AssessmentResult(referral, emptyList(), null),
          referralSent = false,
          sending = false,
          onConfirmReferral = { confirmed++ },
          onDone = {},
        )
      }
    }

    onNodeWithText("Refer urgently to the health facility").assertExists()
    onNodeWithText("Confirm referral").performClick()
    assertEquals(1, confirmed)
  }

  @Test
  fun homeTreatmentHasNothingToConfirm() = runComposeUiTest {
    setContent {
      MaterialTheme {
        AssessmentResultContent(
          AssessmentResult(null, listOf(amoxicillin), null),
          referralSent = false,
          sending = false,
          onConfirmReferral = {},
          onDone = {},
        )
      }
    }

    onNodeWithText("Amoxicillin 250 mg dispersible").assertExists()
    assertTrue(onAllNodesWithText("Confirm referral").fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun sentReferralSaysSo() = runComposeUiTest {
    setContent {
      MaterialTheme {
        AssessmentResultContent(
          AssessmentResult(referral, emptyList(), null),
          referralSent = true,
          sending = false,
          onConfirmReferral = {},
          onDone = {},
        )
      }
    }

    onNodeWithText("Referral sent to the facility").assertExists()
    assertTrue(onAllNodesWithText("Confirm referral").fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun confirmIsDisabledWhileSending() = runComposeUiTest {
    setContent {
      MaterialTheme {
        AssessmentResultContent(
          AssessmentResult(referral, emptyList(), null),
          referralSent = false,
          sending = true,
          onConfirmReferral = {},
          onDone = {},
        )
      }
    }

    onNodeWithText("Confirm referral").assertIsNotEnabled()
  }
}
