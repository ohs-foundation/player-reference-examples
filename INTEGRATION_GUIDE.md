# Open Health Stack + Medplum Integration Guide

This guide describes what an app built on **Open Health Stack (OHS)** components needs in order
to use a self-hosted **Medplum** server as both its FHIR R4 store and its OpenID Connect (OIDC)
identity provider.

Each section covers what to configure, why Medplum needs it, and where it lives in this repo.
All changes are app configuration, app code, or Medplum seed configuration. The OHS libraries
are used unchanged.

> [!NOTE]
> **Status: proof of concept.** The app runs end to end on Android (tested on a Samsung Galaxy
> Tab Active2). Desktop, web and iOS builds compile. The vaccine schedule has **not** had
> clinical review.

## Table of contents

1. [At a glance](#1-at-a-glance)
2. [Architecture](#2-architecture)
3. [Authentication](#3-authentication)
4. [Uploading changes](#4-uploading-changes)
5. [Downloading data](#5-downloading-data)
6. [Access control](#6-access-control)
7. [FHIR engine workarounds](#7-fhir-engine-workarounds)
8. [Medplum configuration notes](#8-medplum-configuration-notes)
9. [SDC forms and ViewDefinitions](#9-sdc-forms-and-viewdefinitions)
10. [Kotlin Multiplatform requirements](#10-kotlin-multiplatform-requirements)
11. [Suggested upstream changes](#11-suggested-upstream-changes)
12. [Key source files](#12-key-source-files)

## 1. At a glance

| # | Area | OHS default (HAPI FHIR / Keycloak) | Required for Medplum | Where |
|---|---|---|---|---|
| 1 | FHIR base URL | `…/fhir/` | `…/fhir/R4/`, including the trailing slash | `local.properties` |
| 2 | Login | Keycloak realm | Medplum issuer via OIDC discovery; `offline_access` scope | [`OAuthConfig.kt`][oauthconfig] |
| 3 | **Upload** | One transaction bundle, `PUT`, up to 500 entries | **Individual `POST` for creates, `PATCH` for updates** | [`AppFhirSyncTask.kt`][synctask] |
| 4 | Download page size | Server default | `_count=1000` (Medplum default is 20) | [`AppFhirSyncTask.kt`][synctask] |
| 5 | Record ids | Assigned by the client | Assigned by the server; match records on `identifier`, not `id` | [`ImmunizationWriter.kt`][immwriter] |
| 6 | Caseload scoping | Gateway or client-side filters | Medplum `AccessPolicy` with a facility `compartment` | `medplum/seed.sh` |
| 7 | Sync watermark | Stored as returned | Stored as UTC `…Z` | [`DataStoreTimestampContext.kt`][tsctx] |
| 8 | Written timestamps | Milliseconds | Whole seconds only | [`ImmunizationWriter.kt`][immwriter] |
| 9 | Practitioner identity | Varies | OIDC `profile` claim (`Practitioner/<id>`) | [`PractitionerRepository.kt`][practrepo] |
| 10 | Clinical logic | On the device | Medplum Bot on the server; the device displays the result | `medplum/bots/eir-forecaster.js` |

> [!IMPORTANT]
> Rows 3, 7 and 8 are mandatory. Without them the app appears to work at first and then
> silently stops syncing.

## 2. Architecture

```text
┌──────────────── Device (this repo) ─────────────────┐
│  Compose Multiplatform UI                           │
│      ▲                                              │
│      │  ViewDefinitions (SQL-on-FHIR) → renderers   │   dev.ohs.player:client
│      │                                              │
│  FhirEngine (local SQLite) + SDC forms              │   dev.ohs.fhir:fhir-engine
│      │                          ▲                   │   dev.ohs.fhir:fhir-data-capture
└──────┼──────────────────────────┼───────────────────┘
       │ upload (POST / PATCH)    │ download (search, _lastUpdated)
       ▼                          │
┌─────────────────────────────────┴───────────────────┐
│  Medplum: FHIR R4 store + OAuth2/OIDC provider      │
│    AccessPolicy  → scopes every read and write      │
│    Subscription(Immunization | Patient)             │
│          ▼                                          │
│    Bot "eir-forecaster" → ImmunizationRecommendation│
└─────────────────────────────────────────────────────┘
```

### Library versions

Pinned in [`gradle/libs.versions.toml`][versions]. These are alpha releases, so keep them pinned
and upgrade deliberately.

```toml
ohsFhirDataCapture = "2.0.0-alpha03"
ohsFhirEngine      = "2.0.0-alpha03"
ohsFhirModelR4     = "1.0.0-beta05"
ohsPlayerClient    = "1.0.0-alpha02"
```

### Toolchain

| Tool | Version |
|---|---|
| Kotlin | 2.3.21 |
| Compose Multiplatform | 1.11.0 |
| Android Gradle Plugin | 8.11.2 |
| Gradle | 8.14.3 |
| JVM target | 21 (required: the `fhir-engine` Android artifact ships inline functions compiled for JVM 21) |

## 3. Authentication

Medplum acts as a standard OAuth2 + PKCE provider. The `auth/` package is written against OIDC,
not against Keycloak, so it only needs the correct configuration values.

### 3.1 Endpoints come from discovery

Configure the **issuer** only. Every endpoint is read from
`{issuer}/.well-known/openid-configuration` ([`OAuthConfig.kt`][oauthconfig]):

```kotlin
data class OAuthConfig(val issuer: String, val clientId: String, val scopes: String) {
  val discoveryUrl: String
    get() = "${issuer.trimEnd('/')}/.well-known/openid-configuration"
}
```

The token exchange is a public-client PKCE exchange with no client secret. A secret shipped in
an APK can be extracted, so PKCE replaces it ([`OidcAuthApi.kt`][oidcapi]):

```kotlin
parameters {
  append("grant_type", "authorization_code")
  append("client_id", config.clientId)
  append("code", code)
  append("redirect_uri", redirectUri)
  append("code_verifier", codeVerifier)
}
```

### 3.2 Configuration values

Set these in `local.properties` (see [`local.properties.sample`][localprops]):

```properties
OAUTH_ISSUER=https://medplum.player.ohs.dev/
OAUTH_CLIENT_ID=<ClientApplication id from seed.sh>
OAUTH_SCOPES=openid profile email offline_access
OAUTH_REDIRECT_SCHEME=dev.ohs.player.medplum
OAUTH_REDIRECT_HOST=auth
FHIR_BASE_URL=https://medplum.player.ohs.dev/fhir/R4/
```

| Value | Requirement |
|---|---|
| `FHIR_BASE_URL` ends in `/fhir/R4/` | Medplum returns `404` for a bare `/fhir`. Keep the trailing slash: the engine appends paths to it. |
| `offline_access` scope | Without it Medplum issues no refresh token and every session ends after one hour. |
| `OAUTH_ISSUER` is the API host | Medplum checks the token's `aud` against its own `baseUrl`. A mismatch fails login with `Invalid audience`. |
| Compiled in, not read at runtime | `generateAuthConfig` writes these values into `GeneratedAuthConfig` before Kotlin compiles. Changing them requires a rebuild. |

The shared library, the Android manifest redirect intent-filter and the desktop packaging all
need these values. They are read in one place,
[`app-config-conventions.gradle.kts`][appconfig], so the manifest and the generated config
always match.

### 3.3 Redirect URIs

Register every platform's redirect URI in the Medplum `ClientApplication.redirectUris`.
A missing entry fails sign-in with `invalid_request`.

| Platform | Redirect URI | Mechanism |
|---|---|---|
| Android | `dev.ohs.player.medplum://auth` | Custom-scheme deep link |
| iOS | `dev.ohs.player.medplum://auth` | `ASWebAuthenticationSession` |
| Desktop | `http://127.0.0.1:8765/callback` | Loopback HTTP server ([`Platform.jvm.kt`][platformjvm]) |
| Web | `http://localhost:8080/callback` | Full-page redirect |

### 3.4 Passing the token to the FHIR engine

The engine requests credentials on **every** request, so it always uses the current token,
including a freshly refreshed one ([`FhirBearerAuthenticator.kt`][bearer]):

```kotlin
internal object FhirBearerAuthenticator : HttpAuthenticator {
  override fun getAuthenticationMethod(): HttpAuthenticationMethod =
    HttpAuthenticationMethod.Bearer(SessionRepository.session.value?.accessToken.orEmpty())
}
```

Wire it into the engine's `ServerConfiguration` ([`OhsPlayerApplication.kt`][application]):

```kotlin
ServerConfiguration(
  baseUrl = GeneratedAuthConfig.FHIR_BASE_URL,
  networkConfiguration = NetworkConfiguration(/* 120 s timeouts */),
  authenticator = FhirBearerAuthenticator,
)
```

### 3.5 Signed-in practitioner

Medplum returns the user's FHIR profile in the OIDC `profile` claim as `Practitioner/<id>`. Use
that reference directly; never derive identity from a name or email address
([`PractitionerRepository.kt`][practrepo]). The practitioner's facility is the single
`Organization` that syncs down, because the AccessPolicy ([Section 6](#6-access-control))
allows no other.

## 4. Uploading changes

> [!IMPORTANT]
> This change is mandatory. The OHS default upload strategy does not work against Medplum.

Upload each change as an individual request ([`AppFhirSyncTask.kt`][synctask]):

```kotlin
override fun getUploadStrategy(): UploadStrategy =
  UploadStrategy.forIndividualRequest(
    methodForCreate = HttpCreateMethod.POST,
    methodForUpdate = HttpUpdateMethod.PATCH,
    squash = true,
  )
```

The default (`forBundleRequest` with `PUT` and up to 500 entries) cannot be used because:

1. Medplum puts transaction bundles behind a super-admin feature flag and **caps them at 50
   operations**.
2. Medplum reports `updateCreate=false`, so it rejects `PUT` for creating a resource.
3. Inside a bundle, a JSON Patch must be wrapped in a `Binary`.

Rules that follow from this strategy:

- **Keep `methodForUpdate` as `PATCH`.** `forIndividualRequest` throws
  `NotImplementedError("PUT for UPDATE not supported yet.")` for `PUT`. A single-request patch
  is sent as `PATCH /{type}/{id}` with `Content-Type: application/json-patch+json`, which
  Medplum accepts.
- **Let the server assign ids.** Create new resources with no id. The engine rewrites the
  references it knows about after upload; anything else that stores a FHIR id must match on
  `identifier` instead.

  ```kotlin
  // ImmunizationWriter.kt
  // No id: with UploadStrategy.forIndividualRequest(POST, ...) the SERVER assigns it.
  // Medplum reports updateCreate=false, so a client-assigned id would be rejected anyway.
  fhirRepository.upsert(FhirJson.instance.decodeFromJsonElement(Immunization.serializer(), json))
  ```

**Trade-off:** N records mean N HTTP requests, and an upload is not atomic. This is acceptable
for a facility register that syncs every few minutes.

## 5. Downloading data

Downloads use the engine's stock `ResourceParamsBasedDownloadWorkManager`. Configure which
resource types to download and the page size ([`AppFhirSyncTask.kt`][synctask]):

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

Always set `_count`. Without it, a first sync of a few hundred patients takes hundreds of round
trips.

Do **not** add facility filters here. The device requests everything, and the server decides
what that means for the signed-in practitioner ([Section 6](#6-access-control)).

### 5.1 Adding a resource type

A new resource type must be added in **both** places:

1. `SYNC_RESOURCE_PARAMS` in the app, so it is downloaded.
2. The practitioner `AccessPolicy` in Medplum, so the server allows it.

Missing either one produces no error. Uploads come from the engine's local change log, not from
this map, so the record still reaches the server and shows on the device that created it, but
other devices (or a reinstall) never receive it.

[`ProfileSyncCoverageTest.kt`][synccoveragetest] checks the app side:

```kotlin
@Test
fun everyResourceTypeOnTheProfileIsAlsoDownloadedBySync() {
  val downloaded = SYNC_RESOURCE_PARAMS.keys.map { it.name }.toSet()
  val missing = PROFILE_REV_INCLUDES.map { (resourceType, _) -> resourceType } - downloaded
  assertTrue(missing.isEmpty(), "The profile renders ${missing.joinToString()} but sync never downloads ...")
}
```

The server side (is the type in the AccessPolicy?) must be checked manually.

## 6. Access control

A Medplum **`AccessPolicy`** scopes each health worker to their facility's caseload on the
server. No FHIR Info Gateway or client-side filtering is used: **the device holds no
access-control logic.**

The policy is created by `medplum/seed.sh` in the separate Medplum backend directory (not part
of this repo). Abridged:

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

How the policy works:

- **One policy serves every facility.** `%organization` is set per user through
  `ProjectMembership.access[].parameter`. A new facility needs a new `Organization` and new
  memberships, not a new policy.
- **`criteria` applies to reads and writes.** A `Patient` created without the facility account
  fails the criteria and returns a bare `403`.
- **The top-level `compartment` is required for on-device registration.** It makes Medplum stamp
  the facility account on every resource the practitioner creates. The client cannot set it:
  `meta.account` is a Medplum extension that the OHS FHIR model does not support.
- **A type missing from the policy is invisible, not read-only.** If `Immunization` is left out,
  the app shows an empty vaccine history with no error.

`Patient.managingOrganization` is still written at registration, but it is **data about the
patient, not a security boundary**. Only the server compartment is enforced.

### Verifying the scoping

The demo data includes a **seventh child registered at a different facility**. The practitioner
must see six children and never the seventh. A count of six alone does not prove scoping; the
absence of the seventh child does.

## 7. FHIR engine workarounds

These are behaviours of `dev.ohs.fhir:fhir-engine` itself, handled in the app. The proper fixes
belong upstream ([Section 11](#11-suggested-upstream-changes)).

### 7.1 Store the sync watermark in UTC

The engine puts its `_lastUpdated` watermark into the query string **without URL-encoding it**.
On a device with a positive UTC offset (for example Kenya, UTC+3), the `+` in
`2026-09-20T20:48:15.252+03:00` is decoded as a space and Medplum rejects the request:

```text
400  Invalid format for date search parameter: 2026-09-20T10:00:00.000 03:00
```

The first sync has no watermark and succeeds; every later sync fails silently. Store and read
the watermark as UTC `…Z` ([`DataStoreTimestampContext.kt`][tsctx]):

```kotlin
override suspend fun saveLastUpdatedTimestamp(resourceType: ResourceType, timestamp: String?) {
  if (timestamp == null) return
  dataStore.edit { prefs -> prefs[stringPreferencesKey(resourceType.name)] = toUtc(timestamp) }
}

// Normalised on read too, so a device that already stored an offset timestamp repairs itself.
override suspend fun getLasUpdateTimestamp(resourceType: ResourceType): String? =
  dataStore.data.first()[stringPreferencesKey(resourceType.name)]?.let(::toUtc)

private fun toUtc(timestamp: String): String =
  runCatching { Instant.parse(timestamp).toString() }.getOrDefault(timestamp)
```

Covered by [`DataStoreTimestampContextTest.kt`][tsctxtest].

### 7.2 Write timestamps in whole seconds

The engine's `ResourceIndexer` calls `second.intValue(exactRequired = true)`, which throws on
fractional seconds such as `…:22.968Z`:

- **Resource written locally:** the app crashes (`ArithmeticException` in `DatabaseImpl.insert`).
- **Resource received through sync:** it is **silently dropped**, and the engine still advances
  its watermark, so it is never fetched again.

Every writer, including the app and the server-side Bot, must write whole seconds only:

```kotlin
// ImmunizationWriter.kt
internal fun isoSeconds(): String =
  Clock.System.now().toString().replace(Regex("\\.\\d+(?=Z$)"), "")
```

### 7.3 Keep FHIRPath evaluations to a minimum

`FhirPathEngine.evaluateExpression` builds a new ANTLR lexer, parser and parse tree on every
call and caches nothing. Opening one patient profile runs roughly 375 evaluations. Keep the
count low:

- Use indexed lookups and avoid N+1 queries.
- Memoise profiles per `(patientId, revision)`, as in [`PatientRepository.kt`][patientrepo].

## 8. Medplum configuration notes

Medplum behaviours that differ from HAPI FHIR or Keycloak. None require Medplum code changes;
all are handled in the seed scripts or app configuration.

| Behaviour | Symptom | Resolution |
|---|---|---|
| Transaction bundles are behind a flag and capped at 50 operations; `updateCreate=false` | Uploads fail | Individual `POST` / `PATCH` ([Section 4](#4-uploading-changes)) |
| Default page size is 20 | Slow first sync | `_count=1000` ([Section 5](#5-downloading-data)) |
| `aud` is checked against `baseUrl` | `Invalid audience` at login | `up.sh` derives every advertised URL from one value; the app issuer must match it exactly |
| A Bot created with `POST /fhir/R4/Bot` has **no `ProjectMembership`** | Manual `$execute` works, but every Subscription-triggered run fails with *"Could not find project membership for bot"* | Create Bots with `POST /admin/projects/<id>/bot`; `seed_bot.py` verifies the membership exists |
| `PATCH` of `Project.features` returns `200` but **discards the change** | `Bots not enabled` | `seed-bot.sh` enables `bots` directly in Postgres via `docker exec` |
| No immunization forecasting and no CQL | — | The KEPI schedule runs in a TypeScript Bot (`medplum/bots/eir-forecaster.js`), triggered by `Subscription`s on `Immunization` and `Patient` |
| Built-in CVX code system contains a single code | Vaccine names show as raw codes | `seed-terminology.sh` loads CVX and CIEL subsets and a ConceptMap between them |
| Deletions are not reliably visible through search | Records deleted on the server remain on the device | **Known limitation, not yet solved** |
| The console sidebar is not derived from the AccessPolicy | Data is readable but has no navigation links | A read-only `UserConfiguration` with saved searches (grants no permissions) |

### 8.1 Namespace URLs are not deployment URLs

`https://medplum.player.ohs.dev/` is used in two distinct ways. Mixing them up causes silent
data loss.

- **As a host:** where the server listens (`OAUTH_ISSUER`, `FHIR_BASE_URL`). Change it per
  deployment.
- **As a namespace:** it identifies a coding system, such as the child-id
  `Patient.identifier.system`. This string is written into every record and matched by the
  `PatientSummary` ViewDefinition. **Do not change it per deployment.**

```json
{
  "name": "mrn",
  "path": "identifier.where(system = 'https://medplum.player.ohs.dev/child-id').value.first()"
}
```

If the namespace changes on one side only, the register shows an empty ID column with no error.

## 9. SDC forms and ViewDefinitions

These issues are not Medplum-specific. They all fail the same way: the form submits, a Bundle
is produced, success is reported, and the data is wrong.

| Pitfall | Effect | Guard |
|---|---|---|
| Extracting from the *loaded* questionnaire instead of the *prepared* one | Records reference the literal `Patient/__PATIENT_ID__` | `GrowthExtractionTest`, `ChildRegistrationExtractionTest` |
| An `Enumeration` field such as `Patient.gender` with no literal value | Dropped on decode, along with its template extension | Add a literal `"gender": "unknown"` placeholder next to `_gender` |
| `extractAllocateId` returns `urn:uuid:…`, not a bare id | Invalid `Patient.id`; references resolve to nothing | Strip the prefix with `.replace('urn:uuid:', '')` |
| An item with both `initial` and `answerOption` (rule que-11) | Crash when the form opens | `BundledQuestionnaireTest` |
| A FHIRPath column that matches nothing | Returns null and renders blank | Check every ViewDefinition change on a device with real data |
| JSON shorthand `doseNumberPositiveInt` used as a FHIRPath path | Returns null | Use `doseNumber.ofType(positiveInt)` (see upstream issue #71) |

### Unknown practitioner or facility ids

If the practitioner or facility id is not yet known when a form opens (for example, a restored
session before the first sync), **remove** the placeholder instead of writing it. A dangling
`Practitioner/__PRACTITIONER_ID__` is valid FHIR that Medplum accepts but that points to
nothing ([`QuestionnaireService.kt`][questionnaire]):

```kotlin
"__PRACTITIONER_ID__" to { context -> context.practitionerId.orNull() },
"__ORGANIZATION_ID__" to { context -> context.organizationId.orNull() },
```

## 10. Kotlin Multiplatform requirements

The shared code builds for Android, iOS, desktop (JVM) and web (Wasm/JS).

| Requirement | Reason |
|---|---|
| `:player-medplum-app` holds all Kotlin and resources; `:androidApp`, `:desktopApp` and `:webApp` hold packaging only | Entry points are small `main()` wrappers calling `launchDesktopApp()` / `launchWebApp()`, so `GeneratedAuthConfig` stays `internal` |
| Use `com.android.library` + `androidTarget`, not AGP 9's `kotlin { android { } }` | That block does not exist in AGP 8.11.2 |
| Include `modules("jdk.unsupported")` in the desktop jlink runtime | Without it KSafe cannot use `sun.misc.Unsafe`, and the packaged desktop app falls back to a software-encrypted file instead of OS key storage for the session token |
| `:webApp` owns the vendored `sqlite-wasm-worker` npm module | A local `file:` npm dependency does not reach consumers, so it must live where webpack runs |
| No commas in test names | Kotlin/Native rejects them ("Name contains illegal characters") |
| No `runBlocking` in shared test code; use `suspend` helpers | `runBlocking` does not exist on JS or Wasm |
| iOS `BGTaskSchedulerPermittedIdentifiers` must match `PERIODIC_SYNC_TASK_IDENTIFIER` exactly | A mismatch crashes the app at launch |

Desktop runtime configuration ([`desktopApp/build.gradle.kts`][desktopbuild]):

```kotlin
nativeDistributions {
  // jlink trims the bundled runtime; KSafe needs sun.misc.Unsafe from jdk.unsupported.
  // `./gradlew :desktopApp:run` never shows the problem because it uses the full JDK.
  modules("jdk.unsupported")
}
```

### Build verification

```bash
CI=true ./gradlew check
```

This passes (124 tests in 33 classes). Linking the iOS tests requires Xcode.app.

## 11. Suggested upstream changes

Changes that would remove the need for the workarounds above.

### OHS FHIR engine (`dev.ohs.fhir:fhir-engine`, `fhir-path`)

1. URL-encode the `_lastUpdated` watermark in the query string ([Section 7.1](#71-store-the-sync-watermark-in-utc)).
2. Accept fractional seconds in `ResourceIndexer` instead of `intValue(exactRequired = true)`
   ([Section 7.2](#72-write-timestamps-in-whole-seconds)). A synced resource with milliseconds is
   currently dropped **without any error**.
3. Support `PUT` for updates in `forIndividualRequest`, or document that only `PATCH` works.
4. Cache parsed FHIRPath expressions in `FhirPathEngine` ([Section 7.3](#73-keep-fhirpath-evaluations-to-a-minimum)).
5. Provide a Medplum preset for `UploadStrategy` and page size, or a compatibility note in the
   documentation.

### Medplum (documentation or behaviour)

1. Make `POST /fhir/R4/Bot` either create the `ProjectMembership` or fail loudly.
2. Return an error, not `200`, when a `Project.features` change is not allowed.
3. Return a clearer error than a bare `403` when a create fails an AccessPolicy `criteria`, for
   example by naming the missing compartment.
4. Provide a supported way to sync deletions to offline clients.

## 12. Key source files

All paths are relative to the repository root. Start with [`AppFhirSyncTask.kt`][synctask]: it
is the smallest file showing where the device and the server meet.

The **Status** column compares each file with the upstream
[`player-reference`](https://github.com/ohs-foundation/player-reference) app, ignoring the
package rename from `dev.ohs.player.reference.app` to `dev.ohs.player.medplum`:

- **New**: added for the Medplum integration; not present upstream.
- **Modified**: present upstream and changed for Medplum.
- **Unchanged**: used as-is from upstream.

| File | Status | Purpose |
|---|---|---|
| [`player-medplum/README.md`][readme] | Modified | Quickstart for each platform and module layout |
| [`AppFhirSyncTask.kt`][synctask] | Modified | Upload strategy, download resource types and page size |
| [`DataStoreTimestampContext.kt`][tsctx] | Modified | UTC sync watermark storage |
| [`ImmunizationWriter.kt`][immwriter] | New | Server-assigned ids and whole-second timestamps |
| [`PatientRepository.kt`][patientrepo] | Modified | Memoised patient profiles |
| [`PractitionerRepository.kt`][practrepo] | New | Practitioner identity from the OIDC `profile` claim |
| [`QuestionnaireService.kt`][questionnaire] | Modified | SDC form placeholder substitution |
| [`OAuthConfig.kt`][oauthconfig] | Unchanged | OIDC issuer and discovery URL |
| [`OidcAuthApi.kt`][oidcapi] | Unchanged | PKCE token exchange |
| [`FhirBearerAuthenticator.kt`][bearer] | Unchanged | Bearer token for FHIR engine requests |
| [`Platform.jvm.kt`][platformjvm] | Unchanged | Desktop loopback redirect server |
| [`OhsPlayerApplication.kt`][application] | Unchanged | Android FHIR engine `ServerConfiguration` |
| [`app-config-conventions.gradle.kts`][appconfig] | New | Single source for auth and FHIR configuration values |
| [`libs.versions.toml`][versions] | Modified | Pinned library versions |
| [`local.properties.sample`][localprops] | Modified | Sample local configuration |
| [`desktopApp/build.gradle.kts`][desktopbuild] | New | Desktop packaging and jlink modules |
| [`ProfileSyncCoverageTest.kt`][synccoveragetest] | New | Checks every profile resource type is synced |
| [`DataStoreTimestampContextTest.kt`][tsctxtest] | Unchanged | Checks UTC watermark handling |

The upstream project this app is based on is
[`ohs-foundation/player-reference`](https://github.com/ohs-foundation/player-reference), which
targets HAPI FHIR and Keycloak.

<!-- Link definitions -->
[readme]: player-medplum/README.md
[versions]: player-medplum/gradle/libs.versions.toml
[localprops]: player-medplum/local.properties.sample
[appconfig]: player-medplum/build-logic/src/main/kotlin/app-config-conventions.gradle.kts
[desktopbuild]: player-medplum/desktopApp/build.gradle.kts
[application]: player-medplum/player-medplum-app/src/androidMain/kotlin/dev/ohs/player/medplum/OhsPlayerApplication.kt
[platformjvm]: player-medplum/player-medplum-app/src/jvmMain/kotlin/dev/ohs/player/medplum/auth/Platform.jvm.kt
[oauthconfig]: player-medplum/player-medplum-app/src/commonMain/kotlin/dev/ohs/player/medplum/auth/OAuthConfig.kt
[oidcapi]: player-medplum/player-medplum-app/src/commonMain/kotlin/dev/ohs/player/medplum/auth/OidcAuthApi.kt
[bearer]: player-medplum/player-medplum-app/src/commonMain/kotlin/dev/ohs/player/medplum/auth/FhirBearerAuthenticator.kt
[synctask]: player-medplum/player-medplum-app/src/commonMain/kotlin/dev/ohs/player/medplum/data/sync/AppFhirSyncTask.kt
[tsctx]: player-medplum/player-medplum-app/src/commonMain/kotlin/dev/ohs/player/medplum/data/sync/DataStoreTimestampContext.kt
[immwriter]: player-medplum/player-medplum-app/src/commonMain/kotlin/dev/ohs/player/medplum/data/repository/ImmunizationWriter.kt
[patientrepo]: player-medplum/player-medplum-app/src/commonMain/kotlin/dev/ohs/player/medplum/data/repository/PatientRepository.kt
[practrepo]: player-medplum/player-medplum-app/src/commonMain/kotlin/dev/ohs/player/medplum/data/repository/PractitionerRepository.kt
[questionnaire]: player-medplum/player-medplum-app/src/commonMain/kotlin/dev/ohs/player/medplum/feature/questionnaire/QuestionnaireService.kt
[synccoveragetest]: player-medplum/player-medplum-app/src/commonTest/kotlin/dev/ohs/player/medplum/data/sync/ProfileSyncCoverageTest.kt
[tsctxtest]: player-medplum/player-medplum-app/src/jvmTest/kotlin/dev/ohs/player/medplum/data/sync/DataStoreTimestampContextTest.kt
