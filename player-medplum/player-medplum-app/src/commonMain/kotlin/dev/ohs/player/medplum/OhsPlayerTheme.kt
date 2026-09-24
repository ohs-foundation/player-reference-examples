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
package dev.ohs.player.medplum

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import dev.ohs.player.medplum.data.settings.ThemePreference

/*
 * OHS Medplum EIR palette.
 *
 * A clinical blue/green pair: blue carries the chrome, green means "done". The app reads
 * as part of that family rather than as a look-alike:
 *
 *   #40A1DA  primary      clinical blue
 *   #32647D  secondary    deep slate-blue
 *   #8FD1EC  tint         light blue
 *   #8EC641  accent       green -- "immunised"
 *   #1E2328  neutral      body text
 *
 * The green is DARKENED to #6A9B23 where it carries text or a status chip: #8EC641 is a fill
 * colour and does not meet contrast requirements against white at body-text size.
 *
 * Status colours are load-bearing on the register and the due list -- tertiary = complete,
 * secondary = due, error = overdue -- so they are chosen to stay distinguishable rather than
 * merely to match the brand.
 *
 * THE DARK SCHEME KEEPS THE SAME BRAND BLUE. Material's convention is to lighten `primary` for
 * dark themes, but this app paints large chrome with it -- app bars, the login header, the
 * register header, avatars -- all with white on top. Lightening it would turn those into glaring
 * pale slabs, exactly what a dark theme is meant to avoid. #40A1DA reads at 6.5:1 as accent text
 * on the dark surface below, which is comfortably AA, so it can stay put in both schemes. Dark
 * mode therefore changes the SURFACES, not the brand.
 *
 * Every dark pair below was contrast-checked, including the three status chips, which carry
 * clinical meaning and so are the last thing that may quietly wash out:
 *
 *   complete  #D3EDA6 on #33500A   7.2:1
 *   due       #FFDDB0 on #5C3D00   7.6:1
 *   overdue   #FFDAD6 on #93000A   7.2:1
 *
 * (The one pair that does NOT meet AA is white on #40A1DA at 2.9:1 -- but that is the existing
 * light-theme chrome, unchanged here, and it is brand colour rather than a value we can pick.)
 */

private val OhsPrimary = Color(0xFF40A1DA)
private val OhsOnPrimary = Color.White
private val OhsPrimaryContainer = Color(0xFFD6ECF8)
private val OhsOnPrimaryContainer = Color(0xFF00344D)

private val OhsSecondary = Color(0xFF32647D)
private val OhsOnSecondary = Color.White
private val OhsSecondaryContainer = Color(0xFFFFE3C2)
private val OhsOnSecondaryContainer = Color(0xFF4A2800)

private val OhsTertiary = Color(0xFF6A9B23)
private val OhsOnTertiary = Color.White
private val OhsTertiaryContainer = Color(0xFFDDF0BE)
private val OhsOnTertiaryContainer = Color(0xFF1B3300)

private val OhsError = Color(0xFFB3261E)
private val OhsOnError = Color.White
private val OhsErrorContainer = Color(0xFFF9DEDC)
private val OhsOnErrorContainer = Color(0xFF601410)

private val OhsBackground = Color(0xFFFFFBFF)
private val OhsSurface = Color(0xFFFFFBFF)
private val OhsOnSurface = Color(0xFF1E2328)
private val OhsSurfaceVariant = Color(0xFFE1E7EB)
private val OhsOnSurfaceVariant = Color(0xFF41484D)
private val OhsOutline = Color(0xFF71787D)

