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

import org.kotlincrypto.hash.sha2.SHA256

/**
 * A stable identifier for one scanned certificate. A DVC has no certificate id (no UVCI in the
 * CWT), so the SHA-256 of the HC1 string stands in. The same card scanned twice yields the same
 * fingerprint, which is how the clinic detects a certificate it already imported (CLN-09).
 */
object CertificateFingerprint {
  /** Identifier system on the Immunization and the Provenance entity. */
  const val SYSTEM = "http://ohs.dev/vaxlink/identifier/hcert-sha256"

  fun of(hc1: String): String = SHA256().digest(hc1.trim().encodeToByteArray()).toHex()

  internal fun ByteArray.toHex(): String =
    joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
