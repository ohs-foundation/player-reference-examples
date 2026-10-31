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

import dev.ohs.vaxlink.hcert.CertificateFingerprint.toHex
import who.smart.trust.HcertResult
import who.smart.trust.core.DEFAULT_LIMITS
import who.smart.trust.core.Hcert
import who.smart.trust.core.SubclaimRegistry
import who.smart.trust.payloads.Payload
import who.smart.trust.trust.TrustError
import who.smart.trust.trust.TrustStore
import who.smart.trust.trust.TrustedDsc
import who.smart.trust.types.Diagnostic
import who.smart.trust.verifier.HcertEntry as LibraryEntry
import who.smart.trust.verifier.UnverifiedEntry
import who.smart.trust.verifier.VerificationFailure as LibraryFailure
import who.smart.trust.verifier.VerificationResult as LibraryResult
import who.smart.trust.verifier.VerifierOptions
import who.smart.trust.vhl.VhlError
import who.smart.trust.vhl.VhlPayload as LibraryVhl

/** Android and desktop: Carl Leitner's hcert-core, used as published, adapted to the facade. */
object JvmHcertEngine : HcertEngine {
  override val name: String = "io.github.litlfred:hcert-core (Kotlin, JVM)"

  private val registry = SubclaimRegistry()

  override fun newTrustStore(): HcertTrustStore = JvmTrustStore(Hcert.trust.newTrustStore())

  override fun readUnverified(hc1: String): ReadResult =
    when (val r = Hcert.verifier.readUnverified(hc1, registry, DEFAULT_LIMITS)) {
      is HcertResult.Ok ->
        ReadResult.Decoded(
          UnverifiedHcert(
            kid = r.value.kid?.toHex(),
            iss = r.value.iss,
            iat = r.value.iat,
            exp = r.value.exp,
            entries = r.value.entries.map { it.toModel() },
            diagnostics = r.value.diagnostics.map { it.text() },
          )
        )
      is HcertResult.Err -> ReadResult.Failed(r.error.toModel())
    }

  override fun verify(
    hc1: String,
    trust: HcertTrustStore,
    nowEpochSeconds: Long,
    options: VerifyOptions,
  ): VerificationResult {
    val store =
      (trust as? JvmTrustStore)?.store ?: error("Trust store was not created by this engine")
    val libraryOptions =
      VerifierOptions(DEFAULT_LIMITS, options.clockSkewSeconds, options.maxPayloads)
    return when (
      val r = Hcert.verifier.verify(hc1, store, registry, nowEpochSeconds, libraryOptions)
    ) {
      is LibraryResult.Verified ->
        VerificationResult.Verified(
          VerifiedHcert(
            iss = r.value.iss,
            iat = r.value.iat,
            exp = r.value.exp,
            entries = r.value.entries.map { it.toModel() },
            dsc = r.value.dsc.toModel(),
            diagnostics = r.value.diagnostics.map { it.text() },
          )
        )
      is LibraryResult.Failed -> VerificationResult.Failed(r.value.toModel())
    }
  }

  override fun buildManifestRequest(
    vhl: VhlPayload,
    recipient: String,
    passcode: String?,
  ): ManifestRequest {
    val payload =
      LibraryVhl(
        url = vhl.url,
        key = vhl.key,
        exp = vhl.exp,
        flag = vhl.flag,
        label = vhl.label,
        v = vhl.v,
        fhirBaseUrl = vhl.fhirBaseUrl,
      )
    val r = Hcert.vhl.buildManifestRequest(payload, recipient, passcode, null).orThrow()
    return ManifestRequest(url = r.url, contentType = r.contentType, body = r.body)
  }

  override fun parseManifest(bundleJson: String): List<ManifestDocument> =
    Hcert.vhl.parseManifest(bundleJson).orThrow().map {
      ManifestDocument(
        url = it.url,
        reference = it.reference,
        contentType = it.contentType,
        title = it.title,
      )
    }

