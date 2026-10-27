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
// The root project name is half the Compose Resources package
// (player_medplum.player_medplum_app.generated.resources) -- renaming it moves every `Res` import.
rootProject.name = "player-medplum"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Standalone build that provides the dev.ohs.ig-codegen plugin.
includeBuild("ig-codegen")

pluginManagement {
  includeBuild("build-logic")
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral()
    gradlePluginPortal()
    mavenLocal()
  }
}

dependencyResolutionManagement {
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral()
    // Not load-bearing today -- every dev.ohs.* artifact resolves from Maven Central. Kept so a
    // locally built fhir-engine / player-client can be dropped in without editing this file.
    mavenLocal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

// The shared library: all Kotlin, all Compose resources, ig-codegen. The other three are packaging.
include(":player-medplum-app")

include(":androidApp")

include(":desktopApp")

include(":webApp")
