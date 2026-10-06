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
package dev.ohs.workflow.examples.workflow

import dev.ohs.fhir.engine.FhirEngine
import dev.ohs.fhir.engine.FhirEngineConfiguration
import dev.ohs.fhir.engine.FhirEngineProvider
import dev.ohs.fhir.model.r4.Boolean as FhirBoolean
import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Encounter
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.Integer
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Procedure
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.QuestionnaireResponse.Item.Answer.Value
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.ServiceRequest
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Task
import dev.ohs.fhir.model.r4.terminologies.ResourceType
import dev.ohs.fhir.workflow.FhirOperator
import dev.ohs.fhir.workflow.WorkflowRepository
import dev.ohs.workflow.examples.auth.AppRole
import dev.ohs.workflow.examples.auth.UserContext
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class ProtocolServiceTest {
  private lateinit var fhirEngine: FhirEngine
  private lateinit var repository: EngineWorkflowRepository
  private lateinit var service: ProtocolService

  private val chw = UserContext(AppRole.CHW, "p-chw", "chu-1", null, facilityOrganizationId = "o1")
  private val nurse = UserContext(AppRole.NURSE, "p-nurse", "o1", "l1")
  private val child = Patient(id = "child-1")

  @BeforeTest
  fun setUp() = runTest {
    if (FhirEngineProvider.isNotInitialized()) {
      FhirEngineProvider.init(
        FhirEngineConfiguration(
          storageDirectory = Files.createTempDirectory("protocol-service-test").toString()
        )
      )
    }
    fhirEngine = FhirEngineProvider.getInstance()
    fhirEngine.clearDatabase()
    fhirEngine.create(child)
    repository = EngineWorkflowRepository(fhirEngine)
    service =
      ProtocolService(
        repository,
        { FhirOperator(repository, resolver = BundledProtocols.load()) },
        now = { Instant.parse("2026-10-05T08:00:00Z") },
      )
  }

  private fun response(vararg answers: Pair<String, Value>) =
    QuestionnaireResponse(
      status = Enumeration(value = QuestionnaireResponse.QuestionnaireResponseStatus.Completed),
      item =
        answers.map { (linkId, value) ->
          QuestionnaireResponse.Item(
            linkId = FhirString(value = linkId),
            answer = listOf(QuestionnaireResponse.Item.Answer(value = value)),
          )
        },
    )

  private fun yes(linkId: String) = linkId to Value.Boolean(FhirBoolean(value = true))

  private fun no(linkId: String) = linkId to Value.Boolean(FhirBoolean(value = false))

  private fun number(linkId: String, n: Int) = linkId to Value.Integer(Integer(value = n))

  private val noDangerSigns =
    arrayOf(
      no("unable-to-drink"),
      no("vomits-everything"),
      no("convulsions"),
      no("lethargic"),
      no("chest-indrawing"),
    )

  @Test
  fun referralRunsFromCommunityAssessmentToFacilityClosure() = runTest {
    val assessed =
      service.assessSickChild(
        child,
        response(number("age-months", 14), yes("chest-indrawing"), no("fever"), no("cough")),
        chw,
      )
    val proposal = assertNotNull(assessed.referral)
    assertEquals(ServiceRequest.RequestIntent.Proposal, proposal.intent.value)
    assertEquals("Practitioner/p-chw", proposal.requester?.reference?.value)
    assertTrue(proposal.basedOn.isEmpty())
    assertNull(assessed.followUp)

    val order = service.confirmReferral(proposal, chw)
    assertEquals(ServiceRequest.RequestIntent.Order, order.intent.value)
    assertEquals(ServiceRequest.RequestStatus.Active, order.status.value)
    assertEquals("Organization/o1", order.performer.single().reference?.value)
    assertEquals(
      ServiceRequest.RequestStatus.Completed,
      (repository.read("ServiceRequest", proposal.id!!) as ServiceRequest).status.value,
    )

    val consult =
      service.checkIn(
        child,
        response(
          FhirString(value = "Fast breathing").let { "reason" to Value.String(it) },
          number("spo2", 95),
        ),
        nurse,
      )
    assertEquals(Task.RequestPriority.Stat, consult.priority?.value)
    assertEquals("ServiceRequest/${order.id}", consult.focus?.reference?.value)
    assertEquals("Organization/o1", consult.owner?.reference?.value)

    service.completeConsult(consult, "Admitted for oxygen")

    assertEquals(
      ServiceRequest.RequestStatus.Completed,
      (repository.read("ServiceRequest", order.id!!) as ServiceRequest).status.value,
    )
    val procedure =
      repository.searchByReferenceParam("Procedure", "subject", "Patient/child-1").single()
        as Procedure
    assertEquals(Procedure.EventStatus.Completed, procedure.status.value)
    assertEquals("ServiceRequest/${order.id}", procedure.basedOn.single().reference?.value)
    assertEquals("Admitted for oxygen", procedure.outcome?.text?.value)
    assertEquals(
      Task.TaskStatus.Completed,
      (repository.read("Task", consult.id!!) as Task).status.value,
    )
    val encounterId = consult.encounter?.reference?.value!!.substringAfter("Encounter/")
    assertEquals(
      Encounter.EncounterStatus.Finished,
      (repository.read("Encounter", encounterId) as Encounter).status.value,
    )
  }

  @Test
  fun malariaIsTreatedAtHomeWithAFollowUpForTheChw() = runTest {
    val assessed =
      service.assessSickChild(
        child,
        response(
          number("age-months", 30),
          *noDangerSigns,
          yes("fever"),
          "rdt-result" to Value.Coding(Coding(code = Code(value = "positive"))),
          no("cough"),
        ),
        chw,
      )

    assertNull(assessed.referral)
    val medication = assessed.medications.single()
    assertEquals("Practitioner/p-chw", medication.requester?.reference?.value)
    assertEquals(MedicationRequest.MedicationRequestIntent.Proposal, medication.intent.value)
    assertEquals("Practitioner/p-chw", assessed.followUp?.owner?.reference?.value)
    val stored = repository.read("Task", assessed.followUp!!.id!!) as Task
    assertEquals("Practitioner/p-chw", stored.owner?.reference?.value)
    assertEquals(FhirDateTime.Date(LocalDate(2026, 10, 8)), stored.restriction?.period?.end?.value)
    assertEquals(Task.TaskStatus.Requested, stored.status.value)
  }

  @Test
  fun walkInWithoutVitalsIsQueuedRoutine() = runTest {
    val consult =
      service.checkIn(
        child,
        response("reason" to Value.String(FhirString(value = "Headache"))),
        nurse,
      )

    assertEquals(Task.RequestPriority.Routine, consult.priority?.value)
    assertNull(consult.focus)
  }

  @Test
  fun completingAConsultTwiceClosesTheReferralOnce() = runTest {
    val proposal =
      service
        .assessSickChild(
          child,
          response(number("age-months", 14), yes("convulsions"), no("fever"), no("cough")),
          chw,
        )
        .referral!!
    service.confirmReferral(proposal, chw)
    val consult = service.checkIn(child, response(number("spo2", 97)), nurse)

    service.completeConsult(consult, "Seen")
    service.completeConsult(consult, "Seen again")

    assertEquals(
      1,
      repository.searchByReferenceParam("Procedure", "subject", "Patient/child-1").size,
    )
    assertEquals(
      Task.TaskStatus.Completed,
      (repository.read("Task", consult.id!!) as Task).status.value,
    )
  }

  @Test
  fun consultWithoutItsEncounterOnThisDeviceStillCloses() = runTest {
    val consult = service.checkIn(child, response(number("spo2", 97)), nurse)
    val encounterId = consult.encounter?.reference?.value!!.substringAfter("Encounter/")
    fhirEngine.delete(ResourceType.Encounter, encounterId)

    service.completeConsult(consult, "Seen")

    assertEquals(
      Task.TaskStatus.Completed,
      (repository.read("Task", consult.id!!) as Task).status.value,
    )
  }

  @Test
  fun confirmingAReferralTwiceKeepsOneOrder() = runTest {
    val proposal =
      service
        .assessSickChild(
          child,
          response(number("age-months", 14), yes("lethargic"), no("fever"), no("cough")),
          chw,
        )
        .referral!!

    val first = service.confirmReferral(proposal, chw)
    val second = service.confirmReferral(proposal, chw)

    assertEquals(first.id, second.id)
    val orders =
      repository
        .searchByReferenceParam("ServiceRequest", "subject", "Patient/child-1")
        .filterIsInstance<ServiceRequest>()
        .filter { it.intent.value == ServiceRequest.RequestIntent.Order }
    assertEquals(listOf(first.id), orders.map { it.id })
  }

  @Test
  fun realClockTimestampsWithNanosecondsAreStored() = runTest {
    val service =
      ProtocolService(
        repository,
        { FhirOperator(repository, resolver = BundledProtocols.load()) },
        now = { Instant.parse("2026-10-05T16:54:12.123456789Z") },
      )

    val assessed =
      service.assessSickChild(
        child,
        response(number("age-months", 36), yes("chest-indrawing"), no("fever"), no("cough")),
        chw,
      )

    assertNotNull(assessed.referral?.authoredOn)
  }

  @Test
  fun closingAReferralHandsTheChildBackToTheChw() = runTest {
    val proposal =
      service
        .assessSickChild(
          child,
          response(number("age-months", 14), yes("convulsions"), no("fever"), no("cough")),
          chw,
        )
        .referral!!
    val order = service.confirmReferral(proposal, chw)
    val consult = service.checkIn(child, response(number("spo2", 97)), nurse)

    service.completeConsult(consult, "Treated and discharged")

    val tasks =
      repository
        .searchByReferenceParam("Task", "subject", "Patient/child-1")
        .filterIsInstance<Task>()
    val followUp =
      assertNotNull(
        tasks.singleOrNull {
          it.code?.coding?.any { coding -> coding.code?.value == "iccm-follow-up" } == true
        },
        tasks.joinToString { "${it.code?.coding?.firstOrNull()?.code?.value}/${it.status.value}" },
      )
    assertEquals("Practitioner/p-chw", followUp.owner?.reference?.value)
    assertEquals("ServiceRequest/${order.id}", followUp.focus?.reference?.value)
    assertEquals(Task.TaskStatus.Requested, followUp.status.value)
    assertEquals(
      FhirDateTime.Date(LocalDate(2026, 10, 7)),
      followUp.restriction?.period?.end?.value,
    )
    assertEquals(
      "Follow up after facility visit: Treated and discharged",
      followUp.description?.value,
    )
  }

  @Test
  fun aFailedHandBackLeavesTheReferralOpenForARetry() = runTest {
    var failHandBack = true
    val flaky =
      object : WorkflowRepository by repository {
        override suspend fun create(resource: Resource): String {
          if (failHandBack && resource is Task && resource.isFollowUp()) error("Disk full")
          return repository.create(resource)
        }
      }
    val service =
      ProtocolService(
        flaky,
        { FhirOperator(flaky, resolver = BundledProtocols.load()) },
        now = { Instant.parse("2026-10-05T08:00:00Z") },
        transactor = EngineTransactor(fhirEngine),
      )
    val proposal =
      service
        .assessSickChild(child, response(number("age-months", 14), yes("convulsions")), chw)
        .referral!!
    service.confirmReferral(proposal, chw)
    val consult = service.checkIn(child, response(number("spo2", 97)), nurse)

    assertFails { service.completeConsult(consult, "Treated") }
    failHandBack = false
    service.completeConsult(consult, "Treated")

    val tasks =
      repository
        .searchByReferenceParam("Task", "subject", "Patient/child-1")
        .filterIsInstance<Task>()
    assertEquals(
      1,
      tasks.count { it.isFollowUp() && it.owner?.reference?.value == "Practitioner/p-chw" },
    )
  }

  @Test
  fun walkInConsultCreatesNoFollowUp() = runTest {
    val consult = service.checkIn(child, response(number("spo2", 97)), nurse)

    service.completeConsult(consult, "Advised rest")

    assertTrue(
      repository.searchByReferenceParam("Task", "subject", "Patient/child-1").all {
        (it as Task).code?.coding?.none { coding -> coding.code?.value == "iccm-follow-up" } !=
          false
      }
    )
  }

  @Test
  fun followUpVisitClosesTheTaskAndRefersAWorseningChild() = runTest {
    val followUp =
      service
        .assessSickChild(
          child,
          response(
            number("age-months", 30),
            *noDangerSigns,
            yes("fever"),
            "rdt-result" to Value.Coding(Coding(code = Code(value = "positive"))),
            no("cough"),
          ),
          chw,
        )
        .followUp!!

    val visit =
      service.recordFollowUp(
        child,
        response(
          "condition" to Value.Coding(Coding(code = Code(value = "worse"))),
          no("danger-sign"),
        ),
        followUp.id!!,
        chw,
      )

    assertEquals(
      Task.TaskStatus.Completed,
      (repository.read("Task", followUp.id!!) as Task).status.value,
    )
    assertEquals(ServiceRequest.RequestIntent.Proposal, visit.referral?.intent?.value)
    assertEquals("Practitioner/p-chw", visit.referral?.requester?.reference?.value)
  }

  @Test
  fun followUpVisitOfARecoveredChildOnlyClosesTheTask() = runTest {
    val followUp =
      service
        .assessSickChild(
          child,
          response(
            number("age-months", 30),
            *noDangerSigns,
            yes("fever"),
            "rdt-result" to Value.Coding(Coding(code = Code(value = "positive"))),
            no("cough"),
          ),
          chw,
        )
        .followUp!!

    val visit =
      service.recordFollowUp(
        child,
        response(
          "condition" to Value.Coding(Coding(code = Code(value = "better"))),
          no("danger-sign"),
        ),
        followUp.id!!,
        chw,
      )

    assertNull(visit.referral)
    assertEquals(
      Task.TaskStatus.Completed,
      (repository.read("Task", followUp.id!!) as Task).status.value,
    )
  }
}

private fun Task.isFollowUp() = code?.coding?.any { it.code?.value == "iccm-follow-up" } == true
