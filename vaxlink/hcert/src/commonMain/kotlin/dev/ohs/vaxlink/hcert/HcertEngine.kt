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

import kotlin.time.Clock

/**
 * Options for [HcertEngine.verify]. The clock skew allows for a device clock that is a little off;
 * the library checks `exp` and `iat` against `now` widened by this many seconds.
 */
data class VerifyOptions(
  val clockSkewSeconds: Int = DEFAULT_CLOCK_SKEW_SECONDS,
  val maxPayloads: Int? = null,
) {
  companion object {
    const val DEFAULT_CLOCK_SKEW_SECONDS = 300
  }
}

/**
 * The DSCs a verifier trusts. The app fills it, from the GDHCN DID trust list or pinned PEM, and
 * the engine only reads it: no network inside. Each add returns the number of DSCs added and throws
 * [HcertTrustException] when the material is refused, in which case nothing was added.
 */
interface HcertTrustStore {
  /** A GDHCN DID trustlist v2 document, embedded or reference variant, exactly as served. */
  fun addDidDocument(json: String): Int

  fun addPem(pem: String): Int

  fun size(): Int

  fun clear()
}

/**
 * The verification library behind one Kotlin surface. [loadHcertEngine] returns the engine for the
 * platform: hcert-core on Android and desktop, the Rust component on the web.
 */
interface HcertEngine {
  /** Which implementation this is, for the About screen and bug reports. */
  val name: String

  fun newTrustStore(): HcertTrustStore

  /** Decodes the QR text without a trust lookup or signature check. The result is unverified. */
  fun readUnverified(hc1: String): ReadResult

  /**
   * The full pipeline: prefix, base45, inflate, COSE, header, trust lookup, signature, key usage,
   * CWT, time, hcert, payload. [nowEpochSeconds] is passed in rather than read from an ambient
   * clock; use [HcertClock.nowEpochSeconds] on the device.
   */
  fun verify(
    hc1: String,
    trust: HcertTrustStore,
    nowEpochSeconds: Long,
    options: VerifyOptions = VerifyOptions(),
  ): VerificationResult

  /*
   * J4.1, after [verify] returned a VHL entry. Pure functions; the app does the HTTP and writes the
   * AuditEvent. Each throws [VhlException] when the library refuses the input.
   */

  /** The ITI-YY5 manifest request for [vhl]; a passcode is required when the link's flag has P. */
  fun buildManifestRequest(vhl: VhlPayload, recipient: String, passcode: String?): ManifestRequest

  /** The documents an ITI-YY5 searchset Bundle lists. */
  fun parseManifest(bundleJson: String): List<ManifestDocument>

  /** Decrypts one fetched document (JWE compact, dir / A256GCM) with the link's key. */
  fun decrypt(jwe: String, key: String): DecryptedDocument
}

/** Loads the platform engine once; the web engine instantiates its WebAssembly module here. */
expect suspend fun loadHcertEngine(): HcertEngine

/** The device clock as the library wants it: integer seconds since the Unix epoch. */
object HcertClock {
  fun nowEpochSeconds(): Long = Clock.System.now().epochSeconds
}
