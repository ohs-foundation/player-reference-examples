/** @module Interface who:smart-trust/types@0.1.0 **/
/**
 * # Variants
 * 
 * ## `"es256"`
 * 
 * ## `"ps256"`
 */
export type CoseAlgorithm = 'es256' | 'ps256';
export type Kid = Uint8Array;
export type NumericDate = bigint;
export type CborBytes = Uint8Array;
export interface Subclaim {
  key: number,
  title: string,
  logicalModel?: string,
}
export interface DecodeLimits {
  maxInputChars: number,
  maxInflatedBytes: number,
  maxDepth: number,
  maxItems: number,
  maxStringBytes: number,
}
/**
 * # Variants
 * 
 * ## `"blocking"`
 * 
 * ## `"warning"`
 * 
 * ## `"information"`
 */
export type Severity = 'blocking' | 'warning' | 'information';
export interface ValidationIssue {
  path: string,
  code: string,
  message: string,
  severity: Severity,
}
/**
 * # Variants
 * 
 * ## `"prefix"`
 * 
 * ## `"base45"`
 * 
 * ## `"inflate"`
 * 
 * ## `"cose"`
 * 
 * ## `"header"`
 * 
 * ## `"trust-lookup"`
 * 
 * ## `"signature"`
 * 
 * ## `"key-usage"`
 * 
 * ## `"cwt"`
 * 
 * ## `"time"`
 * 
 * ## `"hcert"`
 * 
 * ## `"payload"`
 */
export type Step = 'prefix' | 'base45' | 'inflate' | 'cose' | 'header' | 'trust-lookup' | 'signature' | 'key-usage' | 'cwt' | 'time' | 'hcert' | 'payload';
/**
 * # Variants
 * 
 * ## `"bad-prefix"`
 * 
 * ## `"unsupported-context-version"`
 * 
 * ## `"base45-invalid"`
 * 
 * ## `"inflate-failed"`
 * 
 * ## `"limit-exceeded"`
 * 
 * ## `"cose-malformed"`
 * 
 * ## `"header-invalid"`
 * 
 * ## `"unsupported-algorithm"`
 * 
 * ## `"kid-not-found"`
 * 
 * ## `"signature-invalid"`
 * 
 * ## `"key-usage-mismatch"`
 * 
 * ## `"expired"`
 * 
 * ## `"not-yet-valid"`
 * 
 * ## `"hcert-missing"`
 * 
 * ## `"multiple-subclaims"`
 * 
 * ## `"payload-invalid"`
 */
export type FailureReason = 'bad-prefix' | 'unsupported-context-version' | 'base45-invalid' | 'inflate-failed' | 'limit-exceeded' | 'cose-malformed' | 'header-invalid' | 'unsupported-algorithm' | 'kid-not-found' | 'signature-invalid' | 'key-usage-mismatch' | 'expired' | 'not-yet-valid' | 'hcert-missing' | 'multiple-subclaims' | 'payload-invalid';
export interface Diagnostic {
  step: Step,
  severity: Severity,
  message: string,
}
