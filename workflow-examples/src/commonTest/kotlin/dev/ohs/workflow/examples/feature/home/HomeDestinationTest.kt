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
package dev.ohs.workflow.examples.feature.home

import dev.ohs.workflow.examples.auth.AppRole
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeDestinationTest {

  @Test
  fun chwWorksPatientsFollowUpsAndReferrals() {
    assertEquals(
      listOf(HomeDestination.Patients, HomeDestination.FollowUps, HomeDestination.Referrals),
      AppRole.CHW.destinations(),
    )
  }

  @Test
  fun nurseWorksPatients() {
    assertEquals(listOf(HomeDestination.Patients), AppRole.NURSE.destinations())
  }

  @Test
  fun clinicianWorksTheQueue() {
    assertEquals(listOf(HomeDestination.Queue), AppRole.CLINICIAN.destinations())
  }
}
