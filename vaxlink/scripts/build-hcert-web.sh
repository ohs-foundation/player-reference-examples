#!/usr/bin/env bash
# Rebuilds the SMART Trust HCERT WebAssembly component the web build verifies with, from a checkout
# of litlfred/smart-trust-tools, into hcert/src/wasmJsMain/npm/hcert-web/component/.
#
#   scripts/build-hcert-web.sh [path/to/smart-trust-tools]
#
# Needs rustup (the toolchain and the wasm32 target come from the repository's rust-toolchain.toml)
# and Node 20+; jco is installed from the repository's pinned toolchain/node/package-lock.json.
# Android and desktop do not use this: they consume io.github.litlfred:hcert-core from Maven.
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
repo="${1:-$here/../../smart-trust-tools}"
repo="$(cd "$repo" && pwd)"
rust="$repo/tools/hcert-rust"
out="$here/hcert/src/wasmJsMain/npm/hcert-web/component"

export PATH="$HOME/.cargo/bin:$PATH"
command -v cargo >/dev/null || { echo "cargo not found; install rustup from https://rustup.rs" >&2; exit 1; }

# The pinned Node tools (jco among them), installed without scripts, as upstream does.
(cd "$repo/toolchain/node" && npm ci --ignore-scripts --no-audit --no-fund --loglevel=error)
export PATH="$repo/toolchain/node/node_modules/.bin:$PATH"

# Upstream adds --remap-path-prefix flags for reproducible bytes; they fail on a path with a space.
unset RUSTFLAGS
(cd "$rust" && cargo build --locked --release --features component --target wasm32-unknown-unknown)

tmp="$(mktemp -d)"
jco new "$rust/target/wasm32-unknown-unknown/release/hcert.wasm" -o "$tmp/hcert.component.wasm"
jco transpile "$tmp/hcert.component.wasm" -o "$tmp/js" --name hcert \
  --map 'wasi:random/random=./wasi-random.js#random' --quiet
cp "$rust/js/component/wasi-random.js" "$tmp/js/"

# The component's own test suite, against the shared vectors, before anything is copied.
node "$rust/js/component/component-test.mjs" "$tmp"

mkdir -p "$out/interfaces"
cp "$tmp/js/hcert.js" "$tmp/js/hcert.core.wasm" "$tmp/js/wasi-random.js" "$tmp/js/hcert.d.ts" "$out/"
cp "$tmp/js/interfaces/"*.d.ts "$out/interfaces/"
rm -rf "$tmp"

commit="$(git -C "$repo" rev-parse HEAD)"
date="$(git -C "$repo" log -1 --format=%ad --date=short)"
echo "built from smart-trust-tools $commit ($date)"
(cd "$out" && shasum -a 256 hcert.core.wasm hcert.js)
echo "update $out/SOURCE.md with the commit and hashes above"
