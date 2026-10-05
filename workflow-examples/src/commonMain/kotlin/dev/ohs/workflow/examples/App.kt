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
package dev.ohs.workflow.examples

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import dev.ohs.player.client.registry.LocalViewRegistry
import dev.ohs.workflow.examples.auth.AppRole
import dev.ohs.workflow.examples.auth.AuthState
import dev.ohs.workflow.examples.auth.AuthViewModel
import dev.ohs.workflow.examples.auth.UserContext
import dev.ohs.workflow.examples.auth.rememberAuthorizationLauncher
import dev.ohs.workflow.examples.feature.home.HomeScreen
import dev.ohs.workflow.examples.feature.login.LoginScreen
import dev.ohs.workflow.examples.feature.patient.profile.PatientProfileScreen
import dev.ohs.workflow.examples.feature.questionnaire.QuestionnaireHostScreen
import dev.ohs.workflow.examples.feature.questionnaire.QuestionnaireIds
import dev.ohs.workflow.examples.feature.role.NoRoleScreen
import dev.ohs.workflow.examples.feature.role.UserContextState
import dev.ohs.workflow.examples.feature.role.UserContextViewModel
import dev.ohs.workflow.examples.feature.sync.InitialSyncGateState
import dev.ohs.workflow.examples.feature.sync.InitialSyncScreen
import dev.ohs.workflow.examples.feature.sync.InitialSyncViewModel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import player_reference_examples.workflow_examples.generated.resources.Res
import player_reference_examples.workflow_examples.generated.resources.patient_profile_assess_sick_child

@Composable
fun App() {
  val registry = remember { buildAppViewRegistry() }

  CompositionLocalProvider(LocalViewRegistry provides registry) {
    OhsPlayerTheme {
      val authViewModel: AuthViewModel = koinViewModel()
      val launcher = rememberAuthorizationLauncher()
      val authState by authViewModel.state.collectAsStateWithLifecycle()
      val signingIn by authViewModel.signingIn.collectAsStateWithLifecycle()
      val authError by authViewModel.error.collectAsStateWithLifecycle()

      LaunchedEffect(launcher) { authViewModel.bootstrap(launcher) }

      when (authState) {
        is AuthState.Loading ->
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        is AuthState.Unauthenticated ->
          LoginScreen(
            signingIn = signingIn,
            error = authError,
            onSignIn = { authViewModel.login(launcher) },
            onErrorDismiss = { authViewModel.clearError() },
          )
        is AuthState.Authenticated -> {
          val session = (authState as AuthState.Authenticated).session
          val userName =
            session.user.fullName.ifBlank { session.user.username }.ifBlank { session.user.email }
          val contextViewModel: UserContextViewModel = koinViewModel(key = session.user.subject)
          val contextState by contextViewModel.state.collectAsStateWithLifecycle()
          LaunchedEffect(session.accessToken) { contextViewModel.resolve() }

          when (val state = contextState) {
            UserContextState.Loading ->
              Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
              }
            UserContextState.NoRole ->
              NoRoleScreen(message = null, onRetry = null, onSignOut = { authViewModel.logout() })
            is UserContextState.Failed ->
              NoRoleScreen(
                message = state.message,
                onRetry = { contextViewModel.resolve() },
                onSignOut = { authViewModel.logout() },
              )
            is UserContextState.Ready ->
              SignedInApp(state.context, userName, onSignOut = { authViewModel.logout() })
          }
        }
      }
    }
  }
}

@Composable
private fun SignedInApp(context: UserContext, userName: String, onSignOut: () -> Unit) {
  val initialSyncViewModel: InitialSyncViewModel = koinViewModel()
  val gateState by initialSyncViewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(Unit) { initialSyncViewModel.start() }

  when (gateState) {
    InitialSyncGateState.Checking,
    InitialSyncGateState.Syncing,
    is InitialSyncGateState.Failed ->
      InitialSyncScreen(
        state = gateState,
        onRetry = { initialSyncViewModel.retry() },
        onContinueAnyway = { initialSyncViewModel.continueAnyway() },
      )
    InitialSyncGateState.Passed -> {
      val navController = rememberNavController()
      NavHost(navController = navController, startDestination = "home") {
        composable("home") {
          HomeScreen(
            context = context,
            userName = userName,
            onPatientClick = { id -> navController.navigate("patientProfile/$id") },
            onRegisterPatient = {
              navController.navigate("questionnaireHost/${QuestionnaireIds.PATIENT_REGISTRATION}")
            },
            onSignOut = onSignOut,
          )
        }

        composable(
          route = "questionnaireHost/{questionnaireId}?patientId={patientId}",
          arguments =
            listOf(
              navArgument("questionnaireId") { type = NavType.StringType },
              navArgument("patientId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
              },
            ),
        ) { back ->
          val questionnaireId =
            back.arguments?.read { getStringOrNull("questionnaireId") }.orEmpty()
          val patientId = back.arguments?.read { getStringOrNull("patientId") }
          QuestionnaireHostScreen(
            questionnaireId = questionnaireId,
            patientId = patientId,
            user = context,
            onBack = { navController.popBackStack() },
          )
        }

        composable(
          route = "patientProfile/{patientId}",
          arguments = listOf(navArgument("patientId") { type = NavType.StringType }),
        ) { back ->
          val patientId = back.arguments?.read { getStringOrNull("patientId") }.orEmpty()
          PatientProfileScreen(
            patientId = patientId,
            onBack = { navController.popBackStack() },
            actions = {
              if (context.role == AppRole.CHW) {
                Button(
                  onClick = {
                    navController.navigate(
                      "questionnaireHost/${QuestionnaireIds.ICCM_SICK_CHILD}?patientId=$patientId"
                    )
                  },
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(stringResource(Res.string.patient_profile_assess_sick_child))
                }
              }
            },
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalUuidApi::class) fun generateId(): String = Uuid.random().toString()
