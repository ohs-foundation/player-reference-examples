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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.ohs.vaxlink.hcert.TrustListSnapshot
import dev.ohs.vaxlink.hcert.TrustListStorage
import kotlinx.coroutines.flow.first

internal const val TRUST_LIST_DATASTORE_FILE_NAME = "trust_list.preferences_pb"

/**
 * Its own DataStore file, apart from the sync timestamps, so a worker change (which clears those)
 * does not throw away trust material that belongs to the device, not the user.
 */
internal expect fun createTrustListDataStore(): DataStore<Preferences>

/**
 * The fetched trust list, kept in Preferences: a file on Android and desktop, localStorage on the
 * web.
 */
internal class DataStoreTrustListStorage(private val dataStore: DataStore<Preferences>) :
  TrustListStorage {
  override suspend fun read(): TrustListSnapshot? {
    val preferences = dataStore.data.first()
    val json = preferences[JSON] ?: return null
    return TrustListSnapshot(
      json = json,
      source = preferences[SOURCE] ?: "",
      fetchedAtEpochSeconds = preferences[FETCHED_AT] ?: 0,
      bundled = preferences[BUNDLED] ?: false,
    )
  }

  override suspend fun write(snapshot: TrustListSnapshot) {
    dataStore.edit {
      it[JSON] = snapshot.json
      it[SOURCE] = snapshot.source
      it[FETCHED_AT] = snapshot.fetchedAtEpochSeconds
      it[BUNDLED] = snapshot.bundled
    }
  }

  private companion object {
    val JSON = stringPreferencesKey("trust_list_json")
    val SOURCE = stringPreferencesKey("trust_list_source")
    val FETCHED_AT = longPreferencesKey("trust_list_fetched_at")
    val BUNDLED = booleanPreferencesKey("trust_list_bundled")
  }
}
