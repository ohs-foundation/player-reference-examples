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

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.ohs.vaxlink.generated.resources.Res
import dev.ohs.vaxlink.generated.resources.scan_camera_permission
import org.jetbrains.compose.resources.stringResource
import org.ncgroup.kscan.BarcodeFormat
import org.ncgroup.kscan.BarcodeResult
import org.ncgroup.kscan.ScannerView

actual val cameraScanningAvailable: Boolean = true

@Composable
actual fun QrScannerView(modifier: Modifier, onResult: (QrScan) -> Unit) {
  val context = LocalContext.current
  var granted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var asked by remember { mutableStateOf(false) }
  val launcher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
      granted = result
      asked = true
    }
  LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

  Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    if (granted) {
      ScannerView(codeTypes = listOf(BarcodeFormat.FORMAT_QR_CODE)) { result ->
        onResult(
          when (result) {
            is BarcodeResult.OnSuccess -> QrScan.Text(result.barcode.data)
            is BarcodeResult.OnFailed -> QrScan.Failed(result.exception.message ?: "scan failed")
            is BarcodeResult.OnCanceled -> QrScan.Cancelled
          }
        )
      }
    } else if (asked) {
      Text(stringResource(Res.string.scan_camera_permission), Modifier.padding(24.dp))
    }
  }
}
