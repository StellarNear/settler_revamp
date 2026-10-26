# Tiwanaku Codebase Audit

Date: 2026-10-26. Purpose: evidence for the [modernization plan](modernization-plan.md), not application changes.

Deployment clarification, 2026-10-26: the user wants a Java 25 application launched locally, with its UI opened in a browser at localhost. The companion plan revision 2 is authoritative for the target architecture. The historical findings below remain valid; earlier browser-worker/PWA suggestions are superseded. The new target uses a mandatory local Java service with backend-owned storage, not a standalone browser engine. No rewrite has begun.

Paths in source references below are relative to `core/src/com/apogames/settler/` unless they explicitly name another module. Line numbers describe the source inspected for this audit and may move during implementation.

## 1. Scope And Confidence

The review covered all **78 tracked Java source files** through three subsystem reviews and the root application/build review. It traced reachable behavior rather than assuming README descriptions or class names were accurate. Build scripts, platform configuration, translations, asset inventories, generated-site structure, and the archived source ZIP were also inspected.

Generated/minified JavaScript and third-party runtime files were inspected through references, headers, searches, and sizes, not exhaustively audited line by line. Binary assets were inventoried, not all visually reviewed. No GUI automation or interactive playthrough was performed. Findings about behavior are code-derived unless marked as executed build results.

No test sources were found. The legacy test task under a supported JDK confirmed `NO-SOURCE`, not a passing regression suite. This makes characterization and independent solver checks a prerequisite for refactoring.

## 2. What The Game Actually Does

### Game Rules

Tiwanaku is a single-player number-placement puzzle, not a real-time settlement simulation or a networked board game. The repository/package name `settler` is historical.

- The rectangular board is divided into orthogonally connected regions represented by matching positive biome values.
- Each region with N cells must contain each value 1 through N exactly once.
- Identical numbers must not be adjacent horizontally, vertically, or diagonally: all eight neighbors count.
- Disconnected areas with the same biome appearance are separate regions.
- The solution is generated first, and a subset becomes fixed clues. Player entries and manual hints are mutable state.
- In-game completion currently compares entries with the stored canonical solution. Displayed conflicts detect duplicates, not every possible input-invalidity condition.

Relevant source: `level/Level.java`, `level/Solve.java`, `level/PossibleValueCheck.java`, `level/famiru/App.java`, `level/NewLevelCreate.java`.

### Settings

Source of truth: `game/tiwanaku/LevelSize.java:4-10` and `game/menu/Menu.java`.

| Stored preset | Columns x rows | Cells |
| --- | --- | --- |
| `SUPERMINI` | 3 x 3 | 9 |
| `MINI` | 4 x 4 | 16 |
| `SMALL` | 5 x 5 | 25 |
| `MEDIUM` | 7 x 5 | 35 |
| `BIG` | 9 x 5 | 45 |
| `UBER` | 15 x 10 | 150 |
| `TITAN` | 20 x 12 | 240 |

Difficulty choices are Easy, Medium, Hard. Maximum region size cycles from default 5 through 9; it is an upper limit, not the size of every region. The menu also exposes an Algorithm screen, local leaderboard, language switch, and Play.

### Reachable Screen Flow

```text
Platform launcher -> Settler.create -> MainPanel -> Menu
Menu -> Tiwanaku play -> PlayerSelection after win -> Menu/history navigation
Menu -> Leaderboard -> PlayerSelectionForEdit
Menu -> Algorithm visualization
```

There are six reachable screen models: Menu, Tiwanaku, Leaderboard, PlayerSelection, PlayerSelectionForEdit, and Algorithm. The main gameplay win-overlay drawing code is effectively bypassed by immediate transition to PlayerSelection (`game/tiwanaku/Tiwanaku.java:450-461,737-756`). Do not build parity around an unreachable congratulation overlay.

### Input And Help

