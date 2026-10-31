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
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * NE-1 acceptance in the browser: the Rust component behind the same facade, driven from
 * Kotlin/Wasm through the hcert-web glue. Runs under headless Chrome (`:hcert:wasmJsBrowserTest`).
 * The vectors are the worked example, inlined because a browser test has no resource files.
 */
class WasmEngineTest {
  private val hepb =
    """HC1:NCFOXN%TSMAHN-HHPC*8W${"$"}BM70GZW8WI47X5JU9V21HJI.OR.6J:X90${"$"}TZIPAN9I6TSQLMN2SRPOK62YU%E6SOEGW2Z0W/TO+4W3R3ZP3XQ7KUHMQQTOE2QERM2ZGA2:UG%UBPISWUO FFPEUCAQZ4K%I7YV4SVO*I1R5LK90DQK7E${"$"}BESW6846OVQICMKR6PK96M53A5/41B${"$"}P6 0JIIO*LM4UJH2/JT0DJ${"$"}JAGU09VCRCKCWC.XI5TBC+J${"$"}ZIMOBKEALOB3YIX1JM7JZIJXUK0HIQVBPEB:CJZTAI8A+BDWZD${"$"}BCTD0D4OZFJ3JKJMOZD5+B9${"$"}/G547Z2ETZVJFD-ST53VMK9E/AUJ6R9KFAQB+RL7JYX69${"$"}1K-PF:E+7AYCTGARL6KGND1K6CPFT*P-8AHFSA16U9FS34:TG/0E  6${"$"}TP*705SS44"""
  private val bcg =
    """HC1:NCFOXN%TSMAHN-HHPC*8W${"$"}BM70GZW8WI4%%5JU9V21HJI.OR.6J:X90${"$"}TZIPAN9I6TSQLMN2SRPOK62YU%E6SOEGW2Z0W/TO+4W3R3ZP3XQ7KUHMQQTOE2QERM2ZGA2:UG%UBPISWUO FFPEUCAQZ4K%I7YV4SVO*I1R5LK99AQ7LED9RNF66J6QW6:9631A*2QYDEUGAEHP8X9HSRH8KE62.13%USNCTZXIK/IL-A9AD.PD0DJGDBAB4X3C+1MRCA0ZKI8AZ3CYIJ*ZJFDJ0UJM2C8BI/NI6SI1-ACYI0+TKQ3200M:C IJM5Q52IHJP7NVQ794S8E41F.520NRYE2.JDAOSZFZNM*SJ7ONT5DE*VMKL${"$"}ASR%514QW*PA4DOQF%MV9CUYMDO:0VWKY4M.QPYI4I${"$"}U*.885E L1JJBHA5T B961BC0/:E"""
  private val iat = 1792657800L
  private val exp = 1824193800L
  private val xxrDscDidDocument =
    """{"@context":["https://www.w3.org/ns/did/v1","https://w3id.org/security/suites/jws-2020/v1","https://smart.who.int/trust/tng-context/v1-DEV.jsonld"],"id":"did:web:tng-cdn-dev.who.int:v2:trustlist:-:XXR:DSC","controller":"did:web:tng-cdn-dev.who.int:v2:trustlist:-:XXR","verificationMethod":[{"id":"did:web:tng-cdn-dev.who.int:v2:trustlist:-:XXR:DSC#kW/6VnwPRKI=","type":"JsonWebKey2020","controller":"did:web:tng-cdn-dev.who.int:v2:trustlist:-:XXR","publicKeyJwk":{"kty":"EC","kid":"kW/6VnwPRKI=","x5c":["MIICmDCCAj+gAwIBAgIUEO6AfJTjJwcQ4llwKSZXLq79Di8wCgYIKoZIzj0EAwIwaTELMAkGA1UEBhMCWFIxGjAYBgNVBAgMEVRlc3QgUml0aWthIFN0YXRlMQ8wDQYDVQQHDAZNdW1iYWkxDDAKBgNVBAoMA1dITzEMMAoGA1UECwwDSVRCMREwDwYDVQQDDAhOYXRpb25YUjAeFw0yNjEwMjExMTU4MjNaFw0yODEwMjAxMTU5MjNaMGkxCzAJBgNVBAYTAlhSMRowGAYDVQQIDBFUZXN0IFJpdGlrYSBTdGF0ZTEPMA0GA1UEBwwGTXVtYmFpMQwwCgYDVQQKDANXSE8xDDAKBgNVBAsMA0lUQjERMA8GA1UEAwwITmF0aW9uWFIwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAR3eFCoZlcnAGEMrrNQtERcNL/1mLeyEYw1VGp5E2jyHKmoso0zOrDW0nuOm8xcPTgOcxAsTSfG6IVhPCT3SiF8o4HEMIHBMB0GA1UdDgQWBBQi57ZjisrKtqUwAVfZLSXo9rmHoTAfBgNVHSMEGDAWgBSM4K6CrjBpQe14rO1KPx3B41XP7DAOBgNVHQ8BAf8EBAMCB4AwMAYDVR0lBCkwJwYLKwYBBAGON49lAQEGCysGAQQBjjePZQECBgsrBgEEAY43j2UBAzA9BgNVHR8ENjA0MDKgMKAuhixodHRwOi8vY3JsLmV4YW1wbGVkb21haW4uZXhhbXBsZS9DUkwvU0NBLmNybDAKBggqhkjOPQQDAgNHADBEAiAboktra240NWCEqYN4+X1Y0zULoFu7vwukQQZ24alZ7AIgd7ypdhYVHDm554kjHOlFwxF7hX7gtKEGE6PcQxAjskY=","MIICNjCCAd2gAwIBAgIUDthXOya1M401mvbhchxNNNJwT3owCgYIKoZIzj0EAwIwaTELMAkGA1UEBhMCWFIxGjAYBgNVBAgMEVRlc3QgUml0aWthIFN0YXRlMQ8wDQYDVQQHDAZNdW1iYWkxDDAKBgNVBAoMA1dITzEMMAoGA1UECwwDSVRCMREwDwYDVQQDDAhOYXRpb25YUjAeFw0yNjA3MzAwOTAxNDlaFw0zMDA3MjkwOTAxNDlaMGkxCzAJBgNVBAYTAlhSMRowGAYDVQQIDBFUZXN0IFJpdGlrYSBTdGF0ZTEPMA0GA1UEBwwGTXVtYmFpMQwwCgYDVQQKDANXSE8xDDAKBgNVBAsMA0lUQjERMA8GA1UEAwwITmF0aW9uWFIwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQBbC3d1v1ubdUbgHraz673iqhmQKSEcLfZBl/AURELa04zW7MOdrhD3NykndRKKCyvFkVox52SYnra/zhI5u7jo2MwYTAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwIBBjAdBgNVHQ4EFgQUjOCugq4waUHteKztSj8dweNVz+wwHwYDVR0jBBgwFoAUjOCugq4waUHteKztSj8dweNVz+wwCgYIKoZIzj0EAwIDRwAwRAIgX2Wk8qaFwpBsvsNdO7oBMM0BNVwm/y7uuehTaFFGP38CIBcS2jNwXDXRM4eadX+3bvoGSWq4vRqyfX9Gjth2zwvZ"],"crv":"P-256","x":"d3hQqGZXJwBhDK6zULREXDS_9Zi3shGMNVRqeRNo8hw","y":"AKmoso0zOrDW0nuOm8xcPTgOcxAsTSfG6IVhPCT3SiF8"},"domain":{"code":"#PH4H"},"participant":{"code":"#XXR"},"keyusage":{"code":"#DSC"}}],"proof":{"type":"JsonWebSignature2020","created":"2026-10-22T12:00:16Z","nonce":"z9n9PBzMPjpF5duCo4BLb4q2i9AGVA1J","proofPurpose":"assertionMethod","verificationMethod":"did:web:raw.githubusercontent.com:WorldHealthOrganization:tng-participants-dev:main:WHO:signing:DID","jws":"eyJiNjQiOmZhbHNlLCJjcml0IjpbImI2NCJdLCJhbGciOiJFUzI1NiJ9..MEUCIA_02IywDtyK20wmd896Eyfr1VQ1s0ub9I8UEl6pk6-hAiEAtVeTESxOvn7_eO-0IdPSVFxPALHuxiDUdY2K__GpcZ8"}}"""

