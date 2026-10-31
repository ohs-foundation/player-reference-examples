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

import dev.ohs.fhir.model.r4.Boolean as FhirBoolean
import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Date
import dev.ohs.fhir.model.r4.DateTime
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Extension
import dev.ohs.fhir.model.r4.FhirDate
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.HumanName
import dev.ohs.fhir.model.r4.Identifier
import dev.ohs.fhir.model.r4.Immunization
import dev.ohs.fhir.model.r4.Instant
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Provenance
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Uri
import dev.ohs.fhir.model.r4.terminologies.AdministrativeGender
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toLocalDateTime

/** What verification established about a certificate, carried onto the record at J3. */
data class VerificationEvidence(
  /** CWT `iss`, the issuing country's ISO 3166-1 alpha-2 code. */
  val issuer: String?,
  /** The DSC that verified the signature, lower-case hex kid. */
  val dscKid: String,
  /** The DSC subject's country, when the certificate carries one. */
  val dscCountry: String? = null,
  /** The trust list the DSC came from, for the Provenance policy. */
  val trustListUrl: String = TrustListEndpoints.DEV,
  /** Who performed the verification, typically the signed-in practitioner. */
  val verifier: Reference? = null,
  val verifiedAtEpochSeconds: Long,
)

data class DvcMappingOptions(
  /** The Patient the Immunization belongs to; a new id when the scan also registers the child. */
  val patientId: String? = null,
  val immunizationId: String? = null,
  /** From [CertificateFingerprint.of], stored as an identifier for duplicate detection. */
  val certificateFingerprint: String,
  /** Resolves `vp` to a display name; the code itself is never changed. */
  val products: PreQualProducts = PreQualProducts.DEMO,
  val recordedAtEpochSeconds: Long,
  /** Present only on the clinic's verified import; the CHW path leaves it null. */
  val verification: VerificationEvidence? = null,
)

/** The FHIR resources one certificate becomes. [provenance] exists only when verified. */
data class DvcFhirResources(
  val patient: Patient,
  val immunization: Immunization,
  val provenance: Provenance? = null,
)

/**
 * The one DVCMin to FHIR mapping shared by J2 (unverified, family-provided), J3 (verified, with
 * Provenance) and J4.1 (the record as the IPS carries it). The rules, agreed in the handover:
 * - Patient: name, date of birth, sex, nationality; no national identifier; the guardian name on
 *   `Patient.contact`, because the certificate names a person, not a household link (NE-2's
 *   registration form links the real mother).
 * - One Immunization per certificate; `vaccineCode` is the PreQual product coding exactly as
 *   issued, with a display name added for the reader; `occurrenceDateTime` is `dv`; `lotNumber` is
 *   `bo`; the clinician is a performer; the issuing organization is the location.
 * - `primarySource` is false and `reportOrigin` says the family presented a written record until
 *   verification; then [markVerified] sets it true and attaches a Provenance naming the issuer and
 *   the DSC, with the certificate fingerprint as the source entity.
 * - Nothing here translates codes. A Lao coding is added at J3 outside this mapper: automatically
 *   when the national ConceptMap has a target (and recorded as automatic), otherwise only after a
 *   clinician confirms it. With the Lao list of 30 Oct 2026, HepB maps and BCG is the card that asks.
 */
object DvcFhirMapper {
  const val NATIONALITY_EXTENSION = "http://hl7.org/fhir/StructureDefinition/patient-nationality"
  const val ISO_3166 = "urn:iso:std:iso:3166"
  const val V3_ROLE_CODE = "http://terminology.hl7.org/CodeSystem/v3-RoleCode"
  const val IMMUNIZATION_ORIGIN = "http://terminology.hl7.org/CodeSystem/immunization-origin"
  const val PARTICIPANT_TYPE = "http://terminology.hl7.org/CodeSystem/provenance-participant-type"
  const val DSC_KID_SYSTEM = "http://ohs.dev/vaxlink/identifier/gdhcn-dsc-kid"
  const val ISSUER_SYSTEM = "http://ohs.dev/vaxlink/identifier/gdhcn-issuer"
  const val REPORT_ORIGIN_TEXT = "Vaccination certificate presented by the family"

