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
package dev.ohs.player.medplum.feature.questionnaire

import dev.ohs.fhir.datacapture.extraction.template.TemplateExtractionEngine
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Immunization
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.Questionnaire as QuestionnaireR4
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.player.medplum.data.repository.FhirRepository
import dev.ohs.player.medplum.util.FhirJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.ExperimentalResourceApi
import player_medplum.player_medplum_app.generated.resources.Res

/**
 * Identifiers describing why/for-whom a questionnaire was launched.
 *
 * [patientId] is a caller choice (which child is this about). [practitionerId] and [organizationId]
 * are session facts resolved from the signed-in user, not route arguments — see
 * [dev.ohs.player.medplum.feature.questionnaire.QuestionnaireHostViewModel]. Any of them may be
 * absent, and an absent value removes what depended on it rather than writing a broken reference.
 */
data class QuestionnaireLaunchContext(
  val patientId: String? = null,
  val practitionerId: String? = null,
  val organizationId: String? = null,
)

/** Outcome of submitting a QuestionnaireResponse, ready for the UI to render. */
data class QuestionnaireSubmissionResult(
  val savedResourceCount: Int,
  val bundleJson: String,
  val successMessage: String,
)

/** Canonical Questionnaire.id values this app can launch. */
object QuestionnaireIds {
  const val CHILD_REGISTRATION = "child-registration"
  const val GROWTH_MONITORING = "growth-monitoring"
  const val ADVERSE_EVENT = "adverse-event"
}

/**
 * The AEFI form's "which vaccine" question.
 *
 * Its answers cannot be written into the bundled JSON: they are the doses THIS child actually
 * received, so they are injected at launch by [QuestionnaireService.prepareForLaunch]. The option
 * code is the Immunization's id, which is what lets the extraction template point
 * `AdverseEvent.suspectEntity.instance` at the real dose rather than at a vaccine name.
 */
internal const val AEFI_VACCINE_LINK_ID = "aefi-vaccine"

/** Bundled Questionnaire JSON, keyed by the id it should be read under. */
private val BUNDLED_QUESTIONNAIRE_PATHS: Map<String, String> =
  mapOf(
    QuestionnaireIds.CHILD_REGISTRATION to "files/configs/Questionnaire-ChildRegistration.json",
    QuestionnaireIds.GROWTH_MONITORING to "files/configs/Questionnaire-GrowthMonitoring.json",
    QuestionnaireIds.ADVERSE_EVENT to "files/configs/Questionnaire-AdverseEvent.json",
  )

/**
 * Reads bundled FHIR Questionnaires and persists what SDC template extraction produces from them.
 *
 * Nothing here stamps the facility. The practitioner's Medplum AccessPolicy carries a
 * `compartment`, so the server assigns the facility account on create — the app posts plain FHIR.
 * That is deliberate: `meta.account` is a Medplum extension that the FHIR model used here cannot
 * even represent, so trying to scope from the client would silently drop the field and 403.
 */
class QuestionnaireService(private val repository: FhirRepository) {

  private val fhirJson = FhirJson.instance

  /** Launch-context values a questionnaire can opt into prepopulating, by linkId. */
  private val launchContextLinkIds: Map<String, (QuestionnaireLaunchContext) -> String?> =
    mapOf("patient-id" to { context -> context.patientId })

  /**
   * Raw string placeholders that extraction templates may embed directly (e.g. inside a FHIRPath
   * reference like "Patient/__PATIENT_ID__"). These are substituted on the encoded JSON text, since
   * they aren't tied to any particular questionnaire item/linkId.
   */
  private val launchContextPlaceholders: Map<String, (QuestionnaireLaunchContext) -> String?> =
    mapOf(
      "__PATIENT_ID__" to { context -> context.patientId.orNull() },
      "__PRACTITIONER_ID__" to { context -> context.practitionerId.orNull() },
      "__ORGANIZATION_ID__" to { context -> context.organizationId.orNull() },
    )

