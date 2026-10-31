// Glue between the Kotlin/Wasm facade (dev.ohs.vaxlink.hcert, WasmHcertEngine.kt) and the SMART
// Trust HCERT Rust library as a WebAssembly component (component/hcert.js, transpiled by jco).
//
// The component implements the same WIT contract as the Kotlin library the Android and desktop
// builds use, so this file only reshapes its results into the JSON envelopes HcertModels.kt
// declares: enum tags become UPPER_SNAKE names, bigint dates become numbers, byte arrays become
// lower-case hex, and a thrown ComponentError becomes a typed failure. Nothing here verifies
// anything itself.

let hcert = null;
let registry = null;

// The library's DEFAULT_LIMITS (hcert-core CodecCose.kt), the bounds applied to untrusted input.
const LIMITS = {
  maxInputChars: 8192,
  maxInflatedBytes: 65536,
  maxDepth: 16,
  maxItems: 4096,
  maxStringBytes: 16384,
};

export const ENGINE_NAME = "litlfred-hcert (Rust, WebAssembly component via jco)";

/** Loads and instantiates the component once. Resolves to the engine's name. */
export async function init() {
  if (!hcert) {
    const mod = await import("./component/hcert.js");
    if (mod.$init) await mod.$init;
    hcert = mod;
    registry = new hcert.subclaims.Registry();
  }
  return ENGINE_NAME;
}

const hex = (bytes) => Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
const num = (v) => (v === undefined || v === null ? undefined : Number(v));
const tagName = (tag) => String(tag).toUpperCase().replace(/-/g, "_");
const diagnostic = (d) => `${tagName(d.step)}: ${d.message}`;

const dvc = (d) => ({
  n: d.n,
  dob: d.dob,
  s: d.s,
  nt: d.nt,
  id: d.id,
  dt: d.dt,
  gn: d.gn,
  vx: { vp: d.vx.vp, dv: d.vx.dv, cn: d.vx.cn, is: d.vx.is, bo: d.vx.bo },
  v: d.v,
});

const vhl = (p) => ({
  url: p.url,
  key: p.key,
  exp: num(p.exp),
  flag: p.flag,
  label: p.label,
  v: num(p.v),
  fhirBaseUrl: p.fhirBaseUrl,
});

const entry = (e) => {
  const out = { subclaim: e.subclaim, keyUsageAuthorised: e.keyUsageAuthorised ?? true };
  const p = e.payload;
  if (p) {
    if (p.tag === "dvc-min") out.dvc = dvc(p.val);
    else if (p.tag === "vhl") out.vhl = vhl(p.val);
    else out.raw = hex(p.val.cbor);
  }
  return out;
};

const failure = (f) => ({
  step: tagName(f.step),
  reason: tagName(f.reason),
  detail: f.detail ?? "",
  diagnostics: (f.diagnostics ?? []).map(diagnostic),
});

/** A thrown ComponentError carries the WIT error value as `payload`; anything else is unexpected. */
const failureOf = (error) => {
  const p = error && error.payload;
  if (p && p.step && p.reason) return failure(p);
  return { step: "COSE", reason: "COSE_MALFORMED", detail: String(error && error.message ? error.message : error), diagnostics: [] };
};

export function readUnverified(hc1) {
  try {
    const u = hcert.verifier.readUnverified(hc1, registry, LIMITS);
    return JSON.stringify({
      decoded: {
        kid: u.kid ? hex(u.kid) : undefined,
        iss: u.iss,
        iat: num(u.iat),
        exp: num(u.exp),
        entries: u.entries.map(entry),
        diagnostics: u.diagnostics.map(diagnostic),
      },
    });
  } catch (e) {
    return JSON.stringify({ failure: failureOf(e) });
  }
}

export function newTrustStore() {
  return new hcert.trust.TrustStore();
}

const trustCall = (fn) => {
  try {
    return JSON.stringify({ count: fn() });
  } catch (e) {
    const p = e && e.payload;
    const text = p && p.tag ? `${tagName(p.tag)}: ${p.val ?? ""}` : String(e && e.message ? e.message : e);
    return JSON.stringify({ error: text });
  }
};

export const addDidDocument = (store, json) => trustCall(() => store.addDidDocument(json));
export const addPem = (store, pem) => trustCall(() => store.addPem(pem));
export const trustSize = (store) => store.size();
export const clearTrust = (store) => store.clear();

export function verify(hc1, store, nowEpochSeconds, clockSkewSeconds, maxPayloads) {
  try {
    const options = { limits: LIMITS, clockSkewSeconds };
    if (maxPayloads > 0) options.maxPayloads = maxPayloads;
    const r = hcert.verifier.verify(hc1, store, registry, BigInt(Math.floor(nowEpochSeconds)), options);
    if (r.tag === "verified") {
      const v = r.val;
      return JSON.stringify({
        verified: {
          iss: v.iss,
          iat: num(v.iat),
          exp: num(v.exp),
          entries: v.entries.map(entry),
          dsc: {
            kid: hex(v.dsc.kid),
            country: v.dsc.country,
            notBefore: num(v.dsc.notBefore),
            notAfter: num(v.dsc.notAfter),
            domain: v.dsc.domain,
            participant: v.dsc.participant,
          },
          diagnostics: v.diagnostics.map(diagnostic),
        },
      });
    }
    return JSON.stringify({ failure: failure(r.val) });
  } catch (e) {
    return JSON.stringify({ failure: failureOf(e) });
  }
}

// Verifiable Health Links: the pure parts. The app does the HTTP around them.

const vhlError = (e) => {
  const p = e && e.payload;
  if (p && p.tag) {
    const val = Array.isArray(p.val) ? p.val.map((i) => i.message).join("; ") : p.val ?? "";
    return `${tagName(p.tag)}: ${val}`;
  }
  return String(e && e.message ? e.message : e);
};

export function buildManifestRequest(vhlJson, recipient, passcode) {
  try {
    const p = JSON.parse(vhlJson);
    const payload = {
      url: p.url,
      key: p.key,
      exp: p.exp == null ? undefined : BigInt(p.exp),
      flag: p.flag ?? undefined,
      label: p.label ?? undefined,
      v: p.v ?? undefined,
      fhirBaseUrl: p.fhirBaseUrl ?? undefined,
    };
    const r = hcert.vhl.buildManifestRequest(payload, recipient, passcode ?? undefined, undefined);
    return JSON.stringify({ request: { url: r.url, contentType: r.contentType, body: r.body } });
  } catch (e) {
    return JSON.stringify({ error: vhlError(e) });
  }
}

export function parseManifest(bundleJson) {
  try {
    const docs = hcert.vhl.parseManifest(bundleJson);
    return JSON.stringify({
      documents: docs.map((d) => ({ url: d.url, reference: d.reference, contentType: d.contentType, title: d.title })),
    });
  } catch (e) {
    return JSON.stringify({ error: vhlError(e) });
  }
}

const base64 = (bytes) => {
  let s = "";
  for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
  return btoa(s);
};

export function decrypt(jwe, key) {
  try {
    const d = hcert.vhl.decrypt(jwe, key);
    return JSON.stringify({ contentType: d.contentType, contentBase64: base64(d.content) });
  } catch (e) {
    return JSON.stringify({ error: vhlError(e) });
  }
}
