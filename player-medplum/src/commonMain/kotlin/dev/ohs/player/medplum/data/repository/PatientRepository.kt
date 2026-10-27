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

import dev.ohs.player.generated.state.PatientAdverseEventState
import dev.ohs.player.generated.state.PatientContactState
import dev.ohs.player.generated.state.PatientGrowthState
import dev.ohs.player.generated.state.PatientImmunizationRecommendationState
import dev.ohs.player.generated.state.PatientImmunizationState
import dev.ohs.player.generated.state.PatientSummaryState
import dev.ohs.player.medplum.data.Extraction.extractor
import dev.ohs.player.medplum.data.datasource.patientListSearchResults
import dev.ohs.player.medplum.data.datasource.patientProfileSearchResult
import dev.ohs.player.medplum.feature.patient.profile.ProfileUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PatientRepository(private val fhirRepository: FhirRepository) {

  // FhirPathEvaluator holds mutable state is not concurrent-safe.
  // limitedParallelism(1) serializes all extraction on a single background thread without any
  // explicit locking.
  private val extractorDispatcher = Dispatchers.Default.limitedParallelism(1)

  /**
   * Last extracted profile, keyed by patient and data revision.
   *
   * Extraction is expensive for a reason that is not ours to fix: the FHIRPath engine builds a
   * fresh ANTLR lexer and parser for EVERY column of EVERY row (see `FhirPathEngine`), so one
   * profile is several hundred parses and roughly 11MB of short-lived objects. Nothing about a
   * profile changes between revisions, so re-deriving it for the same revision is pure waste —
   * which is what happened on every recomposition and every screen that re-observed the flow.
   */
  private var cachedProfile: Pair<Pair<String, Long>, ProfileUiState>? = null

  fun observePatients(query: String? = null): Flow<List<PatientSummaryState>> =
    fhirRepository.revision.map { getPatients(query) }

  suspend fun getPatients(query: String? = null): List<PatientSummaryState> =
    withContext(extractorDispatcher) {
      // One indexed search, already ordered newest-first by the engine. Extraction preserves that
      // order, so the register needs no sort of its own.
      patientListSearchResults(fhirRepository, query).mapNotNull {
        extractor.extract<PatientSummaryState>(it).firstOrNull()
      }
    }

  fun observePatientProfile(patientId: String): Flow<ProfileUiState> =
    fhirRepository.revision.map { revision -> getPatientProfile(patientId, revision) }

  suspend fun getPatientProfile(
    patientId: String,
    revision: Long = fhirRepository.revision.value,
  ): ProfileUiState =
    withContext(extractorDispatcher) {
      val key = patientId to revision
      cachedProfile
        ?.takeIf { it.first == key }
        ?.let {
          return@withContext it.second
        }

      val result =
        patientProfileSearchResult(patientId, fhirRepository) ?: return@withContext ProfileUiState()
      val profile =
        ProfileUiState(
          patient = extractor.extract<PatientSummaryState>(result).firstOrNull(),
          immunizations = extractor.extract<PatientImmunizationState>(result),
          // Ordered by due date so the most overdue dose is the first thing a health worker sees.
          recommendations =
            extractor.extract<PatientImmunizationRecommendationState>(result).sortedBy {
              it.dueDate?.toString()
            },
          // Most recent measurement first — the current weight is the one being asked about.
          growth =
            extractor.extract<PatientGrowthState>(result).sortedByDescending {
              it.measurementDate?.toString()
            },
          // Most recent first, like every other clinical list here.
          adverseEvents =
            extractor.extract<PatientAdverseEventState>(result).sortedByDescending {
              it.adverseEventDate?.toString()
            },
          contacts =
            extractor.extract<PatientContactState>(result).filter {
              it.contactGivenName != null || it.contactFamilyName != null
            },
        )

      cachedProfile = key to profile
      profile
    }
}