- Normal pointer release cycles an editable value through blank and valid region values; secondary-button behavior cycles in the other direction.
- The primary gameplay keyboard shortcut is N for a new level. There is no normal direct digit input or full keyboard board-navigation model.
- Clicking a given is counted as wasted input but does not change it.
- The first editable value click starts the timer; selecting/marking hints does not.
- The action still called `FUNCTION_FIX` toggles hint/pencil mode (`Tiwanaku.java:493-495`). It is not a checkpoint.
- Manual hint bits are toggled per cell. Low-number hints are rendered as colored sprites without numerals, while the larger range receives small number labels.
- Help first points out errors, otherwise selects a random cell with a single available candidate. It is not a general explainable solver and does not automatically insert a value.
- Automatic duplicate highlighting occurs when the board is full but unsolved; Help can expose conflicts earlier (`Tiwanaku.java:523-550,567-572`). The README's instant-error wording overstates the default behavior.
- Pencil-button visibility depends on region size and original clues in that region, not the full currently legal candidate set (`Tiwanaku.java:156-167`). Manual notes and computed candidates must remain distinct.
- If no single-candidate suggestion is available, this path can produce no useful explanation.
- Restart routes to `createNewLevel()` (`Tiwanaku.java:575-577`), not `Level.restart()`.

`Level.restart()` itself exists at `level/Level.java:258-262`: restore givens, clear hints, and reset click stats. It has no normal gameplay caller. There are **no checkpoint/fix snapshot methods in the current Level source**, and no active checkpoint persistence. The README advertises functionality that is not implemented in this revision.

### Timer And Accuracy

`GameTimer` is a small counter with reset/start/stop and display formatting, but ticking occurs in rendering (`Tiwanaku.java:620-621`). It accumulates `float` seconds and caps each update at one second (`GameTimer.java:16-60,74-94`). This undercounts long foreground stalls and does not constitute an explicit background-pause policy.

Accuracy is **click efficiency**, not the percentage of logically correct entries. `Level.java:49-97` compares clicks spent at a cell with the shortest cyclic route to its current number, includes wasted given-cell clicks, and rounds `(clicks - misses) / clicks * 100`. Zero clicks yields 100. Moving to direct digit entry changes the metric; old percentages must be preserved as legacy values, not silently compared with a new mistake rate.

## 3. Architecture And Production Paths

### Rendering/Application Coupling

`Settler` loads static assets, installs MainPanel, and requests rendering (`Settler.java:11-25`). MainPanel is the screen coordinator; custom ScreenModel/GameScreen classes implement screen lifecycle, rendering, input queues, buttons, and navigation. Widgets are hand-written Java drawing/input classes, not semantic platform controls.

The internal scene is **1400 x 800** (`Constants.java:45-47`). The 1422 value in AssetLoader is an image crop dimension, not the viewport. Most layout is absolute coordinates; the right HUD begins around x1165. Font sizes, button rectangles, and input hitboxes are tied to this world-space layout.

Terrain is atlas/sprite-based, with 128 px logical square cells and taller 128 x 192 visual strips that can overlap. It is not a union-of-region textured renderer. UBER uses 64 px internal cells and TITAN about 53 px to fit the available internal height. Fitting this whole scene into a 390 px phone width gives TITAN cells around 15 px and tiny HUD text. There is no purpose-built large-board touch zoom/pan interface.

Replacing libGDX alone would not automatically fix appearance: the main maintainability issue is UI, state, input, and layout mixed in large classes with mutable globals. Vue addresses those responsibilities with standard platform facilities; art direction and state contracts still need deliberate work.

### Gameplay Generation

```text
Tiwanaku generation lifecycle
  -> NewLevelCreate
  -> FillAndUniqueCheck
     -> famiru.App / Dlx for initial exact-cover solution where applicable
     -> preconstructed solution path for the large-board strategy
  -> UniqueSolution
     -> Solve for clue-removal acceptance
  -> Level
```

The production path is **not simply `level/algorithmX`**, contrary to the README's technical summary. `famiru.Dlx` is a sparse dancing-links exact-cover implementation. `UniqueSolution` removes clues and checks remaining solvability/uniqueness using `Solve`.

