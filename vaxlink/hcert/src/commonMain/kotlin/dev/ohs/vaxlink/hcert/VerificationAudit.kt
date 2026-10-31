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

import dev.ohs.fhir.model.r4.AuditEvent
import dev.ohs.fhir.model.r4.Boolean as FhirBoolean
import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.Identifier
import dev.ohs.fhir.model.r4.Instant
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Uri
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toLocalDateTime

/**
 * The AuditEvent the clinic writes for every verification attempt (CLN-10, AUD-01), successful or
 * not, so the policy track has a real audit trail to look at. The app uploads it through the Info
 * Gateway like any other resource. Nothing from the certificate payload goes in: only the outcome,
 * the certificate fingerprint, the DSC that verified it, who tried and for whom.
 */
object VerificationAudit {
  const val LIFECYCLE = "http://terminology.hl7.org/CodeSystem/iso-21089-lifecycle"
  const val SUBTYPE_SYSTEM = "http://ohs.dev/vaxlink/CodeSystem/audit-subtype"
  const val ENTITY_TYPE = "http://terminology.hl7.org/CodeSystem/audit-entity-type"
  const val OBJECT_ROLE = "http://terminology.hl7.org/CodeSystem/object-role"

  @OptIn(ExperimentalUuidApi::class)
  fun event(
    result: VerificationResult,
    certificateFingerprint: String,
    /** Who scanned, requestor; usually the signed-in Practitioner. */
    practitioner: Reference?,
    /** The child whose record the certificate is for, when known. */
    patient: Reference?,
    /** The app instance as the audit source, a Device reference or a display. */
    observer: Reference,
    recordedAtEpochSeconds: Long,
  ): AuditEvent {
    val success = result is VerificationResult.Verified
    val description =
      when (result) {
        is VerificationResult.Verified ->
          "Verified against GDHCN DSC kid ${result.hcert.dsc.kid}" +
            (result.hcert.iss?.let { ", issuer $it" } ?: "")
        is VerificationResult.Failed ->
          "Failed at ${result.failure.step} with ${result.failure.reason}" +
            result.failure.detail.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
      }
    return AuditEvent(
      id = Uuid.random().toString(),
      type =
        Coding(
          system = Uri(value = LIFECYCLE),
          code = Code(value = "verify"),
          display = FhirString(value = "Verify Record Lifecycle Event"),
        ),
      subtype =
        listOf(
          Coding(
            system = Uri(value = SUBTYPE_SYSTEM),
            code = Code(value = "hcert-verify"),
            display = FhirString(value = "WHO HCERT certificate verification"),
          )
        ),
      action = Enumeration(value = AuditEvent.AuditEventAction.fromCode("E")),
      recorded = Instant(value = utcDateTime(recordedAtEpochSeconds)),
      outcome =
        Enumeration(value = AuditEvent.AuditEventOutcome.fromCode(if (success) "0" else "4")),
      outcomeDesc = FhirString(value = description),
      agent =
        listOf(
          AuditEvent.Agent(
            who = practitioner,
            requestor = FhirBoolean(value = true),
            name = practitioner?.display,
          )
        ),
      source = AuditEvent.Source(observer = observer),
      entity =
        listOfNotNull(
          AuditEvent.Entity(
            what =
              Reference(
                identifier =
                  Identifier(
                    system = Uri(value = CertificateFingerprint.SYSTEM),
                    value = FhirString(value = certificateFingerprint),
                  ),
                display = FhirString(value = "WHO DVC certificate"),
              ),
            type =
              Coding(
                system = Uri(value = ENTITY_TYPE),
                code = Code(value = "2"),
                display = FhirString(value = "System Object"),
              ),
            role =
              Coding(
                system = Uri(value = OBJECT_ROLE),
                code = Code(value = "4"),
                display = FhirString(value = "Domain Resource"),
              ),
            name = FhirString(value = "certificate"),
          ),
          patient?.let {
            AuditEvent.Entity(
              what = it,
              type =
                Coding(
                  system = Uri(value = ENTITY_TYPE),
                  code = Code(value = "1"),
                  display = FhirString(value = "Person"),
                ),
              role =
                Coding(
                  system = Uri(value = OBJECT_ROLE),
                  code = Code(value = "1"),
                  display = FhirString(value = "Patient"),
                ),
            )
          },
        ),
    )
  }

  private fun utcDateTime(epochSeconds: Long): FhirDateTime =
    FhirDateTime.DateTime(
      kotlin.time.Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(TimeZone.UTC),
      UtcOffset.ZERO,
    )
}
