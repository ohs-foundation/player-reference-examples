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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The subset of the SMART Trust HCERT contract (smart-trust-tools, api/wit) the Player needs,
 * declared once for every target. The Kotlin engine maps its own types onto these; the web engine
 * receives them as JSON from the Rust component's glue. Field names follow the WIT contract and
 * the trust-phw logical models, so a reader can cross-check against
 * https://litlfred.github.io/smart-trust-tools/viewers/api.html.
 */

/** DVCMinVaccineDetails: the one immunization event a certificate documents. */
@Serializable
data class DvcVaccineDetails(
  /** WHO PreQual product identifier, required binding to PreQualProductIds. */
  val vp: String,
  /** Date of vaccination, a FHIR date. */
  val dv: String,
  /** Supervising clinician. */
  val cn: String? = null,
  /** Issuing organization identifier. The element is `is`, a Kotlin keyword. */
  @SerialName("is") val issuerId: String? = null,
  /** Batch or lot number. */
  val bo: String,
)

/** DVCMin (trust-phw): the certificate payload under hcert subclaim -6. */
@Serializable
data class DvcMin(
  val n: String,
  val dob: String,
  val s: String,
  /** Nationality, ISO 3166-1 alpha-3. */
  val nt: String,
  /** Identification document number; absent for a newborn. */
  val id: String? = null,
  /** Identification document type. */
  val dt: String? = null,
  /** Parent or guardian name. */
  val gn: String? = null,
  val vx: DvcVaccineDetails,
  val v: String,
)

/** The SMART Health Links payload a Verifiable Health Link carries at hcert key 5. */
@Serializable
data class VhlPayload(
  val url: String,
  val key: String,
  val exp: Long? = null,
  val flag: String? = null,
  val label: String? = null,
  val v: Int? = null,
  val fhirBaseUrl: String? = null,
)

/** One entry of the hcert claim. Exactly one of [dvc], [vhl] and [raw] is set. */
@Serializable
data class HcertEntry(
  val subclaim: Int,
  val dvc: DvcMin? = null,
  val vhl: VhlPayload? = null,
  /** Lower-case hex of the CBOR for a subclaim the registry does not know. */
  val raw: String? = null,
  /** False when the DSC's extended key usage does not permit this entry's type. */
  val keyUsageAuthorised: Boolean = true,
)

/**
 * Decoded without any trust lookup or signature check. For display and triage where verification is
 * impossible, the CHW's offline household visit (J2); never a basis for trust.
 */
@Serializable
data class UnverifiedHcert(
  /** Key identifier from the COSE header, lower-case hex. */
  val kid: String? = null,
  val iss: String? = null,
  val iat: Long? = null,
  val exp: Long? = null,
  val entries: List<HcertEntry> = emptyList(),
  val diagnostics: List<String> = emptyList(),
) {
  val dvc: DvcMin?
    get() = entries.firstNotNullOfOrNull { it.dvc }

  val vhl: VhlPayload?
    get() = entries.firstNotNullOfOrNull { it.vhl }
}

/** The Document Signing Certificate that verified a signature. */
@Serializable
data class TrustedDscInfo(
  /** Lower-case hex, first 8 bytes of the SHA-256 of the DER certificate. */
  val kid: String,
  /** Subject C attribute, ISO 3166-1 alpha-2. */
  val country: String? = null,
  val notBefore: Long,
  val notAfter: Long,
  /** Trust domain and participant from the DID trust list path, when loaded from one. */
  val domain: String? = null,
  val participant: String? = null,
)

/** A certificate whose signature verified against a DSC in the trust store. */
@Serializable
data class VerifiedHcert(
  val iss: String? = null,
  val iat: Long,
  val exp: Long,
  val entries: List<HcertEntry>,
  val dsc: TrustedDscInfo,
  val diagnostics: List<String> = emptyList(),
) {
  val dvc: DvcMin?
    get() = entries.firstNotNullOfOrNull { it.dvc }

  val vhl: VhlPayload?
    get() = entries.firstNotNullOfOrNull { it.vhl }
}

