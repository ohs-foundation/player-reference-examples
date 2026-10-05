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
package dev.ohs.workflow.examples.auth

import dev.ohs.fhir.model.r4.PractitionerRole
import dev.ohs.fhir.model.r4.Reference
import kotlinx.serialization.Serializable

const val APP_ROLE_SYSTEM = "http://ohs.dev/fhir/CodeSystem/app-role"

@Serializable
enum class AppRole(val code: String) {
  CHW("chw"),
  NURSE("nurse"),
  CLINICIAN("clinician"),
}

/** Who the signed-in user is in FHIR terms: the role and assignment that scope what they see. */
@Serializable
data class UserContext(
  val role: AppRole,
  val practitionerId: String,
  val organizationId: String,
  val locationId: String?,
)

/** The context from the first [PractitionerRole] that carries a known app role and organization. */
fun userContextOf(practitionerId: String, roles: List<PractitionerRole>): UserContext? =
  roles.firstNotNullOfOrNull { role ->
    val appRole =
      role.code
        .flatMap { it.coding }
        .filter { it.system?.value == APP_ROLE_SYSTEM }
        .firstNotNullOfOrNull { coding ->
          AppRole.entries.firstOrNull { it.code == coding.code?.value }
        } ?: return@firstNotNullOfOrNull null
    val organizationId = role.organization.idOf("Organization") ?: return@firstNotNullOfOrNull null
    UserContext(
      appRole,
      practitionerId,
      organizationId,
      role.location.firstOrNull().idOf("Location"),
    )
  }

private fun Reference?.idOf(type: String): String? =
  this?.reference?.value?.removePrefix("$type/")?.takeIf { it.isNotBlank() && '/' !in it }
