/** @module Interface who:smart-trust/dvc@0.1.0 **/
export function validate(p: DvcMin): Array<ValidationIssue>;
export function toCbor(p: DvcMin): CborBytes;
export function fromCbor(cbor: CborBytes, limits: DecodeLimits): DvcMin;
export type ValidationIssue = import('./who-smart-trust-types.js').ValidationIssue;
export type CborBytes = import('./who-smart-trust-types.js').CborBytes;
export type DecodeLimits = import('./who-smart-trust-types.js').DecodeLimits;
export interface DvcMinVaccineDetails {
  vp: string,
  dv: string,
  cn?: string,
  is?: string,
  bo: string,
}
export interface DvcMin {
  n: string,
  dob: string,
  s: string,
  nt: string,
  id?: string,
  dt?: string,
  gn?: string,
  vx: DvcMinVaccineDetails,
  v: string,
  extensions: Array<[string, CborBytes]>,
}
