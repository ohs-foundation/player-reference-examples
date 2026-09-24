import dev.ohs.build.AppConfig
import java.util.Properties

// Auth and deployment settings live in the root local.properties (git-ignored), with env-var
// overrides for CI. Read through the providers API so the configuration cache tracks them.
val localProperties: Map<String, String> =
  providers
    .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
    .asText
    .map { text ->
      val props = Properties().apply { load(text.reader()) }
      props.stringPropertyNames().associateWith(props::getProperty)
    }
    .getOrElse(emptyMap())

/*
 * Reads an environment variable, but yields a value only when it is non-blank. Blank or
 * whitespace-only vars are treated as absent, leaving the provider empty so the fallbacks below take
 * over. This stops an accidentally exported-but-empty var (e.g. `VERSION_NAME=`) slipping through.
 */
fun nonBlankEnv(name: String): Provider<String> =
  providers.environmentVariable(name).filter { it.isNotBlank() }

fun prop(key: String, default: String): String =
  nonBlankEnv(key).orNull ?: localProperties[key]?.takeIf { it.isNotBlank() } ?: default

// "0.0.0-dev" flags an accidental dev build and stops a silent "1.0" fallback on CI.
val resolvedVersionName: String =
  nonBlankEnv("VERSION_NAME").map { it.removePrefix("v") }.getOrElse("0.0.0-dev")

val resolvedVersionCode: Int =
  nonBlankEnv("VERSION_CODE")
    .map { raw -> raw.toIntOrNull() ?: error("VERSION_CODE='$raw' must be an integer") }
    .getOrElse(1)

/*
 * Example -- VERSION_NAME=v1.2.3-alpha.1:
 *   Android versionName   1.2.3-alpha.1   (prefix dropped, suffix kept)
 *   Desktop packageVersion 1.2.3          (prefix and suffix stripped)
 * The drift between platforms is intentional; jpackage rejects the suffix.
 */
val resolvedPackageVersion: String =
  nonBlankEnv("VERSION_NAME")
    .map { raw ->
      val numeric = raw.removePrefix("v").substringBefore('-')
      if (numeric.matches(Regex("""\d+\.\d+\.\d+"""))) {
        numeric
      } else {
        error("VERSION_NAME='$raw' is not MAJOR.MINOR.PATCH; cannot derive jpackage packageVersion")
      }
    }
    .getOrElse("1.0.0")

extensions.add(
  AppConfig::class.java,
  "appConfig",
  AppConfig(
    // Provider-agnostic: the app resolves authorization, token, userinfo and end-session endpoints
    // at runtime from {OAUTH_ISSUER}/.well-known/openid-configuration.
    issuer = prop("OAUTH_ISSUER", "https://medplum.player.ohs.dev/"),
    clientId = prop("OAUTH_CLIENT_ID", "ohs-medplum-eir"),
    redirectScheme = prop("OAUTH_REDIRECT_SCHEME", "dev.ohs.player.medplum"),
    redirectHost = prop("OAUTH_REDIRECT_HOST", "auth"),
    webRedirectUrl = prop("OAUTH_WEB_REDIRECT_URL", "http://localhost:8080/callback"),
    desktopRedirectPort = prop("OAUTH_DESKTOP_REDIRECT_PORT", "8765"),
    // offline_access is what yields a refresh token; without it every session ends when the access
    // token expires.
    scopes = prop("OAUTH_SCOPES", "openid profile email offline_access"),
    fhirBaseUrl = prop("FHIR_BASE_URL", "https://medplum.player.ohs.dev/fhir/R4/"),
    versionName = resolvedVersionName,
    versionCode = resolvedVersionCode,
    packageVersion = resolvedPackageVersion,
  ),
)
