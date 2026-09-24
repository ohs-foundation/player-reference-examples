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
package dev.ohs.build

/**
 * Build-time app configuration, resolved once per project by the `app-config-conventions` plugin
 * from env vars, then the root `local.properties`, then a default.
 *
 * Three modules read this same object, which is the point: `:player-medplum-app` bakes the OAuth
 * values into `GeneratedAuthConfig`, `:androidApp` feeds [redirectScheme]/[redirectHost] into the
 * `LoginRedirectActivity` intent-filter as manifest placeholders, and `:desktopApp` uses
 * [packageVersion] for jpackage. Resolving them separately per module is how the manifest and the
 * baked-in config silently drift apart.
 */
class AppConfig(
  val issuer: String,
  val clientId: String,
  val redirectScheme: String,
  val redirectHost: String,
  val webRedirectUrl: String,
  val desktopRedirectPort: String,
  val scopes: String,
  val fhirBaseUrl: String,
  val versionName: String,
  val versionCode: Int,
  /** jpackage and WiX/MSI demand a strict MAJOR.MINOR.PATCH, so any pre-release suffix is stripped. */
  val packageVersion: String,
)
