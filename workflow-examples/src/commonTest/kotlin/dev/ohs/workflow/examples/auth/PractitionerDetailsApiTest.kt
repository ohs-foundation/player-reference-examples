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

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class PractitionerDetailsApiTest {
  private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
  private val requests = mutableListOf<Pair<String, String?>>()

  private fun apiResponding(status: HttpStatusCode, body: String = "") =
    PractitionerDetailsApi(
      baseUrl = "https://gateway.example.org/",
      httpClient =
        HttpClient(
          MockEngine { request ->
            requests += request.url.toString() to request.headers[HttpHeaders.Authorization]
            respond(body, status, jsonHeaders)
          }
        ),
    )

  @Test
  fun mapsTheCallersPractitionerDetailsToAUserContext() = runTest {
    val api = apiResponding(HttpStatusCode.OK, DETAILS)

    val context = api.fetch("token-1")

    assertEquals(UserContext(AppRole.CHW, "p1", "o1", "l1", facilityOrganizationId = "f1"), context)
    assertEquals(
      "https://gateway.example.org/api/practitioner-details" to "Bearer token-1",
      requests.single(),
    )
  }

  @Test
  fun unknownUserHasNoContext() = runTest {
    assertNull(apiResponding(HttpStatusCode.NotFound).fetch("token-1"))
  }

  @Test
  fun serverErrorFails() = runTest {
    assertFailsWith<IllegalStateException> {
      apiResponding(HttpStatusCode.InternalServerError).fetch("token-1")
    }
  }

  private companion object {
    const val DETAILS =
      """
      {
        "practitioner": { "resourceType": "Practitioner", "id": "p1" },
        "practitionerRoles": [
          {
            "practitionerRole": {
              "resourceType": "PractitionerRole",
              "id": "r1",
              "code": [ { "coding": [ { "system": "http://ohs.dev/fhir/CodeSystem/app-role", "code": "chw" } ] } ],
              "organization": { "reference": "Organization/o1" },
              "location": [ { "reference": "Location/l1" } ]
            },
            "organization": {
              "resourceType": "Organization",
              "id": "o1",
              "partOf": { "reference": "Organization/f1" }
            },
            "locations": [],
            "careTeams": []
          }
        ]
      }
      """
  }
}
