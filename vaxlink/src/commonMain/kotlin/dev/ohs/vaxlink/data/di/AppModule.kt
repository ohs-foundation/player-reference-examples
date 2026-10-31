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
package dev.ohs.vaxlink.data.di

import dev.ohs.fhir.engine.FhirEngineProvider
import dev.ohs.vaxlink.auth.AuthService
import dev.ohs.vaxlink.auth.AuthViewModel
import dev.ohs.vaxlink.auth.OAuthConfig
import dev.ohs.vaxlink.auth.OidcAuthApi
import dev.ohs.vaxlink.auth.PractitionerDetailsApi
import dev.ohs.vaxlink.auth.SessionRepository
import dev.ohs.vaxlink.auth.SessionStore
import dev.ohs.vaxlink.data.hcert.BundledTrustList
import dev.ohs.vaxlink.data.hcert.DataStoreTrustListStorage
import dev.ohs.vaxlink.data.hcert.HcertService
import dev.ohs.vaxlink.data.hcert.createTrustListDataStore
import dev.ohs.vaxlink.data.repository.FhirEngineRepository
import dev.ohs.vaxlink.data.repository.FhirRepository
import dev.ohs.vaxlink.data.repository.PatientRepository
import dev.ohs.vaxlink.data.sync.DataStoreInitialSyncStore
import dev.ohs.vaxlink.data.sync.InitialSyncStore
import dev.ohs.vaxlink.data.sync.createSyncTimestampDataStore
import dev.ohs.vaxlink.feature.home.HomeViewModel
import dev.ohs.vaxlink.feature.patient.list.PatientListViewModel
import dev.ohs.vaxlink.feature.patient.profile.PatientProfileViewModel
import dev.ohs.vaxlink.feature.questionnaire.QuestionnaireHostViewModel
import dev.ohs.vaxlink.feature.questionnaire.QuestionnaireLaunchContext
import dev.ohs.vaxlink.feature.questionnaire.QuestionnaireService
import dev.ohs.vaxlink.feature.role.UserContextViewModel
import dev.ohs.vaxlink.feature.scan.CertificateScanViewModel
import dev.ohs.vaxlink.feature.scan.ScanMode
import dev.ohs.vaxlink.feature.sync.InitialSyncViewModel
import dev.ohs.vaxlink.hcert.TrustListCache
import dev.ohs.vaxlink.workflow.EngineTransactor
import dev.ohs.vaxlink.workflow.Transactor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Binds the production [FhirRepository], backed by [FhirEngineRepository]. Requires a `FhirEngine`
 * binding, supplied by each platform's module (see `initKoin` callers).
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
  single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
}

internal val serviceModule = module {
  single<Transactor> { EngineTransactor(get()) }
  factory { QuestionnaireService(get(), get()) }
  // The GDHCN DEV trust list: bundled until the first sync fetches a fresh copy.
  single {
    TrustListCache(
      DataStoreTrustListStorage(createTrustListDataStore()),
      httpClient = OidcAuthApi.defaultHttpClient(),
      bundled = BundledTrustList::load,
      bundledFetchedAtEpochSeconds = BundledTrustList.FETCHED_AT_EPOCH_SECONDS,
    )
  }
  single { HcertService(get()) }
}

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
  single { PractitionerDetailsApi() }
}

internal val viewModelModule = module {
  viewModel { PatientListViewModel(get()) }
  viewModel { (patientId: String) -> PatientProfileViewModel(patientId, get()) }
  viewModel { (questionnaireId: String, launchContext: QuestionnaireLaunchContext) ->
    QuestionnaireHostViewModel(questionnaireId, launchContext, get(), get(), get())
  }
  viewModel {
    // Resolved lazily and optionally, so the screen tests that stub the sync layer need no engine.
    HomeViewModel(
      get(),
      get(),
      afterSync = { getOrNull<HcertService>()?.refreshTrustListQuietly() },
    )
  }
  viewModel { (mode: ScanMode) -> CertificateScanViewModel(get(), mode) }
  viewModel { AuthViewModel(get(), get()) }
  viewModel {
    UserContextViewModel(
      get<PractitionerDetailsApi>()::fetch,
      get(),
      resetSync = get<InitialSyncStore>()::reset,
    )
  }
  viewModel { InitialSyncViewModel(get(), get()) }
}
