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
package dev.ohs.player.medplum.data.repository

import dev.ohs.fhir.model.r4.Immunization
import dev.ohs.player.generated.state.PatientImmunizationRecommendationState
import dev.ohs.player.medplum.util.FhirJson
import kotlin.time.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Records an administered dose.
 *
 * The resource is built as JSON and decoded, rather than assembled from the model's wrapper types
 * (`Enumeration<ImmunizationStatusCodes>`, `Occurrence.DateTime`, ...). The JSON is the FHIR wire
 * format either way, it reads like the spec, and the serializer validates it on the way in -- a
 * typo fails here rather than at upload time.
 *
 * Everything identifying the dose is copied VERBATIM from the forecast the server produced: vaccine
 * code, system, series and dose number. The device invents nothing clinical; it only supplies the
 * timestamp.
 */
class ImmunizationWriter(private val fhirRepository: FhirRepository) {

  companion object {
    /**
     * Seconds-precision instant, deliberately WITHOUT milliseconds.
     *
     * The engine's ResourceIndexer calls `second.intValue(exactRequired = true)` when indexing a
     * dateTime, which throws on a fractional seconds value. Writing `...:12.345Z` therefore CRASHES
     * the app on insert (ArithmeticException inside DatabaseImpl.insert), and the same defect
     * silently prevents any synced resource with sub-second precision from being stored. Verified
     * on-device.
     */
    internal fun isoSeconds(): String =
      Clock.System.now().toString().replace(Regex("\\.\\d+(?=Z$)"), "")
  }

  suspend fun recordAdministered(
    patientId: String,
    dose: PatientImmunizationRecommendationState,
    now: String = isoSeconds(),
  ) {
    val json = buildJsonObject {
      put("resourceType", "Immunization")
      put("status", "completed")
      putJsonObject("vaccineCode") {
        putJsonArray("coding") {
          add(
            buildJsonObject {
              dose.vaccineSystem?.let { put("system", it) }
              dose.vaccineCode?.let { put("code", it) }
              dose.vaccineDisplay?.let { put("display", it) }
            }
          )
        }
        dose.doseLabel?.let { put("text", it) }
      }
      putJsonObject("patient") { put("reference", "Patient/$patientId") }
      put("occurrenceDateTime", now)
      putJsonArray("protocolApplied") {
        add(
          buildJsonObject {
            dose.series?.let { put("series", it) }
            dose.doseNumber?.let { put("doseNumberPositiveInt", it) }
          }
        )
      }
    }
    // No id: with UploadStrategy.forIndividualRequest(POST, ...) the SERVER assigns it.
    // Medplum reports updateCreate=false, so a client-assigned id would be rejected anyway.
    fhirRepository.upsert(FhirJson.instance.decodeFromJsonElement(Immunization.serializer(), json))
  }
}
