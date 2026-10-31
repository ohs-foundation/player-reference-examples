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
package dev.ohs.vaxlink.hcert

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** J4.1 on the JVM engine: the pure VHL steps around the HTTP the viewer does (NE-4 Part C). */
class VhlTest {
  private val engine = runBlocking { loadHcertEngine() }

  /** The IHE ITI-YY3 example payload. */
  private val iheLink =
    VhlPayload(
      url =
        "https://vhl-sharer.example.org/List/_search?_id=abc123def456&code=folder&status=current&patient.identifier=urn:oid:2.16.840.1.113883.2.4.6.3|PASSPORT123&_include=List:item",
      key = "86F8LY5LlWAa1-OS_FgrTnYNqFHJP2ey5RSKLJBN9jk",
      exp = 1735689600,
      flag = "LP",
      label = "Patient Health Summary",
      v = 1,
      fhirBaseUrl = "https://vhl-sharer.example.org",
    )

  @Test
  fun `the manifest request is the IHE example's POST, with the passcode the father gives`() {
    val request = engine.buildManifestRequest(iheLink, "Dr. Smith Hospital", "user-pin")
    assertEquals("https://vhl-sharer.example.org/List/_search", request.url)
    assertEquals("application/x-www-form-urlencoded", request.contentType)
    assertEquals(
      "_id=abc123def456&code=folder&status=current&patient.identifier=urn%3Aoid%3A2.16.840.1.113883.2.4.6.3%7CPASSPORT123&_include=List%3Aitem&recipient=Dr.+Smith+Hospital&passcode=user-pin",
      request.body,
    )
  }

  @Test
  fun `a passcode-protected link without a passcode is refused before any request is made`() {
    assertFailsWith<VhlException> { engine.buildManifestRequest(iheLink, "Clinic B", null) }
  }

  @Test
  fun `the IHE Case B manifest lists four documents`() {
    val documents = engine.parseManifest(Vectors.resource("/vectors/iti-yy5-manifest-case-b.json"))
    assertEquals(4, documents.size)
    assertEquals("https://vhl-sharer.example.org/Binary/doc001-content", documents[0].url)
    assertEquals("application/pdf", documents[0].contentType)
    assertEquals(
      listOf("DocumentReference/doc002", "DocumentReference/doc003", "DocumentReference/doc004"),
      documents.drop(1).map { it.reference },
    )
    assertFailsWith<VhlException> {
      engine.parseManifest("""{"resourceType":"Bundle","type":"document"}""")
    }
  }

  @Test
  fun `a fetched document decrypts with the link key and not with another`() {
    val fixture =
      Json.parseToJsonElement(Vectors.resource("/vectors/jwe-dir-a256gcm.json")).jsonObject
    val key = fixture["key"]!!.jsonPrimitive.content
    for (variant in listOf("jwe", "jweZipDef")) {
      val document = engine.decrypt(fixture[variant]!!.jsonPrimitive.content, key)
      assertEquals(fixture["contentType"]!!.jsonPrimitive.content, document.contentType, variant)
      assertEquals(fixture["plaintext"]!!.jsonPrimitive.content, document.text, variant)
    }
    val wrongKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    assertFailsWith<VhlException> {
      engine.decrypt(fixture["jwe"]!!.jsonPrimitive.content, wrongKey)
    }
  }
}
