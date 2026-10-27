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
package dev.ohs.player.medplum.feature.patient.list

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class PatientCardTest {

  private val today = LocalDate(2026, 6, 21)

  @Test
  fun calculateAge_subtractsOne_whenBirthdayNotYetReached() {
    assertEquals("39y", calculateAge("1986-12-25", today))
  }

  @Test
  fun calculateAge_full_whenBirthdayPassed() {
    assertEquals("40y", calculateAge("1986-01-10", today))
  }

  @Test
  fun calculateAge_full_onBirthday() {
    assertEquals("40y", calculateAge("1986-06-21", today))
  }

  // This is a child immunization register: the whole KEPI schedule is measured in weeks for
  // the first months, so reporting "0" for every infant would hide the only distinction that
  // matters on this screen.
  @Test
  fun calculateAge_reportsWeeks_forNewborns() {
    assertEquals("0w", calculateAge("2026-06-18", today))
    assertEquals("6w", calculateAge("2026-05-10", today))
  }

  @Test
  fun calculateAge_reportsMonths_fromTwoMonthsToTwoYears() {
    assertEquals("3mo", calculateAge("2026-03-21", today))
    assertEquals("18mo", calculateAge("2024-12-21", today))
  }

  @Test
  fun calculateAge_returnsNull_forNullOrMalformed() {
    assertNull(calculateAge(null, today))
    assertNull(calculateAge("not-a-date", today))
    assertNull(calculateAge("1986", today))
  }
}
