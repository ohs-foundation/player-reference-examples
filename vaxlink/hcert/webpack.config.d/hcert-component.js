// The jco-transpiled component (src/wasmJsMain/npm/hcert-web/component/hcert.js) keeps a Node-only
// branch that imports "node:fs/promises" to read its core wasm from disk. In the browser that branch
// is never taken, so the module is declared external and resolves to an empty object instead of
// failing on the "node:" scheme, which webpack checks before any alias.
config.externals = [].concat(config.externals || [], [{ "node:fs/promises": "var {}" }]);
