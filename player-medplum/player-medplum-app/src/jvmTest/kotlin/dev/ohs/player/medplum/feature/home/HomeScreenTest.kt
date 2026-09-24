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
package dev.ohs.player.medplum.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.ohs.fhir.engine.sync.FhirDataStore
import dev.ohs.fhir.engine.sync.SyncJobStatus
import dev.ohs.fhir.engine.sync.createDataStore
import dev.ohs.player.client.registry.LocalViewRegistry
import dev.ohs.player.medplum.buildAppViewRegistry
import dev.ohs.player.medplum.data.di.repositoryModule
import dev.ohs.player.medplum.data.di.viewModelModule
import dev.ohs.player.medplum.data.repository.FhirRepository
import dev.ohs.player.medplum.data.repository.InMemorySampleFhirRepository
import dev.ohs.player.medplum.data.settings.ThemeRepository
import dev.ohs.player.medplum.data.sync.FakeSyncManager
import dev.ohs.player.medplum.data.sync.SyncManager
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {

  private fun newFhirDataStore(): FhirDataStore {
    val path = Files.createTempFile("home-screen-test", ".preferences_pb").toString()
    return FhirDataStore(createDataStore { path })
  }

  /** Backed by its own temp file so a test never inherits the developer's real theme choice. */
  private fun newThemeRepository(): ThemeRepository {
    val path = Files.createTempFile("home-screen-test-theme", ".preferences_pb").toString()
    return ThemeRepository(createDataStore { path })
  }

  private fun startTestKoin(syncManager: SyncManager) {
    startKoin {
      modules(
        module {
          single<FhirRepository> { InMemorySampleFhirRepository() }
          single { newFhirDataStore() }
          single<SyncManager> { syncManager }
          single { newThemeRepository() }
        },
        repositoryModule,
        viewModelModule,
      )
    }
  }

  @AfterTest fun tearDown() = stopKoin()

  @Test
  fun homeScreen_opensOnChildRegisterAndRevealsTheDrawerFromTheHamburger() = runComposeUiTest {
    startTestKoin(FakeSyncManager { SyncJobStatus.Succeeded() })
    val registry = buildAppViewRegistry()
    setContent {
      CompositionLocalProvider(LocalViewRegistry provides registry) {
        MaterialTheme {
          HomeScreen(
            userName = "Test User",
            onPatientClick = {},
            onRegisterChild = {},
            onRecordGrowth = {},
            onViewOwnProfile = {},
            onSignOut = {},
          )
        }
      }
    }

    // The child register is the only register, and it is what the app opens on.
    waitUntil(timeoutMillis = 5_000L) {
      onAllNodesWithText("Children").fetchSemanticsNodes().isNotEmpty()
    }

    revealDrawer()

    waitUntil(timeoutMillis = 5_000L) {
      onAllNodesWithText("Registers").fetchSemanticsNodes().isNotEmpty()
    }
    assertTrue(onAllNodesWithText("Sync now").fetchSemanticsNodes().isNotEmpty())
    // Registering a child has to be reachable without opening a record first -- that entry point
    // was missing entirely before, which is the bug this asserts against.
    assertTrue(onAllNodesWithText("REGISTER NEW CHILD").fetchSemanticsNodes().isNotEmpty())
  }

  @Test
  fun tappingSyncNow_showsProgressIndicatorWhileSyncPending() = runComposeUiTest {
    val syncStarted = CompletableDeferred<Unit>()
    val releaseSyncResult = CompletableDeferred<SyncJobStatus>()
    startTestKoin(
      FakeSyncManager {
        syncStarted.complete(Unit)
        releaseSyncResult.await()
      }
    )
    val registry = buildAppViewRegistry()
    setContent {
      CompositionLocalProvider(LocalViewRegistry provides registry) {
        MaterialTheme {
          HomeScreen(
            userName = "Test User",
            onPatientClick = {},
            onRegisterChild = {},
            onRecordGrowth = {},
            onViewOwnProfile = {},
            onSignOut = {},
          )
        }
      }
    }

    // Sync lives in the drawer, so the drawer has to be showing before it can be tapped.
    revealDrawer()
    waitUntil(timeoutMillis = 5_000L) {
      onAllNodesWithText("Sync now").fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithText("Sync now").performClick()

    waitUntil(timeoutMillis = 5_000L) { syncStarted.isCompleted }
    waitUntil(timeoutMillis = 5_000L) {
      onAllNodesWithContentDescription("Sync in progress").fetchSemanticsNodes().isNotEmpty()
    }

    releaseSyncResult.complete(SyncJobStatus.Succeeded())

    waitUntil(timeoutMillis = 5_000L) {
      onAllNodesWithContentDescription("Sync in progress").fetchSemanticsNodes().isEmpty()
    }
  }

  @Test
  fun tappingCancelSync_whileSyncing_callsCancelAndShowsCancelledMessage() = runComposeUiTest {
    val syncStarted = CompletableDeferred<Unit>()
    val releaseSyncResult = CompletableDeferred<SyncJobStatus>()
    val fake = FakeSyncManager {
      syncStarted.complete(Unit)
      releaseSyncResult.await()
    }
    startTestKoin(fake)
    val registry = buildAppViewRegistry()
    setContent {
      CompositionLocalProvider(LocalViewRegistry provides registry) {
        MaterialTheme {
          HomeScreen(
            userName = "Test User",
            onPatientClick = {},
            onRegisterChild = {},
            onRecordGrowth = {},
            onViewOwnProfile = {},
            onSignOut = {},
          )
        }
      }
    }

    // Sync lives in the drawer, so the drawer has to be showing before it can be tapped.
    revealDrawer()
    waitUntil(timeoutMillis = 5_000L) {
      onAllNodesWithText("Sync now").fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithText("Sync now").performClick()
    waitUntil(timeoutMillis = 5_000L) { syncStarted.isCompleted }
    waitUntil(timeoutMillis = 5_000L) {
      onAllNodesWithText("Cancel sync").fetchSemanticsNodes().isNotEmpty()
    }

    onNodeWithText("Cancel sync").performClick()
    waitUntil(timeoutMillis = 5_000L) { fake.cancelSyncNowCount > 0 }
    releaseSyncResult.complete(SyncJobStatus.Failed())

    waitUntil(timeoutMillis = 5_000L) {
      onAllNodesWithText("Sync cancelled.").fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun tappingSyncNow_onFailure_showsSnackbarMessage() = runComposeUiTest {
    startTestKoin(FakeSyncManager { SyncJobStatus.Failed() })
    val registry = buildAppViewRegistry()
    setContent {
      CompositionLocalProvider(LocalViewRegistry provides registry) {
        MaterialTheme {
          HomeScreen(
            userName = "Test User",
            onPatientClick = {},
            onRegisterChild = {},
            onRecordGrowth = {},
            onViewOwnProfile = {},
            onSignOut = {},
          )
        }
      }
    }

    // Sync lives in the drawer, so the drawer has to be showing before it can be tapped.
    revealDrawer()
    waitUntil(timeoutMillis = 5_000L) {
      onAllNodesWithText("Sync now").fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithText("Sync now").performClick()

    waitUntil(timeoutMillis = 5_000L) {
      onAllNodesWithText("Sync failed. Please try again.").fetchSemanticsNodes().isNotEmpty()
    }
  }

  /**
   * Makes the drawer's contents reachable, whichever layout is in play.
   *
   * Below the expanded breakpoint the drawer slides over the register and has to be opened from the
   * hamburger; at expanded width it is always on screen and there is no hamburger to tap. The test
   * asserts on what the drawer contains, not on which of the two it got.
   */
  @OptIn(ExperimentalTestApi::class)
  private fun ComposeUiTest.revealDrawer() {
    val hamburger = onAllNodesWithContentDescription("Open navigation menu")
    if (hamburger.fetchSemanticsNodes().isNotEmpty()) {
      hamburger.onFirst().performClick()
    }
  }
}
