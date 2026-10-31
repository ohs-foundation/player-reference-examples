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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ohs.vaxlink.generated.resources.Res
import dev.ohs.vaxlink.generated.resources.scan_camera_unavailable
import org.jetbrains.compose.resources.stringResource

// iOS is out of scope for the demo: the verification engine has no iOS build yet either.
actual val cameraScanningAvailable: Boolean = false

@Composable
actual fun QrScannerView(modifier: Modifier, onResult: (QrScan) -> Unit) {
  Box(modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
    Text(stringResource(Res.string.scan_camera_unavailable))
  }
}
