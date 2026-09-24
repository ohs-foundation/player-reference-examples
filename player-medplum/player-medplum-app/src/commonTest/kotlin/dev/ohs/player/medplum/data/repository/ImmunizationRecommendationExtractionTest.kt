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

import dev.ohs.fhir.model.r4.Resource
import dev.ohs.player.client.model.SearchResult
import dev.ohs.player.generated.state.PatientImmunizationRecommendationState
import dev.ohs.player.medplum.data.Extraction.extractor
import dev.ohs.player.medplum.util.FhirJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The ImmunizationRecommendation ViewDefinition uses a NESTED select with `forEach` over
 * `recommendation[]` -- the only one in this app that does. If the extractor silently returns
 * nothing, the profile's "Immunisations due" section just never appears, with no error anywhere.
 * That is the failure mode this test exists to catch.
 */
class ImmunizationRecommendationExtractionTest {

  private val patient: Resource =
    FhirJson.instance.decodeFromString(
      Resource.serializer(),
      """
        {
          "resourceType": "Patient",
          "id": "child-1",
          "name": [{"family": "Chebet", "given": ["Baraka"]}],
          "gender": "male",
          "birthDate": "2025-11-12",
          "active": true
        }
      """,
    )

  private val recommendation: Resource =
    FhirJson.instance.decodeFromString(
      Resource.serializer(),
      """
        {
          "resourceType": "ImmunizationRecommendation",
          "id": "rec-1",
          "patient": {"reference": "Patient/child-1"},
          "date": "2026-09-20T17:51:22.000Z",
          "recommendation": [
            {
              "vaccineCode": [{
                "coding": [{"system": "http://hl7.org/fhir/sid/cvx", "code": "19", "display": "BCG"}],
                "text": "BCG"
              }],
              "forecastStatus": {"coding": [{"code": "complete"}]},
              "dateCriterion": [
                {"code": {"coding": [{"system": "http://loinc.org", "code": "30980-7"}]},
                 "value": "2025-11-12T00:00:00.000Z"}
              ],
              "doseNumberPositiveInt": 1,
              "series": "BCG"
            },
            {
              "vaccineCode": [{
                "coding": [{"system": "http://hl7.org/fhir/sid/cvx", "code": "120", "display": "pentavalent"}],
                "text": "Penta 2"
              }],
              "forecastStatus": {"coding": [{"code": "overdue"}]},
              "dateCriterion": [
                {"code": {"coding": [{"system": "http://loinc.org", "code": "30980-7"}]},
                 "value": "2026-01-21T00:00:00.000Z"},
                {"code": {"coding": [{"system": "http://loinc.org", "code": "59778-1"}]},
                 "value": "2026-02-18T00:00:00.000Z"}
              ],
              "doseNumberPositiveInt": 2,
              "series": "PENTA"
            }
          ]
        }
      """,
    )

  private val searchResult =
    SearchResult(
      resource = patient,
      included = mapOf("patient" to listOf(patient)),
      revIncluded = mapOf(("ImmunizationRecommendation" to "patient") to listOf(recommendation)),
    )

  @Test
  fun `forEach over recommendation produces one state per dose`() {
    runTest {
      val states = extractor.extract<PatientImmunizationRecommendationState>(searchResult)
      assertEquals(2, states.size, "expected one row per recommendation[] entry")
    }
  }

  @Test
  fun `dose columns are populated from the nested select`() {
    runTest {
      val states = extractor.extract<PatientImmunizationRecommendationState>(searchResult)
      val penta = states.single { it.series == "PENTA" }
      assertEquals("Penta 2", penta.doseLabel)
      assertEquals("overdue", penta.forecastStatus)
      assertEquals(2, penta.doseNumber)
      assertEquals("120", penta.vaccineCode)
    }
  }

  @Test
  // No comma in the name: Kotlin/Native rejects it in a backticked identifier, and commonTest
  // compiles for iOS too.
  fun `dueDate resolves the LOINC-coded date criterion rather than merely the first one`() {
    runTest {
      val states = extractor.extract<PatientImmunizationRecommendationState>(searchResult)
      val penta = states.single { it.series == "PENTA" }
      // 30980-7 is "Date vaccine due"; 59778-1 is the overdue date. Picking the wrong one would
      // silently show the overdue date as the due date.
      assertTrue(
        penta.dueDate?.toString()?.startsWith("2026-01-21") == true,
        "expected the 30980-7 due date, got ${penta.dueDate}",
      )
    }
  }

  @Test
  fun `patient columns are joined onto every dose row`() {
    runTest {
      val states = extractor.extract<PatientImmunizationRecommendationState>(searchResult)
      assertTrue(states.all { it.patientId == "child-1" }, "join to PatientSummary did not apply")
    }
  }
}