  /**
   * The child's recorded doses, newest first, as answer options for the AEFI form.
   *
   * The option's `code` is the Immunization id so the template can build a real reference; the
   * `display` is what the health worker reads, so it carries the date as well as the vaccine --
   * "Penta 2" alone is ambiguous once a child has been coming for two years.
   */
  private suspend fun dosesFor(patientId: String): List<JsonObject> =
    repository
      .referencing("Immunization", "patient", "Patient/$patientId")
      .filterIsInstance<Immunization>()
      .sortedByDescending { it.givenOn().orEmpty() }
      .mapNotNull { immunization ->
        val id = immunization.id ?: return@mapNotNull null
        val vaccine =
          immunization.vaccineCode.text?.value
            ?: immunization.vaccineCode.coding.firstOrNull()?.display?.value
            ?: return@mapNotNull null
        val given = immunization.givenOn()?.substringBefore('T')
        JsonObject(
          mapOf(
            "valueCoding" to
              JsonObject(
                mapOf(
                  // No `system`: the code is this server's Immunization id, which belongs to no
                  // code system. Coding.code without a system is valid R4, and the answer never
                  // leaves the QuestionnaireResponse -- extraction turns it into a reference.
                  "code" to JsonPrimitive(id),
                  "display" to JsonPrimitive(if (given == null) vaccine else "$vaccine — $given"),
                )
              )
          )
        )
      }

  /** Reads a Questionnaire from the bundled config files. */
  @OptIn(ExperimentalResourceApi::class)
  suspend fun getQuestionnaire(id: String): QuestionnaireR4 {
    val path =
      BUNDLED_QUESTIONNAIRE_PATHS[id]
        ?: error("Questionnaire '$id' was not found in the app config.")
    val json = Res.readBytes(path).decodeToString()
    return fhirJson.decodeFromString(QuestionnaireR4.serializer(), json).copy(id = id)
  }

  suspend fun prepareForLaunch(
    questionnaire: QuestionnaireR4,
    launchContext: QuestionnaireLaunchContext,
  ): String {
    val questionnaireObject =
      fhirJson.decodeFromString(
        JsonObject.serializer(),
        fhirJson.encodeToString(QuestionnaireR4.serializer(), questionnaire),
      )

    val prepared =
      launchContextLinkIds.entries.fold(questionnaireObject) { current, (linkId, resolve) ->
        val value = resolve(launchContext) ?: return@fold current
        current.withInitialStringAnswer(linkId, value).first
      }

    val withVaccines =
      launchContext.patientId?.let { patientId ->
        prepared.withAnswerOptions(AEFI_VACCINE_LINK_ID, dosesFor(patientId))
      } ?: prepared

    val preparedJson = fhirJson.encodeToString(JsonObject.serializer(), withVaccines)

    val substituted =
      launchContextPlaceholders.entries.fold(preparedJson) { current, (placeholder, resolve) ->
        val value = resolve(launchContext) ?: return@fold current
        current.replace(placeholder, value)
      }

    val resolved =
      fhirJson
        .decodeFromString(JsonObject.serializer(), substituted)
        .withoutUnresolvedPlaceholders(launchContextPlaceholders.keys) as? JsonObject
        ?: error("Questionnaire '${questionnaire.id}' resolved to nothing.")

    return fhirJson.encodeToString(JsonObject.serializer(), resolved)
  }

  /**
   * Extracts and persists a completed form.
   *
   * [preparedQuestionnaireJson] must be the output of [prepareForLaunch], NOT the questionnaire as
   * loaded. Extraction has to run against the same questionnaire the health worker filled in: the
   * launch context is baked in by substitution, so extracting from the unprepared copy writes the
   * literal placeholder (`Patient/__PATIENT_ID__`) as the subject and the record attaches to
   * nobody. Nothing errors when that happens — the Bundle is produced and saved either way.
   */
  suspend fun submit(
    preparedQuestionnaireJson: String,
    response: QuestionnaireResponse,
  ): QuestionnaireSubmissionResult {
    val questionnaire =
      fhirJson.decodeFromString(QuestionnaireR4.serializer(), preparedQuestionnaireJson)
    val bundle = withoutEmptyObservations(TemplateExtractionEngine.extract(questionnaire, response))
    val savedResourceCount = repository.upsert(bundle)
    return QuestionnaireSubmissionResult(
      savedResourceCount = savedResourceCount,
      bundleJson = fhirJson.encodeToString(Bundle.serializer(), bundle),
      successMessage = successMessage(questionnaire.id, savedResourceCount),
    )
  }

  /**
   * Drops Observations that carry no reading.
   *
   * SDC template extraction clones one resource per template regardless of whether its value
   * expression resolved, so a visit where only the weight was taken still yields a height and a
   * MUAC Observation with a null value. An Observation that records nothing is worse than no
   * Observation: it reads as a measurement that was taken and came back blank.
   */
  private fun withoutEmptyObservations(bundle: Bundle): Bundle =
    bundle.copy(
      entry =
        bundle.entry.filter { entry ->
          val observation = entry.resource as? Observation ?: return@filter true
          (observation.value as? Observation.Value.Quantity)?.value?.value != null
        }
    )

