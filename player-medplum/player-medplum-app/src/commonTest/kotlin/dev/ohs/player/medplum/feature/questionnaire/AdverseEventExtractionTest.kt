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

import dev.ohs.fhir.model.r4.AdverseEvent
import dev.ohs.fhir.model.r4.Immunization
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.player.medplum.data.repository.InMemorySampleFhirRepository
import dev.ohs.player.medplum.data.repository.PatientRepository
import dev.ohs.player.medplum.util.FhirJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Template extraction fails SILENTLY -- an unresolved value just produces no field, and the
 * submission still reports success. For an AEFI that matters more than for a growth measurement: a
 * report that loses which vaccine it was about is not a weaker report, it is a useless one.
 *
 * These drive the real bundled Questionnaire through the real service along the screen's path (load
 * -> prepareForLaunch -> submit), because the vaccine options only exist after prepare.
 */
class AdverseEventExtractionTest {

  private val json = FhirJson.instance

  private fun immunization(id: String, vaccine: String, on: String): Immunization =
    json.decodeFromString(
      Immunization.serializer(),
      """
      {
        "resourceType": "Immunization",
        "id": "$id",
        "status": "completed",
        "vaccineCode": {"text": "$vaccine"},
        "patient": {"reference": "Patient/child-1"},
        "occurrenceDateTime": "$on"
      }
      """,
    )

  private suspend fun repositoryWithDoses(): InMemorySampleFhirRepository {
    val repository = InMemorySampleFhirRepository()
    repository.upsert(
      json.decodeFromString(
        dev.ohs.fhir.model.r4.Patient.serializer(),
        """
        {"resourceType": "Patient", "id": "child-1", "active": true,
         "name": [{"family": "Kamau", "given": ["Zawadi"]}], "birthDate": "2026-01-01"}
        """,
      )
    )
    repository.upsert(immunization("imm-penta-2", "Penta 2", "2026-05-02T09:00:00Z"))
    repository.upsert(immunization("imm-bcg", "BCG", "2026-01-04T09:00:00Z"))
    return repository
  }

  private fun responseWith(
    vaccineId: String = "imm-penta-2",
    reaction: String = "Fever",
    onset: String = "2026-05-03T14:00:00Z",
    severity: Pair<String, String> = "moderate" to "Moderate",
    serious: Pair<String, String> = "Non-serious" to "Non-serious",
    outcome: Pair<String, String> = "resolved" to "Recovered",
  ): QuestionnaireResponse =
    json.decodeFromString(
      QuestionnaireResponse.serializer(),
      """
      {
        "resourceType": "QuestionnaireResponse",
        "status": "completed",
        "item": [
          {"linkId": "patient-id", "answer": [{"valueString": "child-1"}]},
          {"linkId": "aefi-vaccine", "answer": [{"valueCoding": {"code": "$vaccineId"}}]},
          {"linkId": "aefi-onset", "answer": [{"valueDateTime": "$onset"}]},
          {"linkId": "aefi-reaction", "answer": [{"valueString": "$reaction"}]},
          {"linkId": "aefi-severity", "answer": [{"valueCoding":
            {"system": "http://terminology.hl7.org/CodeSystem/adverse-event-severity",
             "code": "${severity.first}", "display": "${severity.second}"}}]},
          {"linkId": "aefi-serious", "answer": [{"valueCoding":
            {"system": "http://terminology.hl7.org/CodeSystem/adverse-event-seriousness",
             "code": "${serious.first}", "display": "${serious.second}"}}]},
          {"linkId": "aefi-outcome", "answer": [{"valueCoding":
            {"system": "http://terminology.hl7.org/CodeSystem/adverse-event-outcome",
             "code": "${outcome.first}", "display": "${outcome.second}"}}]}
        ]
      }
      """,
    )

  private suspend fun submit(response: QuestionnaireResponse): List<AdverseEvent> {
    val repository = repositoryWithDoses()
    val service = QuestionnaireService(repository)
    val questionnaire = service.getQuestionnaire(QuestionnaireIds.ADVERSE_EVENT)
    val prepared =
      service.prepareForLaunch(
        questionnaire,
        QuestionnaireLaunchContext(patientId = "child-1", practitionerId = "prac-1"),
      )
    service.submit(prepared, response)
    return repository.all("AdverseEvent").filterIsInstance<AdverseEvent>()
  }

