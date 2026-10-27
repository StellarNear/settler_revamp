import { spawn } from "node:child_process";
import { createWriteStream } from "node:fs";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { createServer } from "node:net";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import { finished } from "node:stream/promises";

export default async function setup() {
  const probe = createServer();
  await new Promise((resolve, reject) => {
    probe.once("error", reject);
    probe.listen(18093, "127.0.0.1", resolve);
  });
  await new Promise((resolve, reject) =>
    probe.close((error) => (error ? reject(error) : resolve())),
  );
  const directory = await mkdtemp(join(tmpdir(), "tiwanaku-e2e-"));
  const outputPath = join(directory, "launch.log");
  const output = createWriteStream(outputPath);
  const java = spawn(
    "java",
    [
      "-jar",
      resolve("../server/target/p02-build/tiwanaku.jar"),
      "--server.port=18093",
    ],
    {
      env: { ...process.env, TIWANAKU_DATA_DIR: directory },
      stdio: ["ignore", "pipe", "pipe"],
    },
  );
  java.stdout.pipe(output, { end: false });
  java.stderr.pipe(output, { end: false });
  let exited = false;
  let spawnError;
  const exit = new Promise((resolve) => {
    java.once("exit", () => {
      exited = true;
      resolve();
    });
    java.once("error", (error) => {
      spawnError = error;
      exited = true;
      resolve();
    });
  });
  async function stop() {
    if (!exited) java.kill();
    await exit;
    await Promise.all([finished(java.stdout), finished(java.stderr)]);
    output.end();
    await finished(output);
    await rm(directory, { recursive: true, force: true });
  }
  let ready = false;
  try {
    const deadline = Date.now() + 30_000;
    while (Date.now() < deadline) {
      if (spawnError) throw spawnError;
      if (exited) throw new Error(await readFile(outputPath, "utf8"));
      try {
        const response = await globalThis.fetch(
          "http://localhost:18093/api/v1/bootstrap",
          {
            signal: globalThis.AbortSignal.timeout(1_000),
          },
        );
        if (response.ok) {
          ready = true;
          return stop;
        }
      } catch (error) {
        if (!(error instanceof TypeError) && error.name !== "TimeoutError")
          throw error;
      }
      await delay(100);
    }
    throw new Error(
      `Java test server did not become ready: ${await readFile(outputPath, "utf8")}`,
    );
  } finally {
    if (!ready) await stop();
  }
}