The stepped generator API spreads outer phases/clue checks across calls, but individual recursive/search phases can still run unbounded. It is not proof of asynchronous/nonblocking behavior. In particular, normal biome coloring has no deadline, and the production Solve/Dlx searches have no general cancellation/deadline contract.

Easy suppresses guessing in the logical Solve path; Medium and Hard share the same MRV/backtracking strategy. Target clue fractions differ (approximately .35/.30/.24), but this is not a calibrated human-difficulty rating. A sparse clue set is not inherently harder for humans than every denser one.

`Solve`'s `max=1` call is not automatically a uniqueness defect: its stopping condition intentionally permits discovering a second solution. Nevertheless, uniqueness depends on the correctness and exhaustive completion of the underlying solver. The audit found concrete input-validation defects; it did **not** demonstrate that standard generated boards are nonunique.

### Generation Details Worth Preserving Or Bounding

- Regular generation builds numbered connected shapes, applies a lightweight candidate-union prefilter, then colors regions with four terrain colors (`NewLevelCreate.java:223-320,606-650`). The prefilter is not an exact solvability proof. Requested four-/five-cell counts are placement attempts, not guaranteed final distributions.
- The constructive large-board branch applies above 45 cells with width at least five and even height (`NewLevelCreate.java:223-226,322-456`). It starts from 2x2 permutations, handles odd widths with a five-column strip, transfers cells while preserving connectivity and values, colors terrain, and flips orientation. Official UBER and TITAN use this branch.
- Large block placement already has limited retries (2,000 placements per restart, six restarts) and a valid-pattern fallback strategy. Its terrain coloring has a 2.5-second wall-clock deadline; the regular path's coloring has no equivalent deadline. These local limits are not a global cancel/time budget.
- Initial solving can request **up to one million retained solutions** for area at most 31 and maximum region size at most five (`FillAndUniqueCheck.java:106-120,185-191`). Other applicable boards request one solution. This is a major memory/time hotspot to replace with bounded sampling or a limited witness strategy, not preserve by default.
- Clue removal starts with a larger random batch, reduces it on failure, then tries individual clues (`UniqueSolution.java:87-151`). Small/ordinary boards can undergo up to nine removal passes; large ones use one (`FillAndUniqueCheck.java:259-283`). Returned puzzles can retain more clues than the target fraction.
- Region topology and candidate collections are repeatedly reconstructed during search; guessed branches allocate another Solve and clone arrays. Precomputed immutable region membership and neighbor lists are a higher-value optimization than changing language.
- Algorithms consume global `Math.random()`, include unused random draws, and rely partly on wall time. There is no current seed/version replay contract. A seed cannot reproduce historical puzzles that were never stored.

Additional engine-boundary risks: byte coordinate loops overflow at dimensions of 128; obsolete byte clue counters overflow beyond 127; mutable arrays and solution lists escape getters. Official presets avoid the coordinate limit, but imports must not silently expand the accepted domain. `Dlx`'s AtomicReference does not make one instance thread-safe: concurrent solve callers can return before work finishes, and failure can still mark it solved (`Dlx.java:143-164,268-270`). Use separate state per worker/job.

### Algorithm Explorer

This is reachable, not dead code: Menu dispatches to Algorithm (`Menu.java:61,152-153`), which builds a small 4 x 3 example with `LevelCreate`, converts it with `AlgorithmX`, and animates `AlgorithmXSolve` (`game/algorithm/Algorithm.java:72-86`).

The educational path has its own legacy generator and dense matrix representation. Retaining the feature does not mean retaining that implementation: the matrix can grow cubically in cell count and its region flood fill is inconsistent with production topology.

## 4. Findings Requiring Design/Test Coverage

