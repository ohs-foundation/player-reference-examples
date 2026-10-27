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
package dev.ohs.player.medplum.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.ohs.player.medplum.data.settings.ThemePreference
import dev.ohs.player.medplum.data.settings.ThemeRepository
import dev.ohs.player.medplum.feature.patient.list.PatientListScreen
import dev.ohs.player.medplum.feature.patient.profile.PatientProfileScreen
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import player_medplum.player_medplum_app.generated.resources.Res
import player_medplum.player_medplum_app.generated.resources.home_cancel_sync
import player_medplum.player_medplum_app.generated.resources.home_last_synced
import player_medplum.player_medplum_app.generated.resources.home_register_child
import player_medplum.player_medplum_app.generated.resources.home_registers
import player_medplum.player_medplum_app.generated.resources.home_select_child
import player_medplum.player_medplum_app.generated.resources.home_sign_out
import player_medplum.player_medplum_app.generated.resources.home_signed_in
import player_medplum.player_medplum_app.generated.resources.home_sync_cancelled
import player_medplum.player_medplum_app.generated.resources.home_sync_failed
import player_medplum.player_medplum_app.generated.resources.home_sync_in_progress
import player_medplum.player_medplum_app.generated.resources.home_sync_now
import player_medplum.player_medplum_app.generated.resources.home_theme
import player_medplum.player_medplum_app.generated.resources.home_theme_dark
import player_medplum.player_medplum_app.generated.resources.home_theme_light
import player_medplum.player_medplum_app.generated.resources.home_theme_not_selected
import player_medplum.player_medplum_app.generated.resources.home_theme_selected
import player_medplum.player_medplum_app.generated.resources.home_theme_system
import player_medplum.player_medplum_app.generated.resources.home_view_profile

/**
 * The navigation drawer is deliberately dark, independent of the app's light colour scheme.
 *
 * Deliberate: the register behind it stays white and clinical, and the drawer reads as a separate
 * surface sliding over it rather than more of the same page.
 */
