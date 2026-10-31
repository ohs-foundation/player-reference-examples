/** @module Interface who:smart-trust/cose@0.1.0 **/
export function kidFromDsc(dscDer: Uint8Array): Kid;
export function kidToBase64(k: Kid, alphabet: Base64Alphabet): string;
export function kidFromBase64(text: string): Kid;
export function parseSign1(bytes: Uint8Array, limits: DecodeLimits): Sign1;
export function sigStructure(protected_: Uint8Array, payload: Uint8Array): Uint8Array;
export function verifySign1(s: Sign1, dscDer: Uint8Array): void;
export function ecdsaDerToRaw(der: Uint8Array): Uint8Array;
export function ecdsaRawToDer(raw: Uint8Array): Uint8Array;
export type CoseAlgorithm = import('./who-smart-trust-types.js').CoseAlgorithm;
export type Kid = import('./who-smart-trust-types.js').Kid;
export type DecodeLimits = import('./who-smart-trust-types.js').DecodeLimits;
export type CoseError = CoseErrorMalformed | CoseErrorHeaderInvalid | CoseErrorUnsupportedAlgorithm | CoseErrorBadSignatureFormat | CoseErrorSignatureInvalid | CoseErrorBadCertificate;
export interface CoseErrorMalformed {
  tag: 'malformed',
  val: string,
}
export interface CoseErrorHeaderInvalid {
  tag: 'header-invalid',
  val: string,
}
export interface CoseErrorUnsupportedAlgorithm {
  tag: 'unsupported-algorithm',
  val: number,
}
export interface CoseErrorBadSignatureFormat {
  tag: 'bad-signature-format',
  val: string,
}
export interface CoseErrorSignatureInvalid {
  tag: 'signature-invalid',
}
export interface CoseErrorBadCertificate {
  tag: 'bad-certificate',
  val: string,
}
export interface Sign1 {
  'protected': Uint8Array,
  payload: Uint8Array,
  signature: Uint8Array,
  alg?: CoseAlgorithm,
  kid?: Kid,
  kidInProtected: boolean,
  tagged: boolean,
}
/**
 * # Variants
 * 
 * ## `"standard"`
 * 
 * ## `"url-safe"`
 */
export type Base64Alphabet = 'standard' | 'url-safe';