| Priority | Finding and evidence | Modernization consequence |
| --- | --- | --- |
| High | `Solve.isSolved()` only checks nonzero and is consulted before contradiction validation (`level/Solve.java:230-238,263-270`) | Full invalid boards must never count as solutions; independent validation before completion |
| High | Individual generation/search steps have no general cancellation/deadline | Dedicated bounded JVM CPU executor, cooperative cancellation, bounded exact search, explicit unknown result |
| High | Winning only opens PlayerSelection; save happens on later actions (`Tiwanaku.java:450-460`; `PlayerSelection.java:160-220,317-319`) | Transactional completion before optional name dialog |
| High | `resultSaved` is set before persistence succeeds | Failed writes must be retryable and visible |
| High | Numeric parsing of one legacy record can abort all history loading (`Scoreboard.java:98-139`) | Partial recovery, backup, schema validation, never destructive first migration |
| High | Asset resume chain calls load twice without disposing previous resources (`Settler.java:34-36`, `backend/Game.java:62-64`, `backend/GameScreen.java:344-345`) | Replace static resource ownership; test repeated resume/visibility |
| Medium | Returning to retained play resets timer/state while keeping board (`Tiwanaku.java:190-208`) | Attempt state belongs outside screen initialization |
| Medium | Menu snapshots selected settings on menu entry, not actual puzzle identity (`Menu.java:76-78,136-138`) | Changing options through another route can retain/relabel the wrong board; compare against immutable puzzle spec |
| Medium | Old result identity is index or partial value tuple (`Scoreboard.java:35-65`) | Stable IDs for edits and completion idempotency |
| Medium | All scores serialized/reparsed/replaced together; cross-tab writes can lose data | Backend database transactions/revisions, idempotent commands, and explicit multi-tab ownership |
| Medium | Browser preference flush removes old namespace keys before replacement in pinned backend | Quota failure recovery and non-destructive migration |
| Medium | `Dlx.search()` stops when retained-solution capacity fills even if count-all was requested (`level/famiru/Dlx.java:167-170,202-213`) | Make count-up-to-two/count-all contracts explicit; no ambiguous flags |
| Medium | `famiru.App` encodes adjacency through 2x2 blocks; one-row/column inputs lack those blocks | Validate supported dimensions or support degenerate boards correctly; not a shipped-preset failure claim |
| Medium | `AlgorithmX.fillRegion` uses NW diagonal instead of up (`level/algorithmX/AlgorithmX.java:366-370`) | Do not use visualization encoder as a trusted topology oracle |
| Medium | Help/hint bubbles overlap timer coordinates; fixed hint sprite sizes do not scale cleanly | Reflow inspector/keypad; numeric semantic notes |
| Medium | Player list has no proper scrolling; title/controls and leaderboard rows can overlap; filter/reset can leave invalid scroll state | Standard scroll containers, tables, forms, modal focus behavior |
| Medium | Leaderboard reset lacks confirmation | Separate destructive commands and confirm them |
| Medium | Raw player keystrokes/tags are logged (`PlayerSelectionForEdit.java:168-174,229-271`) | No raw input diagnostics in release |
| Medium | Generated public site differs materially from source | CI-generated deployment with provenance and explicit migration origin |
| Low | `hudMenuTexture` omitted from disposal (`AssetLoader.java:135-138,263-285`); Game.dispose hides but does not dispose screen (`backend/Game.java:51-54`) | Unified resource lifecycle while legacy remains supported |
| Low | Duplicate localization globals, hardcoded English, incomplete translation coverage | One locale store and translated component strings |

### Concrete Engine Regression Fixtures

These are code-derived characterization examples, not executed test results from this planning task.

1. `Solve` with biome array `[[1,1]]` and filled values `[[1,1]]` must be rejected: the two-cell region repeats 1 and misses 2. Current completion ordering can accept it.
2. Two adjacent singleton regions with background `[[1,2]]` have values fixed to 1 by region size and are unsatisfiable by adjacency. The `famiru.App` 2x2 encoding can miss this in a one-row board. Official presets start at 3 x 3, so treat this as an engine-boundary test.
3. Region topology `[[1,2,1],[1,1,1]]` has one five-cell region of biome 1 and one singleton region of biome 2. Correct flood fill reaches the upper-right 1 through the cell below it. The legacy AlgorithmX traversal's missing up edge can split/misencode it.
4. A solver budget ending after the first solution but before exhaustive remaining search must return unknown, not unique.
5. A cancellation followed by a new request must never install the first request's result into the new attempt.

