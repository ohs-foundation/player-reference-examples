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
package dev.ohs.vaxlink.feature.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** What the camera gave back. [Text] is the raw QR content, an `HC1:` string for a certificate. */
sealed interface QrScan {
  data class Text(val value: String) : QrScan

  data class Failed(val message: String) : QrScan

  data object Cancelled : QrScan
}

/** Whether this platform build can open a camera; when false the screen offers paste only. */
expect val cameraScanningAvailable: Boolean

/**
 * The live camera QR reader. Android and the web use KScan (zxing-cpp and the browser's
 * BarcodeDetector); the Android actual also asks for the camera permission. Desktop and iOS show a
 * notice, since neither is a demo device, and the paste field beside it does the job.
 */
@Composable expect fun QrScannerView(modifier: Modifier = Modifier, onResult: (QrScan) -> Unit)
