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

import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.String as FhirString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * One AuditEvent per verification attempt, success or failure, with no certificate content in it.
 */
class VerificationAuditTest {
  private val engine = runBlocking { loadHcertEngine() }
  private val practitioner =
    Reference(
      reference = FhirString(value = "Practitioner/nurse-1"),
      display = FhirString(value = "Nurse"),
    )
  private val patient = Reference(reference = FhirString(value = "Patient/child-1"))
  private val device = Reference(reference = FhirString(value = "Device/clinic-tablet-1"))
  private val fingerprint = CertificateFingerprint.of(Vectors.hepb.hc1)

  @Test
  fun `a successful verification is a success event naming the DSC`() {
    val trust = engine.newTrustStore().apply { addDidDocument(Vectors.xxrDscDidDocument) }
    val result = engine.verify(Vectors.hepb.hc1, trust, Vectors.hepb.iat + 3600)
    val event =
      VerificationAudit.event(
        result,
        fingerprint,
        practitioner,
        patient,
        device,
        Vectors.hepb.iat + 3600,
      )
    assertEquals("verify", event.type.code?.value)
    assertEquals("hcert-verify", event.subtype.single().code?.value)
    assertEquals("E", event.action?.value?.toString())
    assertEquals("0", event.outcome?.value?.toString())
    assertTrue(event.outcomeDesc?.value.orEmpty().contains(Vectors.XXR_KID))
    assertEquals("Practitioner/nurse-1", event.agent.single().who?.reference?.value)
    assertEquals(true, event.agent.single().requestor.value)
    assertEquals("Device/clinic-tablet-1", event.source.observer.reference?.value)
    assertEquals(fingerprint, event.entity.first().what?.identifier?.value?.value)
    assertEquals("Patient/child-1", event.entity.last().what?.reference?.value)
    val text = event.toString()
    assertTrue(
      "SOUVANNALATH" !in text && "a3K3X000006MiiXUAS" !in text,
      "no certificate content in the audit",
    )
  }

  @Test
  fun `a failed verification is a failure event naming the step and reason`() {
    val result = engine.verify(Vectors.hepb.hc1, engine.newTrustStore(), Vectors.hepb.iat + 3600)
    val event =
      VerificationAudit.event(
        result,
        fingerprint,
        practitioner,
        null,
        device,
        Vectors.hepb.iat + 3600,
      )
    assertEquals("4", event.outcome?.value?.toString())
    assertTrue(event.outcomeDesc?.value.orEmpty().contains("TRUST_LOOKUP"))
    assertTrue(event.outcomeDesc?.value.orEmpty().contains("KID_NOT_FOUND"))
    assertEquals(1, event.entity.size)
  }
}
