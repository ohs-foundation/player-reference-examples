/** @module Interface who:smart-trust/vhl@0.1.0 **/
export function validate(p: VhlPayload, now: NumericDate | undefined): Array<ValidationIssue>;
export function toVhlink(p: VhlPayload): string;
export function fromVhlink(text: string): VhlPayload;
export function newKey(): string;
export function buildManifestRequest(p: VhlPayload, recipient: string, passcode: string | undefined, embeddedLengthMax: number | undefined): ManifestRequest;
export function parseManifest(bundleJson: string): Array<ManifestDocument>;
export function encrypt(content: Uint8Array, contentType: string, key: string): string;
export function decrypt(jwe: string, key: string): Decrypted;
export function buildSignatureBase(r: SignatureRequest): SignatureBase;
export function signatureHeader(signature: Uint8Array, alg: string): string;
export type ValidationIssue = import('./who-smart-trust-types.js').ValidationIssue;
export type NumericDate = import('./who-smart-trust-types.js').NumericDate;
export interface VhlPayload {
  url: string,
  key: string,
  exp?: bigint,
  flag?: string,
  label?: string,
  v?: number,
  fhirBaseUrl?: string,
}
export type VhlError = VhlErrorInvalid | VhlErrorNotAVhlink | VhlErrorManifest | VhlErrorDecryptFailed | VhlErrorUnsupported;
export interface VhlErrorInvalid {
  tag: 'invalid',
  val: Array<ValidationIssue>,
}
export interface VhlErrorNotAVhlink {
  tag: 'not-a-vhlink',
  val: string,
}
export interface VhlErrorManifest {
  tag: 'manifest',
  val: string,
}
export interface VhlErrorDecryptFailed {
  tag: 'decrypt-failed',
  val: string,
}
export interface VhlErrorUnsupported {
  tag: 'unsupported',
  val: string,
}
export interface ManifestRequest {
  url: string,
  contentType: string,
  body: string,
}
export interface ManifestDocument {
  url?: string,
  reference?: string,
  contentType?: string,
  title?: string,
}
export interface Decrypted {
  contentType?: string,
  content: Uint8Array,
}
export interface SignatureRequest {
  method: string,
  authority: string,
  path: string,
  contentType?: string,
  body?: Uint8Array,
  keyid: string,
  alg: string,
  created: NumericDate,
}
export interface SignatureBase {
  contentDigest?: string,
  signatureInput: string,
  base: Uint8Array,
}
