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
package dev.ohs.workflow.examples.util

import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Reference
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** "Given Family" for list rows, or blank when the patient is unknown. */
fun Patient?.displayName(): String =
  this?.name?.firstOrNull()?.let { name ->
    (name.given.mapNotNull { it.value } + listOfNotNull(name.family?.value)).joinToString(" ")
  } ?: ""

/** The id in a relative reference of [type], e.g. "p1" from "Patient/p1"; null for any other. */
fun Reference?.idOf(type: String): String? =
  this?.reference?.value?.takeIf { it.startsWith("$type/") }?.removePrefix("$type/")

/** The calendar day of a FHIR date-time, e.g. "2026-10-05", or null when it is not that precise. */
fun FhirDateTime?.calendarDate(): String? =
  when (this) {
    is FhirDateTime.Date -> "$date"
    is FhirDateTime.DateTime -> "${dateTime.date}"
    else -> null
  }

/** The local wall-clock time of a FHIR date-time, e.g. "09:20", or null without a time part. */
fun FhirDateTime?.clockTime(timeZone: TimeZone): String? =
  (this as? FhirDateTime.DateTime)?.let {
    val local = it.dateTime.toInstant(it.utcOffset).toLocalDateTime(timeZone)
    "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
  }
