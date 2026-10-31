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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The three terminology layers on the VaxLink IG's generated ConceptMaps: HepB maps, BCG asks. */
class ConceptMapsTest {
  private val typeToDose = Vectors.resource("/terminology/ConceptMap-PreQualTypeToLaoDose.json")
  private val productToType =
    Vectors.resource("/terminology/ConceptMap-PreQualProductToTypeLocal.json")

  @Test
  fun `the two demo products resolve to their vaccine type through the local map`() {
    val products =
      PreQualProducts.fromCodeSystem(
          Vectors.resource("/terminology/CodeSystem-PreQualProductIds.json"),
          overlay = PreQualProducts(emptyMap()),
        )
        .withTypesFrom(productToType)
    assertEquals("HepB", products.vaccineType("a3K3X000006MiiXUAS"))
    assertEquals("BCG", products.vaccineType("a3K3X000006MiiCUAS"))
    assertNull(
      products.vaccineType("a3K3X000005atRtUAI"),
      "a product the map does not mention keeps no type",
    )
  }

  @Test
  fun `the HepB birth dose maps to the Lao birth dose, so no prompt`() {
    val map = LaoDoseMap.parse(typeToDose)
    val proposal = assertIs<DoseProposal.Mapped>(map.propose("HepB"))
    assertEquals("HB0-24H", proposal.code)
    assertEquals("HB0 <24 Hours", proposal.display)
    assertTrue(map.targetSystem!!.endsWith("/CodeSystem/lao-epi-schedule-dose"))
  }

  @Test
  fun `BCG is explicitly unmatched, so the clinician decides`() {
    val proposal = assertIs<DoseProposal.Unmatched>(LaoDoseMap.parse(typeToDose).propose("BCG"))
    assertTrue(proposal.comment.orEmpty().contains("Not on the Lao PDR 2026 schedule"))
  }

  @Test
  fun `a type the map never mentions is unknown, not unmatched`() {
    val map = LaoDoseMap.parse(typeToDose)
    assertEquals(DoseProposal.Unknown, map.propose("YF"))
    assertEquals(DoseProposal.Unknown, map.propose(null))
    assertIs<DoseProposal.Mapped>(map.propose("DTwPHepBHib")).let {
      assertEquals("PENTA-1", it.code)
    }
  }

  @Test
  fun `the whole chain from a scanned product to a proposed dose`() {
    val products = PreQualProducts.DEMO.withTypesFrom(productToType)
    val doses = LaoDoseMap.parse(typeToDose)
    assertIs<DoseProposal.Mapped>(doses.propose(products.vaccineType(Vectors.hepb.payload.vx.vp)))
    assertIs<DoseProposal.Unmatched>(doses.propose(products.vaccineType(Vectors.bcg.payload.vx.vp)))
  }
}
