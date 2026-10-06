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
package dev.ohs.workflow.examples.auth

import dev.ohs.fhir.model.r4.Organization
import dev.ohs.fhir.model.r4.PractitionerRole
import dev.ohs.workflow.examples.util.FhirJson
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads the signed-in user's Practitioner and PractitionerRoles from the gateway's
 * `/api/practitioner-details` endpoint, which resolves them from the bearer token.
 */
internal class PractitionerDetailsApi(
  private val baseUrl: String =
    GeneratedAuthConfig.FHIR_BASE_URL.removeSuffix("/").removeSuffix("fhir"),
  private val httpClient: HttpClient = OidcAuthApi.defaultHttpClient(),
) {
  private val json = FhirJson.instance

  /** The user's context, or null when the server knows no practitioner or app role for them. */
  suspend fun fetch(accessToken: String): UserContext? {
    val response =
      httpClient.get("${baseUrl.removeSuffix("/")}/api/practitioner-details") {
        bearerAuth(accessToken)
      }
    if (response.status == HttpStatusCode.NotFound) return null
    check(response.status.isSuccess()) { "Practitioner details failed: ${response.status}" }

    val details = json.parseToJsonElement(response.bodyAsText()).jsonObject
    val practitionerId =
      details["practitioner"]?.jsonObject?.get("id")?.jsonPrimitive?.content ?: return null
    val entries = details["practitionerRoles"]?.jsonArray.orEmpty().map { it.jsonObject }
    val roles =
      entries.mapNotNull { entry ->
        (entry["practitionerRole"] as? JsonObject)?.let {
          json.decodeFromJsonElement(PractitionerRole.serializer(), it)
        }
      }
    val organizations =
      entries.mapNotNull { entry ->
        (entry["organization"] as? JsonObject)?.let {
          json.decodeFromJsonElement(Organization.serializer(), it)
        }
      }
    return userContextOf(practitionerId, roles, organizations)
  }
}
