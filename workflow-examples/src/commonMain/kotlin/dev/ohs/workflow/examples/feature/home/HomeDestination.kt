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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import dev.ohs.workflow.examples.auth.AppRole
import org.jetbrains.compose.resources.StringResource
import player_reference_examples.workflow_examples.generated.resources.Res
import player_reference_examples.workflow_examples.generated.resources.home_destination_follow_ups
import player_reference_examples.workflow_examples.generated.resources.home_destination_patients
import player_reference_examples.workflow_examples.generated.resources.home_destination_queue
import player_reference_examples.workflow_examples.generated.resources.home_destination_referrals
import player_reference_examples.workflow_examples.generated.resources.role_chw
import player_reference_examples.workflow_examples.generated.resources.role_clinician
import player_reference_examples.workflow_examples.generated.resources.role_nurse

/** A top-level destination reachable from [HomeScreen]'s navigation drawer. */
enum class HomeDestination(val label: StringResource, val icon: ImageVector) {
  Patients(label = Res.string.home_destination_patients, icon = Icons.Filled.Person),
  FollowUps(label = Res.string.home_destination_follow_ups, icon = Icons.Filled.DateRange),
  Referrals(label = Res.string.home_destination_referrals, icon = Icons.AutoMirrored.Filled.Send),
  Queue(label = Res.string.home_destination_queue, icon = Icons.AutoMirrored.Filled.List),
}

/** The destinations a role works in, the first being where it lands. */
fun AppRole.destinations(): List<HomeDestination> =
  when (this) {
    AppRole.CHW ->
      listOf(HomeDestination.Patients, HomeDestination.FollowUps, HomeDestination.Referrals)
    AppRole.NURSE -> listOf(HomeDestination.Patients)
    AppRole.CLINICIAN -> listOf(HomeDestination.Queue)
  }

val AppRole.label: StringResource
  get() =
    when (this) {
      AppRole.CHW -> Res.string.role_chw
      AppRole.NURSE -> Res.string.role_nurse
      AppRole.CLINICIAN -> Res.string.role_clinician
    }
