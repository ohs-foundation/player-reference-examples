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

import dev.ohs.workflow.examples.auth.AppRole
import dev.ohs.workflow.examples.auth.PendingAuth
import dev.ohs.workflow.examples.auth.Session
import dev.ohs.workflow.examples.auth.SessionStore
import dev.ohs.workflow.examples.auth.UserContext
import dev.ohs.workflow.examples.auth.UserInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest

private class FakeSessionStore(initial: Session?) : SessionStore {
  private val _session = MutableStateFlow(initial)
  override val session: StateFlow<Session?> = _session

  override suspend fun load(): Session? = _session.value

  override suspend fun save(session: Session) {
    _session.value = session
  }

  override suspend fun clear() {
    _session.value = null
  }

  override suspend fun savePending(pending: PendingAuth) = Unit

  override suspend fun takePending(): PendingAuth? = null
}

class UserContextViewModelTest {
  private val context = UserContext(AppRole.NURSE, "p1", "o1", "l1")
  private val session =
    Session(
      accessToken = "token-1",
      refreshToken = null,
      idToken = null,
      expiresInSeconds = 300,
      obtainedAtEpochSeconds = 0,
      user = UserInfo(),
    )

  @Test
  fun storedContextIsReadyWithoutAServerCall() = runTest {
    var calls = 0
    var resets = 0
    val viewModel =
      UserContextViewModel(
        {
          calls++
          null
        },
        FakeSessionStore(session.copy(context = context)),
        resetSync = { resets++ },
      )

    viewModel.resolve().join()

    assertEquals(UserContextState.Ready(context), viewModel.state.value)
    assertEquals(0, calls)
    assertEquals(0, resets)
  }

  @Test
  fun fetchedContextIsReadySavedAndStartsAFreshDownload() = runTest {
    val store = FakeSessionStore(session)
    var resets = 0
    val viewModel =
      UserContextViewModel(
        { token -> context.takeIf { token == "token-1" } },
        store,
        resetSync = { resets++ },
      )

    viewModel.resolve().join()

    assertEquals(UserContextState.Ready(context), viewModel.state.value)
    assertEquals(context, store.session.value?.context)
    assertEquals(1, resets)
  }

  @Test
  fun userWithoutAnAppRoleHasNoRole() = runTest {
    val viewModel = UserContextViewModel({ null }, FakeSessionStore(session), resetSync = {})

    viewModel.resolve().join()

    assertEquals(UserContextState.NoRole, viewModel.state.value)
  }

  @Test
  fun failureCanBeRetried() = runTest {
    var fail = true
    val viewModel =
      UserContextViewModel(
        { if (fail) error("offline") else context },
        FakeSessionStore(session),
        resetSync = {},
      )

    viewModel.resolve().join()
    assertIs<UserContextState.Failed>(viewModel.state.value)

    fail = false
    viewModel.resolve().join()
    assertEquals(UserContextState.Ready(context), viewModel.state.value)
  }

  @Test
  fun aTokenRefreshedDuringTheFetchIsKept() = runTest {
    val store = FakeSessionStore(session)
    val viewModel =
      UserContextViewModel(
        {
          store.save(session.copy(accessToken = "token-2"))
          context
        },
        store,
        resetSync = {},
      )

    viewModel.resolve().join()

    assertEquals(session.copy(accessToken = "token-2", context = context), store.session.value)
  }
}
