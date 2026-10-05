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
package dev.ohs.workflow.examples.data.repository

import dev.ohs.fhir.engine.FhirEngine
import dev.ohs.fhir.engine.db.ResourceNotFoundException
import dev.ohs.fhir.engine.resourceType
import dev.ohs.fhir.engine.search.Search
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.terminologies.ResourceType
import dev.ohs.workflow.examples.data.DataChangeSignal
import dev.ohs.workflow.examples.generateId
import dev.ohs.workflow.examples.util.FhirJson
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** [FhirRepository] backed by a real on-disk database via [FhirEngine]. */
class FhirEngineRepository(private val fhirEngine: FhirEngine) : FhirRepository {

  private val json = FhirJson.instance

  override val revision: StateFlow<Long> = DataChangeSignal.revision

  override suspend fun upsert(resource: Resource) {
    upsertResource(resource)
    DataChangeSignal.notifyChanged()
  }

  override suspend fun get(resourceType: String, id: String): Resource? {
    return runCatching { fhirEngine.get(ResourceType.valueOf(resourceType), id) }
      .getOrElse { if (it is ResourceNotFoundException) null else throw it }
  }

  override suspend fun all(resourceType: String): List<Resource> {
    return fhirEngine.search<Resource>(Search(ResourceType.valueOf(resourceType))).map {
      it.resource
    }
  }

  private suspend fun upsertResource(resource: Resource) {
    val withId = if (resource.id == null) resource.withId(generateId()) else resource
    val type = ResourceType.valueOf(withId.resourceType)
    val exists = runCatching { fhirEngine.get(type, withId.id!!) }.isSuccess
    if (exists) fhirEngine.update(withId) else fhirEngine.create(withId)
  }

  private fun Resource.withId(newId: String): Resource {
    val obj = json.encodeToJsonElement(Resource.serializer(), this).jsonObject
    return json.decodeFromJsonElement(
      Resource.serializer(),
      JsonObject(obj + ("id" to JsonPrimitive(newId))),
    )
  }
}
