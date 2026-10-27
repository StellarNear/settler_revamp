# Tiwanaku

## Local Java 25 + Vue rewrite (in progress)

From the repository root, using Java 25:

```powershell
java -jar server\target\tiwanaku.jar
```

Open `http://localhost:8080`. Java must remain running. The Vue UI is bundled
in the JAR; Node and an internet connection are not runtime requirements.
The minimal engine/server slice is verified. Internet-disconnected cold-launch
testing was explicitly waived by the user; it has not been reported as passed.
Gameplay, durable data and the P06 terrain/responsive visual rework are implemented.
The overall rewrite is still in progress; P07-P10 validation, packaging, explorer
and cutover work remain.

If the browser reports `ERR_CONNECTION_REFUSED`, check that the Java terminal
is still running and has printed `Tiwanaku ready: http://localhost:8080`.
Use the URL printed there if you configured a different port.
The rewrite displays `TIWANAKU / LOCAL`; a black canvas requesting `app.js`
belongs to the legacy page, not the bundled Vue UI. Open the server URL directly
rather than a local HTML file or the legacy development server. If an old page
persists at that URL, try a hard refresh (`Ctrl+Shift+R`) or a private window.

Storage is an embedded H2 file, not a separately installed database server.
The default directory is `${user.home}/.tiwanaku`; an absolute
`TIWANAKU_DATA_DIR` overrides it. Do not delete this directory to fix a launch error.

### Backend diagnostics

The server writes console output and dated files such as
`${user.home}/.tiwanaku/logs/game_2026-10-27.0.log` (or `logs` inside
`TIWANAKU_DATA_DIR`). The startup message prints the log directory.
Files roll at 10 MB and daily, with 14 days / 100 MB of retained archives.
Generation settings/seeds/outcomes, accepted commands/revisions and rejected API
requests with their reasons are recorded; request tokens and full board snapshots
are not logged. Logs contain local game identifiers and move details.

For a bug report, note the approximate time and keep the latest log file.
A 409 can mean a rule-breaking move, an expired/other-tab editor lease, or a stale
revision; the UI now displays the server's specific reason rather than generic JSON.
An earlier generic 409 cannot be diagnosed retroactively from its status alone.
After a server restart, use **Reconnect** (or reload) to restore the saved game.
Conflicting commands refresh the saved snapshot and ask you to review/retry;
they are never automatically replayed or queued.

### Recovering an earlier rewrite database

An earlier prototype used an empty H2 username and a schema without Flyway history.
If startup reports `Wrong user name or password [28000-232]` for that database,
stop all Tiwanaku Java processes and run this one-time command:

```powershell
java -jar server\target\tiwanaku.jar --migrate-legacy-store
java -jar server\target\tiwanaku.jar
```

Use the same `TIWANAKU_DATA_DIR` setting for both commands, if you set one.
The migration holds the exclusive data-directory lock, accepts only the recognized
prototype table/column layout, creates a consistent H2 ZIP backup under `backups`,
preserves existing rows and notes, registers the initial Flyway baseline, and
normalizes the prototype database login. It does not start the web server.
Unknown schemas or failed backups stop migration; no database reset is performed.
Run it only once. If it fails, keep the directory and printed backup intact and
report the error rather than repeatedly running it or deleting files.

To restore a backup for investigation, stop Java, extract its `tiwanaku.mv.db` into
a **different empty directory**, and point `TIWANAKU_DATA_DIR` there. A pre-migration
backup still uses the prototype login, so run the migration on that restored copy
before normal launch. Keep the original data directory untouched.

### Building and checking the current rewrite

```powershell
Push-Location web
npm.cmd ci
npm.cmd run typecheck
npm.cmd run lint
npm.cmd run format:check
npm.cmd run test:unit -- --run
npm.cmd run build
Pop-Location
.\mvnw.cmd -B verify
Copy-Item server\target\p02-build\tiwanaku.jar server\target\tiwanaku.jar
```

