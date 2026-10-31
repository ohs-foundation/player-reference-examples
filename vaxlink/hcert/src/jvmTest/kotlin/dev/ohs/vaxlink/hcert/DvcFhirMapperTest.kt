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
package dev.ohs.vaxlink.hcert

import dev.ohs.fhir.model.r4.Immunization
import dev.ohs.fhir.model.r4.Provenance
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.terminologies.AdministrativeGender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The one DVCMin to FHIR mapping, pinned on the worked example. */
class DvcFhirMapperTest {
  private val fingerprint = CertificateFingerprint.of(Vectors.hepb.hc1)
  private val recordedAt = 1_793_000_000L

  private fun unverified(dvc: DvcMin = Vectors.hepb.payload) =
    DvcFhirMapper.map(
      dvc,
      DvcMappingOptions(
        patientId = "child-1",
        immunizationId = "imm-1",
        certificateFingerprint = fingerprint,
        recordedAtEpochSeconds = recordedAt,
      ),
    )

  @Test
  fun `the patient carries name, sex, date of birth, nationality and guardian, and no national id`() {
    val patient = unverified().patient
    assertEquals("child-1", patient.id)
    val name = patient.name.single()
    assertEquals("Chanthala SOUVANNALATH", name.text?.value)
    assertEquals("SOUVANNALATH", name.family?.value)
    assertEquals(listOf("Chanthala"), name.given.map { it.value })
    assertEquals(AdministrativeGender.Female, patient.gender?.value)
    assertEquals("2026-10-21", patient.birthDate?.value.toString())
    assertTrue(patient.identifier.isEmpty(), "a newborn has no identification document")
    val nationality = patient.extension.single { it.url == DvcFhirMapper.NATIONALITY_EXTENSION }
    val code =
      nationality.extension
        .single { it.url == "code" }
        .value
        ?.asCodeableConcept()
        ?.value
        ?.coding
        ?.single()
    assertEquals(DvcFhirMapper.ISO_3166 to "LAO", code?.system?.value to code?.code?.value)
    val guardian = patient.contact.single()
    assertEquals("Mali SOUVANNALATH", guardian.name?.text?.value)
    assertEquals("GUARD", guardian.relationship.single().coding.single().code?.value)
  }

  @Test
  fun `the unverified immunization keeps the PreQual coding as issued and says the family reported it`() {
    val immunization = unverified().immunization
    assertEquals("imm-1", immunization.id)
    assertEquals(Immunization.ImmunizationStatusCodes.Completed, immunization.status.value)
    val coding = immunization.vaccineCode.coding.single()
    assertEquals(PreQualTerminology.PRODUCT_IDS, coding.system?.value)
    assertEquals("a3K3X000006MiiXUAS", coding.code?.value)
    assertEquals("Hepatitis B Vaccine (rDNA) (Paediatric)", coding.display?.value)
    assertEquals("Patient/child-1", immunization.patient.reference?.value)
    assertEquals(
      "2026-10-21",
      assertIs<Immunization.Occurrence.DateTime>(immunization.occurrence).value.value.toString(),
    )
    assertEquals("HEPB-2026-0914", immunization.lotNumber?.value)
    assertEquals(false, immunization.primarySource?.value)
    assertEquals("record", immunization.reportOrigin?.coding?.single()?.code?.value)
    assertEquals(DvcFhirMapper.REPORT_ORIGIN_TEXT, immunization.reportOrigin?.text?.value)
    assertEquals("Dr Pimchanok WATTANA", immunization.performer.single().actor.display?.value)
    assertEquals("org-xxr-provincial-hospital", immunization.location?.identifier?.value?.value)
    val identifier = immunization.identifier.single()
    assertEquals(CertificateFingerprint.SYSTEM, identifier.system?.value)
    assertEquals(fingerprint, identifier.value?.value)
    assertEquals(64, fingerprint.length)
    assertNull(unverified().provenance)
  }

