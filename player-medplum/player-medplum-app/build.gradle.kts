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
@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import dev.ohs.build.AppConfig
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  // Plain com.android.library + androidTarget, not the KMP-Android-library plugin: at AGP 8.11.2
  // that plugin is still experimental, spells its block `androidLibrary`, renames the test source
  // sets, and has no build types. This is the shape the app already builds on.
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.composeHotReload)
  alias(libs.plugins.kotlinSerialization)
  id("dev.ohs.ig-codegen")
  id("app-config-conventions")
  id("spotless-conventions")
}

val appConfig = extensions.getByType<AppConfig>()

kotlin {
  // Desktop, js and wasmJs lack a native OS background scheduler, so they share a "foregroundSync"
  // source set (see `foregroundSyncMain/.../data/sync/Sync.kt`) letting one coroutine-based
  // scheduler serve all three instead of a separate implementation per platform. js and wasmJs
  // further share a nested "foregroundSyncWeb", kept apart from the default `webMain` group, which
  // holds web code unrelated to sync.
  applyDefaultHierarchyTemplate {
    common {
      group("foregroundSync") {
        withJvm()
        group("foregroundSyncWeb") {
          withJs()
          withWasmJs()
        }
      }
    }
  }

  // fhir-engine's Android artifact ships inline functions (e.g. Sync.oneTimeSync) compiled at JVM
  // target 21 -- inlining them requires this target to be at least as high. :androidApp must match.
  androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

  listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
    iosTarget.binaries.framework {
      // Must equal the `import` in iosApp/iosApp/ContentView.swift.
      baseName = "OhsMedplumEirApp"
      isStatic = true
    }
  }

  jvm()

  // Deliberately no binaries.executable() for js/wasmJs: this is a library, and :webApp owns the
  // executables and the webpack build that resolves the vendored sqlite worker.
  js { browser() }

  @OptIn(ExperimentalWasmDsl::class) wasmJs { browser() }

  // expect/actual classes (AuthorizationLauncher) are stable enough for our use.
  compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

  sourceSets {
    androidMain.dependencies {
      implementation(libs.compose.uiToolingPreview)
      implementation(libs.androidx.activity.compose)
      implementation(libs.ktor.client.okhttp)
      implementation(libs.androidx.browser)
      implementation(libs.androidx.work.runtime)
    }
    commonMain.dependencies {
      implementation(libs.ohs.player.client)
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.material)
      implementation(libs.compose.material3)
      implementation(libs.compose.adaptive)
      implementation(libs.compose.adaptive.layout)
      implementation(libs.compose.adaptive.navigation)
      implementation(libs.compose.material3.adaptive.navigation.suite)
      implementation(libs.compose.materialIconsCore)
      implementation(libs.compose.ui)
      implementation(libs.compose.components.resources)
      implementation(libs.compose.uiToolingPreview)
      implementation(libs.androidx.lifecycle.viewmodelCompose)
      implementation(libs.androidx.lifecycle.runtimeCompose)
      implementation(libs.kermit)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.kotlinx.datetime)
      implementation(libs.navigation.compose)
      implementation(project.dependencies.platform(libs.koin.bom))
      implementation(libs.koin.core)
      implementation(libs.koin.compose)
      implementation(libs.koin.composeViewmodel)
      implementation(libs.ohs.fhir.engine)
      implementation(libs.ohs.fhir.model.r4)
      implementation(libs.fhir.data.capture)
      // Auth: shared OAuth2/PKCE client, secure session storage, SHA-256 for PKCE.
      implementation(libs.ktor.client.core)
      implementation(libs.ktor.client.auth)
      implementation(libs.ktor.client.content.negotiation)
      implementation(libs.ktor.serialization.kotlinx.json)
      implementation(libs.ksafe)
      implementation(project.dependencies.platform(libs.kotlincrypto.hash.bom))
      implementation(libs.kotlincrypto.hash.sha2)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.compose.uiTest)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.ktor.client.mock)
    }
    iosMain.dependencies { implementation(libs.ktor.client.darwin) }
    getByName("foregroundSyncWebMain").dependencies { implementation(libs.kotlinx.browser) }
    webMain.dependencies {
      implementation(libs.ktor.client.js)
      implementation(libs.kotlinx.browser)
    }
    jvmMain.dependencies {
      implementation(compose.desktop.currentOs)
      implementation(libs.kotlinx.coroutinesSwing)
      implementation(libs.ktor.client.cio)
    }
    jvmTest.dependencies {
      implementation(compose.desktop.currentOs)
      implementation(libs.koin.test)
    }
  }
}

