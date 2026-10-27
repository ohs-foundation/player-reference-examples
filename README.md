# Player Reference Examples

Reference integrations that show how the [Open Health Stack][ohs] **player** — JSON-configured,
SQL-on-FHIR-driven UI — is wired up against a real FHIR backend.

Each example is a self-contained project in its own top-level directory, with its own build,
its own README and its own CI workflow. Nothing is shared between examples except the licence
and the repository conventions in this root.

## Examples

| Example | Backend | Platforms | Status |
|---|---|---|---|
| [`player-medplum/`](player-medplum) — OHS Medplum EIR, an electronic immunization registry | Self-hosted [Medplum][medplum] FHIR R4 | Android, iOS, desktop (JVM), web (Wasm/JS) | Proof of concept |

## Working on an example

Everything you need is inside the example's directory — change into it first:

```bash
cd player-medplum
./gradlew :desktopApp:run
```

See that example's `README.md` for prerequisites, configuration and the full task list.

## Repository layout

```
.
├── .github/workflows/     # one CI + release workflow pair per example,
│                          #   named <example>-ci.yml / <example>-release.yml
├── LICENSE                # Apache 2.0, applies to the whole repository
├── license-header.txt     # header the per-example formatters enforce
└── player-medplum/        # first example — see its README
```

## Adding an example

1. Create a top-level directory with the example's build, sources, README and `.gitignore`.
2. Copy `.github/workflows/player-medplum-ci.yml` to `<example>-ci.yml` and retarget the
   `paths:` filter, `working-directory:` and `build-root-directory:` at the new directory.
3. Add a row to the table above.

Release tags are per-example. Prefix the tag with the directory name so the right release
workflow picks it up — for example `player-medplum-v1.0.0`.

## Licence

Apache License 2.0 — see [LICENSE](LICENSE).

[ohs]: https://ohs-foundation.github.io/
[medplum]: https://www.medplum.com/