  @OptIn(ExperimentalUuidApi::class)
  fun map(dvc: DvcMin, options: DvcMappingOptions): DvcFhirResources {
    val patientId = options.patientId ?: Uuid.random().toString()
    val immunizationId = options.immunizationId ?: Uuid.random().toString()
    val patient = patient(dvc, patientId)
    val unverified =
      Immunization(
        id = immunizationId,
        identifier =
          listOf(
            Identifier(
              system = Uri(value = CertificateFingerprint.SYSTEM),
              value = FhirString(value = options.certificateFingerprint),
            )
          ),
        status = Enumeration(value = Immunization.ImmunizationStatusCodes.Completed),
        vaccineCode = vaccineCode(dvc.vx.vp, options.products),
        patient = reference("Patient/$patientId"),
        occurrence =
          Immunization.Occurrence.DateTime(DateTime(value = FhirDateTime.fromString(dvc.vx.dv))),
        recorded = DateTime(value = utcDateTime(options.recordedAtEpochSeconds)),
        primarySource = FhirBoolean(value = false),
        reportOrigin =
          CodeableConcept(
            coding =
              listOf(
                Coding(
                  system = Uri(value = IMMUNIZATION_ORIGIN),
                  code = Code(value = "record"),
                  display = FhirString(value = "Written Record"),
                )
              ),
            text = FhirString(value = REPORT_ORIGIN_TEXT),
          ),
        location =
          dvc.vx.issuerId?.let {
            Reference(
              identifier = Identifier(value = FhirString(value = it)),
              display = FhirString(value = it),
            )
          },
        lotNumber = FhirString(value = dvc.vx.bo),
        performer =
          listOfNotNull(
            dvc.vx.cn?.let {
              Immunization.Performer(actor = Reference(display = FhirString(value = it)))
            }
          ),
      )
    val verification = options.verification ?: return DvcFhirResources(patient, unverified)
    val (verified, provenance) = markVerified(unverified, verification)
    return DvcFhirResources(patient, verified, provenance)
  }

  /**
   * J3: the same Immunization the CHW recorded becomes verified in place, and a Provenance records
   * who issued the certificate, which DSC verified it and who verified it. Using one resource
   * through its lifecycle keeps the schedule logic simple and the referral story visible.
   */
  @OptIn(ExperimentalUuidApi::class)
  fun markVerified(
    immunization: Immunization,
    evidence: VerificationEvidence,
  ): Pair<Immunization, Provenance> {
    val verified = immunization.copy(primarySource = FhirBoolean(value = true), reportOrigin = null)
    val fingerprint =
      immunization.identifier
        .firstOrNull { it.system?.value == CertificateFingerprint.SYSTEM }
        ?.value
        ?.value
    val provenance =
      Provenance(
        id = Uuid.random().toString(),
        target = listOf(reference("Immunization/${immunization.id}")),
        recorded = Instant(value = utcDateTime(evidence.verifiedAtEpochSeconds)),
        policy = listOf(Uri(value = evidence.trustListUrl)),
        agent =
          listOfNotNull(
            Provenance.Agent(
              type = participantType("author", "Author"),
              who =
                Reference(
                  identifier =
                    evidence.issuer?.let {
                      Identifier(
                        system = Uri(value = ISSUER_SYSTEM),
                        value = FhirString(value = it),
                      )
                    },
                  display = FhirString(value = issuerDisplay(evidence)),
                ),
            ),
            evidence.verifier?.let {
              Provenance.Agent(type = participantType("verifier", "Verifier"), who = it)
            },
          ),
        entity =
          listOf(
            Provenance.Entity(
              role = Enumeration(value = Provenance.ProvenanceEntityRole.Source),
              what =
                Reference(
                  identifier =
                    fingerprint?.let {
                      Identifier(
                        system = Uri(value = CertificateFingerprint.SYSTEM),
                        value = FhirString(value = it),
                      )
                    },
                  display = FhirString(value = "WHO DVC certificate, DSC kid ${evidence.dscKid}"),
                ),
            ),
            Provenance.Entity(
              role = Enumeration(value = Provenance.ProvenanceEntityRole.Quotation),
              what =
                Reference(
                  identifier =
                    Identifier(
                      system = Uri(value = DSC_KID_SYSTEM),
                      value = FhirString(value = evidence.dscKid),
                    ),
                  display = FhirString(value = "GDHCN document signing certificate"),
                ),
            ),
          ),
      )
    return verified to provenance
  }

