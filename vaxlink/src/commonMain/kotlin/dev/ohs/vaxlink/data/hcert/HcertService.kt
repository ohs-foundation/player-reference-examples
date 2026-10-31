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

import co.touchlab.kermit.Logger
import dev.ohs.vaxlink.generated.resources.Res
import dev.ohs.vaxlink.hcert.HcertClock
import dev.ohs.vaxlink.hcert.HcertEngine
import dev.ohs.vaxlink.hcert.HcertTrustStore
import dev.ohs.vaxlink.hcert.PreQualProducts
import dev.ohs.vaxlink.hcert.ReadResult
import dev.ohs.vaxlink.hcert.TrustListCache
import dev.ohs.vaxlink.hcert.TrustListSnapshot
import dev.ohs.vaxlink.hcert.VerificationResult
import dev.ohs.vaxlink.hcert.loadHcertEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** The outcome of a verification together with the trust list it ran against, for the screen. */
data class Verification(val result: VerificationResult, val trustList: TrustListSnapshot?)

/**
 * The app's one door to the HCERT engine: loads it once, keeps a trust store built from the cached
 * trust list and rebuilds it when the list changes, and refreshes the list on sync. Screens never
 * touch the engine directly.
 */
class HcertService(
  private val trustListCache: TrustListCache,
  val products: PreQualProducts = PreQualProducts.DEMO,
) {
  private val lock = Mutex()
  private var engine: HcertEngine? = null
  private var trustStore: HcertTrustStore? = null
  private var loadedFrom: TrustListSnapshot? = null

  suspend fun engine(): HcertEngine =
    lock.withLock { engine ?: loadHcertEngine().also { engine = it } }

  /** J2: decode and show, never trust. Works with no trust list on the device. */
  suspend fun read(hc1: String): ReadResult = engine().readUnverified(hc1)

  /** J3 and J4.1: the full check against the cached trust list, offline. */
  suspend fun verify(hc1: String, now: Long = HcertClock.nowEpochSeconds()): Verification {
    val engine = engine()
    val (store, snapshot) = trustStore(engine)
    return Verification(engine.verify(hc1, store, now), snapshot)
  }

  suspend fun trustList(): TrustListSnapshot? = trustListCache.current()

  /** Fetches the trust list; the cached copy stays when the fetch fails. */
  suspend fun refreshTrustList(
    now: Long = HcertClock.nowEpochSeconds()
  ): Result<TrustListSnapshot> = trustListCache.refresh(now)

  /** The sync hook: a failed refresh is logged, never surfaced as a sync failure. */
  suspend fun refreshTrustListQuietly() {
    refreshTrustList().onFailure {
      Logger.w(it) { "Trust list refresh failed; keeping the cached copy" }
    }
  }

  private suspend fun trustStore(engine: HcertEngine): Pair<HcertTrustStore, TrustListSnapshot?> =
    lock.withLock {
      val current = trustListCache.current()
      val store = trustStore
      if (store != null && current == loadedFrom) return@withLock store to current
      val fresh = engine.newTrustStore()
      current?.let { fresh.addDidDocument(it.json) }
      trustStore = fresh
      loadedFrom = current
      fresh to current
    }
}

/** The GDHCN DEV trust list shipped with the app, so a device verifies before its first sync. */
object BundledTrustList {
  /** When the bundled copy was fetched from tng-cdn-dev.who.int: 2026-10-23T07:58Z. */
  const val FETCHED_AT_EPOCH_SECONDS = 1_792_742_280L

  @OptIn(ExperimentalResourceApi::class)
  suspend fun load(): String =
    Res.readBytes("files/trust/gdhcn-dev-trustlist.json").decodeToString()
}