// Material draws cards, sheets and dividers from the surfaceContainer family and outlineVariant.
// darkColorScheme()/lightColorScheme() default those to Material's own PURPLE-tinted baseline, so
// leaving them unset puts lilac cards on a blue-slate background. They are a neutral ramp stepping
// away from `surface`, tinted to the same blue-slate as the rest of the palette.
private val OhsSurfaceDim = Color(0xFFDDE3E7)
private val OhsSurfaceBright = Color(0xFFFFFBFF)
private val OhsSurfaceContainerLowest = Color(0xFFFFFFFF)
private val OhsSurfaceContainerLow = Color(0xFFF7FAFC)
private val OhsSurfaceContainer = Color(0xFFF1F5F8)
private val OhsSurfaceContainerHigh = Color(0xFFEBEFF3)
private val OhsSurfaceContainerHighest = Color(0xFFE5EAEE)
private val OhsOutlineVariant = Color(0xFFC1C8CD)
private val OhsInverseSurface = Color(0xFF2E3438)
private val OhsInverseOnSurface = Color(0xFFEFF3F6)
private val OhsInversePrimary = Color(0xFF8FD1EC)

// --- dark ---------------------------------------------------------------------------------------
// Neutrals are tinted towards the brand's blue-slate (#1E2328) rather than Material's purple-grey,
// so the two schemes look like one app. Several values are the light scheme's inverses reused on
// purpose: dark onSurface is light surfaceVariant, dark surfaceVariant is light onSurfaceVariant.
private val OhsDarkPrimaryContainer = Color(0xFF004C6F)
private val OhsDarkOnPrimaryContainer = Color(0xFFCDE7F7)

private val OhsDarkSecondary = Color(0xFF9CC9E4)
private val OhsDarkOnSecondary = Color(0xFF14384D)
private val OhsDarkSecondaryContainer = Color(0xFF5C3D00)
private val OhsDarkOnSecondaryContainer = Color(0xFFFFDDB0)

private val OhsDarkTertiary = Color(0xFFA3CC6B)
private val OhsDarkOnTertiary = Color(0xFF1B3300)
private val OhsDarkTertiaryContainer = Color(0xFF33500A)
private val OhsDarkOnTertiaryContainer = Color(0xFFD3EDA6)

private val OhsDarkError = Color(0xFFFFB4AB)
private val OhsDarkOnError = Color(0xFF690005)
private val OhsDarkErrorContainer = Color(0xFF93000A)
private val OhsDarkOnErrorContainer = Color(0xFFFFDAD6)

private val OhsDarkSurface = Color(0xFF101417)
private val OhsDarkOnSurface = Color(0xFFE1E7EB)
private val OhsDarkSurfaceVariant = Color(0xFF41484D)
private val OhsDarkOnSurfaceVariant = Color(0xFFC0C7CC)
private val OhsDarkOutline = Color(0xFF8A9297)
private val OhsDarkSurfaceDim = Color(0xFF101417)
private val OhsDarkSurfaceBright = Color(0xFF363B3F)
private val OhsDarkSurfaceContainerLowest = Color(0xFF0B0F12)
private val OhsDarkSurfaceContainerLow = Color(0xFF181C20)
private val OhsDarkSurfaceContainer = Color(0xFF1C2124)
private val OhsDarkSurfaceContainerHigh = Color(0xFF262B2F)
private val OhsDarkSurfaceContainerHighest = Color(0xFF31363A)
private val OhsDarkOutlineVariant = Color(0xFF41484D)
private val OhsDarkInverseSurface = Color(0xFFE1E7EB)
private val OhsDarkInverseOnSurface = Color(0xFF2E3438)
private val OhsDarkInversePrimary = Color(0xFF00517A)

