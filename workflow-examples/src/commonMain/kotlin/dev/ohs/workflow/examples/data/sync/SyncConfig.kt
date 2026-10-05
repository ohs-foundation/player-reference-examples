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
package dev.ohs.workflow.examples.data.sync

import dev.ohs.fhir.engine.sync.download.ResourceSearchParams
import dev.ohs.fhir.model.r4.terminologies.ResourceType
import dev.ohs.workflow.examples.auth.AppRole
import dev.ohs.workflow.examples.auth.UserContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import player_reference_examples.workflow_examples.generated.resources.Res

/** What one role downloads: per resource type, the search params and/or the ids to fetch. */
@Serializable data class SyncConfig(val resources: List<SyncResource>)

@Serializable
data class SyncResource(
  val type: String,
  val params: Map<String, String> = emptyMap(),
  val ids: List<String> = emptyList(),
)

@OptIn(ExperimentalResourceApi::class)
suspend fun loadSyncConfig(role: AppRole): SyncConfig =
  Json.decodeFromString(Res.readBytes("files/sync/${role.code}.json").decodeToString())

/**
 * Turns a [SyncConfig] into the engine's download params for one user. `{practitioner}`,
 * `{organization}` and `{location}` are filled from the [UserContext]; an entry that needs a value
 * the user does not have is skipped, so a missing assignment narrows the sync instead of failing
 * it.
 */
object SyncConfigResolver {
  private val placeholder = Regex("""\{(\w+)}""")

  fun resolve(config: SyncConfig, context: UserContext): ResourceSearchParams {
    val types = config.resources.map { it.type }
    require(types.size == types.toSet().size) { "Duplicate resource type in sync config: $types" }
    val values =
      mapOf(
        "practitioner" to context.practitionerId,
        "organization" to context.organizationId,
        "location" to context.locationId,
      )
    return config.resources
      .mapNotNull { resource ->
        val ids =
          resource.ids.takeIf { it.isNotEmpty() }?.let { mapOf("_id" to it.joinToString(",")) }
        val filled =
          (resource.params + ids.orEmpty()).mapValues { (_, value) ->
            fill(value, values) ?: return@mapNotNull null
          }
        ResourceType.valueOf(resource.type) to filled
      }
      .toMap()
  }

  private fun fill(value: String, values: Map<String, String?>): String? {
    var missing = false
    val filled =
      placeholder.replace(value) { match ->
        val key = match.groupValues[1]
        require(key in values) { "Unknown sync placeholder {$key}" }
        values[key] ?: "".also { missing = true }
      }
    return filled.takeUnless { missing }
  }
}
