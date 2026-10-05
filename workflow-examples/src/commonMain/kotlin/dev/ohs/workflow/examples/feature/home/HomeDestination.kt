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
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import dev.ohs.workflow.examples.auth.AppRole
import org.jetbrains.compose.resources.StringResource
import player_reference_examples.workflow_examples.generated.resources.Res
import player_reference_examples.workflow_examples.generated.resources.home_destination_patients

/** A top-level destination reachable from [HomeScreen]'s navigation drawer. */
enum class HomeDestination(val label: StringResource, val icon: ImageVector) {
  Patients(label = Res.string.home_destination_patients, icon = Icons.Filled.Person)
}

/** The destinations a role works in, the first being where it lands. */
fun AppRole.destinations(): List<HomeDestination> =
  when (this) {
    AppRole.CHW,
    AppRole.NURSE,
    AppRole.CLINICIAN -> listOf(HomeDestination.Patients)
  }