private val OhsLightColorScheme =
  lightColorScheme(
    primary = OhsPrimary,
    onPrimary = OhsOnPrimary,
    primaryContainer = OhsPrimaryContainer,
    onPrimaryContainer = OhsOnPrimaryContainer,
    secondary = OhsSecondary,
    onSecondary = OhsOnSecondary,
    secondaryContainer = OhsSecondaryContainer,
    onSecondaryContainer = OhsOnSecondaryContainer,
    tertiary = OhsTertiary,
    onTertiary = OhsOnTertiary,
    tertiaryContainer = OhsTertiaryContainer,
    onTertiaryContainer = OhsOnTertiaryContainer,
    error = OhsError,
    onError = OhsOnError,
    errorContainer = OhsErrorContainer,
    onErrorContainer = OhsOnErrorContainer,
    background = OhsBackground,
    onBackground = OhsOnSurface,
    surface = OhsSurface,
    onSurface = OhsOnSurface,
    surfaceVariant = OhsSurfaceVariant,
    onSurfaceVariant = OhsOnSurfaceVariant,
    outline = OhsOutline,
    outlineVariant = OhsOutlineVariant,
    surfaceDim = OhsSurfaceDim,
    surfaceBright = OhsSurfaceBright,
    surfaceContainerLowest = OhsSurfaceContainerLowest,
    surfaceContainerLow = OhsSurfaceContainerLow,
    surfaceContainer = OhsSurfaceContainer,
    surfaceContainerHigh = OhsSurfaceContainerHigh,
    surfaceContainerHighest = OhsSurfaceContainerHighest,
    inverseSurface = OhsInverseSurface,
    inverseOnSurface = OhsInverseOnSurface,
    inversePrimary = OhsInversePrimary,
    surfaceTint = OhsPrimary,
  )

private val OhsDarkColorScheme =
  darkColorScheme(
    // Brand blue and white-on-blue are shared with the light scheme -- see the note above.
    primary = OhsPrimary,
    onPrimary = OhsOnPrimary,
    primaryContainer = OhsDarkPrimaryContainer,
    onPrimaryContainer = OhsDarkOnPrimaryContainer,
    secondary = OhsDarkSecondary,
    onSecondary = OhsDarkOnSecondary,
    secondaryContainer = OhsDarkSecondaryContainer,
    onSecondaryContainer = OhsDarkOnSecondaryContainer,
    tertiary = OhsDarkTertiary,
    onTertiary = OhsDarkOnTertiary,
    tertiaryContainer = OhsDarkTertiaryContainer,
    onTertiaryContainer = OhsDarkOnTertiaryContainer,
    error = OhsDarkError,
    onError = OhsDarkOnError,
    errorContainer = OhsDarkErrorContainer,
    onErrorContainer = OhsDarkOnErrorContainer,
    background = OhsDarkSurface,
    onBackground = OhsDarkOnSurface,
    surface = OhsDarkSurface,
    onSurface = OhsDarkOnSurface,
    surfaceVariant = OhsDarkSurfaceVariant,
    onSurfaceVariant = OhsDarkOnSurfaceVariant,
    outline = OhsDarkOutline,
    outlineVariant = OhsDarkOutlineVariant,
    surfaceDim = OhsDarkSurfaceDim,
    surfaceBright = OhsDarkSurfaceBright,
    surfaceContainerLowest = OhsDarkSurfaceContainerLowest,
    surfaceContainerLow = OhsDarkSurfaceContainerLow,
    surfaceContainer = OhsDarkSurfaceContainer,
    surfaceContainerHigh = OhsDarkSurfaceContainerHigh,
    surfaceContainerHighest = OhsDarkSurfaceContainerHighest,
    inverseSurface = OhsDarkInverseSurface,
    inverseOnSurface = OhsDarkInverseOnSurface,
    inversePrimary = OhsDarkInversePrimary,
    surfaceTint = OhsPrimary,
  )

@Composable
fun OhsPlayerTheme(
  preference: ThemePreference = ThemePreference.System,
  content: @Composable () -> Unit,
) {
  val dark =
    when (preference) {
      ThemePreference.System -> isSystemInDarkTheme()
      ThemePreference.Light -> false
      ThemePreference.Dark -> true
    }
  val colorScheme = if (dark) OhsDarkColorScheme else OhsLightColorScheme
  MaterialTheme(colorScheme = colorScheme) {
    // MaterialTheme does NOT set LocalContentColor -- only Surface does, and this app paints its
    // backgrounds with Modifier.background(). So any Text without an explicit `color` fell through
    // to Compose's default of BLACK. That looked fine on the light scheme by luck and turned the
    // login heading into black-on-black the moment a dark scheme existed. Anchoring it to
    // onSurface makes unstyled text correct in both.
    CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface, content = content)
  }
}
