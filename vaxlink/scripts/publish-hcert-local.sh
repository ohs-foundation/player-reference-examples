#!/usr/bin/env bash
# Builds the WHO SMART Trust HCERT Kotlin library from source and publishes it to the local Maven
# cache as io.github.litlfred:hcert-api and hcert-core, which hcert/build.gradle.kts resolves through
# mavenLocal(). Needed until the author releases it to Maven Central; then this script goes away.
#
#   scripts/publish-hcert-local.sh [checkout-dir]
#
# Clones litlfred/smart-trust-tools at the pinned commit (the same one the web component under
# hcert/src/wasmJsMain/npm/hcert-web/component/ was built from) when the directory is absent, then
# publishes the JVM artifacts and the Kotlin Multiplatform metadata with this project's Gradle
# wrapper. The Node.js target is not built: Android and desktop only need the JVM artifact.
set -euo pipefail

COMMIT="bad1e36a98c3746dc9ddbac2b045ba9201a8f236"
here="$(cd "$(dirname "$0")/.." && pwd)"
checkout="${1:-$here/build/smart-trust-tools}"

if [ ! -d "$checkout/.git" ]; then
  git clone --quiet https://github.com/litlfred/smart-trust-tools.git "$checkout"
fi
git -C "$checkout" fetch --quiet origin "$COMMIT"
git -C "$checkout" checkout --quiet "$COMMIT"

"$here/gradlew" -p "$checkout/tools/hcert-kotlin" --no-configuration-cache \
  :hcert-api:publishJvmPublicationToMavenLocal \
  :hcert-api:publishKotlinMultiplatformPublicationToMavenLocal \
  :hcert-core:publishJvmPublicationToMavenLocal \
  :hcert-core:publishKotlinMultiplatformPublicationToMavenLocal

echo "published io.github.litlfred:hcert-core from smart-trust-tools $COMMIT to ~/.m2"
