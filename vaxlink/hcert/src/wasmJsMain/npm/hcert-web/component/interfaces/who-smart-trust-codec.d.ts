/** @module Interface who:smart-trust/codec@0.1.0 **/
export function base45Encode(data: Uint8Array): string;
export function base45Decode(text: string): Uint8Array;
export function zlibCompress(data: Uint8Array, level: number): Uint8Array;
export function zlibInflate(data: Uint8Array, maxOut: number): Uint8Array;
export function detectContext(text: string): [string, string];
export function toHc1(cose: Uint8Array): string;
export function fromHc1(text: string, limits: DecodeLimits): Uint8Array;
export function cborDeterministic(cbor: CborBytes, limits: DecodeLimits): CborBytes;
export type DecodeLimits = import('./who-smart-trust-types.js').DecodeLimits;
export type CborBytes = import('./who-smart-trust-types.js').CborBytes;
export type CodecError = CodecErrorBase45Invalid | CodecErrorInflateFailed | CodecErrorLimitExceeded | CodecErrorCborMalformed | CodecErrorBadPrefix | CodecErrorUnsupportedContextVersion;
export interface CodecErrorBase45Invalid {
  tag: 'base45-invalid',
  val: string,
}
export interface CodecErrorInflateFailed {
  tag: 'inflate-failed',
  val: string,
}
export interface CodecErrorLimitExceeded {
  tag: 'limit-exceeded',
  val: string,
}
export interface CodecErrorCborMalformed {
  tag: 'cbor-malformed',
  val: string,
}
export interface CodecErrorBadPrefix {
  tag: 'bad-prefix',
}
export interface CodecErrorUnsupportedContextVersion {
  tag: 'unsupported-context-version',
  val: string,
}
