# OHS + Medplum Integration Guide

What an Open Health Stack (OHS) app needs to use a self-hosted Medplum server as both its FHIR R4 store and its OpenID Connect provider. Everything here is app code, app configuration or Medplum seed configuration; the OHS libraries are used unchanged.

> **Proof of concept.** Runs end to end on Android (Samsung Galaxy Tab Active2). Desktop, web and iOS compile. The vaccine schedule hasn't had clinical review.

## At a glance

| Area | OHS default (HAPI FHIR, Keycloak) | Medplum needs | Where |
|---|---|---|---|
| FHIR base URL | `…/fhir/` | `…/fhir/R4/`, trailing slash included | `local.properties` |
| Login | Keycloak realm | Medplum issuer via OIDC discovery, plus `offline_access` | [`OAuthConfig.kt`][oauthconfig] |
| **Upload** | One `PUT` transaction bundle, up to 500 entries | **One request per change: `POST` creates, `PATCH` updates** | [`AppFhirSyncTask.kt`][synctask] |
| Page size | Server default | `_count=1000` (Medplum defaults to 20) | [`AppFhirSyncTask.kt`][synctask] |
| Record ids | Client-assigned | Server-assigned; match on `identifier`, not `id` | [`ImmunizationWriter.kt`][immwriter] |
| Caseload scoping | Gateway or client filters | Medplum `AccessPolicy` with a facility `compartment` | `medplum/seed.sh` |
| **Sync watermark** | Stored as returned | **Stored as UTC `…Z`** | [`DataStoreTimestampContext.kt`][tsctx] |
| **Written timestamps** | Milliseconds | **Whole seconds** | [`ImmunizationWriter.kt`][immwriter] |
| Practitioner identity | Varies | OIDC `profile` claim (`Practitioner/<id>`) | [`PractitionerRepository.kt`][practrepo] |
| Clinical logic | On the device | A Medplum Bot on the server | `medplum/bots/eir-forecaster.js` |

The three bold rows are mandatory. Without them the app seems fine at first, then quietly stops syncing.

## Architecture

```text
┌──────────────── Device ─────────────────────────────┐
│  Compose Multiplatform UI                           │
│      ▲  ViewDefinitions (SQL-on-FHIR) → renderers   │   dev.ohs.player:client
│  FhirEngine (local SQLite) + SDC forms              │   dev.ohs.fhir:fhir-engine, fhir-data-capture
└──────┼──────────────────────────┼───────────────────┘
       │ upload (POST / PATCH)    │ download (search, _lastUpdated)
       ▼                          │
┌─────────────────────────────────┴───────────────────┐
│  Medplum: FHIR R4 store + OAuth2/OIDC provider      │
│    AccessPolicy → scopes every read and write       │
│    Subscription(Immunization | Patient)             │
│          ▼                                          │
│    Bot "eir-forecaster" → ImmunizationRecommendation│
└─────────────────────────────────────────────────────┘
```

Versions are pinned in [`gradle/libs.versions.toml`][versions]. They're alphas, so upgrade deliberately: `ohsFhirDataCapture` and `ohsFhirEngine` 2.0.0-alpha03, `ohsFhirModelR4` 1.0.0-beta05, `ohsPlayerClient` 1.0.0-alpha02. Toolchain: Kotlin 2.3.21, Compose Multiplatform 1.11.0, AGP 8.11.2, Gradle 8.14.3, and JVM target 21, which `fhir-engine` requires because its Android artifact ships JVM 21 inline functions.

## Authentication

Medplum is a standard OAuth2 + PKCE provider. The `auth/` package is written against OIDC, not Keycloak, so it only needs the right values.

**Configure the issuer only.** Every endpoint comes from `{issuer}/.well-known/openid-configuration` ([`OAuthConfig.kt`][oauthconfig]). The token exchange is a public-client PKCE exchange with no secret, since a secret in an APK can be extracted ([`OidcAuthApi.kt`][oidcapi]).

```properties
OAUTH_ISSUER=https://medplum.player.ohs.dev/
OAUTH_CLIENT_ID=<ClientApplication id from seed.sh>
OAUTH_SCOPES=openid profile email offline_access
OAUTH_REDIRECT_SCHEME=dev.ohs.player.medplum
OAUTH_REDIRECT_HOST=auth
FHIR_BASE_URL=https://medplum.player.ohs.dev/fhir/R4/
```

