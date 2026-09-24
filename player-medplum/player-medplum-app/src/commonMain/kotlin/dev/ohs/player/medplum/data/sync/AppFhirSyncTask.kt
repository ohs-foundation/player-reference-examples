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

import dev.ohs.fhir.engine.FhirEngine
import dev.ohs.fhir.engine.sync.AcceptLocalConflictResolver
import dev.ohs.fhir.engine.sync.ConflictResolver
import dev.ohs.fhir.engine.sync.DownloadWorkManager
import dev.ohs.fhir.engine.sync.FhirSyncTask
import dev.ohs.fhir.engine.sync.download.ResourceParamsBasedDownloadWorkManager
import dev.ohs.fhir.engine.sync.download.ResourceSearchParams
import dev.ohs.fhir.engine.sync.upload.HttpCreateMethod
import dev.ohs.fhir.engine.sync.upload.HttpUpdateMethod
import dev.ohs.fhir.engine.sync.upload.UploadStrategy
import dev.ohs.fhir.model.r4.terminologies.ResourceType

/**
 * Resource types downloaded on every sync, with their search parameters. Empty parameters mean
 * "everything of this type, since the last sync". Adding a resource type later is a new map entry.
 */
private const val PAGE_SIZE = "1000"

/**
 * Medplum's default page size is 20 and its maximum is 1000, so an unset `_count` turns a cold sync
 * into hundreds of round trips. Downloads are additionally bounded server-side by the
 * practitioner's facility AccessPolicy, which is strictly better than filtering here: the device
 * cannot ask for what it is not allowed to see.
 */
internal val SYNC_RESOURCE_PARAMS: ResourceSearchParams =
  mapOf(
    ResourceType.Patient to mapOf("_count" to PAGE_SIZE),
    ResourceType.RelatedPerson to mapOf("_count" to PAGE_SIZE),
    ResourceType.Immunization to mapOf("_count" to PAGE_SIZE),
    ResourceType.ImmunizationRecommendation to mapOf("_count" to PAGE_SIZE),
    ResourceType.Observation to mapOf("_count" to PAGE_SIZE),
    // Uploads are driven by the local change log, not by this map, so an AEFI recorded here did
    // reach the server without this entry -- it just never came back. On any other device, and on
    // a fresh install, the adverse event simply did not exist, with no error anywhere.
    ResourceType.AdverseEvent to mapOf("_count" to PAGE_SIZE),
    // Who is signed in and where they work. Both are tiny and facility-scoped by the AccessPolicy,
    // so this is a handful of rows, not a second register.
    ResourceType.Practitioner to mapOf("_count" to PAGE_SIZE),
    ResourceType.Organization to mapOf("_count" to PAGE_SIZE),
  )

const val SYNC_TIMEOUT_DURATION = 120L

/**
 * This app's [FhirSyncTask]: downloads [SYNC_RESOURCE_PARAMS], resolves conflicts in favor of the
 * local change, and uploads pending local changes as individual requests.
 */
class AppFhirSyncTask(private val fhirEngine: FhirEngine) : FhirSyncTask {
  private val timestampContext = DataStoreTimestampContext(createSyncTimestampDataStore())

  override fun getFhirEngine(): FhirEngine = fhirEngine

  override fun getDownloadWorkManager(): DownloadWorkManager =
    ResourceParamsBasedDownloadWorkManager(SYNC_RESOURCE_PARAMS, timestampContext)

  override fun getConflictResolver(): ConflictResolver = AcceptLocalConflictResolver

  /**
   * Individual requests, not a transaction bundle. Medplum gates transaction bundles behind a
   * super-admin feature flag and caps them at 50 operations, so the stock 500-entry bundle fails
   * either way. POST-for-create is Medplum's documented create path; PUT-for-create is
   * update-as-create, which it does not document.
   *
   * `methodForUpdate` must stay PATCH: [UploadStrategy.forIndividualRequest] throws
   * `NotImplementedError("PUT for UPDATE not supported yet.")` for PUT. As an individual request
   * this goes out as `PATCH /{type}/{id}` with `Content-Type: application/json-patch+json`, which
   * Medplum supports — the Binary-wrapped JSON Patch problem only applies inside bundles.
   *
   * Consequence: the server assigns ids. The engine rewrites references it owns, but anything
   * holding a FHIR id outside the engine must key on `identifier`, not `id`.
   */
  override fun getUploadStrategy(): UploadStrategy =
    UploadStrategy.forIndividualRequest(
      methodForCreate = HttpCreateMethod.POST,
      methodForUpdate = HttpUpdateMethod.PATCH,
      squash = true,
    )
}
