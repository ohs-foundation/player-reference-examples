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
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.workflow.WorkflowRepository

/** [WorkflowRepository] test double; reference search matches on the raw JSON reference value. */
class InMemoryWorkflowRepository : WorkflowRepository {
  val resources = mutableMapOf<Pair<String, String>, Resource>()

  override suspend fun read(type: String, id: String): Resource? = resources[type to id]

  override suspend fun create(resource: Resource): String {
    val id = requireNotNull(resource.id)
    resources[resource.resourceType to id] = resource
    return id
  }

  override suspend fun update(resource: Resource) {
    resources[resource.resourceType to requireNotNull(resource.id)] = resource
  }

  override suspend fun delete(type: String, id: String) {
    resources.remove(type to id)
  }

  override suspend fun searchByReferenceParam(
    type: String,
    param: String,
    referenceValue: String,
  ): List<Resource> =
    resources
      .filterKeys { it.first == type }
      .values
      .filter { it.toString().contains("reference=String(value=$referenceValue") }

  override suspend fun searchByUri(type: String, param: String, uri: String): List<Resource> =
    emptyList()
}
