# Tiwanaku

Tiwanaku is a local Java 25 game with a Vue 3 interface served at
`http://localhost:8080`. The browser UI is bundled into the application JAR;
Node.js and an internet connection are not needed to run a built release.

## Requirements

- **Run:** Java 25 and a modern browser.
- **Build:** JDK 25, Node.js >=22.12, and npm. Set `JAVA_HOME` to your JDK 25
  installation before running Maven.

## Package the application

From the repository root in PowerShell, stop any Tiwanaku process using the
output JAR, then run:

```powershell
.\mvnw.cmd -Prelease clean verify
```

This checks/builds the Vue UI, runs the Java tests, packages the executable JAR,
and runs its isolated startup/API smoke test. The result is
`server\target\tiwanaku.jar`. The first build may need internet access to obtain
Maven/npm dependencies.

## Launch the game

From the repository root:

```powershell
java -jar server\target\tiwanaku.jar
```

Open [http://localhost:8080](http://localhost:8080) while the Java process is
running. Stop it with Ctrl+C. For an orderly console shutdown instead:

```powershell
java -jar server\target\tiwanaku.jar --console
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
