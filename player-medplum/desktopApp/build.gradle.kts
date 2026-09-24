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
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.composeHotReload)
  id("app-config-conventions")
  id("spotless-conventions")
}

val appConfig = extensions.getByType<AppConfig>()

dependencies {
  implementation(project(":player-medplum-app"))
  implementation(compose.desktop.currentOs)
  implementation(libs.kotlinx.coroutinesSwing)
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) } }

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

compose.desktop {
  application {
    // src/main/kotlin/dev/ohs/player/medplum/main.kt -> MainKt. The window itself is built by
    // launchDesktopApp() in :player-medplum-app.
    mainClass = "dev.ohs.player.medplum.MainKt"

    nativeDistributions {
      targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)

      /*
       * jlink trims the bundled runtime to the modules named here, and KSafe needs
       * `sun.misc.Unsafe` from jdk.unsupported. Without it the packaged app silently falls back to
       * a software-encrypted JSON file instead of Jetpack DataStore plus OS key custody -- the
       * session token still persists, just with materially weaker protection, and only a runtime
       * NOTICE on stdout says so. `./gradlew :desktopApp:run` never shows this because it uses the
       * full JDK; only the distributable is affected.
       */
      modules("jdk.unsupported")

      packageName = "OHS Medplum EIR"
      packageVersion = appConfig.packageVersion

      val iconsDir = project.layout.projectDirectory.dir("desktop-icons")
      macOS { iconFile.set(iconsDir.file("app-icon.icns")) }
      windows { iconFile.set(iconsDir.file("app-icon.ico")) }
      // jpackage rejects spaces and capitals in a .deb/.rpm package name, so Linux gets its own.
      linux {
        packageName = "ohs-medplum-eir"
        iconFile.set(iconsDir.file("app-icon.png"))
      }
    }
  }
}
