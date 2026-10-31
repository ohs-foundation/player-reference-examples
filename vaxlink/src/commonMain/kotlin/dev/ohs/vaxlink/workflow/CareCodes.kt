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
package dev.ohs.vaxlink.workflow

/**
 * SNOMED CT "Patient referral" (3457005): the code on a ServiceRequest that refers a child to the
 * facility. NE-2 creates these with the reason "foreign immunization record to verify".
 */
const val REFERRAL_CODE = "3457005"

/**
 * Task.code for a follow-up the community health worker owes after a facility visit. The same code
 * sits in `files/states/Binary-FollowUp.json` and `files/sync/chw.json`; rename all three together.
 */
const val FOLLOW_UP_TASK = "iccm-follow-up"