  override fun decrypt(jwe: String, key: String): DecryptedDocument {
    val d = Hcert.vhl.decrypt(jwe, key).orThrow()
    return DecryptedDocument(contentType = d.contentType, content = d.content)
  }

  private fun <T> HcertResult<T, VhlError>.orThrow(): T =
    when (this) {
      is HcertResult.Ok -> value
      is HcertResult.Err ->
        throw VhlException(
          when (val e = error) {
            is VhlError.Invalid -> "Link payload invalid: " + e.value.joinToString { it.message }
            is VhlError.NotAVhlink -> "Not a vhlink: ${e.value}"
            is VhlError.Manifest -> "Manifest: ${e.value}"
            is VhlError.DecryptFailed -> "Decryption failed: ${e.value}"
            is VhlError.Unsupported -> "Unsupported: ${e.value}"
          }
        )
    }

  private fun UnverifiedEntry.toModel(): HcertEntry =
    entry(subclaim, payload, keyUsageAuthorised = true)

  private fun LibraryEntry.toModel(): HcertEntry = entry(subclaim, payload, keyUsageAuthorised)

  private fun entry(subclaim: Int, payload: Payload?, keyUsageAuthorised: Boolean): HcertEntry =
    when (payload) {
      is Payload.DvcMin ->
        HcertEntry(
          subclaim = subclaim,
          dvc =
            payload.value.let {
              DvcMin(
                n = it.n,
                dob = it.dob,
                s = it.s,
                nt = it.nt,
                id = it.id,
                dt = it.dt,
                gn = it.gn,
                vx =
                  DvcVaccineDetails(
                    vp = it.vx.vp,
                    dv = it.vx.dv,
                    cn = it.vx.cn,
                    issuerId = it.vx.`is`,
                    bo = it.vx.bo,
                  ),
                v = it.v,
              )
            },
          keyUsageAuthorised = keyUsageAuthorised,
        )
      is Payload.Vhl ->
        HcertEntry(
          subclaim = subclaim,
          vhl =
            payload.value.let {
              VhlPayload(
                url = it.url,
                key = it.key,
                exp = it.exp,
                flag = it.flag,
                label = it.label,
                v = it.v,
                fhirBaseUrl = it.fhirBaseUrl,
              )
            },
          keyUsageAuthorised = keyUsageAuthorised,
        )
      is Payload.Raw ->
        HcertEntry(
          subclaim = subclaim,
          raw = payload.value.cbor.toHex(),
          keyUsageAuthorised = keyUsageAuthorised,
        )
      null -> HcertEntry(subclaim = subclaim, keyUsageAuthorised = keyUsageAuthorised)
    }

  private fun TrustedDsc.toModel() =
    TrustedDscInfo(
      kid = kid.toHex(),
      country = country,
      notBefore = notBefore,
      notAfter = notAfter,
      domain = domain,
      participant = participant,
    )

  private fun LibraryFailure.toModel() =
    VerificationFailure(
      step = VerifyStep.valueOf(step.name),
      reason = FailureReason.valueOf(reason.name),
      detail = detail,
      diagnostics = diagnostics.map { it.text() },
    )

  private fun Diagnostic.text() = "${step.name}: $message"
}

internal class JvmTrustStore(internal val store: TrustStore) : HcertTrustStore {
  override fun addDidDocument(json: String): Int = store.addDidDocument(json).orThrow()

  override fun addPem(pem: String): Int = store.addPem(pem).orThrow()

  override fun size(): Int = store.size()

  override fun clear() = store.clear()

  private fun HcertResult<Int, TrustError>.orThrow(): Int =
    when (this) {
      is HcertResult.Ok -> value
      is HcertResult.Err ->
        throw HcertTrustException(
          when (val e = error) {
            is TrustError.Parse -> "Trust material not understood: ${e.value}"
            is TrustError.UnknownTrustDomain -> "Unknown trust domain: ${e.value}"
            is TrustError.ProofInvalid -> "Trust list proof invalid: ${e.value}"
          }
        )
    }
}

actual suspend fun loadHcertEngine(): HcertEngine = JvmHcertEngine
