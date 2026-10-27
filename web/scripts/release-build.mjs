import { spawnSync } from "node:child_process";

const [major, minor] = process.versions.node.split(".").map(Number);
if (major < 22 || (major === 22 && minor < 12)) {
  throw new Error(
    "Release builds require Node >=22.12 and npm on PATH. Runtime needs Java 25 only.",
  );
}
for (const args of [
  ["ci", "--no-audit", "--no-fund"],
  ["run", "release:check"],
]) {
  const windows = process.platform === "win32";
  const result = spawnSync(
    windows ? (process.env.ComSpec ?? "cmd.exe") : "npm",
    windows ? ["/d", "/s", "/c", `npm.cmd ${args.join(" ")}`] : args,
    { stdio: "inherit" },
  );
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(
      `Locked frontend build failed: npm ${args.join(" ")} (exit ${result.status}, signal ${result.signal})`,
    );
  }
}
