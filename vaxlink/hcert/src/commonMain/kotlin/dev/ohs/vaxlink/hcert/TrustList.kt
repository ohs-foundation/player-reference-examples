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

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray

/**
 * The GDHCN DID trust list distribution endpoints, embedded variant (every DSC in one document).
 */
object TrustListEndpoints {
  const val DEV = "https://tng-cdn-dev.who.int/v2/trustlist/did.json"
  const val UAT = "https://tng-cdn-uat.who.int/v2/trustlist/did.json"
  const val PROD = "https://tng-cdn.who.int/v2/trustlist/did.json"
}

/** A trust list document as fetched, with where and when, so the UI can show the cache age. */
@Serializable
data class TrustListSnapshot(
  val json: String,
  val source: String,
  val fetchedAtEpochSeconds: Long,
  /** True for the copy shipped with the app, used until the first successful refresh. */
  val bundled: Boolean = false,
) {
  fun ageSeconds(nowEpochSeconds: Long): Long =
    (nowEpochSeconds - fetchedAtEpochSeconds).coerceAtLeast(0)
}

/**
 * Where the app keeps the last fetched trust list; a file on Android and desktop, storage on the
 * web.
 */
interface TrustListStorage {
  suspend fun read(): TrustListSnapshot?

  suspend fun write(snapshot: TrustListSnapshot)
}

class InMemoryTrustListStorage : TrustListStorage {
  private var snapshot: TrustListSnapshot? = null

  override suspend fun read(): TrustListSnapshot? = snapshot

  override suspend fun write(snapshot: TrustListSnapshot) {
    this.snapshot = snapshot
  }
}

/**
 * The trust list the verifier works from. [current] is the last fetched copy, or the bundled one
 * when nothing was fetched yet, so `verify` works with no network after one sync (or none, on a
 * pre-provisioned demo device). [refresh] fetches the list and keeps it only when it parses as a
 * DID document, so a captive portal's HTML never replaces a good cache.
 */
class TrustListCache(
  private val storage: TrustListStorage,
  private val url: String = TrustListEndpoints.DEV,
  private val httpClient: HttpClient? = null,
  private val bundled: (suspend () -> String)? = null,
  private val bundledFetchedAtEpochSeconds: Long = 0,
) {
  suspend fun current(): TrustListSnapshot? =
    storage.read()
      ?: bundled?.let {
        TrustListSnapshot(it(), "bundled:$url", bundledFetchedAtEpochSeconds, bundled = true)
      }

  suspend fun refresh(nowEpochSeconds: Long): Result<TrustListSnapshot> = runCatching {
    val client = httpClient ?: error("No HTTP client; the trust list cannot be refreshed offline")
    val response = client.get(url)
    check(response.status.isSuccess()) { "Trust list fetch failed: HTTP ${response.status.value}" }
    val body = response.bodyAsText()
    validateDidDocument(body)
    TrustListSnapshot(body, url, nowEpochSeconds).also { storage.write(it) }
  }

  /** Loads [current] into [store]; returns the snapshot loaded, or null when there is none. */
  suspend fun loadInto(store: HcertTrustStore): TrustListSnapshot? =
    current()?.also { store.addDidDocument(it.json) }

  companion object {
    private val json = Json { ignoreUnknownKeys = true }

    /** A DID trust list document: a JSON object with a non-empty verificationMethod array. */
    fun validateDidDocument(text: String) {
      val document =
        runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
          ?: throw HcertTrustException("Trust list is not a JSON object")
      val methods = document["verificationMethod"]?.jsonArray
      if (methods.isNullOrEmpty()) {
        throw HcertTrustException("Trust list has no verificationMethod entries")
      }
    }
  }
}