private val DrawerSurface = Color(0xFF26292B)
private val DrawerOnSurface = Color(0xFFF2F4F5)
private val DrawerMuted = Color(0xFF9AA3A8)
private val DrawerDivider = Color(0xFF3C4145)
private val DrawerAccent = Color(0xFF4FB3E8)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun HomeScreen(
  userName: String,
  onPatientClick: (String) -> Unit,
  onRegisterChild: () -> Unit,
  onRecordGrowth: (String) -> Unit,
  onReportAdverseEvent: (String) -> Unit = {},
  onViewOwnProfile: () -> Unit,
  onSignOut: () -> Unit,
) {
  val homeViewModel: HomeViewModel = koinViewModel()
  val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
  val themeRepository: ThemeRepository = koinInject()
  val themePreference by themeRepository.preference.collectAsStateWithLifecycle()

  var selectedDestination by remember { mutableStateOf(HomeDestination.Children) }
  var selectedPatientId by rememberSaveable { mutableStateOf<String?>(null) }
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  val syncErrorMessage =
    when (uiState.syncError) {
      SyncError.Failed -> stringResource(Res.string.home_sync_failed)
      SyncError.Cancelled -> stringResource(Res.string.home_sync_cancelled)
      null -> null
    }
  LaunchedEffect(uiState.syncError) {
    if (syncErrorMessage != null) {
      snackbarHostState.showSnackbar(syncErrorMessage)
      homeViewModel.clearSyncError()
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isExpandedWidth =
      windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    fun closeDrawerIfCompact() {
      if (!isExpandedWidth) scope.launch { drawerState.close() }
    }

    val onDrawer = DrawerOnSurface
    val drawerItemColors =
      NavigationDrawerItemDefaults.colors(
        selectedContainerColor = DrawerAccent.copy(alpha = 0.22f),
        unselectedContainerColor = Color.Transparent,
        selectedTextColor = onDrawer,
        unselectedTextColor = onDrawer,
        selectedIconColor = DrawerAccent,
        unselectedIconColor = DrawerMuted,
      )
    val drawerItems: @Composable () -> Unit = {
      val syncInProgressDescription = stringResource(Res.string.home_sync_in_progress)
      Column(modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp)) {
        // The signed-in identity is the way into the practitioner's own record, mirroring the
        // account row the drawer opens with.
        Row(
          modifier =
            Modifier.clickable {
                onViewOwnProfile()
                closeDrawerIfCompact()
              }
              .padding(start = 16.dp, top = 24.dp, bottom = 20.dp, end = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier.size(40.dp).clip(CircleShape).background(DrawerAccent.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = userName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
              style = MaterialTheme.typography.titleMedium,
              color = onDrawer,
            )
          }
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = userName.ifBlank { stringResource(Res.string.home_signed_in) },
              style = MaterialTheme.typography.titleMedium,
              color = onDrawer,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = stringResource(Res.string.home_view_profile),
              style = MaterialTheme.typography.bodySmall,
              color = DrawerMuted,
            )
          }
        }

        NavigationDrawerItem(
          label = {
            Text(
              text = stringResource(Res.string.home_register_child).uppercase(),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
            )
          },
          icon = { Icon(Icons.Filled.Add, contentDescription = null) },
          selected = false,
          colors =
            NavigationDrawerItemDefaults.colors(
              unselectedContainerColor = Color.Transparent,
              unselectedTextColor = DrawerAccent,
              unselectedIconColor = DrawerAccent,
            ),
          onClick = {
            onRegisterChild()
            closeDrawerIfCompact()
          },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = DrawerDivider)

        Text(
          text = stringResource(Res.string.home_registers),
          style = MaterialTheme.typography.titleSmall,
          color = onDrawer.copy(alpha = 0.7f),
          modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        HomeDestination.entries.forEach { destination ->
          NavigationDrawerItem(
            label = { Text(stringResource(destination.label)) },
            icon = { Icon(destination.icon, contentDescription = null) },
            badge = {
              uiState.childCount?.let {
                Text(
                  text = it.toString(),
                  style = MaterialTheme.typography.labelLarge,
                  color = DrawerMuted,
                )
              }
            },
            selected = destination == selectedDestination,
            colors = drawerItemColors,
            onClick = {
              selectedDestination = destination
              closeDrawerIfCompact()
            },
          )
        }

        Spacer(modifier = Modifier.weight(1f))

        HorizontalDivider(color = DrawerDivider)
        uiState.lastSyncedAt?.let { lastSyncedAt ->
          Text(
            text = stringResource(Res.string.home_last_synced, lastSyncedAt),
            style = MaterialTheme.typography.bodySmall,
            color = DrawerMuted,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
          )
        }
        ThemeSwitcher(
          selected = themePreference,
          // No closeDrawerIfCompact(): unlike every other row here this one does not navigate, and
          // the point of it is watching the app change colour, which you cannot do once the drawer
          // has slid shut over it.
          onSelect = themeRepository::select,
        )

        NavigationDrawerItem(
          colors = drawerItemColors,
          label = {
            Text(
              stringResource(
                if (uiState.isSyncing) Res.string.home_cancel_sync else Res.string.home_sync_now
              )
            )
          },
          icon = {
            Icon(
              if (uiState.isSyncing) Icons.Filled.Close else Icons.Filled.Refresh,
              contentDescription = null,
            )
          },
          badge = {
            if (uiState.isSyncing) {
              CircularProgressIndicator(
                modifier =
                  Modifier.size(16.dp).semantics { contentDescription = syncInProgressDescription },
                strokeWidth = 2.dp,
                color = onDrawer,
              )
            }
          },
          selected = false,
          onClick = {
            if (uiState.isSyncing) {
              homeViewModel.cancelSync()
            } else {
              homeViewModel.syncNow()
            }
            closeDrawerIfCompact()
          },
        )
        NavigationDrawerItem(
          colors = drawerItemColors,
          label = { Text(stringResource(Res.string.home_sign_out)) },
          icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
          selected = false,
          onClick = {
            onSignOut()
            closeDrawerIfCompact()
          },
        )
      }
    }

    val content: @Composable () -> Unit = {
      if (isExpandedWidth) {
        Row(modifier = Modifier.fillMaxSize()) {
          Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            PatientListScreen(
              onPatientClick = { selectedPatientId = it },
              onRegisterChild = onRegisterChild,
            )
          }
          VerticalDivider()
          Box(modifier = Modifier.weight(1.5f).fillMaxSize()) {
            val patientId = selectedPatientId
            if (patientId != null) {
              PatientProfileScreen(
                patientId = patientId,
                onBack = { selectedPatientId = null },
                onRecordGrowth = { onRecordGrowth(patientId) },
                onReportAdverseEvent = { onReportAdverseEvent(patientId) },
              )
            } else {
              EmptyDetailPlaceholder()
            }
          }
        }
      } else {
        PatientListScreen(
          onPatientClick = onPatientClick,
          onRegisterChild = onRegisterChild,
          onOpenNavigation = { scope.launch { drawerState.open() } },
        )
      }
    }

    if (isExpandedWidth) {
      Row(modifier = Modifier.fillMaxSize()) {
        Surface(modifier = Modifier.width(260.dp).fillMaxHeight(), color = DrawerSurface) {
          drawerItems()
        }
        VerticalDivider()
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
          Box(modifier = Modifier.padding(padding)) { content() }
        }
      }
    } else {
      ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
          ModalDrawerSheet(
            modifier = Modifier.width(280.dp),
            // Square by choice. Material's default rounds the trailing corners,
            // which reads as a floating card rather than a panel flush to the screen edge.
            drawerShape = RectangleShape,
            drawerContainerColor = DrawerSurface,
          ) {
            drawerItems()
          }
        },
      ) {
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
          Box(modifier = Modifier.padding(padding)) { content() }
        }
      }
    }
  }
}

