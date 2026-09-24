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
package dev.ohs.player.medplum.feature.questionnaire

import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.player.medplum.data.repository.InMemorySampleFhirRepository
import dev.ohs.player.medplum.util.FhirJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Template extraction is where a filled-in form becomes FHIR, and it fails SILENTLY: an unresolved
 * template value simply produces no field, and a submission that extracted nothing still returns a
 * Bundle and still reports success. The only symptom is a measurement the health worker recorded
 * not being there afterwards.
 *
 * These run the REAL bundled Questionnaire through the REAL service, along the same path the screen
 * takes — load, prepare for launch, submit. Extracting from the questionnaire as loaded rather than
 * as prepared passes an isolated extraction test and still writes records attached to
 * `Patient/__PATIENT_ID__`, so the prepare step is part of what is under test here.
 */
class GrowthExtractionTest {

  private val json = FhirJson.instance

  private suspend fun submit(response: QuestionnaireResponse): List<Observation> {
    val repository = InMemorySampleFhirRepository()
    val service = QuestionnaireService(repository)
    val questionnaire = service.getQuestionnaire(QuestionnaireIds.GROWTH_MONITORING)
    val prepared =
      service.prepareForLaunch(questionnaire, QuestionnaireLaunchContext(patientId = "child-1"))
    service.submit(prepared, response)
    return repository.all("Observation").filterIsInstance<Observation>()
  }

  private fun responseWith(vararg measurements: Pair<String, String>): QuestionnaireResponse {
    val answers =
      measurements.joinToString(",") { (linkId, value) ->
        """{"linkId": "$linkId", "answer": [{"valueDecimal": $value}]}"""
      }
    return json.decodeFromString(
      QuestionnaireResponse.serializer(),
      """
      {
        "resourceType": "QuestionnaireResponse",
        "status": "completed",
        "item": [
          {"linkId": "patient-id", "answer": [{"valueString": "child-1"}]},
          {"linkId": "growth-date", "answer": [{"valueDateTime": "2026-09-21T10:00:00Z"}]}
          ${if (answers.isEmpty()) "" else ", $answers"}
        ]
      }
      """,
    )
  }

  @Test
  fun aCompletedVisitIsSavedAsOneObservationPerMeasurement() = runTest {
    val observations =
      submit(
        responseWith("growth-weight" to "8.9", "growth-height" to "71.5", "growth-muac" to "14.4")
      )

    assertEquals(3, observations.size, "expected weight, height and MUAC")
    val byCode = observations.associateBy { it.code.coding.firstOrNull()?.code?.value.orEmpty() }
    assertEquals(setOf("29463-7", "8302-2", "56072-2"), byCode.keys)

    byCode.forEach { (code, observation) ->
      val quantity = (observation.value as? Observation.Value.Quantity)?.value
      assertTrue(quantity?.value?.value != null, "$code lost its recorded value")
      assertTrue(quantity?.unit?.value != null, "$code lost its unit")
      assertTrue(
        observation.effective is Observation.Effective.DateTime,
        "$code lost the date it was measured",
      )
    }
  }

  @Test
  fun measurementsAreAttachedToTheChildTheFormWasOpenedFor() = runTest {
    val observations = submit(responseWith("growth-weight" to "8.9"))

    // The failure this guards against is not an error -- it is a record saved against the literal
    // placeholder, which belongs to no child and never appears on any profile.
    observations.forEach { assertEquals("Patient/child-1", it.subject?.reference?.value) }
  }

  @Test
  fun aSkippedMeasurementIsNotSavedAsAnEmptyReading() = runTest {
    val observations = submit(responseWith("growth-weight" to "8.9"))

    // No tape measure that day must mean no height record, not a height that reads blank.
    assertEquals(1, observations.size)
    assertEquals("29463-7", observations.single().code.coding.firstOrNull()?.code?.value)
  }
}