| Rule | Why |
|---|---|
| `FHIR_BASE_URL` ends in `/fhir/R4/` | Medplum returns `404` for a bare `/fhir`, and the engine appends paths to the slash. |
| Request `offline_access` | Without it there's no refresh token and every session ends after an hour. |
| `OAUTH_ISSUER` is the API host | Medplum checks the token's `aud` against its `baseUrl`; a mismatch fails login with `Invalid audience`. |
| Rebuild after changes | `generateAuthConfig` compiles these values into `GeneratedAuthConfig`. |

[`app-config-conventions.gradle.kts`][appconfig] is the one place these values are read, so the shared code, the Android redirect intent-filter and desktop packaging always agree.

**Register every redirect URI** in `ClientApplication.redirectUris`; a missing one fails sign-in with `invalid_request`.

| Platform | Redirect URI | Mechanism |
|---|---|---|
| Android, iOS | `dev.ohs.player.medplum://auth` | Custom-scheme deep link, `ASWebAuthenticationSession` on iOS |
| Desktop | `http://127.0.0.1:8765/callback` | Loopback server ([`Platform.jvm.kt`][platformjvm]) |
| Web | `http://localhost:8080/callback` | Full-page redirect |

**The engine reads the token per request**, so a refreshed token is picked up at once ([`FhirBearerAuthenticator.kt`][bearer], wired into `ServerConfiguration` in [`OhsPlayerApplication.kt`][application]).

```kotlin
internal object FhirBearerAuthenticator : HttpAuthenticator {
  override fun getAuthenticationMethod(): HttpAuthenticationMethod =
    HttpAuthenticationMethod.Bearer(SessionRepository.session.value?.accessToken.orEmpty())
}
```

**The signed-in practitioner** is the OIDC `profile` claim, `Practitioner/<id>`. Use it directly; never derive identity from a name or email ([`PractitionerRepository.kt`][practrepo]). Their facility is the single `Organization` that syncs down, since the AccessPolicy allows no other.

## Uploading changes

The OHS default doesn't work against Medplum. Upload each change on its own ([`AppFhirSyncTask.kt`][synctask]):

```kotlin
override fun getUploadStrategy(): UploadStrategy =
  UploadStrategy.forIndividualRequest(
    methodForCreate = HttpCreateMethod.POST,
    methodForUpdate = HttpUpdateMethod.PATCH,
    squash = true,
  )
```

Why the default bundle of up to 500 `PUT`s fails:

1. Medplum keeps transaction bundles behind a super-admin flag and caps them at 50 operations.
2. It reports `updateCreate=false`, so a `PUT` can't create.
3. Inside a bundle, a JSON Patch has to be wrapped in a `Binary`.

Two rules follow:

- **Keep updates as `PATCH`.** `forIndividualRequest` throws `NotImplementedError` for `PUT`. A patch goes out as `PATCH /{type}/{id}` with `application/json-patch+json`, which Medplum accepts.
- **Let the server assign ids.** Create resources without an id. The engine rewrites the references it knows about after upload; anything else that stores an id must match on `identifier`.

The cost: N changes are N requests, and an upload isn't atomic. That's fine for a facility register syncing every few minutes.

## Downloading data

Downloads use the stock `ResourceParamsBasedDownloadWorkManager`. List the types and always set `_count`; without it, a first sync of a few hundred patients takes hundreds of round trips.

```kotlin
private const val PAGE_SIZE = "1000"   // Medplum default is 20, max is 1000

internal val SYNC_RESOURCE_PARAMS: ResourceSearchParams =
  mapOf(
    ResourceType.Patient to mapOf("_count" to PAGE_SIZE),
    ResourceType.RelatedPerson to mapOf("_count" to PAGE_SIZE),
    ResourceType.Immunization to mapOf("_count" to PAGE_SIZE),
    ResourceType.ImmunizationRecommendation to mapOf("_count" to PAGE_SIZE),
    ResourceType.Observation to mapOf("_count" to PAGE_SIZE),
    ResourceType.AdverseEvent to mapOf("_count" to PAGE_SIZE),
    ResourceType.Practitioner to mapOf("_count" to PAGE_SIZE),
    ResourceType.Organization to mapOf("_count" to PAGE_SIZE),
  )
```

Don't add facility filters. The device asks for everything and the server decides what that means for the signed-in practitioner.