  fun vaccineCode(vp: String, products: PreQualProducts): CodeableConcept {
    val product = products.find(vp)
    return CodeableConcept(
      coding =
        listOf(
          Coding(
            system = Uri(value = PreQualTerminology.PRODUCT_IDS),
            code = Code(value = vp),
            display = product?.let { FhirString(value = it.display) },
          )
        ),
      text = FhirString(value = products.display(vp)),
    )
  }

  fun patient(dvc: DvcMin, patientId: String): Patient =
    Patient(
      id = patientId,
      identifier =
        listOfNotNull(
          dvc.id?.let { id ->
            Identifier(
              type = dvc.dt?.let { CodeableConcept(text = FhirString(value = it)) },
              value = FhirString(value = id),
            )
          }
        ),
      name = listOf(humanName(dvc.n)),
      gender = Enumeration(value = gender(dvc.s)),
      birthDate = Date(value = FhirDate.fromString(dvc.dob)),
      extension =
        listOf(
          Extension(
            url = NATIONALITY_EXTENSION,
            extension =
              listOf(
                Extension(
                  url = "code",
                  value =
                    Extension.Value.CodeableConcept(
                      CodeableConcept(
                        coding =
                          listOf(
                            Coding(system = Uri(value = ISO_3166), code = Code(value = dvc.nt))
                          )
                      )
                    ),
                )
              ),
          )
        ),
      contact =
        listOfNotNull(
          dvc.gn?.let {
            Patient.Contact(
              relationship =
                listOf(
                  CodeableConcept(
                    coding =
                      listOf(
                        Coding(
                          system = Uri(value = V3_ROLE_CODE),
                          code = Code(value = "GUARD"),
                          display = FhirString(value = "Guardian"),
                        )
                      )
                  )
                ),
              name = humanName(it),
            )
          }
        ),
    )

  /**
   * The certificate carries one name string. Convention in the worked example is given names then
   * the family name in capitals ("Chanthala SOUVANNALATH"); capitalised tokens become the family
   * name and the rest the given names, with the last token as family when nothing is capitalised.
   * The full string is kept as `text`, which is what the screens should show.
   */
  fun humanName(text: String): HumanName {
    val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val capitalised =
      tokens.filter { it.length > 1 && it == it.uppercase() && it.any(Char::isLetter) }
    val family =
      if (capitalised.isNotEmpty()) capitalised.joinToString(" ") else tokens.lastOrNull()
    val given = if (capitalised.isNotEmpty()) tokens - capitalised.toSet() else tokens.dropLast(1)
    return HumanName(
      text = FhirString(value = text),
      family = family?.let { FhirString(value = it) },
      given = given.map { FhirString(value = it) },
    )
  }

  fun gender(s: String): AdministrativeGender =
    when (s.lowercase()) {
      "male" -> AdministrativeGender.Male
      "female" -> AdministrativeGender.Female
      "other" -> AdministrativeGender.Other
      else -> AdministrativeGender.Unknown
    }

  private fun issuerDisplay(evidence: VerificationEvidence): String {
    val issuer = evidence.issuer ?: evidence.dscCountry ?: "unknown issuer"
    return "GDHCN participant $issuer, DSC kid ${evidence.dscKid}"
  }

  private fun participantType(code: String, display: String) =
    CodeableConcept(
      coding =
        listOf(
          Coding(
            system = Uri(value = PARTICIPANT_TYPE),
            code = Code(value = code),
            display = FhirString(value = display),
          )
        )
    )

  private fun reference(value: String) = Reference(reference = FhirString(value = value))

  private fun utcDateTime(epochSeconds: Long): FhirDateTime =
    FhirDateTime.DateTime(
      kotlin.time.Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(TimeZone.UTC),
      UtcOffset.ZERO,
    )
}
