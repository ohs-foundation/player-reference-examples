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
package dev.ohs.vaxlink.hcert

/**
 * iOS has no engine yet: the Kotlin library targets JVM and Node, and the Rust component is for
 * browsers. A Kotlin/Native adapter on Security.framework is feasible for the verify path but no
 * demo device is an iPhone, so the engine refuses to load rather than pretend. The trust list
 * cache, the mapper and the audit builder work on iOS as they are.
 */
actual suspend fun loadHcertEngine(): HcertEngine =
  throw UnsupportedOperationException("The HCERT verification engine has no iOS build yet")