The isolated Maven output directory avoids pre-existing compiled server classes.
The integrated release build is a later packet. After packaging, run the real-server
browser tests:

```powershell
Push-Location web
# Use installed Edge on Windows, or install Playwright Chromium with:
# npx.cmd playwright install chromium
$env:PLAYWRIGHT_CHANNEL = "msedge"
npm.cmd run test:e2e
Remove-Item Env:\PLAYWRIGHT_CHANNEL
Pop-Location
```

The test runner owns a Java process on port 18093, uses a unique temporary H2
directory, and stops Java/removes that directory on normal teardown. An occupied
port fails rather than reusing another server. It never uses the real saved database.
Only replace the launch JAR after stopping any process using it.

### P03 scaffold and frozen v1 boundaries

Routes are `/setup`, `/play`, `/history`, and `/settings`. Java serves their SPA
entry without caching HTML; missing API/asset paths remain 404, never SPA HTML.
Pinia holds validated server snapshots and transient selection/setup state only.
Browser session storage contains editor/attempt IDs and any unresolved command's
immutable retry payload, not authoritative boards or results.
History management and durable settings are implemented in P05 below. Full EN/DE
translations remain later work. Terrain and viewport controls are described below.

The runtime schemas in `web/src/api.ts` and the real-Java serialization/E2E
fixtures define the frozen **v1** boundary. All response fields are required;
nullable fields are explicit JSON nulls. Unknown fields/enum values, unsafe
integers and inconsistent board lengths are rejected. Unknown JSON request
properties are also rejected by Java. A version/schema mismatch pauses editing
and requires matching server/UI versions; a restarted server requires reconnect
with a fresh process token and saved snapshot.

| Boundary | Shape / behavior |
| --- | --- |
| `GET /api/v1/bootstrap` | `apiVersion=v1`, process UUID/token, `maxCells=240`, `maxRegionSize=9` |
| Generation submission | columns/rows (at most 240 cells), maximum 5..9, EASY/MEDIUM/HARD, safe-integer seed |
| Generation job | UUID, explicit status/termination, nullable error, elapsed milliseconds and visited nodes |
| Attempt creation | request ID, exactly one completed job UUID or restart-attempt UUID, editor-session ID; idempotent by request ID |
| Attempt snapshot | attempt/puzzle UUIDs, dimensions, region IDs/givens/values, status, revision, owner/lease, move count and progress (notes/time/counters/history availability/help) |
| Command | command ID, expected revision, editor-session ID and command type; cell/value fields where required; optional paired timing sequence/cumulative milliseconds |
| `GET /api/v1/attempts/{id}/result` | result/attempt UUIDs, completed values, move count, ISO completion instant, nullable player tag/progress; 404 until a result exists |
| API rejection | HTTP status plus ProblemDetail `detail` for application conflicts; no success-shaped fallback |

No mutation is optimistically applied. Only one mutation is in flight; extra
input is dropped, not queued. Epoch/revision guards prevent delayed polling
responses overwriting newer snapshots. Future schema changes require a new
Flyway migration; do not rewrite the released `V1__initial_local_store.sql`.
The explicit prototype compatibility migration remains separate and preserves
its historical notes column.

### P06 atlas and responsive board

The board uses one board-coordinate SVG beneath semantic HTML cells. Each region
gets a deterministic forest, sand, mountain or water pattern; patterns do not
restart on each cell. Bold lines, not terrain colors, define regions. Underlined
numbers are fixed clues, dashed focus and a solid selection frame identify the
active cell, and a red frame with `!` marks Java-reported conflicts. Manual notes
remain numerals in a 3-by-3 layout; candidates remain separate, explicitly labelled
server help.

All seven board sizes start at 44 px cells. Large maps scroll inside the board
viewport instead of shrinking touch targets. Use **+ / -**, **Fit overview**,
**Readable size** or **Zoom to selection**. Overview is an orientation aid, not a
promise of precise touch entry at tiny scale. Keyboard navigation is an alternative:
arrows move, Home/End reach row endpoints, Tab exits the grid, N toggles notes,
and Escape exits note mode. Shortcuts do not capture form input. The rules/help
dialog also documents these controls.

