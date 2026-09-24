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

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import dev.ohs.player.medplum.data.settings.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * LocalSystemTheme is what isSystemInDarkTheme() reads, so providing it here simulates a host set
 * to dark or light without touching the machine running the tests.
 */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class OhsPlayerThemeTest {

  private val brandBlue = Color(0xFF40A1DA)

  /** Resolves the scheme the app would actually draw for [preference] on a [host] set that way. */
  private fun ComposeUiTest.schemeFor(
    preference: ThemePreference,
    host: SystemTheme,
  ): Map<String, Color> {
    var captured: Map<String, Color>? = null
    setContent {
      CompositionLocalProvider(LocalSystemTheme provides host) {
        OhsPlayerTheme(preference) {
          val c = MaterialTheme.colorScheme
          captured =
            mapOf(
              "primary" to c.primary,
              "onPrimary" to c.onPrimary,
              "surface" to c.surface,
              "onSurface" to c.onSurface,
              "complete" to c.tertiaryContainer,
              "onComplete" to c.onTertiaryContainer,
              "due" to c.secondaryContainer,
              "onDue" to c.onSecondaryContainer,
              "overdue" to c.errorContainer,
              "onOverdue" to c.onErrorContainer,
              "content" to LocalContentColor.current,
            )
        }
      }
    }
    return captured!!
  }

  @Test
  fun explicitChoice_overridesTheHost() = runComposeUiTest {
    val lightOnDarkHost = schemeFor(ThemePreference.Light, SystemTheme.Dark)
    val darkOnLightHost = schemeFor(ThemePreference.Dark, SystemTheme.Light)

    assertTrue(
      lightOnDarkHost.getValue("surface").luminance() > 0.5f,
      "picking Light must win over a dark host",
    )
    assertTrue(
      darkOnLightHost.getValue("surface").luminance() < 0.1f,
      "picking Dark must win over a light host",
    )
  }

  @Test
  fun system_followsTheHost() = runComposeUiTest {
    val onDarkHost = schemeFor(ThemePreference.System, SystemTheme.Dark)
    val onLightHost = schemeFor(ThemePreference.System, SystemTheme.Light)

    assertTrue(onDarkHost.getValue("surface").luminance() < 0.1f)
    assertTrue(onLightHost.getValue("surface").luminance() > 0.5f)
  }

  @Test
  fun brandChromeIsIdenticalInBothSchemes() = runComposeUiTest {
    // App bars, the login header and avatars are filled with `primary` and labelled `onPrimary`.
    // Material would lighten primary for a dark scheme, turning those into pale slabs -- this app
    // keeps the brand blue in both and changes the surfaces instead.
    val dark = schemeFor(ThemePreference.Dark, SystemTheme.Light)
    val light = schemeFor(ThemePreference.Light, SystemTheme.Light)

    assertEquals(brandBlue, light.getValue("primary"))
    assertEquals(brandBlue, dark.getValue("primary"))
    assertEquals(Color.White, dark.getValue("onPrimary"))
  }

  @Test
  fun defaultContentColourTracksTheScheme() = runComposeUiTest {
    // Text without an explicit colour falls back to LocalContentColor. MaterialTheme does not set
    // it -- only Surface does -- so unstyled text used to render BLACK regardless of scheme, which
    // is invisible on the dark surface. Regression guard for that.
    val dark = schemeFor(ThemePreference.Dark, SystemTheme.Light)
    assertEquals(dark.getValue("onSurface"), dark.getValue("content"))
    assertTrue(dark.getValue("content").luminance() > 0.5f, "unstyled text must be light on dark")
  }

  @Test
  fun statusColoursStayDistinguishableInBothSchemes() = runComposeUiTest {
    // The register and due list encode clinical state as colour. If any two collapse, or a chip's
    // label stops contrasting with its own fill, the card becomes unreadable.
    listOf(ThemePreference.Light, ThemePreference.Dark).forEach { preference ->
      val s = schemeFor(preference, SystemTheme.Light)
      val fills = listOf(s.getValue("complete"), s.getValue("due"), s.getValue("overdue"))
      assertEquals(fills.size, fills.toSet().size, "$preference: status fills must differ")

      listOf("complete" to "onComplete", "due" to "onDue", "overdue" to "onOverdue").forEach {
        (fill, ink) ->
        val contrast = contrastRatio(s.getValue(ink), s.getValue(fill))
        assertTrue(contrast >= 4.5, "$preference $fill chip: $contrast:1 is below AA")
      }
    }
  }

  @Test
  fun accentTextIsLegibleOnBothSurfaces() = runComposeUiTest {
    // `primary` is not only a fill -- the login subtitle draws it as text
    // directly on `surface`. That is the pairing that made keeping one brand blue viable.
    val dark = schemeFor(ThemePreference.Dark, SystemTheme.Light)
    val contrast = contrastRatio(dark.getValue("primary"), dark.getValue("surface"))
    assertTrue(contrast >= 4.5, "brand blue on the dark surface is only $contrast:1")
  }
}

/** WCAG relative-luminance contrast, on Compose's own luminance(). */
private fun contrastRatio(a: Color, b: Color): Double {
  val hi = maxOf(a.luminance(), b.luminance())
  val lo = minOf(a.luminance(), b.luminance())
  return ((hi + 0.05f) / (lo + 0.05f)).toDouble()
}
