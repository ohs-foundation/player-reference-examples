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

import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.DateTime
import dev.ohs.fhir.model.r4.Decimal
import dev.ohs.fhir.model.r4.Encounter
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.FhirDecimal
import dev.ohs.fhir.model.r4.MedicationRequest
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Period
import dev.ohs.fhir.model.r4.Quantity
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.RequestGroup
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.ServiceRequest
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Task
import dev.ohs.fhir.model.r4.Uri
import dev.ohs.fhir.workflow.FhirOperator
import dev.ohs.fhir.workflow.WorkflowRepository
import dev.ohs.fhir.workflow.activity.ActivityFlow
import dev.ohs.fhir.workflow.activity.resource.event.CPGProcedureEvent
import dev.ohs.fhir.workflow.activity.resource.request.CPGServiceRequest
import dev.ohs.fhir.workflow.activity.resource.request.Status
import dev.ohs.workflow.examples.auth.UserContext
import dev.ohs.workflow.examples.generateId
import dev.ohs.workflow.examples.util.idOf
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

const val ICCM_SICK_CHILD = "http://ohs.dev/fhir/PlanDefinition/iccm-sick-child"
const val OPD_TRIAGE = "http://ohs.dev/fhir/PlanDefinition/opd-triage"
const val REFERRAL_CODE = "3457005"
private const val LOINC = "http://loinc.org"

/** What the iCCM protocol decided for one assessment, as stored resources. */
data class AssessmentResult(
  val referral: ServiceRequest?,
  val medications: List<MedicationRequest>,
  val followUp: Task?,
)

/**
 * Runs the bundled protocols for the app's two workflows and moves the referral through its
 * ActivityFlow. Protocol output is stored as standalone resources; the CarePlan wrapper is not.
 */