Desktop has a 280 px tool inspector. Narrow windows put the tools below the board
and keep the keypad sticky with safe-area spacing. Normal browser zoom, text
scaling and touch scrolling are supported. Settings' **Reduced detail** removes
pattern marks; **High contrast** uses plain white regions and heavier black
boundaries. The atlas itself stays light in dark mode to preserve numeric contrast.
Terrain has no animation or continuous rendering loop; generation remains in Java.

**Artwork and fonts:** the new pattern paths are original vector artwork authored
in `web/src/components/TerrainBoard.vue` for this rewrite, not copies of the old
PNG atlases. Legacy image and Open Sans provenance could not be established from
the repository audit, so none of those files is added to the Vue build. The UI uses
installed system sans-serif fonts and Georgia/serif fallbacks, with no external font
requests or newly redistributed font binaries. This is a deliberate deviation from
the proposed bundled-font design until licensing is verified; legacy assets remain
untouched.

`web/e2e/terrain.spec.ts` uses fixed, schema-validated **presentation-only** snapshots
for concave regions, a hole, adjacent matching terrain, conflicts and notes 1-9.
It writes screenshots to ignored `web/test-results`, not random visual baselines.
Its fixtures do not certify solvability or persistence; the separate real-Java
browser tests cover those behaviors. Fractional DPR/CSS zoom and synthetic 200%
text scaling are automated; actual screen-reader and physical-device verification
remain P07 checks, not claimed results.

### P04 gameplay controls and saved time

Select a cell and use digits or the number pad; Delete/Backspace/0 clears it.
Arrows move selection. Givens are selectable but immutable. **Manual notes (N)**
toggles numeric pencil marks in blank cells; these are not computed candidates.
Enable **Legacy click cycle** for left-click increment and right-click decrement
through blank and the region's values. Extra input during a pending save is
dropped, not queued; selection remains responsive.

Undo/redo retain up to 100 board/note snapshots. Checkpoint restore is one undoable
action; replacing/restoring a checkpoint asks for confirmation. Neither history
nor restore reduces elapsed time, entry counts, or recorded assistance.
**New puzzle** opens generation settings without destroying the existing attempt
on failure. **Restart this puzzle** confirms creation of a fresh replay attempt
against the same puzzle, retaining the original.

In-range entries may conflict: this is deliberate, not an accepted win. Duplicates
are highlighted by **Check conflicts** or a full invalid board. Continuous
highlighting is opt-in and permanently records its use. Candidates use only
visible region/eight-neighbor exclusions, not a global solvability promise.
Explanation currently handles singles only; reveal asks for confirmation and is
counted separately. A changed nonzero assignment counts as an entry; clears,
notes, selection and no-ops do not. Noncanonical-entry counts are hidden until
completion, which Java validates and saves exactly once.

The timer starts on the first actual value change, not selection, notes or help.
Pause, route exit, hidden pages, reload and lease expiry suspend play. Resume is
explicit; an unstarted game stays READY. Closed/disconnected time is never added.
While visibly playing, a five-second heartbeat saves cumulative active time
(15-second editor lease, one-second proposal tolerance). Timing uses a separate
sequence, not gameplay revision. Pending requests freeze the local timer; their
latency can undercount active time rather than fabricate it.

Crash recovery uses the last committed total, including commits whose response
was lost. The healthy-operation loss target is the five-second unsaved interval
plus an in-flight request, not a hard bound under stalled networking/scheduling.
Fake-clock verification measured four seconds lost after a five-second save and
four further unsaved seconds; a one-hour closed interval added zero.
An unresolved request blocks edits and retains its exact ID/payload. Reconnect
checks the receipt/current server state; **Resolve pending action** retries only
that same payload when necessary. Do not clear browser session storage while
diagnosing an unresolved save.

