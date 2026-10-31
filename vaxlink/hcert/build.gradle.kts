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

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * The WHO HCERT verification facade for the Player (NE-1).
 *
 * One Kotlin API, two engines behind it: Android and desktop use Carl Leitner's Kotlin library
 * (io.github.litlfred:hcert-core, JVM and Node only by its author's decision), and the web target
 * calls his Rust WebAssembly component through JS interop. Both implement the same WIT contract, so
 * the facade in commonMain mirrors that contract and nothing else. The trust list cache, the clock
 * and the DVC to FHIR mapper are common code with no UI dependency, so the backend (NE-4 Part A)
 * can reuse them.
 */
plugins {
  alias(libs.plugins.kotlinMultiplatform)
  // No version here: the root app already puts the Android Gradle plugin on the classpath.
  id("com.android.library")
  alias(libs.plugins.kotlinSerialization)
  id("spotless-conventions")
}

kotlin {
  // Android and desktop share the Kotlin engine; the shared source set holds the hcert-core
  // adapter once instead of twice. iOS has targets only so the app's iOS build resolves this
  // module: the library has no Kotlin/Native build yet and no demo device is an iPhone, so the
  // iOS engine refuses to load (see src/iosMain).
  applyDefaultHierarchyTemplate {
    common {
      group("jvmShared") {
        withJvm()
        withAndroidTarget()
      }
    }
  }

  // The published fhir-model-r4 is Java 21 bytecode, so the tests need the same JDK the app uses.
  jvmToolchain(21)
  androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

  jvm()

  iosArm64()
  iosSimulatorArm64()

  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    browser {
      // The web engine is only real in a browser, so its test runs under headless Chrome.
      testTask { useKarma { useChromeHeadless() } }
    }
  }

  compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

  sourceSets {
    commonMain.dependencies {
      api(libs.ohs.fhir.model.r4)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.kotlinx.datetime)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.ktor.client.core)
      implementation(project.dependencies.platform(libs.kotlincrypto.hash.bom))
      implementation(libs.kotlincrypto.hash.sha2)
    }
    getByName("jvmSharedMain").dependencies { implementation(libs.hcert.core) }
    wasmJsMain.dependencies {
      // The Rust component transpiled by jco, plus a small glue module that turns its WIT surface
      // into the JSON strings the Kotlin side parses. Built by scripts/build-hcert-web.sh.
      implementation(
        npm("hcert-web", layout.projectDirectory.dir("src/wasmJsMain/npm/hcert-web").asFile)
      )
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }
    jvmTest.dependencies { implementation(libs.ktor.client.mock) }
  }
}

android {
  namespace = "dev.ohs.vaxlink.hcert"
  compileSdk = libs.versions.android.compileSdk.get().toInt()
  defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}
