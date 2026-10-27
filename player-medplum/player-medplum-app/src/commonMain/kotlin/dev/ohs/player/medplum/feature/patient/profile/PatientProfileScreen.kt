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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ohs.player.client.registry.LocalViewRegistry
import dev.ohs.player.client.registry.componentRenderer
import dev.ohs.player.client.registry.layoutRenderer
import dev.ohs.player.client.renderer.RenderOptions
import dev.ohs.player.generated.state.PatientAdverseEventState
import dev.ohs.player.generated.state.PatientContactState
import dev.ohs.player.generated.state.PatientGrowthState
import dev.ohs.player.generated.state.PatientImmunizationRecommendationState
import dev.ohs.player.generated.state.PatientImmunizationState
import dev.ohs.player.generated.state.PatientSummaryState
import dev.ohs.player.generated.viewtype.ViewTypeCS
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import player_medplum.player_medplum_app.generated.resources.Res
import player_medplum.player_medplum_app.generated.resources.patient_profile_action_failed
import player_medplum.player_medplum_app.generated.resources.patient_profile_back
import player_medplum.player_medplum_app.generated.resources.patient_profile_deceased_recorded
import player_medplum.player_medplum_app.generated.resources.patient_profile_default_name
import player_medplum.player_medplum_app.generated.resources.patient_profile_not_found
import player_medplum.player_medplum_app.generated.resources.patient_profile_record_growth
import player_medplum.player_medplum_app.generated.resources.patient_profile_status_changed_active
import player_medplum.player_medplum_app.generated.resources.patient_profile_status_changed_inactive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientProfileScreen(
  patientId: String,
  onBack: () -> Unit,
  onRecordGrowth: () -> Unit,
  onReportAdverseEvent: () -> Unit = {},
) {
  val viewModel =
    koinViewModel<PatientProfileViewModel>(key = patientId) { parametersOf(patientId) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val justAdministered by viewModel.justAdministered.collectAsStateWithLifecycle()
  val actionResult by viewModel.actionResult.collectAsStateWithLifecycle()
  val registry = LocalViewRegistry.current
  val snackbarHostState = remember { SnackbarHostState() }
  var changingStatus by remember { mutableStateOf(false) }
  var reportingDeceased by remember { mutableStateOf(false) }

  val headerRenderer =
    remember(registry) { registry.componentRenderer<PatientSummaryState>(ViewTypeCS.PatientHeader) }
  val dueSection =
    remember(registry) {
      registry.layoutRenderer<PatientImmunizationRecommendationState>(ViewTypeCS.SectionCard)
    }
  val dueRenderer =
    remember(registry) {
      registry.componentRenderer<PatientImmunizationRecommendationState>(
        ViewTypeCS.ImmunizationRecommendationItem
      )
    }
  val immunizationSection =
    remember(registry) { registry.layoutRenderer<PatientImmunizationState>(ViewTypeCS.SectionCard) }
  val immunizationRenderer =
    remember(registry) {
      registry.componentRenderer<PatientImmunizationState>(ViewTypeCS.ImmunizationItem)
    }
  val adverseEventSection =
    remember(registry) { registry.layoutRenderer<PatientAdverseEventState>(ViewTypeCS.SectionCard) }
  val adverseEventRenderer =
    remember(registry) {
      registry.componentRenderer<PatientAdverseEventState>(ViewTypeCS.AdverseEventItem)
    }
  val growthSection =
    remember(registry) { registry.layoutRenderer<PatientGrowthState>(ViewTypeCS.SectionCard) }
  val growthRenderer =
    remember(registry) { registry.componentRenderer<PatientGrowthState>(ViewTypeCS.GrowthItem) }
  val contactSection =
    remember(registry) { registry.layoutRenderer<PatientContactState>(ViewTypeCS.SectionCard) }
  val contactRenderer =
    remember(registry) { registry.componentRenderer<PatientContactState>(ViewTypeCS.ContactItem) }

  val patient = state?.patient
  val defaultPatientName = stringResource(Res.string.patient_profile_default_name)
  val patientName =
    remember(patient, defaultPatientName) {
      listOfNotNull(patient?.givenName, patient?.familyName).joinToString(" ").ifBlank {
        defaultPatientName
      }
    }

  val isActive = patient?.active ?: false
  val isDeceased = patient?.deceasedDateTime != null

  val actionMessage =
    when (actionResult) {
      PatientProfileViewModel.ProfileActionResult.MarkedActive ->
        stringResource(Res.string.patient_profile_status_changed_active)
      PatientProfileViewModel.ProfileActionResult.MarkedInactive ->
        stringResource(Res.string.patient_profile_status_changed_inactive)
      PatientProfileViewModel.ProfileActionResult.RecordedDeceased ->
        stringResource(Res.string.patient_profile_deceased_recorded)
      PatientProfileViewModel.ProfileActionResult.Failed ->
        stringResource(Res.string.patient_profile_action_failed)
      null -> null
    }
  LaunchedEffect(actionResult) {
    if (actionMessage != null) {
      snackbarHostState.showSnackbar(actionMessage)
      viewModel.clearActionResult()
    }
  }

  if (changingStatus) {
    ChangeStatusDialog(
      patientName = patientName,
      isActive = isActive,
      onConfirm = {
        changingStatus = false
        viewModel.setActive(it)
      },
      onDismiss = { changingStatus = false },
    )
  }
  if (reportingDeceased) {
    ReportDeceasedDialog(
      patientName = patientName,
      onConfirm = { date ->
        reportingDeceased = false
        viewModel.reportDeceased(date)
      },
      onDismiss = { reportingDeceased = false },
    )
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        title = { Text(patientName) },
        actions = {
          PatientActionsMenu(
            isDeceased = isDeceased,
            hasRecordedDoses = state?.immunizations.orEmpty().isNotEmpty(),
            onChangeStatus = { changingStatus = true },
            onReportDeceased = { reportingDeceased = true },
            onReportAdverseEvent = onReportAdverseEvent,
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(Res.string.patient_profile_back),
              tint = MaterialTheme.colorScheme.onPrimary,
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
          ),
      )
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = onRecordGrowth,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        // Fully rounded -- deliberately not the Material default's 16dp corners.
        shape = RoundedCornerShape(percent = 50),
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = {
          Text(
            text = stringResource(Res.string.patient_profile_record_growth).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
          )
        },
      )
    },
  ) { padding ->
    val s = state
    if (s == null) {
      Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
      return@Scaffold
    }
    if (s.patient == null) {
      Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Text(stringResource(Res.string.patient_profile_not_found))
      }
      return@Scaffold
    }

    LazyColumn(
      // Sections are cards; the tinted ground behind them is what makes them read as cards.
      modifier =
        Modifier.fillMaxSize()
          .padding(padding)
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item(key = "patient_header") { headerRenderer.Render(s.patient, RenderOptions()) }

      // Directly under the header: in an immunization registry, "what is this child due for"
      // is the question the record was opened to answer.
      val outstanding =
        s.recommendations.filter {
          val status = it.forecastStatus?.lowercase()
          (status == "due" || status == "overdue") &&
            PatientProfileViewModel.doseKey(it) !in justAdministered
        }
      if (outstanding.isNotEmpty()) {
        item(key = "immunizations_due") {
          dueSection.Render(
            items = outstanding,
            component = dueRenderer,
            onItemClick = { dose -> viewModel.administer(dose) },
          )
        }
      }
      if (s.immunizations.isNotEmpty()) {
        item(key = "immunizations") {
          immunizationSection.Render(
            items = s.immunizations,
            component = immunizationRenderer,
            onItemClick = {},
          )
        }
      }
      // Directly under the doses given, because an AEFI is a fact about one of them.
      if (s.adverseEvents.isNotEmpty()) {
        item(key = "adverseEvents") {
          adverseEventSection.Render(
            items = s.adverseEvents,
            component = adverseEventRenderer,
            onItemClick = {},
          )
        }
      }
      if (s.growth.isNotEmpty()) {
        item(key = "growth") {
          growthSection.Render(items = s.growth, component = growthRenderer, onItemClick = {})
        }
      }
      if (s.contacts.isNotEmpty()) {
        item(key = "contacts") {
          contactSection.Render(items = s.contacts, component = contactRenderer, onItemClick = {})
        }
      }
    }
  }
}