Flyway V2 adds progress without rewriting V1 or deleting old records. Pre-P04
attempts have no recorded elapsed/assistance history; their new timer starts from
zero, not a reconstructed historical duration.

### P05 history, players and durable settings

History lists completed results, with player/board/scoring/assistance filters and
date/time sorting. Apply filters explicitly. Its summary reports the filtered
count, sum/count of **known** elapsed times, and arithmetic mean/count of stored
legacy accuracy percentages only. Direct-entry results never contribute to that
percentage. Missing dates, times, settings or assistance are shown as unknown.
New completions retain their actual generation settings/seed, independently of
subsequent menu changes; older records without that metadata remain unknown.

Edit a player tag to empty/anonymous or 1-3 ASCII letters (saved uppercase).
Imported strings outside that format remain inert text until explicitly edited.
Deleting an entry requires confirmation and hides it from history/logical export;
the completed attempt, result and command receipts are retained so retries cannot
duplicate a win or resurrect a deleted history entry. Export before deleting.

Settings persist locale, theme, reduced-detail preference, default board/region/
clue-density settings, continuous-conflict preference and current player. Click
**Save settings**; another tab's conflicting revision requires a reload.
Defaults apply only to new puzzles. The current player is attached at completion;
history tags are independently editable. Full terrain/detail polish remains P06.
Browser profile/storage clearing or a port change does not erase this Java data.

### Import and logical export

Use **History > Backups and import** to upload/paste a source, preview valid and
rejected records, then explicitly confirm. Legacy sources support semicolon-
separated `map|time|accuracy`, 4-field (+date), 5-field (+player) and 7-field
(+clicks/misses) forms. A zero/missing legacy date means unrecorded, not 1970;
missing click counters/settings/assistance are not invented. Corrupt records are
reported individually without discarding good ones. Duplicate occurrences in one
source remain distinct; reimporting the **same exact source/format** is idempotent.
Do not treat separate files containing the same non-unique legacy tuples as a
universal identity match.

The confirmed import transaction retains the full raw source and rejection report
in H2. **Download retained raw import source** retrieves that exact text after
previewing it again. Original desktop/browser files are never deleted or changed.
Only the known `~/.prefs/TiwanakuScoreboard` desktop file is read, and only after
confirmation; no arbitrary HTTP filesystem path is accepted. Actual libGDX
Java-properties XML is supported without external entity/DTD resource access.

The same-origin button reads only
`pref:app:TiwanakuScoreboard:scoreboard_resultss` (including the final type suffix).
Its fixture was exercised with the real compiled legacy TeaVM preference reader.
It cannot access another scheme/host/port or device. For remote old-browser data,
open developer tools at the **old exact origin**, then run this local-only exporter
and upload the downloaded text into the new app:

```javascript
const raw = localStorage.getItem("pref:app:TiwanakuScoreboard:scoreboard_resultss");
if (raw === null) throw new Error("No scoreboard exists at this origin.");
const url = URL.createObjectURL(new Blob([raw], { type: "text/plain" }));
const link = document.createElement("a");
link.href = url;
link.download = "tiwanaku-legacy-scores.txt";
link.click();
setTimeout(() => URL.revokeObjectURL(url), 1000);
```

Android private preferences require an in-app exporter/update bridge on that
device. This desktop rewrite does not claim automatic Android migration.

**Export history and settings** downloads a version-1 JSON envelope with a
SHA-256 checksum over its exact payload. It preserves stable record IDs and known/
unknown metadata. Import validates version/checksum before any writes. Settings
replacement is a separate confirmation option; repeating a source with a changed
settings-restore choice is rejected, not silently reapplied. Logical export is
history/settings recovery, **not an export of playable/unfinished attempts**;
use the full database backup for those.

