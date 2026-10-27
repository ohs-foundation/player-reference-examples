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
package dev.ohs.player.medplum.feature.patient.profile

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A child's weight has to read as a weight.
 *
 * The arbitrary-precision decimal these values arrive as renders whole numbers in scientific
 * notation from `toString()`, so 14 kg reached the screen as "1.4E+1 kg". That is not a cosmetic
 * bug on a clinical screen — it is an unreadable measurement.
 */
class GrowthReadingFormatTest {

  @Test
  fun wholeNumbersAreNotRenderedInScientificNotation() {
    assertEquals("14", formatReading(14.toBigDecimal()))
    assertEquals("100", formatReading(100.toBigDecimal()))
  }

  @Test
  fun fractionalReadingsKeepTheirPrecision() {
    assertEquals("8.4", formatReading("8.4".toBigDecimal()))
    assertEquals("92.5", formatReading("92.5".toBigDecimal()))
  }

  @Test
  fun trailingZerosAreTrimmedSoAReadingDoesNotLookOverPrecise() {
    assertEquals("8.4", formatReading("8.400".toBigDecimal()))
    assertEquals("9", formatReading("9.0".toBigDecimal()))
  }
}
