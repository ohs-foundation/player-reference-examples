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
package dev.ohs.workflow.examples.data.sync

import dev.ohs.fhir.engine.sync.DownloadWorkManager
import dev.ohs.fhir.engine.sync.download.DownloadRequest
import dev.ohs.fhir.engine.sync.download.ResourceParamsBasedDownloadWorkManager
import dev.ohs.fhir.engine.sync.download.ResourceParamsBasedDownloadWorkManager.TimestampContext
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.terminologies.ResourceType
import dev.ohs.workflow.examples.auth.UserContext

/**
 * Downloads what the user's role [SyncConfig] names. The config is read on first use because
 * `FhirSyncTask.getDownloadWorkManager` cannot suspend.
 */
class RoleDownloadWorkManager(
  private val context: UserContext?,
  private val timestampContext: TimestampContext,
) : DownloadWorkManager {
  private var delegate: DownloadWorkManager? = null

  private suspend fun delegate(): DownloadWorkManager =
    delegate
      ?: ResourceParamsBasedDownloadWorkManager(
          context?.let { SyncConfigResolver.resolve(loadSyncConfig(it.role), it) }.orEmpty(),
          timestampContext,
        )
        .also { delegate = it }

  override suspend fun getNextRequest(): DownloadRequest? = delegate().getNextRequest()

  override suspend fun getSummaryRequestUrls(): Map<ResourceType, String> =
    delegate().getSummaryRequestUrls()

  override suspend fun processResponse(response: Resource): Collection<Resource> =
    delegate().processResponse(response)
}
