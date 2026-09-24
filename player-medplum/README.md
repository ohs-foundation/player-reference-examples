# OHS Medplum EIR

A health-facility **electronic immunization registry**, built with Kotlin Multiplatform and Compose
Multiplatform against a self-hosted [Medplum][medplum] FHIR R4 server.

One Kotlin codebase runs on Android, iOS, desktop (JVM) and the browser (Wasm/JS). Screens are
assembled from JSON configuration — [SQL-on-FHIR][sof] ViewDefinitions and
[Open Health Stack][ohs] player configs — rather than hand-written per-resource UI, so adding a
section to a patient profile is usually a JSON file and a renderer registration, not a new query
layer.

> **Status: proof of concept.** It is a working demonstrator, not a certified product, and it has
> not been through clinical review. The bundled immunization schedule contains placeholder codes.

## What it does

- A facility register of children, scoped server-side to the practitioner's own organization.
- Records doses given, growth measurements, and adverse events following immunization (AEFI).
- Shows what each child is **due** or **overdue** for. That forecast is computed server-side by a
  Medplum Bot, not on the device — the phone holds no clinical logic.
- Works offline. Everything is written locally first and synced when there is a connection.

## Quickstart

You need a JDK 21 toolchain and, for Android, the Android SDK. A Medplum backend must be running.

```bash
cp local.properties.sample local.properties   # then fill in sdk.dir and the OAuth values
```

| Target | Command |
|---|---|
| Android | `./gradlew :androidApp:installDebug` |
| Desktop | `./gradlew :desktopApp:run` |
| Web (Wasm) | `./gradlew :webApp:wasmJsBrowserDevelopmentRun` |
| Web (JS) | `./gradlew :webApp:jsBrowserDevelopmentRun` |
| iOS | open `iosApp/iosApp.xcodeproj` in Xcode and run |
| Tests | `./gradlew :player-medplum-app:jvmTest` |
| Format | `./gradlew spotlessApply` |

## Module layout

| Module | Contains |
|---|---|
| `player-medplum-app` | Everything: all Kotlin source sets, Compose resources, the JSON view configuration, and the code-generation setup. The other modules are packaging. |
| `androidApp` | Android manifest, launcher icons, signing. No Kotlin. |
| `desktopApp` | A one-line `main()` and the jpackage/installer configuration. |
| `webApp` | A one-line `main()`, `index.html`, and the vendored SQLite WASM worker. |
| `iosApp` | Xcode project; links the shared framework. |
| `ig-codegen` | Included build. Generates Kotlin data classes from the ViewDefinition / ViewJoinMap JSON. |
| `build-logic` | Included build. Formatting conventions, and the single resolver for build-time app configuration. |

## Configuration

All build-time configuration lives in `local.properties` (git-ignored), with environment-variable
overrides for CI. See [`local.properties.sample`](local.properties.sample) for every key.

> **The most likely first-run failure:** sign-in returns `invalid_request` because the Medplum
> `ClientApplication` does not list the app's redirect URI. Android and iOS need
> `dev.ohs.player.medplum://auth` in its `redirectUris`, alongside the desktop
> (`http://127.0.0.1:8765/callback`) and web (`http://localhost:8080/callback`) entries.

## Contributing

Issues and pull requests are welcome.

- `./gradlew spotlessApply` before committing — CI runs `spotlessCheck` and it is a ratchet against
  `origin/main`, so it only judges what you actually touched.
- `./gradlew :player-medplum-app:jvmTest` covers the shared code, including the
  questionnaire-extraction tests. Those matter more than they look: SDC extraction fails *silently*,
  so a broken form produces no error, just a resource that is quietly missing a field.
- Keep clinical logic on the server. The device should not be deciding what a child is due for.

## Licence and attribution

Apache-2.0. See [LICENSE](../LICENSE).

Built on [Open Health Stack][ohs] components with a [Medplum][medplum] backend, and began as a fork
of [`ohs-foundation/player-reference`][upstream].

[ohs]: https://ohs-foundation.github.io/
[medplum]: https://www.medplum.com/
[sof]: https://sql-on-fhir.org/ig/latest/
[upstream]: https://github.com/ohs-foundation/player-reference
