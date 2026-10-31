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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.ncgroup.kscan.BarcodeFormat
import org.ncgroup.kscan.BarcodeResult
import org.ncgroup.kscan.ScannerView

actual val cameraScanningAvailable: Boolean = true

// The browser asks for the camera itself on the first scan; the page must be served over https
// (or localhost) or getUserMedia is withheld and no camera opens.
@Composable
actual fun QrScannerView(modifier: Modifier, onResult: (QrScan) -> Unit) {
  Box(modifier.fillMaxSize()) {
    ScannerView(codeTypes = listOf(BarcodeFormat.FORMAT_QR_CODE)) { result ->
      onResult(
        when (result) {
          is BarcodeResult.OnSuccess -> QrScan.Text(result.barcode.data)
          is BarcodeResult.OnFailed -> QrScan.Failed(result.exception.message ?: "scan failed")
          is BarcodeResult.OnCanceled -> QrScan.Cancelled
        }
      )
    }
  }
}