**A new resource type goes in two places:** `SYNC_RESOURCE_PARAMS`, so it downloads, and the practitioner `AccessPolicy`, so the server allows it. Missing either gives no error. Uploads come from the local change log, so the record reaches the server and shows on the device that made it, but other devices never get it. [`ProfileSyncCoverageTest.kt`][synccoveragetest] checks the app side; check the policy by hand.

## Access control

A Medplum `AccessPolicy` scopes each health worker to their facility on the server. There's no gateway and no client-side filtering: the device holds no access-control logic. `medplum/seed.sh` (in the separate backend directory) creates it. Abridged:

```json
{
  "resourceType": "AccessPolicy",
  "name": "ohs-medplum-eir-practitioner",
  "compartment": { "reference": "%organization" },
  "resource": [
    { "resourceType": "Patient",       "criteria": "Patient?_compartment=%organization" },
    { "resourceType": "Immunization",  "criteria": "Immunization?_compartment=%organization" },
    { "resourceType": "AdverseEvent",  "criteria": "AdverseEvent?_compartment=%organization" },
    { "resourceType": "Organization",  "criteria": "Organization?_id=%organization", "readonly": true },
    { "resourceType": "Practitioner",  "readonly": true },
    { "resourceType": "Questionnaire", "readonly": true }
  ]
}
```

- **One policy serves every facility.** `%organization` is set per user through `ProjectMembership.access[].parameter`; a new facility needs an `Organization` and memberships, not a new policy.
- **`criteria` covers reads and writes.** A `Patient` created outside the facility account gets a bare `403`.
- **The top-level `compartment` makes on-device registration work.** Medplum stamps the facility account on everything the practitioner creates. The client can't set it itself: `meta.account` is a Medplum extension the OHS model doesn't support.
- **A type missing from the policy is invisible, not read-only.** Leave out `Immunization` and the vaccine history is just empty, with no error.

`Patient.managingOrganization` is still written at registration, but it's data, not a security boundary; only the compartment is enforced.

To verify scoping, the demo data has a seventh child at another facility. The practitioner must see six children and never the seventh. The count alone proves nothing; the seventh child's absence does.

## FHIR engine workarounds

These are `fhir-engine` behaviours handled in the app. The real fixes belong upstream (see below).

**Store the sync watermark in UTC.** The engine puts its `_lastUpdated` watermark in the query string without URL-encoding it. With a positive UTC offset (Kenya is UTC+3), the `+` becomes a space and Medplum rejects the request:

```text
400  Invalid format for date search parameter: 2026-09-20T10:00:00.000 03:00
```

The first sync has no watermark and works; every later one fails silently. [`DataStoreTimestampContext.kt`][tsctx] stores and reads the watermark as UTC `…Z`, normalising on read too so a device with an old offset value repairs itself. [`DataStoreTimestampContextTest.kt`][tsctxtest] covers it.

**Write timestamps in whole seconds.** `ResourceIndexer` calls `second.intValue(exactRequired = true)`, which throws on fractional seconds. A locally written resource crashes the app (`ArithmeticException` in `DatabaseImpl.insert`). A synced one is silently dropped, and the watermark still advances, so it's never fetched again. Every writer, the server Bot included, must write whole seconds:

```kotlin
internal fun isoSeconds(): String =
  Clock.System.now().toString().replace(Regex("\\.\\d+(?=Z$)"), "")
```

**Keep FHIRPath evaluations few.** `FhirPathEngine.evaluateExpression` builds a new ANTLR lexer, parser and tree on every call and caches nothing; one patient profile runs about 375 evaluations. Use indexed lookups, avoid N+1 queries, and memoise profiles per `(patientId, revision)` as [`PatientRepository.kt`][patientrepo] does.

## Medplum behaviour to know

None of these need Medplum code changes; the seed scripts or app configuration handle them.

