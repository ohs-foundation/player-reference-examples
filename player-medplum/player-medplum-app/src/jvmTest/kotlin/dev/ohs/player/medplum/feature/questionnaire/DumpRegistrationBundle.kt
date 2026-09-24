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

import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.player.medplum.data.repository.InMemorySampleFhirRepository
import dev.ohs.player.medplum.util.FhirJson
import java.io.File
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * Writes the Bundle a real child registration extracts, so it can be POSTed to Medplum verbatim.
 *
 * Not an assertion — a fixture generator. Set `EIR_BUNDLE_OUT` to enable it; it is a no-op in a
 * normal test run. The point is that the bytes sent to the server are produced by the same code
 * path the device uses, rather than being hand-written to match what we hope it produces.
 */
class DumpRegistrationBundle {

  @Test
  fun dumpBundle() = runTest {
    val out = System.getenv("EIR_BUNDLE_OUT") ?: return@runTest
    val repository = InMemorySampleFhirRepository()
    val service = QuestionnaireService(repository)
    val questionnaire = service.getQuestionnaire(QuestionnaireIds.CHILD_REGISTRATION)
    val prepared =
      service.prepareForLaunch(
        questionnaire,
        QuestionnaireLaunchContext(
          practitionerId = System.getenv("EIR_PRACTITIONER_ID"),
          organizationId = System.getenv("EIR_ORGANIZATION_ID"),
        ),
      )
    val result =
      service.submit(
        prepared,
        FhirJson.instance.decodeFromString(
          QuestionnaireResponse.serializer(),
          """
          {
            "resourceType": "QuestionnaireResponse",
            "status": "completed",
            "item": [
              {"linkId": "child-given", "answer": [{"valueString": "Zawadi"}]},
              {"linkId": "child-family", "answer": [{"valueString": "Wanjiru"}]},
              {"linkId": "child-sex", "answer": [{"valueCoding": {"system": "http://hl7.org/fhir/administrative-gender", "code": "female", "display": "Female"}}]},
              {"linkId": "child-dob", "answer": [{"valueDate": "2026-08-14"}]},
              {"linkId": "caregiver-given", "answer": [{"valueString": "Grace"}]},
              {"linkId": "caregiver-family", "answer": [{"valueString": "Wanjiru"}]},
              {"linkId": "caregiver-relationship", "answer": [
                {"valueCoding": {"system": "http://terminology.hl7.org/CodeSystem/v3-RoleCode", "code": "MTH", "display": "mother"}}]},
              {"linkId": "caregiver-phone", "answer": [{"valueString": "+254711223344"}]}
            ]
          }
          """,
        ),
      )
    File(out).writeText(result.bundleJson)
  }
}
