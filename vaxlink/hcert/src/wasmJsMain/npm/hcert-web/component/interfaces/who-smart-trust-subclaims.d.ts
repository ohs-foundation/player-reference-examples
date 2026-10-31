/** @module Interface who:smart-trust/subclaims@0.1.0 **/
export type Subclaim = import('./who-smart-trust-types.js').Subclaim;
/**
 * # Variants
 * 
 * ## `"dvc-min"`
 * 
 * ## `"vhl"`
 */
export type PayloadKind = 'dvc-min' | 'vhl';

export class Registry implements Disposable {
  constructor()
  lookup(key: number): Subclaim | undefined;
  register(s: Subclaim): void;
  all(): Array<Subclaim>;
  subclaimFor(kind: PayloadKind): number | undefined;
  setSubclaimFor(kind: PayloadKind, key: number): void;
  [Symbol.dispose](): void;
}