Use tiny exhaustive enumeration as an independent oracle; comparing two implementations that share the same faulty region encoder is not independent verification.

## 5. Persistence, Players, And Compatibility

### The Only Active Durable Gameplay Schema

`game/tiwanaku/Scoreboard.java:11-20,68-139` writes Preferences `TiwanakuScoreboard`, key `scoreboard_results`. Records are separated by **semicolons**, fields by pipes:

```text
mapSize|timeMillis|accuracyPercent|dateMillis|playerTrigram|totalClicks|totalMissClicks
```

Historical records have 3, 4, or 5 fields. Missing date/counters default to zero and player to empty in old constructors (`GameResult.java:12-31`). For migration these mean not recorded, not necessarily a real epoch timestamp or zero-click win. Preserve stored accuracy rather than recomputing it from absent counters. Unknown field counts are currently silently discarded.

No difficulty, maximum region size, assistance, seed, puzzle ID, scoring-rule version, or stable result ID is stored. Ranking old and new results as equivalent challenges would be misleading.

Player choices are distinct nonempty tags inferred from history (`PlayerSelection.java:52-63`). There is no account/profile store. Typed tags accept up to three ASCII letters, uppercase; one or two letters can be accepted despite the three-letter wording. Anonymous results exist. Imported strings are not equivalently constrained.

The leaderboard also summarizes the filtered game count, summed elapsed time, and arithmetic mean of per-game rounded accuracy (`Leaderboard.java:222-228,273-277`). The mean is not weighted by click count. Preserve these local-history capabilities while separating legacy accuracy from any new scoring rules.

### Storage Backends

Verified against upstream libGDX 1.14.0 and gdx-teavm 1.5.3 implementations; still capture an actual persisted fixture before production migration.

| Platform | Legacy location/behavior |
| --- | --- |
| TeaVM browser | `localStorage` key `pref:app:TiwanakuScoreboard:scoreboard_resultss`; plain delimited value; suffix `s` marks a string |
| LWJGL desktop | `$USER_HOME/.prefs/TiwanakuScoreboard`, no extension; Java Properties XML |
| Android | App-private SharedPreferences named `TiwanakuScoreboard` under `com.apogames.tiwanaku`; use Android APIs for export |

