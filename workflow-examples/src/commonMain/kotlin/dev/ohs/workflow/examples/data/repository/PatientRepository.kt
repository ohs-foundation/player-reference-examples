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

import dev.ohs.player.generated.state.PatientSummaryState
import dev.ohs.workflow.examples.data.Extraction.extractor
import dev.ohs.workflow.examples.data.datasource.allPatientIds
import dev.ohs.workflow.examples.data.datasource.patientProfileSearchResult
import dev.ohs.workflow.examples.data.datasource.patientSummarySearchResult
import dev.ohs.workflow.examples.feature.patient.profile.ProfileUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PatientRepository(private val fhirRepository: FhirRepository) {

  // FhirPathEvaluator holds mutable state is not concurrent-safe.
  // limitedParallelism(1) serializes all extraction on a single background thread without any
  // explicit locking.
  private val extractorDispatcher = Dispatchers.Default.limitedParallelism(1)

  fun observePatients(): Flow<List<PatientSummaryState>> =
    fhirRepository.revision.map { getPatients() }

  suspend fun getPatients(): List<PatientSummaryState> =
    withContext(extractorDispatcher) {
      allPatientIds(fhirRepository).mapNotNull { id ->
        patientSummarySearchResult(id, fhirRepository)?.let {
          extractor.extract<PatientSummaryState>(it).firstOrNull()
        }
      }
    }

  fun observePatientProfile(patientId: String): Flow<ProfileUiState> =
    fhirRepository.revision.map { getPatientProfile(patientId) }

  suspend fun getPatientProfile(patientId: String): ProfileUiState =
    withContext(extractorDispatcher) {
      val summary =
        patientSummarySearchResult(patientId, fhirRepository) ?: return@withContext ProfileUiState()
      val record = patientProfileSearchResult(patientId, fhirRepository)!!
      ProfileUiState(
        patient = extractor.extract<PatientSummaryState>(summary).firstOrNull(),
        referrals = extractor.extract(record),
        outcomes = extractor.extract(record),
        treatments = extractor.extract(record),
        followUps = extractor.extract(record),
        visits = extractor.extract(record),
        vitals = extractor.extract(record),
      )
    }
}
