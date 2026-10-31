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
import kotlin.test.assertNull

/**
 * Display names for scanned products: the demo table, then the published CodeSystem, then the code.
 */
class PreQualProductsTest {
  private val codeSystem = Vectors.resource("/terminology/CodeSystem-PreQualProductIds.json")

  @Test
  fun `the demo products show a vaccine name and a type`() {
    assertEquals(
      "Hepatitis B Vaccine (rDNA) (Paediatric)",
      PreQualProducts.DEMO.display("a3K3X000006MiiXUAS"),
    )
    assertEquals("HepB", PreQualProducts.DEMO.vaccineType("a3K3X000006MiiXUAS"))
    assertEquals("BCG Vaccine", PreQualProducts.DEMO.display("a3K3X000006MiiCUAS"))
    assertEquals("BCG", PreQualProducts.DEMO.vaccineType("a3K3X000006MiiCUAS"))
  }

  @Test
  fun `the published CodeSystem resolves every product to at least its product code`() {
    val products = PreQualProducts.fromCodeSystem(codeSystem)
    val other = products.find("a3K3X000005atRtUAI")
    assertEquals("FVP-P-447", other?.productCode)
    assertEquals("FVP-P-447 (a3K3X000005atRtUAI)", other?.display)
    assertNull(other?.vaccineType, "no product-to-type ConceptMap is published yet")
    // The demo overlay wins over the CodeSystem's code-shaped display.
    assertEquals("BCG Vaccine", products.display("a3K3X000006MiiCUAS"))
    assertEquals("FVP-P-73", products.find("a3K3X000006MiiCUAS")?.productCode)
  }

  @Test
  fun `an unknown code is shown as itself and never translated`() {
    assertEquals("XM9V38", PreQualProducts.DEMO.display("XM9V38"))
    assertNull(PreQualProducts.DEMO.find("XM9V38"))
  }

  @Test
  fun `another CodeSystem is refused`() {
    assertFailsWith<IllegalArgumentException> {
      PreQualProducts.fromCodeSystem(
        Vectors.resource("/terminology/CodeSystem-PreQualVaccineType.json")
      )
    }
  }
}
