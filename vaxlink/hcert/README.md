# hcert

The WHO HCERT verification library inside the Player, for the VaxLink vignette (ticket NE-1). It reads
a vaccination certificate QR offline without trusting it (J2), verifies one against the GDHCN DEV trust
list (J3), and will verify a Verifiable Health Link and fetch the records behind it (J4.1).

Nothing here does cryptography. The library is Carl Leitner's
[smart-trust-tools](https://github.com/litlfred/smart-trust-tools), built from the SMART Trust HCERT
specification. This module hosts it and does the parts the library leaves to the app.

## Two engines, one Kotlin surface

| Target | Engine | Why |
|---|---|---|
| Android, desktop | `io.github.litlfred:hcert-core`, the Kotlin library, JVM artifact | The author's library as published |
| Web (Wasm) | `hcert-rust` as a WebAssembly component, transpiled by jco | The author decided on 24 Oct 2026 that the Kotlin library stays JVM and Node only; browsers use the Rust build |
| iOS | none yet | The library has no Kotlin/Native target and no demo device is an iPhone |

Both implement the same WIT contract, so `HcertEngine` in `commonMain` mirrors that contract and the
platform code only adapts types. `loadHcertEngine()` returns the engine for the platform; the web one
instantiates its WebAssembly module there, once.

```kotlin
val engine = loadHcertEngine()

// J2, offline, no trust list on the device: decode and show, never trust.
when (val read = engine.readUnverified(hc1)) {
  is ReadResult.Decoded -> read.hcert.dvc   // DvcMin, marked by a "NOT VERIFIED" diagnostic
  is ReadResult.Failed -> read.failure      // step and reason, no payload
}

// J3, with the cached trust list.
val trust = engine.newTrustStore()
trustListCache.loadInto(trust)
when (val result = engine.verify(hc1, trust, HcertClock.nowEpochSeconds())) {
  is VerificationResult.Verified -> result.hcert.dsc.kid   // and iss, dvc, ...
  is VerificationResult.Failed -> result.failure.reason    // SIGNATURE_INVALID, KID_NOT_FOUND, EXPIRED, ...
}
```

## What the module adds

| Piece | File | Does |
|---|---|---|
| Trust list cache | `TrustList.kt` | Fetches the DEV DID trust list on sync, keeps it only when it parses as a DID document, serves the bundled copy until then, and reports the cache age for the UI |
| Clock | `HcertEngine.kt` | `HcertClock.nowEpochSeconds()` plus a 300 second skew by default; `verify` never reads an ambient clock |
| Certificate fingerprint | `CertificateFingerprint.kt` | SHA-256 of the HC1 string, stored as an Immunization identifier so a card scanned twice is recognised |
| PreQual display | `PreQualProducts.kt` | Names for the demo products, overlaid on the published PreQualProductIds CodeSystem, whose displays are product codes |
| DVC to FHIR | `DvcFhirMapper.kt` | One mapping shared by J2, J3 and J4.1: Patient, Immunization, and on verification a Provenance |

### Mapping rules

- `Patient`: `name.text` is the certificate's name string, split into family (the capitalised token) and
  given names; `gender` from `s`; `birthDate` from `dob`; nationality as the `patient-nationality`
  extension with an ISO 3166 code; the guardian `gn` on `Patient.contact` with relationship GUARD. No
  national identifier is invented; `id` and `dt` become an identifier only when the certificate has them.
- `Immunization`: one per certificate; `vaccineCode` is the PreQual product coding exactly as issued plus
  a display name; `occurrenceDateTime` is `dv`; `lotNumber` is `bo`; the clinician `cn` is a performer;
  the issuing organization `is` is the location; `primarySource` false and `reportOrigin` "Written
  Record" with the text "Vaccination certificate presented by the family".
- Verification (`markVerified`) updates the same Immunization in place: `primarySource` true,
  `reportOrigin` cleared, and a `Provenance` whose author agent is the issuing GDHCN participant, whose
  verifier agent is the practitioner, whose policy is the trust list URL, and whose entities are the
  certificate fingerprint (source) and the DSC kid.
- Codes are never translated. The Lao coding is added at J3 only after a clinician confirms it, outside
  this module (NE-3).

## Building the library

The library is not on Maven Central yet. Build it from the checkout and publish it locally:

```bash
cd ../../smart-trust-tools/tools/hcert-kotlin
../../../player-reference-examples/vaxlink/gradlew -p . :hcert-core:jvmTest publishToMavenLocal
```

That is what `io.github.litlfred:hcert-core:0.1.0` in `gradle/libs.versions.toml` resolves to, through
the `mavenLocal()` repository in `settings.gradle.kts`. When Carl publishes a release, change the version
and drop nothing else.

The web component is committed under `src/wasmJsMain/npm/hcert-web/component/` with its source commit
and hashes in `SOURCE.md`. Rebuild it with `scripts/build-hcert-web.sh`, which needs rustup and Node.

## Tests

```bash
./gradlew :hcert:jvmTest
```

`XxrVectorsTest` is the NE-1 acceptance on the JVM engine: both worked-example certificates (HepB and
BCG, DEV participant XXR, kid `916ffa567c0f44a2`) read unverified with no trust list, verify against the
XXR DSC document and against the whole DEV trust list snapshot, and a tampered copy fails at
`SIGNATURE` with `SIGNATURE_INVALID` and no payload. `DvcFhirMapperTest` pins the mapping rules above.
`TrustListCacheTest` covers bundled, refreshed and refused trust lists. `:hcert:wasmJsBrowserTest`
runs `WasmEngineTest` under headless Chrome: the same reads, verifications and failures through the
web engine, plus the VHL calls.

The test vectors under `src/jvmTest/resources/vectors/` are copied from smart-trust-tools `vectors/`
(TEST DATA ONLY, issued by GDHCN DEV participant XXR). The DEV trust list snapshot there is from
23 Oct 2026.

## Verifiable Health Links

`verify` returns a VHL entry when the QR is a Verifiable Health Link (hcert key 5). The engine then
offers the pure steps around the HTTP the viewer does (NE-4 Part C): `buildManifestRequest` (the
ITI-YY5 POST, passcode included when the link's flag has P), `parseManifest` (the searchset Bundle
of DocumentReferences) and `decrypt` (JWE dir / A256GCM with the link key). `VhlTest` pins them on
the IHE examples. The HTTPS calls, the passcode prompt and the AuditEvent remain the app's.

## Audit

`VerificationAudit.event` builds the AuditEvent for every verification attempt (CLN-10, AUD-01):
outcome, the certificate fingerprint, the DSC kid on success, who tried and for whom, and never any
certificate content. The app uploads it through the Info Gateway like any other resource.
