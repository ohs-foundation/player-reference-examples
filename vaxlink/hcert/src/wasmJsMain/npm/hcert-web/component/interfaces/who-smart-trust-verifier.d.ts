/** @module Interface who:smart-trust/verifier@0.1.0 **/
export function verify(hc1: string, store: TrustStore, reg: Registry, now: NumericDate, options: VerifierOptions): VerificationResult;
export function verifyCose(cose: Uint8Array, store: TrustStore, reg: Registry, now: NumericDate, options: VerifierOptions): VerificationResult;
export function readUnverified(hc1: string, reg: Registry, limits: DecodeLimits): UnverifiedHcert;
export type Kid = import('./who-smart-trust-types.js').Kid;
export type NumericDate = import('./who-smart-trust-types.js').NumericDate;
export type DecodeLimits = import('./who-smart-trust-types.js').DecodeLimits;
export type CborBytes = import('./who-smart-trust-types.js').CborBytes;
export type Step = import('./who-smart-trust-types.js').Step;
export type FailureReason = import('./who-smart-trust-types.js').FailureReason;
export type Diagnostic = import('./who-smart-trust-types.js').Diagnostic;
export type Payload = import('./who-smart-trust-payloads.js').Payload;
export type TrustStore = import('./who-smart-trust-trust.js').TrustStore;
export type TrustedDsc = import('./who-smart-trust-trust.js').TrustedDsc;
export type Registry = import('./who-smart-trust-subclaims.js').Registry;
export interface VerifierOptions {
  limits: DecodeLimits,
  clockSkewSeconds: number,
  maxPayloads?: number,
}
export interface HcertEntry {
  subclaim: number,
  payload: Payload,
  keyUsageAuthorised: boolean,
}
export interface VerifiedHcert {
  iss?: string,
  iat: NumericDate,
  exp: NumericDate,
  entries: Array<HcertEntry>,
  dsc: TrustedDsc,
  otherClaims: Array<[CborBytes, CborBytes]>,
  diagnostics: Array<Diagnostic>,
}
export interface VerificationFailure {
  step: Step,
  reason: FailureReason,
  detail: string,
  diagnostics: Array<Diagnostic>,
}
export type VerificationResult = VerificationResultVerified | VerificationResultFailed;
export interface VerificationResultVerified {
  tag: 'verified',
  val: VerifiedHcert,
}
export interface VerificationResultFailed {
  tag: 'failed',
  val: VerificationFailure,
}
export interface UnverifiedEntry {
  subclaim: number,
  payload?: Payload,
}
export interface UnverifiedHcert {
  kid?: Kid,
  iss?: string,
  iat?: NumericDate,
  exp?: NumericDate,
  entries: Array<UnverifiedEntry>,
  diagnostics: Array<Diagnostic>,
}
