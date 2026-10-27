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
package dev.ohs.player.medplum.data.di

import dev.ohs.fhir.engine.FhirEngineProvider
import dev.ohs.player.medplum.auth.AuthService
import dev.ohs.player.medplum.auth.AuthViewModel
import dev.ohs.player.medplum.auth.OAuthConfig
import dev.ohs.player.medplum.auth.OidcAuthApi
import dev.ohs.player.medplum.auth.SessionRepository
import dev.ohs.player.medplum.auth.SessionStore
import dev.ohs.player.medplum.data.repository.FhirEngineRepository
import dev.ohs.player.medplum.data.repository.FhirRepository
import dev.ohs.player.medplum.data.repository.ImmunizationWriter
import dev.ohs.player.medplum.data.repository.PatientRepository
import dev.ohs.player.medplum.data.repository.PatientWriter
import dev.ohs.player.medplum.data.repository.PractitionerRepository
import dev.ohs.player.medplum.data.settings.ThemeRepository
import dev.ohs.player.medplum.data.sync.DataStoreInitialSyncStore
import dev.ohs.player.medplum.data.sync.InitialSyncStore
import dev.ohs.player.medplum.data.sync.createSyncTimestampDataStore
import dev.ohs.player.medplum.feature.home.HomeViewModel
import dev.ohs.player.medplum.feature.patient.list.PatientListViewModel
import dev.ohs.player.medplum.feature.patient.profile.PatientProfileViewModel
import dev.ohs.player.medplum.feature.practitioner.PractitionerViewModel
import dev.ohs.player.medplum.feature.questionnaire.QuestionnaireHostViewModel
import dev.ohs.player.medplum.feature.questionnaire.QuestionnaireLaunchContext
import dev.ohs.player.medplum.feature.questionnaire.QuestionnaireService
import dev.ohs.player.medplum.feature.sync.InitialSyncViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Binds the production [FhirRepository], backed by [FhirEngineRepository]. Requires a
 * [dev.ohs.fhir.FhirEngine] binding, supplied by each platform's module (see `initKoin` callers).
 */
internal val fhirEngineRepositoryModule = module {
  single<FhirRepository> { FhirEngineRepository(get()) }
}

/**
 * Repositories that only depend on [FhirRepository] — kept separate from
 * [fhirEngineRepositoryModule] so tests can swap in a fake [FhirRepository] without redeclaring
 * these bindings.
 */
internal val repositoryModule = module {
  single { PatientRepository(get()) }
  single { PractitionerRepository(get()) }
  single { ImmunizationWriter(get()) }
  single { PatientWriter(get()) }
}

internal val serviceModule = module { factory { QuestionnaireService(get()) } }

/**
 * User preferences. [ThemeRepository] shares the sync DataStore rather than opening a second file:
 * `createSyncTimestampDataStore()` hands back one lazily-created instance per platform, and two
 * DataStores over the same path in one process is an error. The file name is historical -- it is a
 * plain preferences store, not sync-specific.
 */
internal val settingsModule = module { single { ThemeRepository(createSyncTimestampDataStore()) } }

/**
 * [SyncManager] isn't bound here — each platform's `initKoin` caller supplies its own
 * implementation, constructing `AppFhirSyncTask` directly rather than through Koin (see
 * `WorkManagerSyncManager` on Android, `ForegroundSyncManager` on JVM/web, and `IosSyncManager` on
 * iOS).
 */
internal val syncModule = module {
  single { FhirEngineProvider.getFhirDataStore() }
  single<InitialSyncStore> { DataStoreInitialSyncStore(createSyncTimestampDataStore()) }
}

/**
 * `SessionStore`/`SessionRepository`/`AuthService` — everything downstream of the plain
 * `SessionRepository` object (kept outside Koin; see its kdoc) is Koin-injected here.
 */
internal val authModule = module {
  single { OAuthConfig.Default }
  single<SessionStore> { SessionRepository }
  single { OidcAuthApi(get()) }
  single { AuthService(get(), get(), get()) }
}

internal val viewModelModule = module {
  viewModel { PatientListViewModel(get()) }
  viewModel { (patientId: String) ->
    PatientProfileViewModel(patientId, get(), get(), get(), get())
  }
  viewModel { PractitionerViewModel(get(), get()) }
  viewModel { (questionnaireId: String, launchContext: QuestionnaireLaunchContext) ->
    QuestionnaireHostViewModel(questionnaireId, launchContext, get(), get(), get(), get())
  }
  viewModel { HomeViewModel(get(), get(), get()) }
  viewModel { AuthViewModel(get(), get()) }
  viewModel { InitialSyncViewModel(get(), get()) }
}
