# Tiwanaku

Tiwanaku is a local Java 25 game with a Vue 3 interface served at
`http://localhost:8080`. The browser UI is bundled into the application JAR;
Node.js and an internet connection are not needed to run a built release.

## Requirements

- **Run:** Java 25 and a modern browser.
- **Build:** JDK 25, Node.js >=22.12, and npm. Set `JAVA_HOME` to your JDK 25
  installation before running Maven.

## Build the application

From the repository root, use one of these Windows command scripts. Close the
game first before building; Windows will not replace the launch JAR while
Tiwanaku is using it.

```cmd
build-fast.cmd
```

`build-fast.cmd` creates the release JAR without running Java or frontend unit
tests or the standalone-JAR smoke test. It still runs frontend type checking,
lint, formatting checks, and the production build.

For the full verification build, including Java and frontend tests and the
standalone-JAR smoke test, run:

```cmd
build.cmd
```

Both scripts use the release Maven profile and update `server\target\tiwanaku.jar`.
The first build may need internet access to obtain Maven/npm dependencies. Do
not use plain `mvn package` to update the game you launch: the default
incremental build writes under `server\target\p02-build` and may leave the
release JAR unchanged.

## Launch the game

From the repository root, run:

```cmd
launch-game.cmd
```

Open [http://localhost:8080](http://localhost:8080) while the Java process is
running. Stop it with Ctrl+C. For an orderly console shutdown instead:

```cmd
launch-game.cmd --console
```

Type `quit` and press Enter; closing standard input also stops console mode.
Only one Tiwanaku process may use a data directory at a time.

## Local data and logs

By default, game data and diagnostic logs are stored under
`%USERPROFILE%\.tiwanaku`. Set `TIWANAKU_DATA_DIR` to use another directory.
Keep this directory intact when updating the application; do not delete it to
troubleshoot a launch problem. Logs are written to its `logs` subdirectory.

## Legacy application and hosted page

The original libGDX desktop/Android/TeaVM source and Gradle wrapper are archived
under [`legacy/`](legacy/); the old project guide is
[`legacy/README.md`](legacy/README.md). Those targets are retained, not part of
the current Java/Vue build. The existing static legacy browser page under
[`docs/`](docs/) is intentionally left at its existing path; this repository
change does not alter or redeploy that hosted page.
