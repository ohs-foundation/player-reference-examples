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

import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.RelatedPerson
import dev.ohs.fhir.model.r4.terminologies.AdministrativeGender
import dev.ohs.player.medplum.data.repository.InMemorySampleFhirRepository
import dev.ohs.player.medplum.util.FhirJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Registering a child writes two linked resources from one form. The link is the part that breaks
 * quietly: if the caregiver's `patient` reference does not resolve to the Patient created in the
 * same submission, both records save happily and the child simply has no caregiver.
 */
class ChildRegistrationExtractionTest {

  private val json = FhirJson.instance

  private suspend fun register(
    launchContext: QuestionnaireLaunchContext = SIGNED_IN
  ): InMemorySampleFhirRepository {
    val repository = InMemorySampleFhirRepository()
    val service = QuestionnaireService(repository)
    val questionnaire = service.getQuestionnaire(QuestionnaireIds.CHILD_REGISTRATION)
    val prepared = service.prepareForLaunch(questionnaire, launchContext)
    service.submit(prepared, response())
    return repository
  }

  private companion object {
    val SIGNED_IN = QuestionnaireLaunchContext(practitionerId = "prac-7", organizationId = "org-3")
  }

  private fun response(): QuestionnaireResponse =
    json.decodeFromString(
      QuestionnaireResponse.serializer(),
      """
      {
        "resourceType": "QuestionnaireResponse",
        "status": "completed",
        "item": [
          {"linkId": "child-given", "answer": [{"valueString": "Mercy"}]},
          {"linkId": "child-family", "answer": [{"valueString": "Otieno"}]},
          {"linkId": "child-sex", "answer": [{"valueCoding": {"system": "http://hl7.org/fhir/administrative-gender", "code": "female", "display": "Female"}}]},
          {"linkId": "child-dob", "answer": [{"valueDate": "2026-06-01"}]},
          {"linkId": "caregiver-given", "answer": [{"valueString": "Esther"}]},
          {"linkId": "caregiver-family", "answer": [{"valueString": "Otieno"}]},
          {"linkId": "caregiver-relationship", "answer": [
            {"valueCoding": {"system": "http://terminology.hl7.org/CodeSystem/v3-RoleCode",
                             "code": "MTH", "display": "mother"}}]},
          {"linkId": "caregiver-phone", "answer": [{"valueString": "+254700000001"}]}
        ]
      }
      """,
    )

  @Test
  fun theChildIsSavedWithTheDetailsTheRegisterShows() = runTest {
    val child = register().all("Patient").filterIsInstance<Patient>().single()

    assertEquals("Mercy", child.name.first().given.first().value)
    assertEquals("Otieno", child.name.first().family?.value)
    assertEquals(AdministrativeGender.Female, child.gender?.value)
    assertEquals("2026-06-01", child.birthDate?.value?.toString())
    // No ID pool: the business identifier IS the allocated UUID, so two devices registering
    // offline cannot collide on a number.
    val identifier = child.identifier.single()
    assertEquals("https://medplum.player.ohs.dev/child-id", identifier.system?.value)
    assertEquals(child.id, identifier.value?.value)
    // The allocated variable is a full "urn:uuid:" URL; carrying that through as the id yields an
    // invalid FHIR id and a reference that resolves to nothing.
    assertTrue(
      identifier.value?.value?.startsWith("urn:uuid:") == false,
      "the identifier kept the urn:uuid: prefix",
    )
  }

  @Test
  fun theCaregiverIsLinkedToTheChildCreatedInTheSameSubmission() = runTest {
    val repository = register()
    val child = repository.all("Patient").filterIsInstance<Patient>().single()
    val caregiver = repository.all("RelatedPerson").filterIsInstance<RelatedPerson>().single()

    assertEquals("Patient/${child.id}", caregiver.patient.reference?.value)
    assertEquals("MTH", caregiver.relationship.first().coding.first().code?.value)
    assertEquals("Esther", caregiver.name.first().given.first().value)
    assertEquals(
      "+254700000001",
      caregiver.telecom.first().value?.value,
      "the caregiver's phone is the only way to follow up a defaulter",
    )
  }