Limits are 4 MB per uploaded file/export, 4 million characters per API import
source, and 10,000 records per import/history-management view. Exceeding a limit
reports an error, never a truncated success; preserve a physical backup if the
history-management limit is reached. Raw import sources and manually requested
backups are retained until the user deliberately manages their files.

### Schema upgrades, full backups and recovery

V3 adds history/settings without rewriting V1/V2. Startup rejects newer schemas,
checksum failures and incomplete earlier upgrades rather than resetting data.
Before upgrading an existing schema, Java holds the data-directory lock and uses
H2's consistent `BACKUP TO` to create a timestamped/version-labelled ZIP in
`backups`. A failed required backup stops the upgrade. After a successful upgrade,
automatic backups rotate to the newest five; manual/prototype-migration backups
are not removed by that rotation. Fresh databases need no pre-upgrade backup.

**Download full database backup** creates/downloads a consistent ZIP of active
attempts, results, history, settings, receipts and raw imports. Never copy a live
`.mv.db` file. H2 schema DDL is not assumed transactional: a failed upgrade leaves
`upgrade-incomplete.txt`, preserves its pre-upgrade backup, and blocks automatic
retry/repair. Keep the failed directory and logs for diagnosis.

For recovery, stop Java and preserve the original directory. Extract a compatible
backup into a **new empty directory** and launch against that directory:

```powershell
$restoreDirectory = Join-Path $env:USERPROFILE ('.tiwanaku-restored-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
Expand-Archive -LiteralPath 'C:\path\to\backup.zip' -DestinationPath $restoreDirectory
$env:TIWANAKU_DATA_DIR = $restoreDirectory
java -jar server\target\tiwanaku.jar
```

Validate history/settings and an active saved game there before deciding which
directory to keep. Never overwrite the original/live database or automatically
delete an upgrade marker. Rolling back also requires a matching older JAR and
its pre-upgrade backup; later edits are absent from that older snapshot. A
pre-credentials-migration backup additionally needs the explicit compatibility
migration described above. Storage write/read-only/disk-full errors are logged
and returned as failures; no empty replacement database is fabricated. A network/
storage error can leave an outcome unknown, so reload/reconcile rather than
assuming a failed response means no commit.

## Legacy application

# to launch on the local environment user this command :
C:\Users\ho.jchatron\Documents\local_bin\jdk-17.0.20.1\bin\java.exe -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :teavm:run

**Explore ancient lands and uncover hidden resources through pure logic!**

Tiwanaku is a free, offline logic puzzle game inspired by the mystery of the Andes. It combines the elegance of Sudoku with a fresh twist — pixel-art biomes waiting to be filled, number by number.

---

## A Unique Logic Puzzle

Each level is a map of biomes — forests, deserts, mountains, oceans — waiting for you to fill with numbers following two simple rules.

### The Rules (Easy to Learn, Hard to Master)

- **Every number in a biome is unique.** A biome with 5 tiles holds the numbers 1 through 5.
- **No two neighboring tiles may share the same value.**

That's it. No tutorial marathon, no overwhelming menus. Tap a tile to set a number, and let your brain do the rest.

---

## Endless Puzzle Generation

Every level is generated on the fly — you will never run out of content. Each puzzle has a guaranteed unique solution, verified by the built-in algorithm. Thousands of hours of gameplay waiting for you.

## Fit Every Skill Level

- **7 board sizes:** from a relaxing 3x3 grid to UBER (15x10) and TITAN (20x12)
- **3 difficulty settings:** Easy, Medium, and Hard
- **Maximum Region Size:** Default (5), 6, 7, 8, or 9, selected with the arrows below Difficulty. This is an upper limit; a board can contain smaller regions too.

During generation, the black status text reports completed clue checks and the number of clues remaining. These are actual counts, not an estimated percentage. The display updates between checks; a difficult individual check can take longer. Changing the maximum region size creates a new puzzle when you press Play.

## Features

