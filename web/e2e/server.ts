import { spawn } from "node:child_process";
import { createWriteStream } from "node:fs";
import { copyFile, mkdir, mkdtemp, readFile, rm } from "node:fs/promises";
import { createServer } from "node:net";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import { finished } from "node:stream/promises";

export async function launchServer(
  port: number,
  directory: string,
  userHome?: string,
): Promise<() => Promise<void>> {
  const probe = createServer();
  await new Promise<void>((resolve, reject) => {
    probe.once("error", reject);
    probe.listen(port, "127.0.0.1", resolve);
  });
  await new Promise<void>((resolve, reject) =>
    probe.close((error) => (error ? reject(error) : resolve())),
  );
  const outputPath = join(directory, `launch-${port}.log`);
  const output = createWriteStream(outputPath);
  const java = spawn(
    "java",
    [
      ...(userHome ? [`-Duser.home=${userHome}`] : []),
      "-jar",
      resolve(
        process.env.TIWANAKU_TEST_JAR ??
          "../server/target/p02-build/tiwanaku.jar",
      ),
      `--server.port=${port}`,
    ],
    {
      env: { ...process.env, TIWANAKU_DATA_DIR: directory },
      stdio: ["ignore", "pipe", "pipe"],
    },
  );
  java.stdout.pipe(output, { end: false });
  java.stderr.pipe(output, { end: false });
  let exited = false;
  let spawnError: Error | undefined;
  const exit = new Promise<void>((resolve) => {
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
    await mkdir("test-results", { recursive: true });
    await copyFile(outputPath, resolve(`test-results/server-${port}.log`));
  }
  let ready = false;
  try {
    const deadline = Date.now() + 30_000;
    while (Date.now() < deadline) {
      if (spawnError) throw spawnError;
      if (exited) throw new Error(await readFile(outputPath, "utf8"));
      try {
        const response = await fetch(
          `http://localhost:${port}/api/v1/bootstrap`,
          { signal: AbortSignal.timeout(1_000) },
        );
        if (response.ok) {
          ready = true;
          return stop;
        }
      } catch (error) {
        if (
          !(error instanceof TypeError) &&
          !(error instanceof Error && error.name === "TimeoutError")
        )
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

export default async function setup() {
  const directory = await mkdtemp(join(tmpdir(), "tiwanaku-e2e-"));
  try {
    const stop = await launchServer(18093, directory);
    return async () => {
      try {
        await stop();
      } finally {
        await rm(directory, { recursive: true, force: true });
      }
    };
  } catch (error) {
    await rm(directory, { recursive: true, force: true });
    throw error;
  }
}