  private fun successMessage(questionnaireId: String?, savedResourceCount: Int): String =
    when {
      savedResourceCount == 0 -> "Nothing was recorded. Fill in at least one measurement."
      questionnaireId == QuestionnaireIds.CHILD_REGISTRATION ->
        "Child registered. They will appear in the register and sync to the facility."
      questionnaireId == QuestionnaireIds.GROWTH_MONITORING ->
        "Recorded $savedResourceCount measurement(s)."
      else -> "Saved $savedResourceCount resource(s)."
    }
}

private fun JsonObject.withInitialStringAnswer(
  linkId: String,
  value: String,
): Pair<JsonObject, Boolean> {
  var updated = false
  val mutableNode = toMutableMap()

  if (this["linkId"]?.jsonPrimitive?.contentOrNull == linkId) {
    mutableNode["initial"] =
      JsonArray(listOf(JsonObject(mapOf("valueString" to JsonPrimitive(value)))))
    updated = true
  }

  this["item"]
    ?.jsonArray
    ?.map { element ->
      val (updatedItem, itemUpdated) = element.jsonObject.withInitialStringAnswer(linkId, value)
      if (itemUpdated) updated = true
      updatedItem
    }
    ?.let { mutableNode["item"] = JsonArray(it) }

  return JsonObject(mutableNode) to updated
}

/**
 * `occurrenceDateTime` as a plain ISO string.
 *
 * The model's FhirDateTime is not Comparable and carries no string accessor, but its toString() is
 * the FHIR wire format -- and ISO-8601 sorts correctly as text, so it doubles as the sort key.
 */
private fun Immunization.givenOn(): String? =
  (occurrence as? Immunization.Occurrence.DateTime)?.value?.value?.toString()

/**
 * Replaces the `answerOption` list of the item with [linkId], recursively.
 *
 * No-op when [options] is empty -- a choice question with zero options renders as an unusable
 * blank, so the caller is expected to keep the form out of reach in that case rather than rely on
 * this to degrade gracefully.
 */
private fun JsonObject.withAnswerOptions(linkId: String, options: List<JsonObject>): JsonObject {
  if (options.isEmpty()) return this
  val node = toMutableMap()
  if (this["linkId"]?.jsonPrimitive?.contentOrNull == linkId) {
    node["answerOption"] = JsonArray(options)
  }
  this["item"]
    ?.jsonArray
    ?.map { it.jsonObject.withAnswerOptions(linkId, options) }
    ?.let { node["item"] = JsonArray(it) }
  return JsonObject(node)
}

private fun String?.orNull(): String? = this?.takeIf { it.isNotBlank() }

/**
 * Removes anything still carrying an unsubstituted launch-context placeholder.
 *
 * A placeholder survives substitution only when the launch context could not supply a value — no
 * practitioner on the session, or no facility synced down yet. Writing it through produces a
 * reference to a resource that does not exist (`Practitioner/__PRACTITIONER_ID__`): structurally
 * valid FHIR, accepted by the server, and silently wrong. That is precisely the failure that once
 * attached every growth measurement to `Patient/__PATIENT_ID__`, so the unresolved element is
 * dropped instead.
 *
 * An object that loses every one of its fields this way is itself dropped, which is what turns an
 * unresolved `managingOrganization: {"reference": ...}` into an ABSENT field rather than an empty
 * object. Returning `null` means "prune me"; the recursion is what lets that propagate up from a
 * leaf string to the field that held it, without ever pruning a parent that still has real content.
 */
private fun JsonElement.withoutUnresolvedPlaceholders(placeholders: Set<String>): JsonElement? =
  when (this) {
    is JsonPrimitive ->
      takeUnless { isString && placeholders.any { placeholder -> content.contains(placeholder) } }

    is JsonArray ->
      mapNotNull { it.withoutUnresolvedPlaceholders(placeholders) }
        .takeIf { it.isNotEmpty() }
        ?.let(::JsonArray)

    is JsonObject ->
      entries
        .mapNotNull { (key, value) ->
          value.withoutUnresolvedPlaceholders(placeholders)?.let { key to it }
        }
        .takeIf { it.isNotEmpty() }
        ?.let { JsonObject(it.toMap()) }
  }