  @Test
  fun `the same card scanned twice has the same fingerprint, different cards differ`() {
    assertEquals(fingerprint, CertificateFingerprint.of(Vectors.hepb.hc1 + "\n"))
    assertTrue(fingerprint != CertificateFingerprint.of(Vectors.bcg.hc1))
  }

  @Test
  fun `marking verified flips primarySource in place and attaches a provenance naming issuer and DSC`() {
    val before = unverified().immunization
    val evidence =
      VerificationEvidence(
        issuer = "XR",
        dscKid = Vectors.XXR_KID,
        dscCountry = "XR",
        verifier = Reference(reference = FhirString(value = "Practitioner/nurse-1")),
        verifiedAtEpochSeconds = recordedAt + 7 * 86_400,
      )
    val (after, provenance) = DvcFhirMapper.markVerified(before, evidence)
    assertEquals(before.id, after.id)
    assertEquals(true, after.primarySource?.value)
    assertNull(after.reportOrigin)
    assertEquals(before.vaccineCode, after.vaccineCode, "codes stay exactly as issued")
    assertEquals(before.identifier, after.identifier)

    assertEquals("Immunization/imm-1", provenance.target.single().reference?.value)
    assertEquals(TrustListEndpoints.DEV, provenance.policy.single().value)
    val author = provenance.agent.first { it.type?.coding?.single()?.code?.value == "author" }
    assertEquals("XR", author.who.identifier?.value?.value)
    assertTrue(author.who.display?.value.orEmpty().contains(Vectors.XXR_KID))
    val verifier = provenance.agent.first { it.type?.coding?.single()?.code?.value == "verifier" }
    assertEquals("Practitioner/nurse-1", verifier.who.reference?.value)
    val source = provenance.entity.first { it.role.value == Provenance.ProvenanceEntityRole.Source }
    assertEquals(fingerprint, source.what.identifier?.value?.value)
    val dsc = provenance.entity.first { it.role.value == Provenance.ProvenanceEntityRole.Quotation }
    assertEquals(Vectors.XXR_KID, dsc.what.identifier?.value?.value)
    assertNotNull(provenance.recorded.value)
  }

  @Test
  fun `mapping with evidence yields the verified record in one step`() {
    val resources =
      DvcFhirMapper.map(
        Vectors.bcg.payload,
        DvcMappingOptions(
          certificateFingerprint = CertificateFingerprint.of(Vectors.bcg.hc1),
          recordedAtEpochSeconds = recordedAt,
          verification =
            VerificationEvidence(
              issuer = "XR",
              dscKid = Vectors.XXR_KID,
              verifiedAtEpochSeconds = recordedAt,
            ),
        ),
      )
    assertEquals(true, resources.immunization.primarySource?.value)
    assertEquals("BCG Vaccine", resources.immunization.vaccineCode.text?.value)
    assertNotNull(resources.provenance)
    assertNotNull(resources.patient.id)
    assertEquals("Patient/${resources.patient.id}", resources.immunization.patient.reference?.value)
  }

  @Test
  fun `an unknown product keeps its code and shows the code as the name`() {
    val coding = DvcFhirMapper.vaccineCode("XM9V38", PreQualProducts.DEMO)
    assertEquals("XM9V38", coding.coding.single().code?.value)
    assertNull(coding.coding.single().display)
    assertEquals("XM9V38", coding.text?.value)
  }

  @Test
  fun `names split on the capitalised family name and fall back to the last token`() {
    assertEquals(
      "WATTANA" to listOf("Dr", "Pimchanok"),
      DvcFhirMapper.humanName("Dr Pimchanok WATTANA").let {
        it.family?.value to it.given.map { g -> g.value }
      },
    )
    assertEquals(
      "Smith" to listOf("Jane", "Ann"),
      DvcFhirMapper.humanName("Jane Ann Smith").let {
        it.family?.value to it.given.map { g -> g.value }
      },
    )
  }
}
