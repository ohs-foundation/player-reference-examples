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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ohs.workflow.examples.auth.SessionStore
import dev.ohs.workflow.examples.auth.UserContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UserContextState {
  data object Loading : UserContextState

  data class Ready(val context: UserContext) : UserContextState

  data object NoRole : UserContextState

  data class Failed(val message: String) : UserContextState
}

/**
 * Gates the signed-in app on knowing the user's role. The context is fetched once per session and
 * stored on it, so later launches and background syncs work offline. A fetch means a new sign-in,
 * which restarts sync from scratch: the download cursors belong to whoever signed in before.
 */
internal class UserContextViewModel(
  private val fetchContext: suspend (accessToken: String) -> UserContext?,
  private val store: SessionStore,
  private val resetSync: suspend () -> Unit,
) : ViewModel() {
  private val _state = MutableStateFlow<UserContextState>(UserContextState.Loading)
  val state: StateFlow<UserContextState> = _state.asStateFlow()

  fun resolve(): Job =
    viewModelScope.launch {
      val session = store.session.value ?: return@launch
      session.context?.let {
        _state.value = UserContextState.Ready(it)
        return@launch
      }
      _state.value = UserContextState.Loading
      _state.value =
        try {
          fetchContext(session.accessToken)
            ?.also {
              resetSync()
              store.save(session.copy(context = it))
            }
            ?.let(UserContextState::Ready) ?: UserContextState.NoRole
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          UserContextState.Failed(e.message ?: "Could not load your role.")
        }
    }
}
