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

/** The WHO SMART Product Dataset for Prequalified Vaccines (smart-pcmt-vaxprequal). */
object PreQualTerminology {
  const val PRODUCT_IDS = "http://smart.who.int/pcmt-vaxprequal/CodeSystem/PreQualProductIds"
  const val VACCINE_TYPE = "http://smart.who.int/pcmt-vaxprequal/CodeSystem/PreQualVaccineType"
}

/** What the app knows about one PreQual product: enough to show a name and derive a type. */
data class PreQualProduct(
  val id: String,
  val display: String,
  /** The FVP-P product code, as the published CodeSystem shows it. */
  val productCode: String? = null,
  /** PreQualVaccineType code, when known; the product-to-type ConceptMap is not published yet. */
  val vaccineType: String? = null,
)

/**
 * Display names for PreQual product identifiers. The published CodeSystem's displays are the
 * product codes ("FVP-P-132 (a3K3X000006MiiXUAS)"), not names, so the demo products carry a local
 * display table that wins over the CodeSystem; everything else falls back to the CodeSystem, then
 * to the bare identifier. Codes are never translated here: this is display only.
 */
class PreQualProducts(private val products: Map<String, PreQualProduct>) {
  fun find(vp: String): PreQualProduct? = products[vp]

  fun display(vp: String): String = products[vp]?.display ?: vp

  fun vaccineType(vp: String): String? = products[vp]?.vaccineType

  fun plus(other: PreQualProducts): PreQualProducts = PreQualProducts(products + other.products)

  /**
   * Sets each product's vaccine type from a product-to-type ConceptMap: WHO's once it is published,
   * `ConceptMap/PreQualProductToTypeLocal` from the VaxLink IG until then. A product the map does
   * not mention keeps whatever type it had.
   */
  fun withTypesFrom(productToTypeConceptMapJson: String): PreQualProducts {
    val index = ConceptMapIndex.parse(productToTypeConceptMapJson)
    val typed =
      products.mapValues { (id, product) ->
        index.firstTarget(id)?.code?.let { product.copy(vaccineType = it) } ?: product
      }
    return PreQualProducts(typed)
  }

  companion object {
    /** The two products of the WHO worked example, named from the PreQual vaccine database. */
    val DEMO: PreQualProducts =
      PreQualProducts(
        listOf(
            PreQualProduct(
              id = "a3K3X000006MiiXUAS",
              display = "Hepatitis B Vaccine (rDNA) (Paediatric)",
              productCode = "FVP-P-132",
              vaccineType = "HepB",
            ),
            PreQualProduct(
              id = "a3K3X000006MiiCUAS",
              display = "BCG Vaccine",
              productCode = "FVP-P-73",
              vaccineType = "BCG",
            ),
          )
          .associateBy { it.id }
      )

    /**
     * Reads a FHIR CodeSystem JSON (PreQualProductIds) and overlays [overlay], so the demo names
     * stay while every other product at least resolves to its product code.
     */
    fun fromCodeSystem(codeSystemJson: String, overlay: PreQualProducts = DEMO): PreQualProducts {
      val document = Json.parseToJsonElement(codeSystemJson).jsonObject
      val url = document["url"]?.jsonPrimitive?.content
      require(url == PreQualTerminology.PRODUCT_IDS) {
        "Not the PreQualProductIds CodeSystem: $url"
      }
      val fromCodeSystem =
        document["concept"]?.jsonArray.orEmpty().associate { element ->
          val concept = element.jsonObject
          val code = concept["code"]!!.jsonPrimitive.content
          val display = concept["display"]?.jsonPrimitive?.content ?: code
          code to
            PreQualProduct(
              id = code,
              display = display,
              productCode = display.substringBefore(" ("),
            )
        }
      return PreQualProducts(fromCodeSystem).plus(overlay)
    }
  }
}
