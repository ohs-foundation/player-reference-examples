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
package dev.ohs.workflow.examples.workflow

import dev.ohs.fhir.engine.resourceType
import dev.ohs.fhir.model.r4.ActivityDefinition
import dev.ohs.fhir.model.r4.PlanDefinition
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.terminologies.ResourceType
import dev.ohs.fhir.workflow.CanonicalResolver
import dev.ohs.workflow.examples.util.FhirJson
import org.jetbrains.compose.resources.ExperimentalResourceApi
import player_reference_examples.workflow_examples.generated.resources.Res

/**
 * PlanDefinitions and ActivityDefinitions bundled with the app. Kept out of the engine on purpose:
 * an engine write is queued for upload, and the gateway refuses these types.
 */
private val PROTOCOL_FILES: List<String> = emptyList()

class BundledProtocols(private val resources: List<Resource>) : CanonicalResolver {

  override suspend fun resolve(type: ResourceType, canonical: String): Resource? {
    val url = canonical.substringBefore('|')
    return resources.firstOrNull { it.resourceType == type.code && it.url() == url }
  }

  private fun Resource.url(): String? =
    when (this) {
      is PlanDefinition -> url?.value
      is ActivityDefinition -> url?.value
      else -> null
    }

  companion object {
    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(): BundledProtocols =
      BundledProtocols(
        PROTOCOL_FILES.map {
          FhirJson.instance.decodeFromString(
            Resource.serializer(),
            Res.readBytes("files/protocols/$it").decodeToString(),
          )
        }
      )
  }
}
