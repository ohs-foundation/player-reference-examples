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
@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.ohs.vaxlink.hcert

import kotlin.io.encoding.Base64
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json

/*
 * The glue module (src/wasmJsMain/npm/hcert-web/index.js) wraps the Rust component's WIT surface
 * and speaks JSON strings, the envelopes in HcertModels.kt, so this side stays a thin parser.
 */
@JsModule("hcert-web")
private external object HcertWeb {
  /** Instantiates the component once; resolves to the engine's name. */
  fun init(): Promise<JsString>

  fun readUnverified(hc1: String): String

  fun newTrustStore(): JsAny

  fun addDidDocument(store: JsAny, json: String): String

  fun addPem(store: JsAny, pem: String): String

  fun trustSize(store: JsAny): Int

  fun clearTrust(store: JsAny)

  fun verify(
    hc1: String,
    store: JsAny,
    nowEpochSeconds: Double,
    clockSkewSeconds: Int,
    maxPayloads: Int,
  ): String

  fun buildManifestRequest(vhlJson: String, recipient: String, passcode: String?): String

  fun parseManifest(bundleJson: String): String

  fun decrypt(jwe: String, key: String): String
}

/** The web: Carl Leitner's Rust library as a WebAssembly component, loaded once at startup. */
private class WasmHcertEngine(override val name: String) : HcertEngine {
  override fun newTrustStore(): HcertTrustStore = WasmTrustStore(HcertWeb.newTrustStore())

  override fun readUnverified(hc1: String): ReadResult {
    val envelope = json.decodeFromString(ReadEnvelope.serializer(), HcertWeb.readUnverified(hc1))
    return envelope.decoded?.let { ReadResult.Decoded(it) }
      ?: ReadResult.Failed(envelope.failure ?: malformed("readUnverified"))
  }

  override fun verify(
    hc1: String,
    trust: HcertTrustStore,
    nowEpochSeconds: Long,
    options: VerifyOptions,
  ): VerificationResult {
    val store =
      (trust as? WasmTrustStore)?.handle ?: error("Trust store was not created by this engine")
    val text =
      HcertWeb.verify(
        hc1,
        store,
        nowEpochSeconds.toDouble(),
        options.clockSkewSeconds,
        options.maxPayloads ?: 0,
      )
    val envelope = json.decodeFromString(VerifyEnvelope.serializer(), text)
    return envelope.verified?.let { VerificationResult.Verified(it) }
      ?: VerificationResult.Failed(envelope.failure ?: malformed("verify"))
  }

  override fun buildManifestRequest(
    vhl: VhlPayload,
    recipient: String,
    passcode: String?,
  ): ManifestRequest {
    val text =
      HcertWeb.buildManifestRequest(
        json.encodeToString(VhlPayload.serializer(), vhl),
        recipient,
        passcode,
      )
    val envelope = json.decodeFromString(ManifestRequestEnvelope.serializer(), text)
    return envelope.request ?: throw VhlException(envelope.error ?: "no manifest request")
  }

  override fun parseManifest(bundleJson: String): List<ManifestDocument> {
    val envelope =
      json.decodeFromString(
        ManifestDocumentsEnvelope.serializer(),
        HcertWeb.parseManifest(bundleJson),
      )
    return envelope.documents ?: throw VhlException(envelope.error ?: "no manifest")
  }

  override fun decrypt(jwe: String, key: String): DecryptedDocument {
    val envelope = json.decodeFromString(DecryptedEnvelope.serializer(), HcertWeb.decrypt(jwe, key))
    val content =
      envelope.contentBase64 ?: throw VhlException(envelope.error ?: "decryption failed")
    return DecryptedDocument(envelope.contentType, Base64.decode(content))
  }

  private fun malformed(op: String) =
    VerificationFailure(
      VerifyStep.PREFIX,
      FailureReason.BAD_PREFIX,
      "hcert-web returned nothing for $op",
    )

  private companion object {
    val json = Json { ignoreUnknownKeys = true }
  }
}

private class WasmTrustStore(val handle: JsAny) : HcertTrustStore {
  override fun addDidDocument(json: String): Int = count(HcertWeb.addDidDocument(handle, json))

  override fun addPem(pem: String): Int = count(HcertWeb.addPem(handle, pem))

  override fun size(): Int = HcertWeb.trustSize(handle)

  override fun clear() = HcertWeb.clearTrust(handle)

  private fun count(text: String): Int {
    val envelope = Json.decodeFromString(CountEnvelope.serializer(), text)
    return envelope.count ?: throw HcertTrustException(envelope.error ?: "trust material refused")
  }
}

private var engine: HcertEngine? = null

actual suspend fun loadHcertEngine(): HcertEngine =
  engine ?: WasmHcertEngine(HcertWeb.init().await<JsString>().toString()).also { engine = it }
