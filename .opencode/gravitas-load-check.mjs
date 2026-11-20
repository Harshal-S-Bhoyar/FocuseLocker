// Loader + behavior check for .opencode/plugins/gravitas.js
// Mirrors the server requirement: default definition with id + setup/effect.
// Behavior checks run the real `gravitas guard` through the hook: destructive
// commands must throw, unread writes to existing in-scope files must throw,
// a write after reading the same file must pass (plugin read tracking), and
// the override must not weaken scope enforcement.
const mod = await import("./plugins/gravitas.js");

const def = mod.default;
if (!def || typeof def !== "object") throw new Error("NO_DEFAULT_EXPORT");
if (typeof def.id !== "string" || def.id.length === 0) throw new Error("NO_ID");
if (typeof def.setup !== "function" && typeof def.effect !== "function")
  throw new Error("NO_SETUP_OR_EFFECT");
console.log("LOADER_OK id=" + def.id);

const hooks = {};
const fakeCtx = {
  directory: process.cwd(),
  tool: {
    hook: async (name, fn) => {
      hooks[name] = fn;
    },
  },
};
await def.setup(fakeCtx);
if (typeof hooks["execute.before"] !== "function") throw new Error("NO_HOOK");
console.log("HOOK_OK");

// Allow path: real guard allows reading README.
await hooks["execute.before"]({
  tool: "read",
  input: { path: "README.md" },
});
console.log("ALLOW_OK");

// Deny path: real guard denies destructive commands; the hook must throw.
let denied = false;
try {
  await hooks["execute.before"]({
    tool: "bash",
    input: { command: "rm -rf /" },
  });
} catch (error) {
  if (/Gravitas denied/.test(error.message)) denied = true;
  else throw error;
}
if (!denied) throw new Error("DENY_NOT_ENFORCED");
console.log("DENY_OK");

// Read-before-write, unread: a write to an existing in-scope file that this
// process never read must still be denied. opencode.json exists and is in
// allowed_write_scope, so only rule 4 (read-before-write) can produce this.
let unreadDenied = false;
try {
  await hooks["execute.before"]({
    tool: "edit",
    input: { path: "opencode.json" },
  });
} catch (error) {
  if (/has not been read/.test(error.message)) unreadDenied = true;
  else throw error;
}
if (!unreadDenied) throw new Error("UNREAD_WRITE_NOT_DENIED");
console.log("UNREAD_WRITE_DENIED_OK");

// Read-before-write, satisfied: after reading the same file the guard still
// reports read-before-write; the plugin's read record must let the write pass.
await hooks["execute.before"]({
  tool: "read",
  input: { path: "opencode.json" },
});
console.log("READ_OK");
await hooks["execute.before"]({
  tool: "edit",
  input: { path: "opencode.json" },
});
console.log("READ_THEN_WRITE_OK");

// Narrowness: a read must not excuse a write outside allowed_write_scope.
// README.md exists but is not in scope; the guard's scope denial must throw
// even though the file was read above.
await hooks["execute.before"]({
  tool: "read",
  input: { path: "README.md" },
});
let scopeDenied = false;
try {
  await hooks["execute.before"]({
    tool: "edit",
    input: { path: "README.md" },
  });
} catch (error) {
  if (/Scope violation/.test(error.message)) scopeDenied = true;
  else throw error;
}
if (!scopeDenied) throw new Error("SCOPE_WRITE_NOT_DENIED");
console.log("SCOPE_STILL_DENIED_OK");

console.log("GUARD_TRACKING_OK");