/**
 * Three-way appearance switch, styled against the drawer's own fixed dark palette rather than the
 * colour scheme -- the drawer stays dark in both themes, so theme tokens would fight it.
 *
 * A segmented control rather than a row that opens a dialog: all three options and the current one
 * are visible at a glance, and switching is a single tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeSwitcher(selected: ThemePreference, onSelect: (ThemePreference) -> Unit) {
  // Labels only. The obvious glyphs for these (LightMode/DarkMode) live in
  // material-icons-extended, and pulling in a few thousand icons to draw three is not a trade
  // worth making -- the words are clearer than the glyphs at this size anyway.
  val options =
    listOf(
      ThemePreference.System to Res.string.home_theme_system,
      ThemePreference.Light to Res.string.home_theme_light,
      ThemePreference.Dark to Res.string.home_theme_dark,
    )

  Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
    Text(
      text = stringResource(Res.string.home_theme),
      style = MaterialTheme.typography.titleSmall,
      color = DrawerOnSurface.copy(alpha = 0.7f),
      modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      options.forEachIndexed { index, (preference, label) ->
        val isSelected = preference == selected
        val name = stringResource(label)
        val description =
          stringResource(
            if (isSelected) Res.string.home_theme_selected else Res.string.home_theme_not_selected,
            name,
          )
        SegmentedButton(
          selected = isSelected,
          onClick = { onSelect(preference) },
          shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
          colors =
            SegmentedButtonDefaults.colors(
              activeContainerColor = DrawerAccent.copy(alpha = 0.22f),
              activeContentColor = DrawerOnSurface,
              activeBorderColor = DrawerAccent,
              inactiveContainerColor = Color.Transparent,
              inactiveContentColor = DrawerMuted,
              inactiveBorderColor = DrawerDivider,
            ),
          // Material's default slot draws a tick when selected, which at a third of a 280dp drawer
          // shoves the label out. Empty slot; the border and fill carry the selection instead.
          icon = {},
          label = { Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
          modifier = Modifier.semantics { contentDescription = description },
        )
      }
    }
  }
}

@Composable
private fun EmptyDetailPlaceholder() {
  Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Text(
      text = stringResource(Res.string.home_select_child),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}