| Behaviour | Symptom | Fix |
|---|---|---|
| Bundles flagged and capped at 50; `updateCreate=false` | Uploads fail | Individual `POST`/`PATCH` |
| Default page size 20 | Slow first sync | `_count=1000` |
| `aud` checked against `baseUrl` | `Invalid audience` at login | `up.sh` derives every advertised URL from one value; the app issuer must match it exactly |
| `POST /fhir/R4/Bot` creates no `ProjectMembership` | Manual `$execute` works, but every Subscription run fails with *"Could not find project membership for bot"* | Create Bots with `POST /admin/projects/<id>/bot`; `seed_bot.py` checks the membership |
| `PATCH` of `Project.features` returns `200` but drops the change | `Bots not enabled` | `seed-bot.sh` enables `bots` directly in Postgres |
| No immunization forecasting, no CQL | — | The KEPI schedule runs in a TypeScript Bot triggered by `Subscription`s on `Immunization` and `Patient` |
| Built-in CVX has a single code | Vaccine names show as raw codes | `seed-terminology.sh` loads CVX and CIEL subsets and a ConceptMap |
| Deletions aren't reliably visible through search | Server deletions stay on the device | **Known limitation, unsolved** |
| The console sidebar ignores the AccessPolicy | Data is readable but has no navigation | A read-only `UserConfiguration` with saved searches |

**`https://medplum.player.ohs.dev/` means two things.** As a host (`OAUTH_ISSUER`, `FHIR_BASE_URL`) it changes per deployment. As a namespace, such as the child-id `Patient.identifier.system`, it's written into every record and matched by the `PatientSummary` ViewDefinition, so it must never change per deployment. Change it on one side only and the register's ID column goes blank with no error.

```json
{ "name": "mrn", "path": "identifier.where(system = 'https://medplum.player.ohs.dev/child-id').value.first()" }
```

## SDC forms and ViewDefinitions

Not Medplum-specific, and they all fail the same way: the form submits, success is reported, and the data is wrong.

