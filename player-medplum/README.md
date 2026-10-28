# OHS Medplum EIR

An electronic immunization registry for health facilities, running against a self-hosted [Medplum][medplum] FHIR R4 server. One Kotlin codebase serves Android, iOS, desktop and the browser. Screens come from JSON configuration ([SQL-on-FHIR][sof] ViewDefinitions and [OHS][ohs] player configs), so a new profile section is usually a JSON file and a renderer, not a new query layer.

> **Proof of concept.** It works end to end but hasn't had clinical review, and the bundled schedule uses placeholder codes.

## What it does

- Keeps a register of children, scoped on the server to the practitioner's own facility.
- Records doses, growth measurements and adverse events following immunization (AEFI).
- Shows what each child is due or overdue for. A Medplum Bot computes that on the server; the device holds no clinical logic.
- Works offline: writes land locally first and sync when there's a connection.

## Run

You need JDK 21, the Android SDK for Android, and a running Medplum backend. Copy `local.properties.sample` to `local.properties` and fill in `sdk.dir` and the OAuth values.

| Target | Command |
|---|---|
| Android | `./gradlew :androidApp:installDebug` |
| Desktop | `./gradlew :desktopApp:run` |
| Web (Wasm) | `./gradlew :webApp:wasmJsBrowserDevelopmentRun` |
| Web (JS) | `./gradlew :webApp:jsBrowserDevelopmentRun` |
| iOS | Open `iosApp/iosApp.xcodeproj` in Xcode |
| Tests | `./gradlew :jvmTest` |
| Format | `./gradlew spotlessApply` |

If sign-in fails with `invalid_request`, the Medplum `ClientApplication` is missing a redirect URI. It needs `dev.ohs.player.medplum://auth` (Android and iOS), `http://127.0.0.1:8765/callback` (desktop) and `http://localhost:8080/callback` (web).

## Layout

| Path | Holds |
|---|---|
| `src/`, `build.gradle.kts` | Everything that matters: all Kotlin source sets, Compose resources, the JSON view configuration and code generation |
| `androidApp/` | Manifest, launcher icons and signing. No Kotlin. |
| `desktopApp/` | A one-line `main()` and installer packaging |
| `webApp/` | A one-line `main()`, `index.html` and the vendored SQLite WASM worker |
| `iosApp/` | The Xcode project, linking the shared framework |
| `ig-codegen/` | Generates Kotlin classes from the ViewDefinition and ViewJoinMap JSON |
| `build-logic/` | Formatting conventions and the one resolver for build-time configuration |

All build-time configuration lives in `local.properties` (git-ignored), with environment-variable overrides for CI. [`local.properties.sample`](local.properties.sample) lists every key. [`INTEGRATION_GUIDE.md`](INTEGRATION_GUIDE.md) explains what Medplum needs and why.

## Contributing

- Run `./gradlew spotlessApply` before committing. CI's `spotlessCheck` ratchets against `origin/main`, so it only judges what you touched.
- `./gradlew :jvmTest` covers the shared code, including questionnaire extraction. Those tests matter: a broken form fails silently, saving a resource with a field quietly missing.
- Keep clinical logic on the server.

## Licence

Apache 2.0, see [LICENSE](../LICENSE). Built on [Open Health Stack][ohs] with a [Medplum][medplum] backend, starting from [`player-reference`][upstream].

[ohs]: https://ohs-foundation.github.io/
[medplum]: https://www.medplum.com/
[sof]: https://sql-on-fhir.org/ig/latest/
[upstream]: https://github.com/ohs-foundation/player-reference
