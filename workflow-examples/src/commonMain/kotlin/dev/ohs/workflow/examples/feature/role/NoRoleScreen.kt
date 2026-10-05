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
package dev.ohs.workflow.examples.feature.role

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import player_reference_examples.workflow_examples.generated.resources.Res
import player_reference_examples.workflow_examples.generated.resources.home_sign_out
import player_reference_examples.workflow_examples.generated.resources.role_failed_title
import player_reference_examples.workflow_examples.generated.resources.role_none_body
import player_reference_examples.workflow_examples.generated.resources.role_none_title
import player_reference_examples.workflow_examples.generated.resources.role_retry

/** Shown instead of the app when the user's role is unknown ([message] null) or failed to load. */
@Composable
fun NoRoleScreen(message: String?, onRetry: (() -> Unit)?, onSignOut: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      stringResource(
        if (message == null) Res.string.role_none_title else Res.string.role_failed_title
      ),
      style = MaterialTheme.typography.headlineSmall,
    )
    Text(
      message ?: stringResource(Res.string.role_none_body),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
    )
    onRetry?.let { Button(onClick = it) { Text(stringResource(Res.string.role_retry)) } }
    OutlinedButton(onClick = onSignOut) { Text(stringResource(Res.string.home_sign_out)) }
  }
}
