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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * The WHO worked example (smart-trust qr_code_generation.md section 12) and the GDHCN DEV trust
 * list documents, copied from litlfred/smart-trust-tools `vectors/` on 29 Oct 2026. TEST DATA ONLY:
 * issued by DEV participant XXR (iss XR), kid 916ffa567c0f44a2.
 */
object Vectors {
  const val XXR_KID = "916ffa567c0f44a2"

  fun resource(path: String): String =
    checkNotNull(Vectors::class.java.getResource(path)) { "missing test resource $path" }.readText()

  /** The XXR DSC's own DID document, one verification method. */
  val xxrDscDidDocument: String by lazy { resource("/vectors/XXR-DSC-did.json") }

  /** The whole DEV trust list, embedded variant, 234 keys, as fetched on 23 Oct 2026. */
  val devTrustList: String by lazy { resource("/vectors/root-did.json") }

  private val xxr: JsonObject by lazy {
    Json.parseToJsonElement(resource("/vectors/dvc-qr-example-XXR.json")).jsonObject
  }

  class Certificate(val name: String, obj: JsonObject) {
    val hc1: String = obj["hc1"]!!.jsonPrimitive.content
    val iat: Long = obj["iat"]!!.jsonPrimitive.long
    val exp: Long = obj["exp"]!!.jsonPrimitive.long
    val payload: DvcMin = Json.decodeFromJsonElement(DvcMin.serializer(), obj["payload"]!!)
  }

  val hepb: Certificate by lazy {
    Certificate("hepb1", xxr["certificates"]!!.jsonObject["hepb1"]!!.jsonObject)
  }
  val bcg: Certificate by lazy {
    Certificate("bcg", xxr["certificates"]!!.jsonObject["bcg"]!!.jsonObject)
  }
  val both: List<Certificate> by lazy { listOf(hepb, bcg) }
}
