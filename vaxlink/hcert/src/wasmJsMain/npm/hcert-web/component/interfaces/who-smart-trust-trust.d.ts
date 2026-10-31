/** @module Interface who:smart-trust/trust@0.1.0 **/
export function verifyDidProof(document: string, signer: string): void;
export type Kid = import('./who-smart-trust-types.js').Kid;
export type NumericDate = import('./who-smart-trust-types.js').NumericDate;
export type TrustError = TrustErrorParse | TrustErrorUnknownTrustDomain | TrustErrorProofInvalid;
export interface TrustErrorParse {
  tag: 'parse',
  val: string,
}
export interface TrustErrorUnknownTrustDomain {
  tag: 'unknown-trust-domain',
  val: string,
}
export interface TrustErrorProofInvalid {
  tag: 'proof-invalid',
  val: string,
}
export interface TrustedDsc {
  der: Uint8Array,
  kid: Kid,
  country?: string,
  notBefore: NumericDate,
  notAfter: NumericDate,
  extendedKeyUsages: Array<string>,
  domain?: string,
  participant?: string,
}

export class TrustStore implements Disposable {
  constructor()
  addPem(pem: string): number;
  addDer(der: Uint8Array): number;
  addDidDocument(json: string): number;
  addVerifiedDidDocument(json: string, signer: string): number;
  loadTrustDomains(codesystemJson: string): number;
  trustDomains(): Array<string>;
  candidates(k: Kid): Array<TrustedDsc>;
  size(): number;
  clear(): void;
  [Symbol.dispose](): void;
}
