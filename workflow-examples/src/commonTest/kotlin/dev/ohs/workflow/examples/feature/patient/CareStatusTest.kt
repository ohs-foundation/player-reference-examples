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

import dev.ohs.player.generated.state.PatientSummaryState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CareStatusTest {
  private fun row(
    referralStatus: String? = null,
    followUpStatus: String? = null,
    followUpFocus: String? = null,
  ) =
    PatientSummaryState(
      referralId = "sr-1",
      referralStatus = referralStatus,
      followUpStatus = followUpStatus,
      followUpFocus = followUpFocus,
    )

  @Test fun noReferralOrFollowUpHasNoStatus() = assertNull(row().careStatus())

  @Test fun openReferralIsReferred() = assertEquals(CareStatus.Referred, row("active").careStatus())

  @Test
  fun closedReferralWithoutFollowUpIsSeenAtFacility() =
    assertEquals(CareStatus.SeenAtFacility, row("completed").careStatus())

  @Test
  fun openFollowUpWinsOverEverything() =
    assertEquals(
      CareStatus.FollowUpDue,
      row("completed", "requested", "ServiceRequest/sr-1").careStatus(),
    )

  @Test
  fun closedFollowUpForTheLatestReferralClosesTheLoop() =
    assertNull(row("completed", "completed", "ServiceRequest/sr-1").careStatus())

  @Test
  fun closedFollowUpForAnOlderReferralLeavesItSeenAtFacility() =
    assertEquals(
      CareStatus.SeenAtFacility,
      row("completed", "completed", "ServiceRequest/older").careStatus(),
    )
}
