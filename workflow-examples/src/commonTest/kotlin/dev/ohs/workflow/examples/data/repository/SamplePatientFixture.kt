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

import dev.ohs.fhir.model.r4.Resource
import dev.ohs.workflow.examples.util.FhirJson

/** Patient "p1" (Amina Diallo) with an open referral and a home treatment, for UI tests. */
object SamplePatientFixture {
  val resources: List<Resource> =
    listOf(
      FhirJson.instance.decodeFromString(
        Resource.serializer(),
        """
          {
            "resourceType": "Patient",
            "id": "p1",
            "name": [{"family": "Diallo", "given": ["Amina"]}],
            "gender": "female",
            "birthDate": "1990-03-14",
            "active": true
          }
        """
          .trimIndent(),
      ),
      FhirJson.instance.decodeFromString(
        Resource.serializer(),
        """
          {
            "resourceType": "ServiceRequest",
            "id": "referral-p1",
            "status": "active",
            "intent": "order",
            "priority": "urgent",
            "code": {"coding": [{"system": "http://snomed.info/sct", "code": "3457005"}]},
            "subject": {"reference": "Patient/p1"},
            "authoredOn": "2026-10-05T08:00:00Z"
          }
        """
          .trimIndent(),
      ),
      FhirJson.instance.decodeFromString(
        Resource.serializer(),
        """
          {
            "resourceType": "MedicationRequest",
            "id": "treatment-p1",
            "status": "active",
            "intent": "proposal",
            "medicationCodeableConcept": {"text": "Amoxicillin 250 mg dispersible"},
            "subject": {"reference": "Patient/p1"},
            "authoredOn": "2026-10-01T08:00:00Z"
          }
        """
          .trimIndent(),
      ),
    )
}