/** Verification steps, in the order the HCERT walkthrough (section 11) runs them. */
enum class VerifyStep {
  PREFIX,
  BASE45,
  INFLATE,
  COSE,
  HEADER,
  TRUST_LOOKUP,
  SIGNATURE,
  KEY_USAGE,
  CWT,
  TIME,
  HCERT,
  PAYLOAD,
}

/** Why verification stopped. */
enum class FailureReason {
  BAD_PREFIX,
  UNSUPPORTED_CONTEXT_VERSION,
  BASE45_INVALID,
  INFLATE_FAILED,
  LIMIT_EXCEEDED,
  COSE_MALFORMED,
  HEADER_INVALID,
  UNSUPPORTED_ALGORITHM,
  KID_NOT_FOUND,
  SIGNATURE_INVALID,
  KEY_USAGE_MISMATCH,
  EXPIRED,
  NOT_YET_VALID,
  HCERT_MISSING,
  MULTIPLE_SUBCLAIMS,
  PAYLOAD_INVALID,
}

/** A typed failure. It carries no payload: content is only reachable through a verified result. */
@Serializable
data class VerificationFailure(
  val step: VerifyStep,
  val reason: FailureReason,
  val detail: String = "",
  val diagnostics: List<String> = emptyList(),
)

sealed interface VerificationResult {
  data class Verified(val hcert: VerifiedHcert) : VerificationResult

  data class Failed(val failure: VerificationFailure) : VerificationResult
}

sealed interface ReadResult {
  data class Decoded(val hcert: UnverifiedHcert) : ReadResult

  data class Failed(val failure: VerificationFailure) : ReadResult
}

/** Trust material that could not be loaded; nothing was added to the store. */
class HcertTrustException(message: String) : Exception(message)

/*
 * Wire envelopes shared with the web glue (src/wasmJsMain/npm/hcert-web/index.js), which returns
 * one of these as a JSON string. The Kotlin engine never serialises them.
 */
@Serializable
internal data class ReadEnvelope(
  val decoded: UnverifiedHcert? = null,
  val failure: VerificationFailure? = null,
)

@Serializable
internal data class VerifyEnvelope(
  val verified: VerifiedHcert? = null,
  val failure: VerificationFailure? = null,
)

@Serializable internal data class CountEnvelope(val count: Int? = null, val error: String? = null)

/*
 * Verifiable Health Links (IHE ITI VHL, SMART Health Links payload). The library does the format
 * work; the app does the HTTPS: POST the manifest request, GET each document, then decrypt.
 */

/** The ITI-YY5 Retrieve Manifest request the app POSTs, form-encoded, to `<base>/List/_search`. */
@Serializable
data class ManifestRequest(val url: String, val contentType: String, val body: String)

/** One document the manifest lists; the app GETs [url] (ITI-68), or reads [reference] first. */
@Serializable
data class ManifestDocument(
  val url: String? = null,
  val reference: String? = null,
  val contentType: String? = null,
  val title: String? = null,
)

/** A decrypted VHL document, typically a FHIR IPS Bundle as JSON. */
class DecryptedDocument(val contentType: String?, val content: ByteArray) {
  val text: String
    get() = content.decodeToString()
}

/** A VHL operation refused by the library: a bad link, manifest or key. */
class VhlException(message: String) : Exception(message)

@Serializable
internal data class ManifestRequestEnvelope(
  val request: ManifestRequest? = null,
  val error: String? = null,
)

@Serializable
internal data class ManifestDocumentsEnvelope(
  val documents: List<ManifestDocument>? = null,
  val error: String? = null,
)

@Serializable
internal data class DecryptedEnvelope(
  val contentType: String? = null,
  /** Standard base64 of the plaintext bytes. */
  val contentBase64: String? = null,
  val error: String? = null,
)
