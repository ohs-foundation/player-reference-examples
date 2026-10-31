/** @module Interface who:smart-trust/issuer@0.1.0 **/
export function prepare(req: IssueRequest, reg: Registry): PendingSignature;
export function finish(pending: PendingSignature, signature: Uint8Array): IssuedHcert;
export type CoseAlgorithm = import('./who-smart-trust-types.js').CoseAlgorithm;
export type Kid = import('./who-smart-trust-types.js').Kid;
export type NumericDate = import('./who-smart-trust-types.js').NumericDate;
export type ValidationIssue = import('./who-smart-trust-types.js').ValidationIssue;
export type Payload = import('./who-smart-trust-payloads.js').Payload;
export type Registry = import('./who-smart-trust-subclaims.js').Registry;
export interface Validity {
  iat: NumericDate,
  exp: NumericDate,
}
export interface IssueRequest {
  payloads: Array<Payload>,
  iss?: string,
  validity: Validity,
  dscDer: Uint8Array,
  alg: CoseAlgorithm,
  allowDevelopmentSubclaims: boolean,
}
export type IssueError = IssueErrorPayloadInvalid | IssueErrorNoPayload | IssueErrorDuplicateSubclaim | IssueErrorDevelopmentSubclaimNotAllowed | IssueErrorUnknownSubclaim | IssueErrorValidityOutsideDsc | IssueErrorBadCertificate | IssueErrorBadSignatureFormat;
export interface IssueErrorPayloadInvalid {
  tag: 'payload-invalid',
  val: Array<ValidationIssue>,
}
export interface IssueErrorNoPayload {
  tag: 'no-payload',
}
export interface IssueErrorDuplicateSubclaim {
  tag: 'duplicate-subclaim',
  val: number,
}
export interface IssueErrorDevelopmentSubclaimNotAllowed {
  tag: 'development-subclaim-not-allowed',
  val: number,
}
export interface IssueErrorUnknownSubclaim {
  tag: 'unknown-subclaim',
  val: number,
}
export interface IssueErrorValidityOutsideDsc {
  tag: 'validity-outside-dsc',
}
export interface IssueErrorBadCertificate {
  tag: 'bad-certificate',
  val: string,
}
export interface IssueErrorBadSignatureFormat {
  tag: 'bad-signature-format',
  val: string,
}
export interface IssuedHcert {
  hc1: string,
  cose: Uint8Array,
}

export class PendingSignature implements Disposable {
  /**
   * This type does not have a public constructor.
   */
  private constructor();
  toBeSigned(): Uint8Array;
  algorithm(): CoseAlgorithm;
  kid(): Kid;
  [Symbol.dispose](): void;
}