Backend sources: [TeaVM WebPreferences](https://raw.githubusercontent.com/xpenatan/gdx-teavm/1.5.3/backends/backend-web/src/main/java/com/github/xpenatan/gdx/teavm/backends/web/WebPreferences.java), [TeaVM WebApplication](https://raw.githubusercontent.com/xpenatan/gdx-teavm/1.5.3/backends/backend-web/src/main/java/com/github/xpenatan/gdx/teavm/backends/web/WebApplication.java), [TeaVM configuration](https://raw.githubusercontent.com/xpenatan/gdx-teavm/1.5.3/backends/backend-web/src/main/java/com/github/xpenatan/gdx/teavm/backends/web/WebApplicationConfiguration.java), [LWJGL preferences](https://raw.githubusercontent.com/libgdx/libgdx/1.14.0/backends/gdx-backend-lwjgl3/src/com/badlogic/gdx/backends/lwjgl3/Lwjgl3Preferences.java), [Android preferences](https://raw.githubusercontent.com/libgdx/libgdx/1.14.0/backends/gdx-backend-android/src/com/badlogic/gdx/backends/android/AndroidPreferences.java).

### Inactive Scaffolding

`TiwanakuPreferences`, `MenuPreferences`, and `AlgorithmPreferences` have empty read overrides and flush-only write overrides. They do not persist attempt/settings data. Their `GameProperties` parent defines `size` plus numbered solved-level strings, but these subclasses bypass that implementation. `MainPanel.saveProperties()` is empty (`MainPanel.java:85-94`).

`backend/io/IOOnlineLibgdx.save()` returns an empty string; `load()` requests an empty configured URL and discards its parsed result. No authored callers were found. It is not a functioning backend, account service, or checkpoint implementation.

`common/IDGenerator` is a process-local incrementing counter, unsuitable as a durable ID. Do not repurpose it for result IDs just because of its name.

### Privacy And Offline

No authored telemetry, purchases, advertisements, authentication, or score upload was found. Android has no INTERNET permission. Desktop and Android use local assets and generation. Browser gameplay can continue locally once assets load, but source and published shells contain no service worker or explicit offline cache contract; uncached/offline cold launch is not guaranteed.

Logger writes `logs/settler_yyyyMMdd_HHmmss.log` relative to the working directory and stdout, flushing every line with no retention/level gate (`utils/Logger.java:12-65`). With desktop Gradle run's `assets` working directory this becomes `assets/logs`. It is local logging, not evidence of remote telemetry.

Android enables OS backup (`android/AndroidManifest.xml:8-10`), which can include preferences depending on device policy. Preserve the distinction between OS backup and app-controlled synchronization in privacy claims.

## 6. Assets, Localization, And Delivery

### Assets

Source `assets/` contains 26 files: 21 PNGs, OpenSans.ttf, three translation bundles, and an asset list. The loader directly references 15 PNGs and the font. Four terrain-source PNGs (`desertDunes00`, `forestBroadleaf00`, `mountain00`, `ocean00`) and two `_old` variants are not referenced by current core code, although still packaged.

`assets/assets.txt:2-18` lists 17 nonexistent font files (`logic.ttf` and pirate bitmap-font pairs). No direct authored reference to this list was found; do not describe it as a confirmed runtime crash. It is stale packaging inventory to clean up when defining a reproducible asset pipeline.

The source ZIP `core/src/com/apogames/settler.zip` contains an older source tree and is not an additional active compilation unit. Its contents were listed with `jar tf`, not treated as current source truth. Version control should replace archived source snapshots after cutover.

BSD-style headers exist in older Java files, including Constants. No root LICENSE/NOTICE file was found. Font/image provenance and the sparse solver's redistribution basis require verification. Existing source-code licensing is not blanket proof for every asset.

### Localization

Source bundles: base English 38 keys, explicit English 35, German 38. The English variant inherits missing entries from the base; this is not itself a missing-string failure. Published bundles are older (22/23/23 keys).

`common/Localization.java:17-29` initializes against Gdx.files, creating a headless-test/initialization dependency. MainPanel applies locale later. Constants also has locale globals. Language selection is not persisted. Leaderboard headings and player-selection copy include hardcoded English (`Leaderboard.java:249-276`, `PlayerSelection.java:272-304`), and date formatting uses runtime locale/timezone rather than necessarily the selected UI language.

Retain the content as translation source, but move all visible strings and formatting through one frontend locale system and validate key/placeholder coverage.

### Platforms

- Desktop launcher: LWJGL3, VSync, 60 foreground FPS, 2x MSAA, 1400x800 initial window. macOS first-thread JVM flag exists in Gradle run but not debug.
- Android: libGDX Android backend, GLES 2 requirement, landscape lock, wake lock, application ID `com.apogames.tiwanaku`, source namespace `com.apogames.settler`, minSdk 28/targetSdk 35, versionName 1.0.2. No explicit custom saved-state restoration.
- TeaVM: auto-sized canvas, user-agent-based mobile detection, antialiasing, FreeType preload, loading-overlay callback. No native DOM game controls or PWA installation/offline machinery.
- Legacy Eclipse/GWT metadata remains: `android/project.properties` targets android-19 for an obsolete tooling path, while actual Gradle SDK settings differ; `core/src/Settler.gwt.xml` is not the active web launcher.

### Published `docs/`

This directory is a generated playable site, **not a documentation source directory**. Do not place the modernization specification there or manually rewrite `docs/app.js`.

Tracked raw sizes from the audit:

| File | Bytes |
| --- | ---: |
| `docs/app.js` | 857,904 |
| `docs/scripts/freetype.js` | 1,091,924 |
| `docs/scripts/gdx.wasm.js` | 190,491 |
| `docs/scripts/howler.js` | 36,173 |
| Source `assets/fonts/OpenSans.ttf` | 217,360 |

These are raw file sizes, not compressed transfer size or measured loading time. The gdx runtime embeds a WASM payload; no separate WASM file is not evidence of missing content. The published app also bundles shaders, bitmap-font resources, and audio infrastructure despite this simple puzzle UI.

Published HTML lacks the source loading overlay, translations lack recent timer/region/history text, and targeted app.js searches find old preference names but no scoreboard schema markers. It appears to predate the current score implementation. Therefore migration must identify actual installed versions/origins, not assume every visitor has current-source history.

## 7. Retain, Extract, Replace, Retire

| Code area | Disposition | Conditions |
| --- | --- | --- |
| Sparse exact-cover algorithm and link structure | Retain the approach; extract and repair | Fixed clues, count-up-to-two, cancellation/budgets, validation, license check |
| Current generator strategies | Preserve initially behind tests | Remove framework coordinates, inject RNG/budgets, verify uniqueness independently |
| `Solve` / candidate logic | Retain useful logic, not unchecked completion semantics | Correct defects and distinguish logical deductions from exact proof |
| Board-size/difficulty concepts | Preserve values and legacy tokens | Separate actual puzzle metadata from menu selection |
| Plain arrays/bit masks | Keep where effective | Add boundary validation and immutable snapshots |
| `GameTimer` concept | Preserve start/stop intent, replace implementation | Monotonic active time, explicit pause/persistence |
| `GameResult` / score history | Preserve data, redesign schema | Stable IDs, versions, legacy importer, no fabricated metadata |
| Translations/font/art | Reuse selectively | Provenance, readability, updated copy and complete coverage |
| libGDX screens, rendering/input framework, custom widgets | Replace with Vue/HTML/CSS/SVG | UI parity and accessibility gates |
| Algorithm Explorer feature | Retain as advanced route | Rebuild from tested engine rather than faulty dense encoding |
| Empty preference wrappers and online stub | Retire after migration review | No invented service/compatibility requirement |
| Unused widget subclasses/helpers and archived source | Retire at final cutover | Verify references/assets; preserve notices; do not delete during scaffold |
| Native launchers | Keep during migration | Explicit supported-distribution decision before removal |

There is no basis for calling the whole existing engine "state of the art" or discarding it wholesale. Sparse dancing links, compact array/bit-mask representations, and the existing shape-generation work are appropriate reusable foundations. The surrounding contracts, correctness tests, cancellation, persistence, and UI separation need substantial improvement.

## 8. Complete Java Inventory

Every filename below was read in its subsystem review. Lists group related files; generated runtime and archived duplicate sources are excluded from the 78-file count.

| Directory | Files | Count |
| --- | --- | ---: |
| root package | `Constants`, `Settler` | 2 |
| `asset` | `AssetLoader` | 1 |
| `backend` | `DrawString`, `Game`, `GameProperties`, `GameScreen`, `MyShapeRenderer`, `ScreenModel`, `SequentiallyThinkingScreenModel` | 7 |
| `backend/io` | `IOOnlineLibgdx` | 1 |
| `common` | `IDGenerator`, `KeyCodes`, `Localization` | 3 |
| `entity` | `ApoButton`, `ApoButtonCheckbox`, `ApoButtonChessball`, `ApoButtonColor`, `ApoButtonColorHexa`, `ApoButtonColorWithoutBorder`, `ApoButtonImage`, `ApoButtonImageThree`, `ApoButtonImageWithThree`, `ApoButtonLanguage`, `ApoButtonMovement`, `ApoButtonPresent`, `ApoButtonSolutionPresent`, `ApoButtonSwap`, `ApoButtonSwitch`, `ApoEntity`, `HintBubble`, `Snowflake`, `Textfield` | 19 |
| `game` | `MainPanel`, `ButtonProvider` | 2 |
| `game/algorithm` | `Algorithm`, `AlgorithmPreferences` | 2 |
| `game/menu` | `Menu`, `MenuPreferences` | 2 |
| `game/leaderboard` | `Leaderboard`, `PlayerSelection`, `PlayerSelectionForEdit` | 3 |
| `game/tiwanaku` | `Tiwanaku`, `TiwanakuPreferences`, `GameState`, `LevelSize`, `GameTimer`, `GameResult`, `Scoreboard` | 7 |
| `level` | `Level`, `LastSaveSpots`, `UniqueSolution`, `SolveCheck`, `Solve`, `PossibleValueCheck`, `NewLevelCreate`, `LevelCreate`, `FillAndUniqueCheck` | 9 |
| `level/algorithmX` | `Node`, `ColumnNode`, `AlgorithmXSolve`, `AlgorithmXHelper`, `AlgorithmX`, `AlgorithmHelp` | 6 |
| `level/famiru` | `Stats`, `MatrixEntry`, `Dlx`, `ChoiceInfo`, `App` | 5 |
| `level/helper` | `LastSaveSpots`, `Helper`, `FillHelp`, `Difficulty` | 4 |
| `utils` | `Logger` | 1 |
| `desktop/src/...` | `DesktopLauncher` | 1 |
| `android/src/...` | `AndroidLauncher` | 1 |
| `teavm/src/...` | `TeaVMLauncher`, `TeaVMBuilder` | 2 |
| **Total** | | **78** |

The two LastSaveSpots classes are different historical helpers; the root int-array variant is unused, while the helper-package byte-array variant is referenced only by the obsolete private uniqueness path inside FillAndUniqueCheck. Neither is the current checkpoint implementation. `AlgorithmHelp` and `AlgorithmXHelper` are unused/comment-only helpers; this does not make the whole `algorithmX` directory dead. `LevelCreate` remains reachable through the Algorithm screen. Widget names that reference other games largely reflect inherited framework code; only the actual reference graph should determine final deletion.

## 9. Executed Validation

Working tree was clean before planning. Only the two planning documents are intended authored changes. Build outputs are ignored artifacts.

| Command / check | Result |
| --- | --- |
| `java -version` | Temurin OpenJDK 25.0.4.1 |
| `mvn -version` | Maven 3.10.0 using Java 25 |
| `node --version` | v22.22.3 |
| `npm --version` | PowerShell blocked npm.ps1 by execution policy |
| `npm.cmd --version` | 10.9.8; no policy change needed |
| `.\gradlew.bat --version` | Actual wrapper Gradle 8.13 |
| `.\gradlew.bat :core:compileJava --no-daemon --offline` | Successful on Java 25; unchecked-operation warning in PossibleValueCheck |
| `.\gradlew.bat :core:test --no-daemon --offline` | Failed configuring Test reporting under Java 25, `Type T not present` |
| JDK 17 wrapper invocation, `:core:test --no-daemon --offline` | Successful build, test/compileTestJava `NO-SOURCE` |
| JDK 17 wrapper invocation, `:desktop:compileJava :teavm:compileJava --no-daemon --offline` | Successful, tasks up-to-date |
| JDK 17 wrapper invocation, `:teavm:buildRelease --no-daemon --offline` | Successful actual web compilation, reported 1,162 classes; output under teavm/build/dist/webapp |
| `jar tf core/src/com/apogames/settler.zip` | Listed historical source snapshot contents |

The supported legacy invocation used:

```powershell
& "C:\Users\ho.jchatron\Documents\local_bin\jdk-17.0.20.1\bin\java.exe" -classpath "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain :core:test --no-daemon --offline
```

Gradle reports deprecations incompatible with Gradle 9. Do not infer full Java 25 support from one successful compile. Keep the new Java 25 build isolated while the old Android/desktop/web baseline remains under JDK 17.

Not verified here: Android packaging/signing, real interactive gameplay, browser performance, mobile touch usability, screen readers, runtime storage fixtures, new Spring/Vue project installation, embedded database durability/packaging, or solver property tests. Applicable checks are explicit implementation/release gates in the revised plan, not completed work. The former plain-TeaVM worker proposal is no longer in the target architecture.