class ProtocolService(
  private val repository: WorkflowRepository,
  private val operatorFactory: suspend () -> FhirOperator,
  private val now: () -> Instant = { Clock.System.now() },
) {
  private val operatorLock = Mutex()
  private var operator: FhirOperator? = null

  suspend fun assessSickChild(
    patient: Patient,
    response: QuestionnaireResponse,
    context: UserContext,
  ): AssessmentResult {
    store(response, patient)
    val chw = reference("Practitioner/${context.practitionerId}")
    val requests =
      apply(ICCM_SICK_CHILD, patient, mapOf("assessment" to collection(listOf(response)))) {
        when (it) {
          is ServiceRequest -> it.copy(requester = chw)
          is MedicationRequest -> it.copy(requester = chw)
          is Task -> it.copy(owner = chw)
          else -> it
        }
      }
    return AssessmentResult(
      referral = requests.filterIsInstance<ServiceRequest>().firstOrNull(),
      medications = requests.filterIsInstance<MedicationRequest>(),
      followUp = requests.filterIsInstance<Task>().firstOrNull(),
    )
  }

  /** Turns the protocol's referral proposal into an active order addressed to the facility. */
  suspend fun confirmReferral(proposal: ServiceRequest, context: UserContext): ServiceRequest {
    val stored =
      repository.read("ServiceRequest", proposal.id!!) as? ServiceRequest
        ?: error("This referral is no longer on the device.")
    if (stored.status.value != ServiceRequest.RequestStatus.Active) {
      return orderFor(stored) ?: error("This referral was already handled.")
    }
    val addressed =
      stored.copy(performer = listOf(reference("Organization/${context.organizationId}")))
    repository.update(addressed)
    val flow = ActivityFlow.of(repository, CPGServiceRequest(addressed))
    val draft = flow.prepareOrder().getOrThrow()
    val order = flow.initiateOrder(draft).getOrThrow()
    order.update(order.getRequestResource().apply { setStatus(Status.ACTIVE) }).getOrThrow()
    return repository.read("ServiceRequest", draft.resource.id!!) as ServiceRequest
  }

  /** Records the arrival and vitals, then queues the patient by the triage protocol's priority. */
  suspend fun checkIn(
    patient: Patient,
    response: QuestionnaireResponse,
    context: UserContext,
  ): Task {
    store(response, patient)
    val subject = reference("Patient/${patient.id}")
    val encounter =
      Encounter(
        id = generateId(),
        status = Enumeration(value = Encounter.EncounterStatus.Arrived),
        `class` =
          Coding(
            system = Uri(value = "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
            code = Code(value = "AMB"),
          ),
        subject = subject,
        reasonCode =
          listOfNotNull(response.string("reason")).map {
            CodeableConcept(text = FhirString(value = it))
          },
        location =
          listOfNotNull(context.locationId).map {
            Encounter.Location(location = reference("Location/$it"))
          },
        serviceProvider = reference("Organization/${context.organizationId}"),
        period = Period(start = dateTimeNow()),
      )
    repository.create(encounter)
    val encounterReference = reference("Encounter/${encounter.id}")
    val vitals =
      listOfNotNull(
          response.decimal("temperature")?.let { vital("8310-5", "Body temperature", it, "Cel") },
          response.integer("respiratory-rate")?.let {
            vital("9279-1", "Respiratory rate", FhirDecimal.fromString("$it"), "/min")
          },
          response.integer("spo2")?.let {
            vital("59408-5", "Oxygen saturation", FhirDecimal.fromString("$it"), "%")
          },
        )
        .map { it.copy(subject = subject, encounter = encounterReference) }
        .onEach { repository.create(it) }
    val referrals = activeReferrals(patient)
    return apply(
        OPD_TRIAGE,
        patient,
        mapOf("vitals" to collection(vitals), "referrals" to collection(referrals)),
      ) {
        (it as Task).copy(
          owner = reference("Organization/${context.organizationId}"),
          encounter = encounterReference,
          focus =
            referrals.firstOrNull()?.let { referral -> reference("ServiceRequest/${referral.id}") },
        )
      }
      .single() as Task
  }

  /**
   * Closes the consult; a still-active referral behind it is performed and completed through its
   * ActivityFlow. Safe to repeat, and to run on a device that never received the consult's
   * encounter.
   */
  suspend fun completeConsult(task: Task, outcome: String) {
    val current = repository.read("Task", task.id!!) as? Task ?: task
    if (current.status.value == Task.TaskStatus.Completed) return
    val referral =
      current.focus.idOf("ServiceRequest")?.let { repository.read("ServiceRequest", it) }
        as? ServiceRequest
    if (referral?.status?.value == ServiceRequest.RequestStatus.Active) {
      val flow = ActivityFlow.of(repository, CPGServiceRequest(referral))
      val event = flow.preparePerform<CPGProcedureEvent>("CPGProcedureEvent").getOrThrow()
      val perform = flow.initiatePerform(event).getOrThrow()
      perform.start().getOrThrow()
      perform.complete().getOrThrow()
      repository.update(
        perform
          .getEventResource()
          .resource
          .copy(outcome = CodeableConcept(text = FhirString(value = outcome)))
      )
    }
    repository.update(current.copy(status = Enumeration(value = Task.TaskStatus.Completed)))
    val encounter =
      current.encounter.idOf("Encounter")?.let { repository.read("Encounter", it) } as? Encounter
    encounter?.let {
      repository.update(it.copy(status = Enumeration(value = Encounter.EncounterStatus.Finished)))
    }
  }

  private suspend fun orderFor(proposal: ServiceRequest): ServiceRequest? =
    repository
      .searchByReferenceParam(
        "ServiceRequest",
        "subject",
        proposal.subject?.reference?.value.orEmpty(),
      )
      .filterIsInstance<ServiceRequest>()
      .firstOrNull { order ->
        order.intent.value == ServiceRequest.RequestIntent.Order &&
          order.basedOn.any { it.reference?.value == "ServiceRequest/${proposal.id}" }
      }

  private suspend fun activeReferrals(patient: Patient): List<ServiceRequest> =
    repository
      .searchByReferenceParam("ServiceRequest", "subject", "Patient/${patient.id}")
      .filterIsInstance<ServiceRequest>()
      .filter {
        it.intent.value == ServiceRequest.RequestIntent.Order &&
          it.status.value == ServiceRequest.RequestStatus.Active &&
          it.code?.coding?.any { coding -> coding.code?.value == REFERRAL_CODE } == true
      }

  private suspend fun apply(
    planDefinition: String,
    patient: Patient,
    variables: Map<String, Any?>,
    assign: (Resource) -> Resource,
  ): List<Resource> {
    val today = now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return operator()
      .generateCarePlan(planDefinition, patient, variables, today)
      .contained
      .filterNot { it is RequestGroup }
      .map { assign(standalone(it)) }
      .onEach { repository.create(it) }
  }

  // Contained "#…" references from the CarePlan are meaningless once the request stands alone.
  private fun standalone(resource: Resource): Resource =
    when (resource) {
      is ServiceRequest ->
        resource.copy(id = generateId(), basedOn = emptyList(), authoredOn = dateTimeNow())
      is MedicationRequest ->
        resource.copy(id = generateId(), basedOn = emptyList(), authoredOn = dateTimeNow())
      is Task -> resource.copy(id = generateId(), basedOn = emptyList(), authoredOn = dateTimeNow())
      else -> error("Unexpected ${resource::class.simpleName} from a protocol")
    }

  private suspend fun store(response: QuestionnaireResponse, patient: Patient) {
    repository.create(
      response.copy(
        id = generateId(),
        subject = reference("Patient/${patient.id}"),
        authored = dateTimeNow(),
      )
    )
  }

  private suspend fun operator(): FhirOperator =
    operatorLock.withLock { operator ?: operatorFactory().also { operator = it } }

  private fun vital(code: String, display: String, value: FhirDecimal, unit: String) =
    Observation(
      id = generateId(),
      status = Enumeration(value = Observation.ObservationStatus.Final),
      code =
        CodeableConcept(
          coding =
            listOf(
              Coding(
                system = Uri(value = LOINC),
                code = Code(value = code),
                display = FhirString(value = display),
              )
            )
        ),
      effective = Observation.Effective.DateTime(dateTimeNow()),
      value =
        Observation.Value.Quantity(
          Quantity(
            value = Decimal(value = value),
            unit = FhirString(value = unit),
            system = Uri(value = "http://unitsofmeasure.org"),
            code = Code(value = unit),
          )
        ),
    )

  private fun dateTimeNow() = DateTime(value = FhirDateTime.fromString(now().toString()))

  private fun reference(value: String) = Reference(reference = FhirString(value = value))

  private fun collection(resources: List<Resource>) =
    Bundle(
      type = Enumeration(value = Bundle.BundleType.Collection),
      entry = resources.map { Bundle.Entry(resource = it) },
    )
}
