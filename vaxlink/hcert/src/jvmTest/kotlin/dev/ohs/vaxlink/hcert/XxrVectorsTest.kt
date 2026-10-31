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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import who.smart.trust.HcertResult
import who.smart.trust.core.DEFAULT_LIMITS
import who.smart.trust.core.Hcert

/**
 * NE-1 acceptance on the JVM engine: both worked-example certificates read unverified without any
 * trust material, verify against the DEV trust list, and a tampered copy fails at the signature
 * with no payload.
 */
class XxrVectorsTest {
  private val engine = runBlocking { loadHcertEngine() }

  @Test
  fun `readUnverified returns both certificates with no trust list at all`() {
    for (c in Vectors.both) {
      val decoded = assertIs<ReadResult.Decoded>(engine.readUnverified(c.hc1), c.name).hcert
      assertEquals(Vectors.XXR_KID, decoded.kid, c.name)
      assertEquals("XR", decoded.iss)
      assertEquals(c.iat to c.exp, decoded.iat to decoded.exp)
      assertEquals(listOf(-6), decoded.entries.map { it.subclaim })
      assertEquals(c.payload, decoded.dvc, c.name)
      // The library marks every unverified read so a screen can never mistake it for a verified
      // one.
      assertTrue(decoded.diagnostics.any { "NOT VERIFIED" in it }, decoded.diagnostics.toString())
    }
  }

  @Test
  fun `both certificates verify against the XXR DSC document`() {
    val trust = engine.newTrustStore()
    assertEquals(1, trust.addDidDocument(Vectors.xxrDscDidDocument))
    for (c in Vectors.both) {
      val verified =
        assertIs<VerificationResult.Verified>(engine.verify(c.hc1, trust, c.iat + 3600), c.name)
          .hcert
      assertEquals("XR", verified.iss)
      assertEquals(Vectors.XXR_KID, verified.dsc.kid)
      assertEquals("XR", verified.dsc.country)
      assertEquals("XXR", verified.dsc.participant)
      assertEquals(c.payload, verified.dvc, c.name)
      assertTrue(verified.entries.single().keyUsageAuthorised)
      assertTrue(verified.diagnostics.isEmpty(), verified.diagnostics.toString())
    }
  }

  @Test
  fun `both certificates verify against the whole DEV trust list snapshot`() {
    val trust = engine.newTrustStore()
    val added = trust.addDidDocument(Vectors.devTrustList)
    assertTrue(added > 100, "DEV list loaded $added DSCs")
    assertEquals(added, trust.size())
    for (c in Vectors.both) {
      val verified =
        assertIs<VerificationResult.Verified>(engine.verify(c.hc1, trust, c.iat + 3600), c.name)
          .hcert
      assertEquals(Vectors.XXR_KID, verified.dsc.kid)
    }
  }

  @Test
  fun `without the DSC verification stops at the trust lookup`() {
    val failure =
      assertIs<VerificationResult.Failed>(
          engine.verify(Vectors.hepb.hc1, engine.newTrustStore(), Vectors.hepb.iat + 3600)
        )
        .failure
    assertEquals(
      VerifyStep.TRUST_LOOKUP to FailureReason.KID_NOT_FOUND,
      failure.step to failure.reason,
    )
  }

  @Test
  fun `a tampered payload fails at the signature and shows no payload`() {
    val trust = engine.newTrustStore().apply { addDidDocument(Vectors.xxrDscDidDocument) }
    for (c in Vectors.both) {
      val failure =
        assertIs<VerificationResult.Failed>(
            engine.verify(tampered(c.hc1), trust, c.iat + 3600),
            c.name,
          )
          .failure
      assertEquals(
        VerifyStep.SIGNATURE to FailureReason.SIGNATURE_INVALID,
        failure.step to failure.reason,
        c.name,
      )
    }
  }

  @Test
  fun `an expired or not yet valid certificate fails at the time check`() {
    val trust = engine.newTrustStore().apply { addDidDocument(Vectors.xxrDscDidDocument) }
    val c = Vectors.hepb
    val expired =
      assertIs<VerificationResult.Failed>(engine.verify(c.hc1, trust, c.exp + 3600)).failure
    assertEquals(VerifyStep.TIME to FailureReason.EXPIRED, expired.step to expired.reason)
    val early =
      assertIs<VerificationResult.Failed>(engine.verify(c.hc1, trust, c.iat - 3600)).failure
    assertEquals(VerifyStep.TIME to FailureReason.NOT_YET_VALID, early.step to early.reason)
    // Inside the clock skew the certificate is accepted.
    assertIs<VerificationResult.Verified>(
      engine.verify(c.hc1, trust, c.iat - 60, VerifyOptions(clockSkewSeconds = 300))
    )
  }

  @Test
  fun `text that is not a certificate fails early with a typed reason`() {
    assertIs<ReadResult.Failed>(engine.readUnverified("not a certificate"))
    val failure = assertIs<ReadResult.Failed>(engine.readUnverified("HC1:!!!")).failure
    assertTrue(
      failure.step in
        setOf(VerifyStep.PREFIX, VerifyStep.BASE45, VerifyStep.INFLATE, VerifyStep.COSE),
      failure.toString(),
    )
  }

  @Test
  fun `bad trust material is refused and nothing is added`() {
    val trust = engine.newTrustStore()
    val error = runCatching { trust.addDidDocument("{\"id\":\"did:web:x\"}") }.exceptionOrNull()
    assertNotNull(error)
    assertIs<HcertTrustException>(error)
    assertEquals(0, trust.size())
  }

  @Test
  fun `pinned PEM certificates load too`() {
    val trust = engine.newTrustStore()
    assertEquals(
      2,
      trust.addPem(Vectors.resource("/vectors/dvc-qr-example-XXA-dsc.pem")),
      "DSC and its CSCA",
    )
  }

  /** Flips one byte inside the COSE payload, leaving the structure and the signature intact. */
  private fun tampered(hc1: String): String {
    val cose =
      assertIs<HcertResult.Ok<ByteArray>>(Hcert.codec.fromHc1(hc1, DEFAULT_LIMITS)).value.copyOf()
    val index =
      cose.size - 64 - 8 // the ES256 signature is the last 64 bytes; step back into the payload
    cose[index] = (cose[index].toInt() xor 0x01).toByte()
    return Hcert.codec.toHc1(cose)
  }
}
