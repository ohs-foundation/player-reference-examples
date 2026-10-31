# VaxLink

The OHS Player configuration for the Pre-GDHF VaxLink vignette: a Lao PDR family's baby is born in Thailand, gets two signed vaccination certificates, and is found by a community health worker, verified at a clinic and later seen at a clinic abroad. One app serves three roles: the CHW community role (J2), the clinic facility role (J3) and the foreign clinic viewer role (J4.1). It started from [`workflow-examples`](../workflow-examples), keeping its sign-in, role resolution, per-role sync and questionnaire host. The [`hcert`](./hcert) module hosts the WHO HCERT verification library for reading and verifying certificate QR codes (NE-1).

The work is tracked as the Nawi engineering issues on [nawitech/pre-gdhf-workshop](https://github.com/nawitech/pre-gdhf-workshop/issues).

## Roles

| Role | PractitionerRole code | Journey |
|---|---|---|
| Community health worker | `chw` | J2: registers the newborn, scans the cards, refers |
| Nurse | `nurse` | J3: verifies the cards, imports, runs the schedule |
| Clinician | `clinician` | J4.1: scans a Verifiable Health Link and views the record |

## Certificates (NE-1)

Every patient profile has a **Scan certificate** action. The CHW role reads the WHO vaccination
certificate QR offline and shows it marked *Not verified* (J2). The nurse and clinician roles verify
the signature against the GDHCN DEV trust list first and show the content only when it checked out
(J3); a tampered card shows the failing step and reason and nothing else. On desktop, where there is
no camera, paste the `HC1:` text from the WHO worked example instead. The web build needs https or
localhost for the camera.

The trust list ships with the app (`files/trust/gdhcn-dev-trustlist.json`, fetched 23 Oct 2026) so a
device verifies before its first sync; every **Sync now** also refreshes it from
`tng-cdn-dev.who.int`, and the scan screen shows how old the copy in use is. The engine, the trust
list cache, the DVC to FHIR mapper and the AuditEvent builder live in [`hcert/`](./hcert), which
also explains how to build the library from source. Recording a scan as an Immunization is NE-2
(community) and NE-3 (clinic).

## Setup

1. **JDK 21.** Gradle rejects JDK 25.
2. **The HCERT library**: publish it to your local Maven cache once, as [`hcert/README.md`](./hcert/README.md#building-the-library) describes; it is not on Maven Central yet.
3. **`local.properties`**: copy `local.properties.sample` and set `OAUTH_ISSUER`, `OAUTH_CLIENT_ID` and `FHIR_BASE_URL`.
4. **Gateway**: it must accept `PATCH`, because the engine sends every update that way.
5. **Demo users**: create the seed service account, fill in `scripts/seed.env` from `scripts/seed.env.sample`, and run `./scripts/seed.sh`.

### Seed service account

`seed.sh` signs in as a Keycloak service account in the app's realm (`ohs-player`).

1. Create an OpenID Connect client, for example `seed-service`.
2. Turn on *Client authentication* and *Service accounts roles*. Leave the other flows off.
3. Copy the client secret from *Credentials*.
4. Under *Service accounts roles*, add `realm-management` → `manage-users` and `manage-realm`.
5. Put `SEED_CLIENT_ID` and `SEED_CLIENT_SECRET` in `scripts/seed.env`. It's git-ignored.

Each run also grants the account `ORG_SCOPE_EXEMPT` and the `GET_*`/`PUT_*` roles the gateway needs for organization and location writes.

### Demo data

The seed creates only what three users need to sign in and sync, and no patients. Ids are fixed UUIDs in [`scripts/seed-bundle.json`](./scripts/seed-bundle.json), so re-running updates rather than duplicates.

```
Kiambu County Department of Health          county code 022
└─ Ruiru Sub-County Health Management Team
   └─ Kimbo Health Centre                    KE-022-0417     Outpatient Department
      └─ Kimbo Community Health Unit         KE-022-0417-01  Kimbo Village
```

| User | Password | Name | Role | Organization |
|---|---|---|---|---|
| `chw1` | `chw_1234!` | Mwanaisha Juma Mwinyi | `chw` | Kimbo Community Health Unit |
| `nurse1` | `nurse_1234!` | Kadzo Zawadi Baya | `nurse` | Kimbo Health Centre |
| `clinician1` | `clinician_1234!` | Hamisi Kazungu Charo | `clinician` | Kimbo Health Centre |

CHW referrals go to Kimbo Health Centre, the unit's `partOf`. Names and codes are realistic but fictional. The server is public, so these passwords are for demos only.

## Run

From this directory:

| Target | Command |
|---|---|
| Desktop | `./gradlew run` |
| Android | `./gradlew assembleDebug` |
| Web (Wasm) | `./gradlew wasmJsBrowserDevelopmentRun` |
| iOS | Open `iosApp/` in Xcode (builds, but the verification engine has no iOS build yet) |

