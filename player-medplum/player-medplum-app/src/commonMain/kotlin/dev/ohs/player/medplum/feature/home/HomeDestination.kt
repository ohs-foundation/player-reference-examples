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
package dev.ohs.player.medplum.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import player_medplum.player_medplum_app.generated.resources.Res
import player_medplum.player_medplum_app.generated.resources.home_destination_children

/**
 * A register reachable from [HomeScreen]'s navigation drawer.
 *
 * `Children` is the only one: this is a child immunization register, and the households register
 * inherited from the upstream reference app was removed with it. Adding a second register later
 * (ANC, say) is a new enum entry, not a rewrite — which is why this stays an enum for one value.
 */
enum class HomeDestination(val label: StringResource, val icon: ImageVector) {
  Children(label = Res.string.home_destination_children, icon = Icons.Filled.Face)
}
