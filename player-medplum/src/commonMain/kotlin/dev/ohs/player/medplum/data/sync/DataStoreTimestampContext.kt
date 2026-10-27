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
package dev.ohs.player.medplum.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.ohs.fhir.engine.sync.download.ResourceParamsBasedDownloadWorkManager
import dev.ohs.fhir.model.r4.terminologies.ResourceType
import kotlin.time.Instant
import kotlinx.coroutines.flow.first

/**
 * File name for the [DataStore] backing [DataStoreTimestampContext] — see
 * [createSyncTimestampDataStore].
 */
internal const val SYNC_TIMESTAMP_DATASTORE_FILE_NAME = "sync_timestamps.preferences_pb"

/** Supplies the platform [DataStore] backing [DataStoreTimestampContext]. */
internal expect fun createSyncTimestampDataStore(): DataStore<Preferences>

/**
 * Persists each [ResourceType]'s download cursor (`_lastUpdated`) in a Preferences [DataStore], so
 * a fresh app launch resumes incremental sync instead of re-downloading every configured resource
 * type from scratch. Mirrors kotlin-fhir-engine's engine-app `DemoDataStore`.
 */
class DataStoreTimestampContext(private val dataStore: DataStore<Preferences>) :
  ResourceParamsBasedDownloadWorkManager.TimestampContext {

  override suspend fun saveLastUpdatedTimestamp(resourceType: ResourceType, timestamp: String?) {
    if (timestamp == null) return
    dataStore.edit { prefs -> prefs[stringPreferencesKey(resourceType.name)] = toUtc(timestamp) }
  }

  /**
   * Normalise to a UTC `...Z` instant.
   *
   * The engine interpolates this straight into `_lastUpdated=gt<timestamp>` without
   * percent-encoding it. On a device in a positive-offset zone the server's `meta.lastUpdated`
   * comes back as e.g. `2026-09-20T20:48:15.252+03:00`, and the `+` in a query string decodes as a
   * SPACE -- so the server sees `2026-09-20T20:48:15.252 03:00` and rejects it:
   *
   * 400 Invalid format for date search parameter: 2026-09-20T10:00:00.000 03:00
   *
   * Every incremental sync then fails while the UI shows stale data and no error, because the FIRST
   * sync has no timestamp and succeeds. `Z` carries the same instant with no `+` in it.
   *
   * Anything unparseable is stored unchanged rather than dropped: a malformed timestamp that still
   * syncs is better than silently restarting from the beginning of time.
   */
  private fun toUtc(timestamp: String): String =
    runCatching { Instant.parse(timestamp).toString() }.getOrDefault(timestamp)

  // Normalised on the way out as well as in: a device that already stored an offset-form
  // timestamp before this fix would otherwise stay wedged forever, because the request it
  // builds 400s and a failed sync never writes a corrected value back.
  override suspend fun getLasUpdateTimestamp(resourceType: ResourceType): String? =
    dataStore.data.first()[stringPreferencesKey(resourceType.name)]?.let(::toUtc)
}
