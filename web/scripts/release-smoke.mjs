import assert from "node:assert/strict";
import { spawn, spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import {
  mkdtemp,
  mkdir,
  readFile,
  readdir,
  realpath,
  rm,
} from "node:fs/promises";
import { createServer } from "node:net";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { setTimeout as delay } from "node:timers/promises";

const jar = resolve(process.argv[2] ?? "");
assert.ok(process.argv[2], "Pass the packaged JAR path");
if (process.argv[3]) {
  const hashes = await Promise.all(
    [jar, resolve(process.argv[3])].map(async (path) =>
      createHash("sha256")
        .update(await readFile(path))
        .digest("hex"),
    ),
  );
  assert.equal(
    hashes[0],
    hashes[1],
    "The stable JAR must exactly match the newly built artifact",
  );
}
const java = process.env.JAVA_HOME
  ? join(
      process.env.JAVA_HOME,
      "bin",
      process.platform === "win32" ? "java.exe" : "java",
    )
  : "java";
const root = await realpath(await mkdtemp(join(tmpdir(), "tiwanaku-release-")));
const home = join(root, "fresh-home");
const firstCwd = join(root, "first-cwd");
const secondCwd = join(root, "second-cwd");
const customData = join(root, "custom-data");
const children = new Set();
const evidence = [];
for (const directory of [home, firstCwd, secondCwd, customData])
  await mkdir(directory);

async function unusedPort() {
  const server = createServer();
  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
  const address = server.address();
  assert.ok(address && typeof address === "object");
  await new Promise((resolve, reject) =>
    server.close((error) => (error ? reject(error) : resolve())),
  );
  return address.port;
}
function launch({ port, cwd, data }) {
  const env = { ...process.env };
  delete env.TIWANAKU_DATA_DIR;
  delete env.TIWANAKU_PORT;
  delete env.JDK_JAVA_OPTIONS;
  delete env.JAVA_TOOL_OPTIONS;
  delete env._JAVA_OPTIONS;
  if (data) env.TIWANAKU_DATA_DIR = data;
  env.TIWANAKU_PORT = String(port);
  // The actual game child cannot find Node/npm; the test harness still uses Node.
  env.PATH = dirname(java);
  const started = Date.now();
  const child = spawn(java, [`-Duser.home=${home}`, "-jar", jar, "--console"], {
    cwd,
    env,
    stdio: ["pipe", "pipe", "pipe"],
  });
  children.add(child);
  let log = "";
  let exitCode;
  let error;
  child.stdout.on("data", (chunk) => {
    log += chunk;
  });
  child.stderr.on("data", (chunk) => {
    log += chunk;
  });
  const closed = new Promise((resolve) => {
    child.once("error", (cause) => {
      error = cause;
    });
    child.once("close", (code) => {
      exitCode = code;
      children.delete(child);
      resolve();
    });
  });
  async function stop() {
    if (exitCode === undefined) child.kill();
    await closed;
  }
  async function gracefulStop() {
    let timer;
    try {
      child.stdin.end("quit\n");
      await Promise.race([
        closed,
        new Promise((resolve, reject) => {
          timer = setTimeout(
            () => reject(new Error(`Graceful shutdown timed out:\n${log}`)),
            10_000,
          );
        }),
      ]);
      assert.equal(exitCode, 0, log);
      assert.ok(log.includes("Shutdown completed"), log);
      evidence.push({ check: "console-graceful-shutdown", port });
    } finally {
      clearTimeout(timer);
    }
  }
  async function ready() {
    const deadline = Date.now() + 30_000;
    while (Date.now() < deadline) {
      if (error) throw error;
      if (exitCode !== undefined)
        throw new Error(`JAR exited before ready (${exitCode}):\n${log}`);
      try {
        const response = await fetch(
          `http://localhost:${port}/api/v1/bootstrap`,
          { signal: AbortSignal.timeout(500) },
        );
        if (
          response.ok &&
          log.includes(`Tiwanaku ready: http://localhost:${port}`) &&
          log.includes("Tiwanaku logs:")
        ) {
          evidence.push({
            check: "startup",
            port,
            cwd,
            data: data ?? join(home, ".tiwanaku"),
            millis: Date.now() - started,
          });
          return await response.json();
        }
      } catch (cause) {
        if (
          !(cause instanceof TypeError) &&
          !(cause instanceof Error && cause.name === "TimeoutError")
        )
          throw cause;
      }
      await delay(50);
    }
    throw new Error(`JAR startup timed out:\n${log}`);
  }
  async function refused(message) {
    let timer;
    try {
      await Promise.race([
        closed,
        new Promise((resolve, reject) => {
          timer = setTimeout(
            () => reject(new Error(`Expected refusal timed out:\n${log}`)),
            20_000,
          );
        }),
      ]);
      if (error) throw error;
      assert.notEqual(exitCode, 0, log);
      assert.ok(log.includes(message), log);
      evidence.push({ check: "refused", message });
    } finally {
      clearTimeout(timer);
      await stop();
    }
  }
  return { ready, stop, gracefulStop, refused, log: () => log };
}
async function api(port, path, token, body) {
  const response = await fetch(`http://localhost:${port}${path}`, {
    ...(body === undefined
      ? {}
      : {
          method: "POST",
          headers: {
            Origin: `http://localhost:${port}`,
            "X-Tiwanaku-Token": token,
            "Content-Type": "application/json",
          },
          body: JSON.stringify(body),
        }),
    signal: AbortSignal.timeout(5_000),
  });
  assert.ok(
    response.ok,
    `${path}: ${response.status} ${await response.clone().text()}`,
  );
  return response.json();
}
try {
  const runtime = spawnSync(java, ["-version"], { encoding: "utf8" });
  if (runtime.error) throw runtime.error;
  assert.match(runtime.stderr, /version "25[."]/);
  assert.equal(runtime.status, 0);
  const port = await unusedPort();
  const first = launch({ port, cwd: firstCwd });
  const bootstrap = await first.ready();
  assert.ok(first.log().includes(join(home, ".tiwanaku")));
  const response = await fetch(`http://localhost:${port}/play`);
  const html = await response.text();
  assert.equal(response.status, 200);
  const assets = [...html.matchAll(/(?:src|href)="([^"]+\.(?:js|css))"/g)].map(
    (match) => match[1],
  );
  assert.ok(assets.length >= 2, "Packaged hashed JS/CSS are required");
  for (const asset of assets) {
    assert.ok(asset.startsWith("/assets/"), asset);
    const resource = await fetch(`http://localhost:${port}${asset}`);
    assert.equal(resource.status, 200, asset);
    assert.ok((await resource.text()).length > 100, asset);
  }
  for (const path of [
    "/api/v1/missing",
    "/assets/missing.js",
    "/unknown-page",
  ]) {
    assert.equal(
      (await fetch(`http://localhost:${port}${path}`)).status,
      404,
      path,
    );
  }
  const job = await api(
    port,
    "/api/v1/generation-jobs",
    bootstrap.requestToken,
    {
      columns: 2,
      rows: 1,
      maxRegionSize: 5,
      difficulty: "EASY",
      seed: 3045,
    },
  );
  const deadline = Date.now() + 10_000;
  let state;
  do {
    state = await api(port, `/api/v1/generation-jobs/${job.jobId}`);
    if (state.status !== "GENERATED") await delay(50);
  } while (state.status === "QUEUED" && Date.now() < deadline);
  assert.equal(state.status, "GENERATED");
  const editorSessionId = crypto.randomUUID();
  const created = await api(port, "/api/v1/attempts", bootstrap.requestToken, {
    requestId: crypto.randomUUID(),
    generationJobId: job.jobId,
    editorSessionId,
  });
  const cellIndex = created.givens.indexOf(0);
  const command = {
    commandId: crypto.randomUUID(),
    expectedRevision: created.revision,
    editorSessionId,
    type: "SET_VALUE",
    cellIndex,
    value: created.givens[1 - cellIndex] === 1 ? 2 : 1,
  };
  const completed = await api(
    port,
    `/api/v1/attempts/${created.attemptId}/commands`,
    bootstrap.requestToken,
    command,
  );
  assert.equal(completed.status, "COMPLETED");
  const result = await api(
    port,
    `/api/v1/attempts/${created.attemptId}/result`,
  );
  const secondPort = await unusedPort();
  await launch({ port: secondPort, cwd: secondCwd }).refused(
    "already holds the data directory lock",
  );
  await launch({ port, cwd: secondCwd, data: customData }).refused(
    `Port ${port} was already in use`,
  );
  await first.gracefulStop();
  const reopened = launch({
    port: secondPort,
    cwd: secondCwd,
    data: join(home, ".tiwanaku"),
  });
  const next = await reopened.ready();
  const restored = await api(
    secondPort,
    `/api/v1/attempts/${created.attemptId}?editorSessionId=${editorSessionId}`,
  );
  assert.deepEqual(restored.values, completed.values);
  assert.equal(restored.revision, completed.revision);
  assert.deepEqual(
    await api(secondPort, `/api/v1/attempts/${created.attemptId}/result`),
    result,
  );
  assert.equal(
    (
      await api(
        secondPort,
        `/api/v1/attempts/${created.attemptId}/commands`,
        next.requestToken,
        command,
      )
    ).revision,
    completed.revision,
  );
  await reopened.gracefulStop();
  const custom = launch({ port, cwd: secondCwd, data: customData });
  await custom.ready();
  assert.ok(custom.log().includes(customData));
  await custom.gracefulStop();
  assert.ok(
    (await readdir(join(home, ".tiwanaku"))).includes("tiwanaku.mv.db"),
  );
  assert.ok((await readdir(customData)).includes("tiwanaku.mv.db"));
  assert.ok(!(await readdir(firstCwd)).includes("tiwanaku.mv.db"));
  evidence.push({ check: "local-assets-and-routing", assets });
  evidence.push({
    check: "cwd-port-data-reopen-completion-retry",
    attemptId: created.attemptId,
    revision: restored.revision,
  });
  console.log(
    "Release JAR smoke passed:\n" + JSON.stringify(evidence, null, 2),
  );
} finally {
  await Promise.all(
    [...children].map(async (child) => {
      const closed = new Promise((resolve) => child.once("close", resolve));
      child.kill();
      await closed;
    }),
  );
  await rm(root, { recursive: true, force: true });
}
