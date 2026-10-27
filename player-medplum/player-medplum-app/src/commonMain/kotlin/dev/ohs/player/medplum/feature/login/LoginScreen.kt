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
package dev.ohs.player.medplum.feature.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import player_medplum.player_medplum_app.generated.resources.Res
import player_medplum.player_medplum_app.generated.resources.app_logo
import player_medplum.player_medplum_app.generated.resources.login_attribution_eyebrow
import player_medplum.player_medplum_app.generated.resources.login_attribution_medplum
import player_medplum.player_medplum_app.generated.resources.login_attribution_open_health_stack
import player_medplum.player_medplum_app.generated.resources.login_attribution_separator
import player_medplum.player_medplum_app.generated.resources.login_brand
import player_medplum.player_medplum_app.generated.resources.login_card_title
import player_medplum.player_medplum_app.generated.resources.login_error_dismiss
import player_medplum.player_medplum_app.generated.resources.login_error_title
import player_medplum.player_medplum_app.generated.resources.login_redirect_hint
import player_medplum.player_medplum_app.generated.resources.login_sign_in
import player_medplum.player_medplum_app.generated.resources.login_subtitle

/** Material 3's "expanded" window size class breakpoint (matches HomeWidthBreakpoint.kt). */
private val LOGIN_EXPANDED_WIDTH_BREAKPOINT = 840.dp

/**
 * PKCE redirect login — no password form; the primary action hands off to the identity provider.
 * Branches on width (never on platform): Expanded (>= 840dp) sets a solid brand panel beside a flat
 * sign-in side; Compact/Medium stack a brand band over a rounded sign-in sheet. Both are flat — the
 * brand color carries the identity, so there is no elevated card.
 */
@Composable
fun LoginScreen(
  signingIn: Boolean,
  error: String?,
  onSignIn: () -> Unit,
  onErrorDismiss: () -> Unit,
) {
  BoxWithConstraints {
    if (maxWidth >= LOGIN_EXPANDED_WIDTH_BREAKPOINT) {
      ExpandedLogin(signingIn, onSignIn)
    } else {
      CompactLogin(signingIn, onSignIn)
    }
  }

  if (error != null) {
    LoginErrorDialog(message = error, onDismiss = onErrorDismiss)
  }
}

/** Expanded: solid brand panel (left) beside a flat sign-in side (right). */
@Composable
private fun ExpandedLogin(signingIn: Boolean, onSignIn: () -> Unit) {
  Row(Modifier.fillMaxSize()) {
    Column(
      modifier =
        Modifier.weight(5f)
          .fillMaxHeight()
          .background(MaterialTheme.colorScheme.primary)
          .safeDrawingPadding()
          .padding(44.dp)
    ) {
      MonoLogoMark(64.dp)
      Spacer(Modifier.height(22.dp))
      Text(
        text = stringResource(Res.string.login_brand),
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onPrimary,
        fontWeight = FontWeight.Bold,
      )
    }

    // The footer is a sibling of the centred column rather than part of it, so crediting the
    // upstreams never drags the sign-in block off the optical centre of the panel.
    Box(
      modifier =
        Modifier.weight(6f)
          .fillMaxHeight()
          .background(MaterialTheme.colorScheme.surface)
          .safeDrawingPadding()
    ) {
      Column(
        modifier =
          Modifier.align(Alignment.Center)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .padding(horizontal = 48.dp)
      ) {
        SignInContent(signingIn, onSignIn, showBrandRow = true)
      }
      AttributionFooter(Modifier.align(Alignment.BottomCenter))
    }
  }
}

/** Compact/Medium: brand band over a rounded sign-in sheet, with the credits as a true footer. */
@Composable
private fun CompactLogin(signingIn: Boolean, onSignIn: () -> Unit) {
  // The outer fill is the brand colour so the sheet's rounded shoulders actually read against it.
  Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary)) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .background(MaterialTheme.colorScheme.primary)
          .statusBarsPadding()
          .padding(horizontal = 28.dp, vertical = 44.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      MonoLogoMark(56.dp)
      Text(
        text = stringResource(Res.string.login_brand),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onPrimary,
        fontWeight = FontWeight.Bold,
      )
    }

    Column(
      modifier =
        Modifier.fillMaxWidth()
          .weight(1f)
          .background(
            MaterialTheme.colorScheme.surface,
            RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
          )
    ) {
      // Centring the block keeps the call to action in the reading path instead of pinning it to
      // the bottom edge; the slack on a tall tablet sheet is split above and below it.
      Column(
        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // Capped so a tablet in portrait does not stretch the copy and the button edge to edge.
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
          SignInContent(signingIn, onSignIn, showBrandRow = false)
        }
      }
      AttributionFooter()
    }
  }
}

