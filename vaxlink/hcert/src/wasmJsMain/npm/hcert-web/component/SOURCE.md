# SMART Trust HCERT WebAssembly component

Generated files. Do not edit; rebuild with `vaxlink/scripts/build-hcert-web.sh`.

| | |
|---|---|
| Source | https://github.com/litlfred/smart-trust-tools, `tools/hcert-rust` |
| Commit | `bad1e36a98c3746dc9ddbac2b045ba9201a8f236` (2026-10-24) |
| Built | 2026-10-29 with Rust 1.97.0 (`rust-toolchain.toml`), `cargo build --locked --release --features component --target wasm32-unknown-unknown`, jco 1.35.0 (`toolchain/node/package-lock.json`) |
| Licence | Apache-2.0, as the source repository |

| File | What it is |
|---|---|
| `hcert.js` | The component transpiled by jco into an ES module; `index.js` next door is the only caller |
| `hcert.core.wasm` | The core WebAssembly module `hcert.js` loads with `new URL("./hcert.core.wasm", import.meta.url)` |
| `wasi-random.js` | The one import the component has, `wasi:random/random`, mapped to the browser's `crypto.getRandomValues` |
| `hcert.d.ts`, `interfaces/*.d.ts` | The TypeScript surface, kept for reading; nothing compiles them |

SHA-256 at build time:

```
c2e0887e04e859eba2845a4549555d3336dc66ac8a6713772da2e678236f7793  hcert.core.wasm
be893aeb6804838adedbcbbf511dbbe1f930360cbf289202b61cec7cf45d1b87  hcert.js
```

The upstream `build-component.sh` adds `--remap-path-prefix` flags for byte-reproducible output; they
break on a checkout path containing a space, so `build-hcert-web.sh` builds without them. The
hashes above therefore depend on the build machine's paths.
