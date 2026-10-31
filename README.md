# Player Reference Examples

Working apps built on the [Open Health Stack][ohs] player, each wired to a real FHIR backend. Every directory is its own Gradle project, shaped like [`player-reference`][pr]: change into it, then build and run from there.

| Example | What it shows | Backend | Platforms |
|---|---|---|---|
| [`workflow-examples/`](workflow-examples) | A community-to-facility referral loop driven by `fhir-workflow` PlanDefinitions | HAPI FHIR behind the OHS gateway, Keycloak | Android, iOS, desktop, web (Wasm) |
| [`player-medplum/`](player-medplum) | An immunization registry | Self-hosted [Medplum][medplum] | Android, iOS, desktop, web (Wasm, JS) |
| [`vaxlink/`](vaxlink) | The Pre-GDHF VaxLink vignette: a CHW reads a WHO vaccination certificate QR offline, a clinic verifies it against the GDHCN DEV trust list, and a clinic abroad opens a Verifiable Health Link; hosts the WHO HCERT library in `hcert/` | HAPI FHIR behind the OHS gateway, Keycloak; GDHCN DEV trust list | Android, desktop, web (Wasm) |

```bash
cd workflow-examples && ./gradlew run
cd player-medplum && ./gradlew :desktopApp:run
cd vaxlink && ./gradlew run
```

## Layout

```
.
├── .github/workflows/   <example>-ci.yml and <example>-release.yml per example
├── player-medplum/      Gradle project: app at the root, plus androidApp/, desktopApp/, webApp/ launchers
├── vaxlink/             Gradle project: app at the root, plus the hcert/ library module
├── workflow-examples/   Gradle project: app at the root
├── LICENSE              Apache 2.0, for the whole repository
└── license-header.txt
```

## Adding an example

1. Create a top-level directory with its own build, README and `.gitignore`.
2. Copy a CI workflow to `.github/workflows/<example>-ci.yml` and point its `paths:`, `working-directory:` and `build-root-directory:` at the new directory.
3. Add a row to the table above.

Release tags start with the directory name, so the right workflow picks them up: `player-medplum-v1.0.0`, `vaxlink-v1.0.0`, `workflow-examples-v1.0.0`.

## Licence

Apache 2.0, see [LICENSE](LICENSE).

[ohs]: https://ohs-foundation.github.io/
[pr]: https://github.com/ohs-foundation/player-reference
[medplum]: https://www.medplum.com/
