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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One source code's targets in a FHIR R4 ConceptMap. An entry whose only target has equivalence
 * `unmatched` is a deliberate statement that nothing corresponds, which is different from a code
 * the map never mentions.
 */
data class ConceptMapTarget(
  val code: String?,
  val display: String?,
  val equivalence: String,
  val comment: String? = null,
) {
  val unmatched: Boolean
    get() = equivalence == "unmatched" || equivalence == "disjoint"
}

/** A FHIR R4 ConceptMap read for lookups by source code, system-agnostic within one group. */
class ConceptMapIndex
private constructor(
  val url: String?,
  val sourceSystem: String?,
  val targetSystem: String?,
  private val elements: Map<String, List<ConceptMapTarget>>,
) {
  /** Every target the map lists for [sourceCode], empty when the map does not mention it. */
  fun targets(sourceCode: String): List<ConceptMapTarget> = elements[sourceCode].orEmpty()

  /** The first real target, or null when the code is absent or explicitly unmatched. */
  fun firstTarget(sourceCode: String): ConceptMapTarget? =
    targets(sourceCode).firstOrNull { !it.unmatched && it.code != null }

  /** True when the map says, in so many words, that nothing corresponds to [sourceCode]. */
  fun isUnmatched(sourceCode: String): Boolean =
    targets(sourceCode).let { it.isNotEmpty() && it.all { t -> t.unmatched } }

  fun contains(sourceCode: String): Boolean = sourceCode in elements

  companion object {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(conceptMapJson: String): ConceptMapIndex {
      val document = json.parseToJsonElement(conceptMapJson).jsonObject
      require(document["resourceType"]?.jsonPrimitive?.content == "ConceptMap") {
        "Not a ConceptMap: ${document["resourceType"]}"
      }
      val groups = document["group"]?.jsonArray.orEmpty().map { it.jsonObject }
      val elements = mutableMapOf<String, List<ConceptMapTarget>>()
      for (group in groups) {
        for (element in group["element"]?.jsonArray.orEmpty()) {
          val obj = element.jsonObject
          val code = obj["code"]?.jsonPrimitive?.content ?: continue
          val targets =
            obj["target"]?.jsonArray.orEmpty().map { t ->
              val target = t.jsonObject
              ConceptMapTarget(
                code = target["code"]?.jsonPrimitive?.content,
                display = target["display"]?.jsonPrimitive?.content,
                equivalence = target["equivalence"]?.jsonPrimitive?.content ?: "equivalent",
                comment = target["comment"]?.jsonPrimitive?.content,
              )
            }
          elements[code] = elements[code].orEmpty() + targets
        }
      }
      val first = groups.firstOrNull()
      return ConceptMapIndex(
        url = document["url"]?.jsonPrimitive?.content,
        sourceSystem = first?.get("source")?.jsonPrimitive?.content,
        targetSystem = first?.get("target")?.jsonPrimitive?.content,
        elements = elements,
      )
    }
  }
}

/** What the national schedule says about a vaccine type read from a foreign certificate. */
sealed interface DoseProposal {
  /** The map names a Lao dose: the app may add the coding automatically, recorded as automatic. */
  data class Mapped(val code: String, val display: String?, val comment: String?) : DoseProposal

  /** The map says nothing corresponds: the clinician decides. The governance moment (BCG). */
  data class Unmatched(val comment: String?) : DoseProposal

  /** The map does not mention the type at all. */
  data object Unknown : DoseProposal
}

/**
 * The national type-to-dose map (`ConceptMap/PreQualTypeToLaoDose` in the VaxLink IG). Deliberately
 * partial: the gaps are the point. The dose number of a later dose is the schedule logic's job
 * (NE-3), not this map's.
 */
class LaoDoseMap(private val index: ConceptMapIndex) {
  val targetSystem: String?
    get() = index.targetSystem

  fun propose(vaccineType: String?): DoseProposal {
    if (vaccineType == null) return DoseProposal.Unknown
    index.firstTarget(vaccineType)?.let {
      return DoseProposal.Mapped(it.code!!, it.display, it.comment)
    }
    if (index.isUnmatched(vaccineType)) {
      return DoseProposal.Unmatched(index.targets(vaccineType).firstOrNull()?.comment)
    }
    return DoseProposal.Unknown
  }

  companion object {
    fun parse(conceptMapJson: String) = LaoDoseMap(ConceptMapIndex.parse(conceptMapJson))
  }
}
