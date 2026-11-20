/**
 * Gravitas enforcement shim for OpenCode.
 *
 * Maps OpenCode tool calls onto the normalized Gravitas action model and
 * asks `gravitas guard` (Python policy engine) for a decision. Denials
 * throw, which blocks the tool call. There is no "ask" channel in
 * tool.execute.before, so force_ask resolves to a hard deny here -- a
 * documented, deliberate difference from Antigravity.
 *
 * Read tracking: `gravitas guard` evaluates read-before-write with an empty
 * read set (cmd_guard passes reads=set() and accepts no reads channel), so
 * its rule 4 fires for every write to an existing file no matter what was
 * read. The Antigravity adapter loads reads from the session evidence ledger
 * (pre_tool.py load_reads); this shim mirrors that intent in-process: every
 * file this server process has read is remembered, and a denial is overridden
 * only when its reason is read-before-write for a path on that list. All
 * other decisions, including scope violations and destructive commands, stay
 * exactly as the guard returned them.
 *
 * Requires `gravitas` (pip install gravitas) and python3 on PATH. If the
 * guard is unreachable, mutations are denied fail-closed while read-only
 * calls are allowed with a warning -- check `gravitas doctor` in the
 * project root.
 */

import { execFileSync } from "node:child_process";
import { isAbsolute, resolve } from "node:path";

export function toEnvelope(tool, args, directory) {
  const envelope = {
    tool: String(tool || ""),
    args: args && typeof args === "object" ? args : {},
    conversation_id: "",
    workspace_roots: directory ? [directory] : [],
    host: "opencode",
    event: "before_action",
  };
  const a = envelope.args;
  switch (envelope.tool) {
    case "read":
      envelope.tool = "view_file";
      envelope.args = { AbsolutePath: a.filePath || a.path || "" };
      break;
    case "edit":
    case "write":
      envelope.tool = "write_to_file";
      envelope.args = { TargetFile: a.filePath || a.path || "" };
      break;
    case "patch":
      envelope.tool = "replace_file_content";
      envelope.args = { target_file: a.filePath || a.path || "" };
      break;
    case "bash":
      envelope.tool = "run_command";
      envelope.args = { CommandLine: a.command || "" };
      break;
    case "grep":
      envelope.tool = "grep_search";
      break;
    case "glob":
    case "list":
      envelope.tool = "list_dir";
      break;
    default:
      break;
  }
  return envelope;
}

// Normalized tools with no write effect: safe to allow with a warning when
// the guard itself cannot be reached. Everything else denies fail-closed.
const READ_ONLY_TOOLS = new Set([
  "view_file",
  "grep_search",
  "find_by_name",
  "list_dir",
]);

// Files read through this plugin since the server process started, keyed by
// the same resolution the guard applies (workspace-relative paths resolve
// against the project directory; Windows comparisons fold case).
const readPaths = new Set();

const READ_BEFORE_WRITE =
  /^Read-before-write violation: (.+?) has not been read this session\./;

function normalizePath(directory, value) {
  if (!value || typeof value !== "string") return "";
  const absolute =
    isAbsolute(value) ? value : resolve(directory || process.cwd(), value);
  return process.platform === "win32" ? absolute.toLowerCase() : absolute;
}

function askGuard(directory, envelope) {
  try {
    const out = execFileSync("gravitas", ["guard", "--envelope", JSON.stringify(envelope)], {
      encoding: "utf8",
      timeout: 10000,
      cwd: directory,
    });
    return JSON.parse(out);
  } catch (error) {
    if (READ_ONLY_TOOLS.has(envelope.tool)) {
      return { decision: "allow", warning: `gravitas guard unreachable: ${error.message}` };
    }
    return { decision: "deny", reason: `gravitas guard unreachable: ${error.message}` };
  }
}

function gate(directory, tool, args) {
  const envelope = toEnvelope(tool, args, directory);
  const verdict = askGuard(directory, envelope);
  if (envelope.tool === "view_file") {
    if (verdict && verdict.decision !== "deny") {
      const target = normalizePath(directory, envelope.args.AbsolutePath);
      if (target) readPaths.add(target);
    }
    return verdict;
  }
  if (verdict && verdict.decision === "deny") {
    const match = READ_BEFORE_WRITE.exec(String(verdict.reason || ""));
    if (match && readPaths.has(normalizePath(directory, match[1]))) {
      return { decision: "allow" };
    }
  }
  return verdict;
}

export const GravitasPlugin = async ({ directory }) => {
  return {
    "tool.execute.before": async (input, output) => {
      const verdict = gate(directory, input.tool, output?.args ?? input?.args ?? {});
      if (verdict && verdict.decision === "deny") {
        throw new Error(`Gravitas denied ${input.tool}: ${verdict.reason || "policy decision"}`);
      }
    },
  };
};

// V2 server loader contract: default definition with id + setup.
// Registers the same guard hook via ctx instead of the V1 return shape.
export default {
  id: "gravitas",
  async setup(ctx) {
    // V2 hosts expose the active project directory at ctx.location; fall back
    // to the V1 shape, then the process directory, so contract loading keeps
    // working whatever the host provides.
    const directory = ctx?.location?.directory ?? ctx?.directory ?? process.cwd();
    await ctx.tool.hook("execute.before", async (event) => {
      const tool = event?.tool ?? event?.input?.tool ?? "";
      const args =
        event?.input?.output?.args ?? event?.input?.args ?? event?.input ?? {};
      const verdict = gate(directory, tool, args);
      if (verdict && verdict.decision === "deny") {
        throw new Error(`Gravitas denied ${tool}: ${verdict.reason || "policy decision"}`);
      }
    });
  },
};