- **Help mode:** reveal possible values for any tile when you're stuck
- **Error detection:** conflicts are highlighted instantly so you can correct course
- **Checkpoint system:** fix your progress and return if you want to try a different approach
- **Charming pixel-art biomes:** desert dunes, lush forests, snowy peaks, ocean shores
- **Relaxing resolution timer:** starts with your first click, stays in the side panel, and shows your total time when the puzzle is solved
- **Clean, calming interface** — no pop-ups, no pressure
- **Available in English and German**
- **Runs entirely offline** — play anywhere, anytime

## Completely Free

No ads. No in-app purchases. No data collection. No paywalls. Ever.

Just a pure puzzle game made with love by a solo developer who believes games should respect your time and attention.

## Train Your Brain

Whether you're a Sudoku veteran looking for something new, a puzzle newcomer wanting a gentle challenge, or simply looking for a mindful way to wind down — Tiwanaku is for you. Perfect for commutes, waiting rooms, or that quiet moment before bed.

Download now and start exploring. Your adventure begins with a single number.

---

## Technical Overview

Tiwanaku is a cross-platform game written in **Java** on top of the [libGDX](https://libgdx.com/) framework, with launchers for Desktop, Android, and the Web.

### Stack

| Area        | Technology                                               |
|-------------|----------------------------------------------------------|
| Language    | Java 11                                                  |
| Framework   | libGDX 1.14.0 (+ gdx-freetype for font rendering)        |
| Desktop     | LWJGL3 backend                                           |
| Android     | libGDX Android backend (minSdk 28, targetSdk 35)         |
| Web (HTML5) | [gdx-teavm](https://github.com/xpenatan/gdx-teavm) 1.5.3 |
| Build       | Gradle 8.12.0                                            |
| i18n        | Java `ResourceBundle` (`assets/i18n/`) — English, German |
| Rendering   | 1400 × 800 internal resolution, VSync, 60 FPS target     |

### Project Structure

The project is a standard multi-module libGDX setup:

```
settler/
├── core/      # Shared game code (rules, generator, UI, rendering)
├── desktop/   # LWJGL3 launcher (Windows / macOS / Linux)
├── android/   # Android launcher + manifest + resources
├── teavm/     # TeaVM launcher for the HTML5 / web build
├── assets/    # Fonts, images, i18n property files (shared by all platforms)
└── docs/      # Published web build (served as static site)
```

The core module contains the gameplay:

- `com.apogames.settler.game.tiwanaku` — game state, level sizes, main game loop
- `com.apogames.settler.level.algorithmX` — puzzle generation / uniqueness solver
- `com.apogames.settler.game.menu` — menus and navigation
- `com.apogames.settler.asset` / `common` / `entity` — assets, helpers, entities
- `com.apogames.settler.backend.io` — save / checkpoint persistence

App identifiers:

- Android application ID: `com.apogames.tiwanaku`
- Java package namespace: `com.apogames.settler`

### Building and Running

Requires a JDK 11+ and, for the Android build, an Android SDK (set via `local.properties` or `ANDROID_HOME`).

**Desktop**
```bash
./gradlew desktop:run
```

**Android (install & launch on a connected device)**
```bash
./gradlew android:installDebug android:run
```

**Web (TeaVM)**
Build the static site into `teavm/build/dist`:
```bash
./gradlew teavm:build
```
The `TeaVMBuilder` main class can also start a local Jetty server (`--serve`) for development. The currently published web build is in `docs/` and can be served directly.

### Signing the Android Release

The Android release build is signed via Gradle properties. Provide them in `~/.gradle/gradle.properties` or on the command line:

- `SETTLER_KEYSTORE_PATH`
- `SETTLER_KEYSTORE_PASSWORD`
- `SETTLER_KEY_ALIAS` (defaults to `settler`)
- `SETTLER_KEY_PASSWORD`

Then run:
```bash
./gradlew android:assembleRelease
```

---

## License / Author

Made by Dirk Aporius. Source code © 2005–present Dirk Aporius, distributed under a BSD-style license (see the header of `Constants.java` for the full notice).
