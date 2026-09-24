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

import dev.ohs.player.medplum.data.datasource.PROFILE_REV_INCLUDES
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The profile reads five resource types back out of the local database; sync has to put all five
 * there. These two lists live in different files and nothing but this test connects them.
 *
 * `AdverseEvent` was missing from the sync map for real. It cost nothing on the device that
 * recorded the reaction — uploads come from the local change log, not from this map, so the
 * AdverseEvent did reach the server and did render locally — but it was never downloaded again.
 * Every other tablet, and the same tablet after a reinstall, showed the child with no adverse
 * events at all. No error, no empty-state hint, no log line: the data was simply absent.
 *
 * That is the failure mode this test exists to prevent, and it will recur the next time someone
 * adds a profile section without touching the sync map.
 */
class ProfileSyncCoverageTest {

  @Test
  fun everyResourceTypeOnTheProfileIsAlsoDownloadedBySync() {
    val downloaded = SYNC_RESOURCE_PARAMS.keys.map { it.name }.toSet()
    val missing = PROFILE_REV_INCLUDES.map { (resourceType, _) -> resourceType } - downloaded

    assertTrue(
      missing.isEmpty(),
      "The profile renders ${missing.joinToString()} but sync never downloads " +
        "${if (missing.size == 1) "it" else "them"}. Records of this type will save, upload and " +
        "display on the device that created them, then be invisible everywhere else. Add " +
        "${missing.joinToString()} to SYNC_RESOURCE_PARAMS in AppFhirSyncTask.kt — and check the " +
        "server AccessPolicy grants the type, or the download 403s just as silently.",
    )
  }

  @Test
  fun theRegisterItselfIsDownloaded() {
    // PROFILE_REV_INCLUDES holds only the reverse-included types; Patient is the subject of the
    // search, so it would never be caught by the assertion above.
    assertTrue(
      SYNC_RESOURCE_PARAMS.keys.any { it.name == "Patient" },
      "Sync does not download Patient; the register would be permanently empty.",
    )
  }
}
