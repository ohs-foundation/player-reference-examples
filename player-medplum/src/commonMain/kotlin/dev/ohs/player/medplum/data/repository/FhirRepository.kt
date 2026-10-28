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

import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Resource
import kotlinx.coroutines.flow.StateFlow

/** Minimal persistence seam for FHIR resources, backed by [FhirEngineRepository] in production. */
interface FhirRepository {
  /**
   * Incremented on every successful [upsert]. Implementers must bump this after each write so that
   * observers (e.g. [PatientRepository], [GroupRepository]) know to re-query.
   *
   * TODO: To be deleted, once https://github.com/ohs-foundation/kotlin-fhir-engine/issues/65 is
   *   done
   */
  val revision: StateFlow<Long>

  suspend fun upsert(resource: Resource)

  suspend fun upsert(bundle: Bundle): Int

  suspend fun get(resourceType: String, id: String): Resource?

  suspend fun all(resourceType: String): List<Resource>

  /**
   * Resources of [resourceType] whose [searchParam] reference equals [reference] (e.g.
   * `referencing("Immunization", "patient", "Patient/abc")`).
   *
   * This exists so callers never have to read a whole table and filter in memory. Every reference
   * search parameter is indexed by the engine, so this is an index lookup; [all] is a full scan
   * that deserializes every row of the type. On a register-sized database the difference is the
   * difference between a profile opening instantly and taking seconds.
   */
  suspend fun referencing(
    resourceType: String,
    searchParam: String,
    reference: String,
  ): List<Resource>

  /**
   * Patients, most-recently-updated first, narrowed to [query] when one is given.
   *
   * [query] matches a name substring (case-insensitive) or an exact business identifier — a health
   * worker either types part of a name or reads an ID off a card. Both are indexed lookups.
   */
  suspend fun searchPatients(query: String? = null): List<Resource>

  /**
   * How many patients are in the local database.
   *
   * A count is a `SELECT COUNT(*)`, not a list — it must never deserialize the rows it is counting,
   * which is why this is its own method rather than `searchPatients().size`.
   */
  suspend fun countPatients(): Int
}