  @Test
  fun readUnverifiedInTheBrowser() = runTest {
    val engine = loadHcertEngine()
    assertTrue("WebAssembly" in engine.name, engine.name)
    val decoded = assertIs<ReadResult.Decoded>(engine.readUnverified(hepb)).hcert
    assertEquals("916ffa567c0f44a2", decoded.kid)
    assertEquals("XR", decoded.iss)
    assertEquals("a3K3X000006MiiXUAS", decoded.dvc?.vx?.vp)
    assertEquals("Chanthala SOUVANNALATH", decoded.dvc?.n)
    assertTrue(decoded.diagnostics.any { "NOT VERIFIED" in it })
    assertIs<ReadResult.Failed>(engine.readUnverified("HC1:!!!"))
  }

  @Test
  fun verifyInTheBrowser() = runTest {
    val engine = loadHcertEngine()
    val trust = engine.newTrustStore()
    assertEquals(1, trust.addDidDocument(xxrDscDidDocument))
    for (hc1 in listOf(hepb, bcg)) {
      val verified =
        assertIs<VerificationResult.Verified>(engine.verify(hc1, trust, iat + 3600)).hcert
      assertEquals("916ffa567c0f44a2", verified.dsc.kid)
      assertEquals("XXR", verified.dsc.participant)
    }
    val expired =
      assertIs<VerificationResult.Failed>(engine.verify(hepb, trust, exp + 3600)).failure
    assertEquals(VerifyStep.TIME to FailureReason.EXPIRED, expired.step to expired.reason)
    val none =
      assertIs<VerificationResult.Failed>(engine.verify(hepb, engine.newTrustStore(), iat + 3600))
        .failure
    assertEquals(VerifyStep.TRUST_LOOKUP to FailureReason.KID_NOT_FOUND, none.step to none.reason)
    val refused = runCatching { trust.addDidDocument("{}") }.exceptionOrNull()
    assertIs<HcertTrustException>(refused)
  }

  @Test
  fun vhlInTheBrowser() = runTest {
    val engine = loadHcertEngine()
    val link =
      VhlPayload(
        url =
          "https://vhl-sharer.example.org/List/_search?_id=abc123def456&code=folder&status=current&patient.identifier=urn:oid:2.16.840.1.113883.2.4.6.3|PASSPORT123",
        key = "86F8LY5LlWAa1-OS_FgrTnYNqFHJP2ey5RSKLJBN9jk",
        flag = "P",
      )
    val request = engine.buildManifestRequest(link, "Clinic", "1234")
    assertEquals("https://vhl-sharer.example.org/List/_search", request.url)
    assertTrue("passcode=1234" in request.body)
    val docs =
      engine.parseManifest(
        """{"resourceType":"Bundle","type":"searchset","entry":[{"resource":{"resourceType":"List","entry":[{"item":{"reference":"DocumentReference/d1"}}]},"search":{"mode":"match"}}]}"""
      )
    assertEquals("DocumentReference/d1", docs.single().reference)
  }
}
