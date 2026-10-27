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
package dev.ohs.player.medplum.data.repository

import dev.ohs.fhir.engine.resourceType
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.player.medplum.util.FhirJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** [FhirRepository] test double for UI tests. */
class InMemorySampleFhirRepository : FhirRepository {
  private val json = FhirJson.instance
  private val resourcesByType = mutableMapOf<String, MutableMap<String, Resource>>()
  private val _revision = MutableStateFlow(0L)

  override val revision: StateFlow<Long> = _revision

  override suspend fun upsert(resource: Resource) {
    store(resource)
    _revision.value += 1
  }

  override suspend fun upsert(bundle: Bundle): Int {
    val resources = bundle.entry.mapNotNull { it.resource }
    resources.forEach(::store)
    if (resources.isNotEmpty()) _revision.value += 1
    return resources.size
  }

  override suspend fun get(resourceType: String, id: String): Resource? {
    return resourcesByType[resourceType]?.get(id)
  }

  override suspend fun all(resourceType: String): List<Resource> {
    return resourcesByType[resourceType]?.values?.toList().orEmpty()
  }

  /**
   * The engine resolves these through indexes; in memory we walk the JSON. Only the reference
   * shapes this app actually writes are understood — a test that stores something else and expects
   * a match is a test making an assumption the real engine would not honour either.
   */
  override suspend fun referencing(
    resourceType: String,
    searchParam: String,
    reference: String,
  ): List<Resource> =
    all(resourceType).filter { resource ->
      val encoded = json.encodeToJsonElement(Resource.serializer(), resource)
      encoded.containsReference(reference)
    }

  override suspend fun searchPatients(query: String?): List<Resource> {
    val patients = all("Patient")
    val term = query?.trim().orEmpty()
    if (term.isEmpty()) return patients
    return patients.filter { patient ->
      json
        .encodeToJsonElement(Resource.serializer(), patient)
        .toString()
        .contains(term, ignoreCase = true)
    }
  }

  override suspend fun countPatients(): Int = all("Patient").size

  private fun store(resource: Resource) {
    val id = resource.id ?: return
    resourcesByType.getOrPut(resource.resourceType) { mutableMapOf() }[id] = resource
  }
}

private fun JsonElement.containsReference(reference: String): Boolean =
  when (this) {
    is JsonObject ->
      entries.any { (key, value) ->
        (key == "reference" && value is JsonPrimitive && value.content == reference) ||
          value.containsReference(reference)
      }

    is JsonArray -> any { it.containsReference(reference) }
    else -> false
  }