  @Test
  fun theChildRecordsWhoRegisteredThemAndWhere() = runTest {
    val child = register().all("Patient").filterIsInstance<Patient>().single()

    assertEquals("Practitioner/prac-7", child.generalPractitioner.single().reference?.value)
    assertEquals("Organization/org-3", child.managingOrganization?.reference?.value)
  }

  /**
   * This is FHIR data about the child, NOT what scopes access. Medplum stamps `meta.account` from
   * the signed-in practitioner's AccessPolicy compartment, which this FHIR model cannot even
   * represent. Treating `managingOrganization` as the security boundary would be a mistake: a
   * client that can write the field can write any value into it.
   */
  @Test
  fun theFacilityOnTheRecordIsNotWhatEnforcesScoping() = runTest {
    val child = register().all("Patient").filterIsInstance<Patient>().single()

    assertNotNull(child.managingOrganization)
    assertEquals(null, child.meta?.security?.firstOrNull())
  }

  /**
   * An unknown practitioner or facility must remove the reference, not write the placeholder
   * through. `Practitioner/__PRACTITIONER_ID__` is structurally valid FHIR that the server accepts
   * and that resolves to nothing — the same silent failure that once attached every growth
   * measurement to `Patient/__PATIENT_ID__`.
   */
  @Test
  fun anUnknownPractitionerOrFacilityLeavesTheFieldOutEntirely() = runTest {
    val child =
      register(QuestionnaireLaunchContext()).all("Patient").filterIsInstance<Patient>().single()

    assertTrue(child.generalPractitioner.isEmpty(), "wrote a dangling practitioner reference")
    assertEquals(null, child.managingOrganization, "wrote a dangling organization reference")
    // Dropping those two must not take the rest of the registration with it.
    assertEquals("Mercy", child.name.first().given.first().value)
  }

  /**
   * Absent `deceased` means "not recorded"; `false` means "asked and alive". The forecast treats a
   * child as active either way, but a register that cannot distinguish the two cannot later report
   * on outcomes at all.
   */
  @Test
  fun theChildIsRecordedAsAlive() = runTest {
    val child = register().all("Patient").filterIsInstance<Patient>().single()

    assertEquals(false, child.deceased?.asBoolean()?.value?.value)
  }

  /**
   * FHIR gives `Patient.contact` no reference to a RelatedPerson — the two are alternatives, and
   * `Patient.link` is wrong here because it asserts the SAME individual. The standard
   * `patient-relatedPerson` extension is the spec's answer for exactly this case, and without it a
   * reader sees a contact and a RelatedPerson who merely happen to share a name and a number.
   *
   * The reference must also point at the caregiver created in THIS submission, which is why it is
   * built from the same allocated id rather than being matched on afterwards.
   */
  @Test
  fun theInlineContactIsLinkedToTheCaregiverResource() = runTest {
    val repository = register()
    val child = repository.all("Patient").filterIsInstance<Patient>().single()
    val caregiver = repository.all("RelatedPerson").filterIsInstance<RelatedPerson>().single()

    val link =
      child.contact.single().extension.single {
        it.url == "http://hl7.org/fhir/StructureDefinition/patient-relatedPerson"
      }

    assertEquals(
      "RelatedPerson/${caregiver.id}",
      link.value?.asReference()?.value?.reference?.value,
    )
  }

  /**
   * The caregiver is written twice from one set of answers: as a standalone RelatedPerson and
   * inline as Patient.contact. The inline copy travels with the child record; the RelatedPerson is
   * what the profile's caregiver card reads and what can be referenced on its own.
   */
  @Test
  fun theCaregiverIsAlsoRecordedInlineOnTheChild() = runTest {
    val repository = register()
    val child = repository.all("Patient").filterIsInstance<Patient>().single()
    val caregiver = repository.all("RelatedPerson").filterIsInstance<RelatedPerson>().single()
    val contact = child.contact.single()

    assertEquals("Esther", contact.name?.given?.first()?.value)
    assertEquals("Otieno", contact.name?.family?.value)
    assertEquals("MTH", contact.relationship.first().coding.first().code?.value)
    assertEquals("+254700000001", contact.telecom.first().value?.value)

    // Both copies come from the same answers, so they can never disagree.
    assertEquals(caregiver.name.first().family?.value, contact.name?.family?.value)
    assertEquals(caregiver.telecom.first().value?.value, contact.telecom.first().value?.value)
  }
}
