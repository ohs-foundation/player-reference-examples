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
package dev.ohs.workflow.examples.workflow

import dev.ohs.fhir.engine.FhirEngine
import dev.ohs.workflow.examples.data.DataChangeSignal

/** Runs a multi-step write as one unit: every write lands, or none does. */
fun interface Transactor {
  suspend fun atomically(block: suspend () -> Unit)
}

/** A [Transactor] over the engine's database transaction; lists refresh once it commits. */
class EngineTransactor(private val engine: FhirEngine) : Transactor {
  override suspend fun atomically(block: suspend () -> Unit) {
    engine.withTransaction { block() }
    DataChangeSignal.notifyChanged()
  }
}
