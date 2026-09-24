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

import dev.ohs.fhir.model.r4.Questionnaire as QuestionnaireR4
import dev.ohs.player.medplum.data.repository.InMemorySampleFhirRepository
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.ExperimentalResourceApi
import player_medplum.player_medplum_app.generated.resources.Res

/**
 * Structural checks on every questionnaire the app can launch.
 *
 * These exist because a malformed questionnaire does not fail at build time or on load — it throws
 * while the data-capture library builds the response, which crashes the app the moment a health
 * worker taps the button that opens the form.
 */
class BundledQuestionnaireTest {

  private val launchable =
    listOf(
      QuestionnaireIds.CHILD_REGISTRATION,
      QuestionnaireIds.GROWTH_MONITORING,
      QuestionnaireIds.ADVERSE_EVENT,
    )

  private suspend fun load(id: String): QuestionnaireR4 =
    QuestionnaireService(InMemorySampleFhirRepository()).getQuestionnaire(id)

  private fun QuestionnaireR4.Item.allItems(): List<QuestionnaireR4.Item> =
    listOf(this) + item.flatMap { it.allItems() }

  @Test
  fun noItemCombinesInitialWithAnswerOption() = runTest {
    launchable.forEach { id ->
      load(id)
        .item
        .flatMap { it.allItems() }
        .forEach { item ->
          // FHIR rule que-11. Pre-select with `answerOption.initialSelected` instead.
          assertTrue(
            item.initial.isEmpty() || item.answerOption.isEmpty(),
            "$id item '${item.linkId.value}' has both initial and answerOption, which throws on open",
          )
        }
    }
  }

  @Test
  fun everyItemHasALinkIdAndAType() = runTest {
    launchable.forEach { id ->
      load(id)
        .item
        .flatMap { it.allItems() }
        .forEach { item ->
          assertTrue(item.linkId.value?.isNotBlank() == true, "$id has an item with no linkId")
        }
    }
  }

  @Test
  fun everyTemplateReferenceResolvesToAContainedResource() = runTest {
    launchable.forEach { id ->
      val raw = rawQuestionnaire(id)
      val containedIds =
        raw["contained"]!!
          .jsonArray
          .mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
          .toSet()
      val referenced = raw.templateReferences()

      assertTrue(referenced.isNotEmpty(), "$id declares no extraction templates")
      referenced.forEach {
        // An unresolvable template reference makes extraction throw on submit — after the health
        // worker has filled in the whole form.
        assertTrue(it in containedIds, "$id references missing contained template '$it'")
      }
    }
  }

  @OptIn(ExperimentalResourceApi::class)
  private suspend fun rawQuestionnaire(id: String): JsonObject {
    // Every id spelled out, and an error rather than a fallback: an `else` branch here meant a
    // newly added questionnaire silently got another form's file checked twice in its place.
    val path =
      when (id) {
        QuestionnaireIds.CHILD_REGISTRATION -> "files/configs/Questionnaire-ChildRegistration.json"
        QuestionnaireIds.GROWTH_MONITORING -> "files/configs/Questionnaire-GrowthMonitoring.json"
        QuestionnaireIds.ADVERSE_EVENT -> "files/configs/Questionnaire-AdverseEvent.json"
        else -> error("No bundled file mapped for questionnaire '$id' in this test.")
      }
    return Json.parseToJsonElement(Res.readBytes(path).decodeToString()).jsonObject
  }

  private fun JsonObject.templateReferences(): List<String> =
    this["extension"]
      ?.jsonArray
      .orEmpty()
      .filter {
        it.jsonObject["url"]
          ?.jsonPrimitive
          ?.content
          ?.endsWith("sdc-questionnaire-templateExtract") == true
      }
      .mapNotNull { outer ->
        outer.jsonObject["extension"]
          ?.jsonArray
          ?.firstOrNull { it.jsonObject["url"]?.jsonPrimitive?.content == "template" }
          ?.jsonObject
          ?.get("valueReference")
          ?.jsonObject
          ?.get("reference")
          ?.jsonPrimitive
          ?.content
          ?.removePrefix("#")
      }
}