| Pitfall | Effect | Guard |
|---|---|---|
| Extracting from the *loaded* questionnaire, not the *prepared* one | Records reference `Patient/__PATIENT_ID__` | `GrowthExtractionTest`, `ChildRegistrationExtractionTest` |
| An `Enumeration` such as `Patient.gender` with no literal value | Dropped on decode, template extension too | Add a literal `"gender": "unknown"` next to `_gender` |
| `extractAllocateId` returns `urn:uuid:…` | Invalid `Patient.id`, dangling references | `.replace('urn:uuid:', '')` |
| An item with both `initial` and `answerOption` (que-11) | Crash when the form opens | `BundledQuestionnaireTest` |
| A FHIRPath column matching nothing | Blank cell | Check ViewDefinition changes on a device with real data |
| JSON shorthand `doseNumberPositiveInt` as a path | Null | `doseNumber.ofType(positiveInt)` (upstream issue #71) |

If the practitioner or facility id isn't known when a form opens, for example on a restored session before the first sync, **remove** the placeholder instead of writing it. A dangling `Practitioner/__PRACTITIONER_ID__` is valid FHIR that Medplum accepts and that points nowhere ([`QuestionnaireService.kt`][questionnaire]).

## Kotlin Multiplatform notes

| Rule | Why |
|---|---|
| All Kotlin and resources live in the root project; `androidApp`, `desktopApp` and `webApp` only package | Entry points are one-line `main()` wrappers calling `launchDesktopApp()` / `launchWebApp()`, so `GeneratedAuthConfig` stays `internal` |
| `com.android.library` + `androidTarget`, not `kotlin { android { } }` | That block doesn't exist in AGP 8.11.2 |
| `modules("jdk.unsupported")` in the desktop jlink runtime ([`desktopApp/build.gradle.kts`][desktopbuild]) | Without it KSafe can't use `sun.misc.Unsafe`, and the packaged app stores the session in a software-encrypted file instead of OS key storage. `:desktopApp:run` hides this, since it uses the full JDK. |
| `webApp` owns the vendored `sqlite-wasm-worker` | A local `file:` npm dependency doesn't reach consumers, so it has to live where webpack runs |
| No commas in test names | Kotlin/Native rejects them |
| No `runBlocking` in shared tests | It doesn't exist on JS or Wasm |
| iOS `BGTaskSchedulerPermittedIdentifiers` must equal `PERIODIC_SYNC_TASK_IDENTIFIER` | A mismatch crashes at launch |

`CI=true ./gradlew check` passes (124 tests in 33 classes). Linking the iOS tests needs Xcode.

## Suggested upstream changes

**OHS FHIR engine** (`fhir-engine`, `fhir-path`):

1. URL-encode the `_lastUpdated` watermark.
2. Accept fractional seconds in `ResourceIndexer`. A synced resource with milliseconds is dropped without any error today.
3. Support `PUT` updates in `forIndividualRequest`, or document that only `PATCH` works.
4. Cache parsed FHIRPath expressions.
5. Offer a Medplum preset for upload strategy and page size.

**Medplum:**

1. Make `POST /fhir/R4/Bot` create the `ProjectMembership`, or fail loudly.
2. Return an error, not `200`, when a `Project.features` change isn't allowed.
3. Explain a `403` from an AccessPolicy `criteria`, for example by naming the missing compartment.
4. Support syncing deletions to offline clients.

## Key files

Start with [`AppFhirSyncTask.kt`][synctask], the smallest file showing where device and server meet. *Status* compares each file with [`player-reference`][upstream], ignoring the package rename.

| File | Status | Purpose |
|---|---|---|
| [`AppFhirSyncTask.kt`][synctask] | Modified | Upload strategy, download types, page size |
| [`DataStoreTimestampContext.kt`][tsctx] | Modified | UTC watermark |
| [`ImmunizationWriter.kt`][immwriter] | New | Server-assigned ids, whole-second timestamps |
| [`PatientRepository.kt`][patientrepo] | Modified | Memoised profiles |
| [`PractitionerRepository.kt`][practrepo] | New | Identity from the OIDC `profile` claim |
| [`QuestionnaireService.kt`][questionnaire] | Modified | Form placeholder substitution |
| [`OAuthConfig.kt`][oauthconfig] | Unchanged | Issuer and discovery URL |
| [`OidcAuthApi.kt`][oidcapi] | Unchanged | PKCE token exchange |
| [`FhirBearerAuthenticator.kt`][bearer] | Unchanged | Bearer token for the engine |
| [`Platform.jvm.kt`][platformjvm] | Unchanged | Desktop loopback redirect |
| [`OhsPlayerApplication.kt`][application] | Unchanged | Android engine `ServerConfiguration` |
| [`app-config-conventions.gradle.kts`][appconfig] | New | Single source for auth and FHIR values |
| [`libs.versions.toml`][versions] | Modified | Pinned versions |
| [`local.properties.sample`][localprops] | Modified | Sample configuration |
| [`desktopApp/build.gradle.kts`][desktopbuild] | New | Desktop packaging, jlink modules |
| [`ProfileSyncCoverageTest.kt`][synccoveragetest] | New | Every profile type is synced |
| [`DataStoreTimestampContextTest.kt`][tsctxtest] | Unchanged | UTC watermark handling |

[upstream]: https://github.com/ohs-foundation/player-reference
[versions]: gradle/libs.versions.toml
[localprops]: local.properties.sample
[appconfig]: build-logic/src/main/kotlin/app-config-conventions.gradle.kts
[desktopbuild]: desktopApp/build.gradle.kts
[application]: src/androidMain/kotlin/dev/ohs/player/medplum/OhsPlayerApplication.kt
[platformjvm]: src/jvmMain/kotlin/dev/ohs/player/medplum/auth/Platform.jvm.kt
[oauthconfig]: src/commonMain/kotlin/dev/ohs/player/medplum/auth/OAuthConfig.kt
[oidcapi]: src/commonMain/kotlin/dev/ohs/player/medplum/auth/OidcAuthApi.kt
[bearer]: src/commonMain/kotlin/dev/ohs/player/medplum/auth/FhirBearerAuthenticator.kt
[synctask]: src/commonMain/kotlin/dev/ohs/player/medplum/data/sync/AppFhirSyncTask.kt
[tsctx]: src/commonMain/kotlin/dev/ohs/player/medplum/data/sync/DataStoreTimestampContext.kt
[immwriter]: src/commonMain/kotlin/dev/ohs/player/medplum/data/repository/ImmunizationWriter.kt
[patientrepo]: src/commonMain/kotlin/dev/ohs/player/medplum/data/repository/PatientRepository.kt
[practrepo]: src/commonMain/kotlin/dev/ohs/player/medplum/data/repository/PractitionerRepository.kt
[questionnaire]: src/commonMain/kotlin/dev/ohs/player/medplum/feature/questionnaire/QuestionnaireService.kt
[synccoveragetest]: src/commonTest/kotlin/dev/ohs/player/medplum/data/sync/ProfileSyncCoverageTest.kt
[tsctxtest]: src/jvmTest/kotlin/dev/ohs/player/medplum/data/sync/DataStoreTimestampContextTest.kt
