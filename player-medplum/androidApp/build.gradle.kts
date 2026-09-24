/*
 * Copyright 2026 Open Health Stack Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import dev.ohs.build.AppConfig
import java.util.Properties

/*
 * Packaging only -- this module has no Kotlin source. MainActivity, OhsPlayerApplication and
 * LoginRedirectActivity all live in :player-medplum-app's androidMain, next to the expect/actual
 * declarations and the internal GeneratedAuthConfig they depend on; the manifest here names them by
 * fully-qualified class name. Hence no Kotlin or Compose compiler plugin.
 */
plugins {
  alias(libs.plugins.androidApplication)
  id("app-config-conventions")
  id("spotless-conventions")
}

val appConfig = extensions.getByType<AppConfig>()

// Release signing inputs: env vars first (CI), then keystore.properties as a dev-time fallback.
// Read via the providers API so the configuration cache tracks them.
val keystoreProperties: Map<String, String> =
  providers
    .fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
    .asText
    .map { text ->
      val props = Properties().apply { load(text.reader()) }
      props.stringPropertyNames().associateWith(props::getProperty)
    }
    .getOrElse(emptyMap())

fun envOrKeystore(envName: String, fileKey: String): String? =
  providers.environmentVariable(envName).filter { it.isNotBlank() }.orNull
    ?: keystoreProperties[fileKey]?.takeIf { it.isNotBlank() }

val keystorePath = envOrKeystore("ANDROID_KEYSTORE_PATH", "KEYSTORE_PATH")
val keystoreAlias = envOrKeystore("ANDROID_KEY_ALIAS", "KEY_ALIAS")
val keystoreKeyPassword = envOrKeystore("ANDROID_KEY_PASSWORD", "KEY_PASSWORD")
val keystoreStorePassword = envOrKeystore("ANDROID_STORE_PASSWORD", "STORE_PASSWORD")

val hasReleaseSigning: Boolean =
  !keystorePath.isNullOrBlank() &&
    !keystoreAlias.isNullOrBlank() &&
    !keystoreKeyPassword.isNullOrBlank() &&
    !keystoreStorePassword.isNullOrBlank()

android {
  namespace = "dev.ohs.player.medplum"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "dev.ohs.player.medplum"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    versionCode = appConfig.versionCode
    versionName = appConfig.versionName
    // Substituted into the LoginRedirectActivity intent-filter in src/main/AndroidManifest.xml,
    // from the same appConfig values :player-medplum-app bakes into GeneratedAuthConfig.
    manifestPlaceholders["oauthRedirectScheme"] = appConfig.redirectScheme
    manifestPlaceholders["oauthRedirectHost"] = appConfig.redirectHost
  }

  // src/debug/ is picked up by convention: it carries a debug-only network security config, because
  // the dev Medplum stack is plain HTTP on a LAN IP and API 28+ blocks cleartext. Release builds
  // must not inherit that.

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        // Disables legacy V1 JAR signing to rely entirely on V2+ signatures required by Android
        // 7.0+.
        enableV1Signing = false
        enableV2Signing = true
        storeFile = file(keystorePath!!)
        keyAlias = keystoreAlias
        keyPassword = keystoreKeyPassword
        storePassword = keystoreStorePassword
      }
    }
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
  }

  compileOptions {
    // Must match :player-medplum-app -- its AAR is Java 21 bytecode, and fhir-engine's inline
    // functions are compiled at 21.
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

dependencies {
  implementation(project(":player-medplum-app"))
  debugImplementation(libs.compose.uiTooling)
}