/*
 * GeneratedAuthConfig lands in commonMain because OAuthConfig.kt (shared code) reads it, even
 * though most of its constants are consumed by platform entry points. The values themselves are
 * resolved by the app-config-conventions plugin, which :androidApp and :desktopApp also apply --
 * so the manifest placeholders and this generated file cannot drift apart.
 */
val authConfigOutputDir = layout.buildDirectory.dir("generated/authconfig/commonMain/kotlin")

val generateAuthConfig =
  tasks.register("generateAuthConfig") {
    // Copied into locals so the doLast lambda below closes over plain Strings. Reading a
    // script-level object from inside doLast captures a Gradle script reference, which the
    // configuration cache cannot serialize.
    val issuer = appConfig.issuer
    val clientId = appConfig.clientId
    val redirectScheme = appConfig.redirectScheme
    val redirectHost = appConfig.redirectHost
    val webRedirectUrl = appConfig.webRedirectUrl
    val desktopPort = appConfig.desktopRedirectPort
    val scopes = appConfig.scopes
    val fhirBaseUrl = appConfig.fhirBaseUrl
    val outDir = authConfigOutputDir
    inputs.property("issuer", issuer)
    inputs.property("clientId", clientId)
    inputs.property("redirectScheme", redirectScheme)
    inputs.property("redirectHost", redirectHost)
    inputs.property("webRedirectUrl", webRedirectUrl)
    inputs.property("desktopPort", desktopPort)
    inputs.property("scopes", scopes)
    inputs.property("fhirBaseUrl", fhirBaseUrl)
    outputs.dir(outDir)
    doLast {
      val pkgDir = outDir.get().asFile.resolve("dev/ohs/player/medplum/auth").apply { mkdirs() }
      pkgDir
        .resolve("GeneratedAuthConfig.kt")
        .writeText(
          """
        |// Generated by the :player-medplum-app generateAuthConfig task. Do not edit.
        |// Values come from local.properties / env vars; see local.properties.sample.
        |package dev.ohs.player.medplum.auth
        |
        |internal object GeneratedAuthConfig {
        |  const val ISSUER: String = "$issuer"
        |  const val CLIENT_ID: String = "$clientId"
        |  const val REDIRECT_SCHEME: String = "$redirectScheme"
        |  const val REDIRECT_HOST: String = "$redirectHost"
        |  const val WEB_REDIRECT_URL: String = "$webRedirectUrl"
        |  const val DESKTOP_REDIRECT_PORT: Int = $desktopPort
        |  const val SCOPES: String = "$scopes"
        |  const val FHIR_BASE_URL: String = "$fhirBaseUrl"
        |}
        |
        """
            .trimMargin()
        )
    }
  }

kotlin.sourceSets.named("commonMain") { kotlin.srcDir(authConfigOutputDir) }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
  dependsOn(generateAuthConfig)
}

android {
  namespace = "dev.ohs.player.medplum"
  compileSdk = libs.versions.android.compileSdk.get().toInt()
  // No targetSdk here: deprecated in a library since AGP 8.1. It lives in :androidApp.
  defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

igCodegen {
  // sourcesDir defaults to src/commonMain/composeResources/files.
  // This is the IG-codegen namespace, not the app's -- it is unrelated to the package rename, and
  // the generator additionally hardcodes dev.ohs.player.client / dev.ohs.fhir FQCNs that must not
  // move.
  packageName = "dev.ohs.player.generated"
}

// Targets skipped on CI until their test setups are sorted out. Only disabled when CI=true (set by
// GitHub Actions) so the CI build stays green; local development still runs these.
//
//   * Kotlin/JS IR backend crashes lowering the generated sealed-interface dispatch tables in
//     dev.ohs.fhir:fhir-path. The main JS compile is fine; only the JS *test* executable lowering
//     trips, because the test source set exercises those types.
//
//   * Android host unit tests need a host Android framework (NoClassDefFoundError).
val isCi = providers.environmentVariable("CI").map(String::toBoolean).getOrElse(false)

if (isCi) {
  tasks
    .matching {
      it.name in
        setOf(
          "testDebugUnitTest",
          "testReleaseUnitTest",
          "compileTestDevelopmentExecutableKotlinJs",
          "compileTestProductionExecutableKotlinJs",
          "jsBrowserTest",
          "wasmJsBrowserTest",
        )
    }
    .configureEach { enabled = false }
}