@Composable
private fun SignInContent(signingIn: Boolean, onSignIn: () -> Unit, showBrandRow: Boolean) {
  if (showBrandRow) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Image(
        painter = painterResource(Res.drawable.app_logo),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
      )
      Text(
        text = stringResource(Res.string.login_brand).uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
      )
    }
    Spacer(Modifier.height(26.dp))
  }

  Text(
    text = stringResource(Res.string.login_card_title),
    style = MaterialTheme.typography.headlineMedium,
  )
  Spacer(Modifier.height(6.dp))
  Text(
    text = stringResource(Res.string.login_subtitle),
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
  )
  Spacer(Modifier.height(14.dp))
  Text(
    text = stringResource(Res.string.login_redirect_hint),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  Spacer(Modifier.height(30.dp))
  SignInButton(signingIn, onSignIn)
}

/**
 * Credit for the two upstreams this app is assembled from. Attribution is credit, not co-equal
 * billing, so the whole band sits at footer scale and nothing in it competes with the app mark
 * above. FlowRow rather than Row so the credits wrap instead of clipping on a narrow phone.
 */
@Composable
private fun AttributionFooter(modifier: Modifier = Modifier) {
  Column(
    modifier =
      modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
  ) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp, vertical = 14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        text = stringResource(Res.string.login_attribution_eyebrow).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.6.sp,
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        AttributionName(stringResource(Res.string.login_attribution_open_health_stack))
        AttributionSeparator()
        AttributionName(stringResource(Res.string.login_attribution_medplum))
      }
    }
  }
}

@Composable
private fun AttributionName(name: String) {
  Text(
    text = name,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontWeight = FontWeight.SemiBold,
  )
}

@Composable
private fun AttributionSeparator() {
  Text(
    text = stringResource(Res.string.login_attribution_separator),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.outline,
  )
}

@Composable
private fun SignInButton(signingIn: Boolean, onSignIn: () -> Unit) {
  Button(
    onClick = onSignIn,
    enabled = !signingIn,
    shape = RoundedCornerShape(14.dp),
    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    modifier = Modifier.fillMaxWidth().height(52.dp),
  ) {
    if (signingIn) {
      CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onPrimary,
      )
    } else {
      Text(
        text = stringResource(Res.string.login_sign_in),
        style = MaterialTheme.typography.titleMedium,
      )
      Spacer(Modifier.size(8.dp))
      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

/** The 2x2 brand mark rendered monochrome for the solid brand panel. */
@Composable
private fun MonoLogoMark(size: Dp) {
  // The app mark: a shield (protection) enclosing a check (immunised). Drawn rather than
  // loaded so it inherits the header's tint and stays crisp at any size on every platform.
  Canvas(Modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    val shield =
      Path().apply {
        moveTo(w * 0.5f, h * 0.06f)
        lineTo(w * 0.90f, h * 0.22f)
        lineTo(w * 0.90f, h * 0.53f)
        cubicTo(w * 0.90f, h * 0.78f, w * 0.72f, h * 0.90f, w * 0.5f, h * 0.97f)
        cubicTo(w * 0.28f, h * 0.90f, w * 0.10f, h * 0.78f, w * 0.10f, h * 0.53f)
        lineTo(w * 0.10f, h * 0.22f)
        close()
      }
    drawPath(shield, Color.White.copy(alpha = 0.92f))
    drawPath(
      Path().apply {
        moveTo(w * 0.30f, h * 0.52f)
        lineTo(w * 0.45f, h * 0.68f)
        lineTo(w * 0.72f, h * 0.36f)
      },
      color = Color.White.copy(alpha = 0f),
    )
    // The check is punched through as the header colour rather than painted white-on-white.
    drawLine(
      color = Color(0xFF40A1DA),
      start = Offset(w * 0.31f, h * 0.53f),
      end = Offset(w * 0.44f, h * 0.67f),
      strokeWidth = w * 0.09f,
      cap = StrokeCap.Round,
    )
    drawLine(
      color = Color(0xFF40A1DA),
      start = Offset(w * 0.44f, h * 0.67f),
      end = Offset(w * 0.71f, h * 0.36f),
      strokeWidth = w * 0.09f,
      cap = StrokeCap.Round,
    )
  }
}

@Composable
private fun LoginErrorDialog(message: String, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Filled.Warning,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(48.dp),
      )
    },
    title = {
      Text(
        text = stringResource(Res.string.login_error_title),
        color = MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    text = {
      Text(text = message, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.login_error_dismiss)) }
    },
    iconContentColor = MaterialTheme.colorScheme.error,
    titleContentColor = MaterialTheme.colorScheme.error,
  )
}