  @Test
  fun theVaccineQuestionIsFilledWithTheDosesThisChildActuallyReceived() = runTest {
    val service = QuestionnaireService(repositoryWithDoses())
    val prepared =
      service.prepareForLaunch(
        service.getQuestionnaire(QuestionnaireIds.ADVERSE_EVENT),
        QuestionnaireLaunchContext(patientId = "child-1"),
      )

    // Newest dose first: a reaction is nearly always to the most recent one.
    assertTrue(
      prepared.indexOf("imm-penta-2") in 0 until prepared.indexOf("imm-bcg"),
      "expected both doses as options, newest first",
    )
    assertTrue(prepared.contains("Penta 2 — 2026-05-02"), "the option must name the dose and date")
  }

  @Test
  fun aReportIsSavedAsOneAdverseEventAgainstTheDoseThatCausedIt() = runTest {
    val events = submit(responseWith())

    val event = events.singleOrNull()
    assertNotNull(event, "expected exactly one AdverseEvent")
    assertEquals("Patient/child-1", event.subject.reference?.value)
    assertEquals(
      "Immunization/imm-penta-2",
      event.suspectEntity.firstOrNull()?.instance?.reference?.value,
      "an AEFI that loses which dose it was about is useless",
    )
    assertEquals("Fever", event.event?.text?.value)
    assertTrue(event.date?.value.toString().startsWith("2026-05-03"))
  }

  @Test
  fun severitySeriousnessAndOutcomeUseFhirsOwnCodeSystems() = runTest {
    val event = submit(responseWith(severity = "severe" to "Severe")).single()

    assertEquals("severe", event.severity?.coding?.firstOrNull()?.code?.value)
    assertEquals(
      "http://terminology.hl7.org/CodeSystem/adverse-event-severity",
      event.severity?.coding?.firstOrNull()?.system?.value,
    )
    assertEquals("Non-serious", event.seriousness?.coding?.firstOrNull()?.code?.value)
    assertEquals("resolved", event.outcome?.coding?.firstOrNull()?.code?.value)
  }

  @Test
  fun aReportedEventShowsUpOnTheChildsProfile() = runTest {
    // Reporting an AEFI and then never seeing it again is the failure this guards against: the
    // resource has to reach the profile through the view pipeline, not just the database.
    val repository = repositoryWithDoses()
    val service = QuestionnaireService(repository)
    val prepared =
      service.prepareForLaunch(
        service.getQuestionnaire(QuestionnaireIds.ADVERSE_EVENT),
        QuestionnaireLaunchContext(patientId = "child-1", practitionerId = "prac-1"),
      )
    service.submit(prepared, responseWith(reaction = "Convulsions"))

    val profile = PatientRepository(repository).getPatientProfile("child-1")

    val reported = profile.adverseEvents.singleOrNull()
    assertNotNull(reported, "the reported AEFI never reached the profile")
    assertEquals("Convulsions", reported.adverseEventReaction)
    assertEquals("moderate", reported.adverseEventSeverity)
    assertEquals("Recovered", reported.adverseEventOutcome)
    assertEquals("imm-penta-2", reported.adverseEventVaccineRef)
    // Joined through the view map: the row must say "Penta 2", never the bare uuid.
    assertEquals("Penta 2", reported.vaccineName, "the row has to name the dose, not its id")
  }

  @Test
  fun anEventAgainstADifferentDoseNamesThatDose() = runTest {
    // Guards the join itself: with the index keyed wrongly every row would show the same vaccine,
    // which looks plausible until two reports exist.
    val repository = repositoryWithDoses()
    val service = QuestionnaireService(repository)
    val prepared =
      service.prepareForLaunch(
        service.getQuestionnaire(QuestionnaireIds.ADVERSE_EVENT),
        QuestionnaireLaunchContext(patientId = "child-1", practitionerId = "prac-1"),
      )
    service.submit(prepared, responseWith(vaccineId = "imm-bcg", reaction = "Lymphadenitis"))

    val reported = PatientRepository(repository).getPatientProfile("child-1").adverseEvents.single()
    assertEquals("BCG", reported.vaccineName)
  }

  @Test
  fun actualityIsSetSoTheResourceIsValidR4() = runTest {
    // actuality is 1..1 in R4. If the template ever loses it the bundle stops decoding entirely,
    // which is a much louder failure than a missing field -- worth pinning.
    val event = submit(responseWith()).single()
    assertEquals("actual", event.actuality.value?.getCode())
  }
}
