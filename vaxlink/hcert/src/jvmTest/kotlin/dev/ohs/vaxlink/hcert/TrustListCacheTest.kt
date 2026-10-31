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
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The trust list cache: bundled until synced, refreshed only with a real DID document, age visible.
 */
class TrustListCacheTest {
  private val now = 1_793_000_000L

  private fun client(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    HttpClient(
      MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }
    )

  @Test
  fun `with nothing fetched the bundled snapshot is used and marked as such`() = runTest {
    val cache =
      TrustListCache(
        InMemoryTrustListStorage(),
        bundled = { Vectors.xxrDscDidDocument },
        bundledFetchedAtEpochSeconds = now - 3 * 86_400,
      )
    val snapshot = assertNotNull(cache.current())
    assertTrue(snapshot.bundled)
    assertEquals(3 * 86_400, snapshot.ageSeconds(now))
    val engine = loadHcertEngine()
    val store = engine.newTrustStore()
    assertEquals(snapshot, cache.loadInto(store))
    assertEquals(1, store.size())
  }

  @Test
  fun `a refresh stores the fetched list and the engine verifies with it offline afterwards`() =
    runTest {
      val storage = InMemoryTrustListStorage()
      val cache = TrustListCache(storage, httpClient = client(Vectors.devTrustList))
      assertNull(cache.current())
      val snapshot = cache.refresh(now).getOrThrow()
      assertEquals(TrustListEndpoints.DEV, snapshot.source)
      assertEquals(0, snapshot.ageSeconds(now))
      assertEquals(snapshot, storage.read())
      assertTrue(!snapshot.bundled)

      val offline = TrustListCache(storage) // no HTTP client at all
      val store = loadHcertEngine().newTrustStore()
      assertNotNull(offline.loadInto(store))
      assertIs<VerificationResult.Verified>(
        loadHcertEngine().verify(Vectors.hepb.hc1, store, Vectors.hepb.iat + 3600)
      )
      assertTrue(offline.refresh(now).isFailure)
    }

  @Test
  fun `a captive portal page or an error never replaces the cached list`() = runTest {
    val storage = InMemoryTrustListStorage()
    val good = TrustListSnapshot(Vectors.xxrDscDidDocument, TrustListEndpoints.DEV, now - 60)
    storage.write(good)
    val html =
      TrustListCache(storage, httpClient = client("<html>Sign in to the venue Wi-Fi</html>"))
    val failure = html.refresh(now).exceptionOrNull()
    assertIs<HcertTrustException>(failure)
    assertEquals(good, storage.read())
    val error = TrustListCache(storage, httpClient = client("", HttpStatusCode.ServiceUnavailable))
    assertTrue(error.refresh(now).isFailure)
    assertEquals(good, storage.read())
    val empty =
      TrustListCache(storage, httpClient = client("""{"id":"did:web:x","verificationMethod":[]}"""))
    assertTrue(empty.refresh(now).isFailure)
    assertEquals(good, storage.read())
  }
}
