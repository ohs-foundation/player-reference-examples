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
package dev.ohs.vaxlink.data.hcert

import dev.ohs.vaxlink.hcert.FailureReason
import dev.ohs.vaxlink.hcert.InMemoryTrustListStorage
import dev.ohs.vaxlink.hcert.ReadResult
import dev.ohs.vaxlink.hcert.TrustListCache
import dev.ohs.vaxlink.hcert.VerificationResult
import dev.ohs.vaxlink.hcert.VerifyStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** The app's door to the engine, on the worked example (TEST DATA ONLY, DEV participant XXR). */
class HcertServiceTest {
  private fun resource(path: String) =
    checkNotNull(HcertServiceTest::class.java.getResource(path)) { path }.readText()

  private val hepb =
    Json.parseToJsonElement(resource("/hcert/dvc-qr-example-XXR.json"))
      .jsonObject["certificates"]!!
      .jsonObject["hepb1"]!!
      .jsonObject
  private val hc1 = hepb["hc1"]!!.jsonPrimitive.content
  private val iat = hepb["iat"]!!.jsonPrimitive.long
  private val xxrDid = resource("/hcert/XXR-DSC-did.json")

  @Test
  fun `reads a certificate with no trust list on the device`() = runTest {
    val service = HcertService(TrustListCache(InMemoryTrustListStorage()))
    val decoded = assertIs<ReadResult.Decoded>(service.read(hc1)).hcert
    assertEquals("a3K3X000006MiiXUAS", decoded.dvc?.vx?.vp)
    assertEquals(
      "Hepatitis B Vaccine (rDNA) (Paediatric)",
      service.products.display(decoded.dvc!!.vx.vp),
    )
  }

  @Test
  fun `verifies with the bundled list and reports its age`() = runTest {
    val service =
      HcertService(
        TrustListCache(
          InMemoryTrustListStorage(),
          bundled = { xxrDid },
          bundledFetchedAtEpochSeconds = iat,
        )
      )
    val verification = service.verify(hc1, now = iat + 3600)
    assertIs<VerificationResult.Verified>(verification.result)
    val trust = assertNotNull(verification.trustList)
    assertTrue(trust.bundled)
    assertEquals(3600, trust.ageSeconds(iat + 3600))
    // A second call reuses the store built from the same snapshot.
    assertIs<VerificationResult.Verified>(service.verify(hc1, now = iat + 7200).result)
  }

  @Test
  fun `without any list verification fails at the trust lookup and the refresh fails quietly`() =
    runTest {
      val service = HcertService(TrustListCache(InMemoryTrustListStorage()))
      val failure =
        assertIs<VerificationResult.Failed>(service.verify(hc1, now = iat + 3600).result).failure
      assertEquals(
        VerifyStep.TRUST_LOOKUP to FailureReason.KID_NOT_FOUND,
        failure.step to failure.reason,
      )
      service.refreshTrustListQuietly() // no HTTP client: logged, not thrown
      assertTrue(service.refreshTrustList().isFailure)
    }
}
