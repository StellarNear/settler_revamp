# Tiwanaku Modernization Plan

Status: revision 2, implementation in progress; P00-P03 accepted, with the user's P02 offline-test waiver; P04 next.
Prepared/revised: 2026-10-26. Repository: `settler`. Product name: Tiwanaku.
Companion evidence: [Codebase audit](codebase-audit.md).

Revision 2 follows the user's final clarification and overrides earlier conversational architecture suggestions: **a mandatory Java 25 backend runs locally; a browser opens the Vue UI on localhost**. Earlier plain-TeaVM/browser-worker, static PWA, optional-server, and independently installed mobile proposals are rejected for this target. Historical audit/build references to those technologies describe the old app, not new requirements. This revision changes only this plan; the audit remains separate evidence.

## 1. Recommendation

Rebuild the presentation and application-state layers, not the puzzle mathematics from scratch.

Use **Java 25 for a pure puzzle engine and a Spring Boot 4 MVC local server**, with **Vue 3 + TypeScript + Vite**, native HTML controls, and a **decorative SVG terrain layer**. Package the built frontend as same-origin static assets in one executable JAR. Java owns rules, generation, attempts, history, and settings. The browser owns rendering and transient interaction state.

The user starts `java -jar server/target/tiwanaku.jar` and opens the printed `http://localhost:8080` URL. The Java process must remain running. **Offline means internet disconnected, not backend disconnected**: locally installed Java, the JAR, database, browser, and all runtime assets suffice for cold launch, generation, play, and history without internet access. Node/npm are build/development tools only, never a runtime dependency after installation.

Use embedded **H2 file-mode via JDBC and Flyway** for durable local data. No separate database server or ORM is required. H2 stays entirely in Java and avoids native SQLite library packaging across operating systems. This is one modular local application, not a hosted service or an independent browser application.

### Decisions

| Area | Decision | Reason |
| --- | --- | --- |
| UI | Vue 3 SFCs, Composition API, TypeScript strict mode | Maintainable, inspectable UI with standard browser input and accessibility |
| Board | HTML interaction grid plus aligned decorative SVG | At most 240 cells; no continuous game loop needed |
| Terrain | Shared board-space patterns clipped to region shapes | Cohesive landscape rather than one repeated picture per cell |
| State | Java authoritative commands; Pinia holds server snapshots and ephemeral view state | No competing browser rules/persistence authority |
| Engine | Framework-free Java 25, independently tested | Reuse useful generation and exact-cover work without a portable Java 17 subset |
| Java runtime | Java 25 required for new engine/server | JDK 17 is only for the legacy Gradle build |
| Build | Maven wrapper for new Java modules; npm lockfile for frontend | Existing Maven/Node are available; isolate from Android Gradle configuration |
| Storage | Embedded H2 file-mode, JDBC, Flyway | Transactional saves in a stable user-home directory; no DB server/native driver packaging |
| HTTP | Mandatory Spring Boot 4 MVC server, loopback only | Logical commands over local HTTP; CPU generation on a bounded executor |
| Distribution | One executable JAR with bundled Vue assets | Same-origin app; no Node, CDN, or service worker needed at runtime |
| Testing | JUnit, contract fixtures, Vitest, Playwright, accessibility checks | Engine/storage/API correctness and behavior before appearance |

### UI Framework Choice

| Option | Fit / tradeoff | Decision |
| --- | --- | --- |
| Vue 3 + TypeScript | SFCs, Composition API, straightforward templates and small Pinia stores suit this grid/forms app | Recommended project fit |
| React + TypeScript | Equally capable DOM/SVG UI; reasonable if maintainers strongly prefer its ecosystem | No demonstrated benefit justifying a switch here |
| Svelte + TypeScript | Concise reactive components; equally capable visual output | Viable alternative, not a necessary improvement |

No framework guarantees aesthetics. Atlas art direction, spacing, typography, responsive layout, contrast, and interaction design determine quality. Use a client-rendered SPA; no SSR, Nuxt, Next, or server-rendered frontend framework is needed for a private localhost puzzle.

Spring Boot is not the smallest possible HTTP runtime; Javalin is a reasonable lighter alternative. The default here favors conventional executable packaging, validation, security integration, JDBC transactions, and tests. Keep dependencies limited to what is used (MVC, JDBC, validation/security, H2, Flyway); do not introduce ORM, reactive infrastructure, or Spring Cloud. Measure startup/heap before claiming the new application is lighter overall: the browser presentation becomes simpler regardless of backend framework size.

Do not add WebGL, PixiJS, Three.js, an ECS, microservices, Redis, Kafka, GraphQL, accounts, cloud synchronization, a database server, or an enterprise UI kit that determines the visual identity. No second browser database or elaborate offline command queue.

### Architecture Gate

Before a large scaffold, demonstrate all of the following with a minimal board:

1. The pure Java 25 engine builds with zero libGDX/Spring dependencies and passes independent correctness fixtures.
2. A minimal Vue board loads from the local Spring server's own origin and uses the real Java engine, not a stub.
3. A newly generated puzzle can be played, transactionally saved/completed, reloaded, and resumed after server restart from an isolated H2 test directory.
4. Duplicate commands and a response lost after commit cannot create duplicate moves/results; competing tabs cannot overwrite each other.
5. Generation is bounded and cooperatively cancellable inside search; HTTP/UI remain responsive and timeouts never certify uniqueness.
6. With Java stopped, edits pause and reconnect/reconciliation are explicit. Internet-disconnected cold launch remains a design goal, but the user explicitly waived that acceptance test on 2026-10-27; it is unrun, not passed.
7. Largest-board/adversarial measurements fit provisional budgets or return a clear bounded failure.

P02 proves this gate before broad UI work. If it fails, stop expansion and report evidence, then repair or request a scoped decision. Do not silently replace the mandatory Java backend with a browser engine, a puzzle-pack-only product, or a hosted fallback.

## 2. Scope And Product Rules

### Preserve

- Single-player number placement on orthogonally connected regions.
- A region of size N contains each value 1 through N exactly once.
- Equal values cannot touch in any of the eight neighboring positions, including diagonals.
- All seven existing board-size presets, three difficulty labels, and maximum region sizes 5 through 9.
- Local generation, local history, player tags, optional help, manual pencil marks, EN/DE support, timer, and error indication.
- Algorithm exploration as an advanced educational feature, not a requirement for the first playable slice.
- No ads, mandatory accounts, purchases, analytics, or automatic uploads of personal results.

### Improve Deliberately

- Direct number entry and keyboard navigation replace mandatory repeated cycling. Optional legacy click-cycle mode can be added only if useful; do not silently count cycling clicks as equivalent to direct entries.
- Add real session autosave, resume, undo/redo, and an explicit checkpoint/restore facility. These are new or restored product capabilities, not existing working persistence to port.
- Separate **New Puzzle** from **Restart This Puzzle**. Restart creates a new attempt of the same puzzle and records it as a replay; it does not produce an unmarked fresh competitive time.
- Keep the requested generation settings separate from the actual active puzzle metadata.
- Save a completed result before opening the player-name dialog. Names are editable metadata, not the trigger for saving the win.
- Split help into inspect conflicts, show candidates, explain a deduction, and explicitly reveal a value. Initial release may offer the first two plus a single-candidate explanation; never advertise a full human-solving tutor before it exists.
- Record assistance and replays. New scores and legacy click-based accuracy must not be combined into a supposedly comparable universal ranking.
- Retain friendly difficulty labels initially, but describe them as legacy generation presets until a calibrated rating system is implemented.

### Defaults Requiring No Further Question To Start

The first new client is a browser connected to the mandatory local Java 25 application. Local generation without a finite content pack remains a release goal, subject to per-job resource limits. Scores remain local and unverified. Existing desktop/Android releases remain available during migration. There is no standalone modern mobile requirement: a phone's localhost is the phone, not the computer running Java, and LAN exposure is not part of this loopback design. Small-window/touch-responsive UI tests remain valuable without promising mobile installation. The educational algorithm screen moves under an advanced section. Terrain is stylized 2D, not 3D. EN/DE remain supported; French is a separate translation task.

Any decision to remove offline generation, discontinue a native distribution, introduce a hosted personal-data service, or remove Algorithm Explorer permanently requires explicit product approval. These decisions do not block the initial engine and UI work.

## 3. Target Structure

Create new work alongside legacy modules. Do not move or delete old modules in the scaffold.

```text
settler/
  pom.xml                       # new Maven aggregator; does not build legacy Gradle
  mvnw, mvnw.cmd, .mvn/          # pinned wrapper
  puzzle-engine/
    src/main/java/com/apogames/tiwanaku/engine/
    src/test/java/...
  server/
    pom.xml                     # Spring Boot 4; final executable name tiwanaku.jar
    src/main/java/...           # API, commands, jobs, JDBC repositories, bootstrap
    src/main/resources/
      db/migration/             # versioned Flyway SQL migrations
    src/test/java/...           # real H2/API/lifecycle tests
    target/                     # generated JAR/resources; never authored UI copies
  web/
    package.json, package-lock.json
    src/
      app/                      # router, shell, startup
      features/game/            # board, input, snapshot/view store, overlays
      features/setup/
      features/history/
      features/settings/
      features/explorer/        # lazy-loaded advanced route, later
      api/                      # HTTP contracts, command serialization, job polling
      ui/                       # small reusable dialogs/buttons/forms
      styles/                   # tokens, typography, terrain styles
      locales/                  # en/de dictionaries
    public/                     # approved textures/fonts/icons
    tests/                      # component, integration, browser tests
  contracts/
    schemas/                    # versioned JSON contracts
    fixtures/                   # engine vectors and API serialization examples
  core/, desktop/, android/, teavm/, assets/, docs/  # legacy, initially untouched
```

Keep this a small modular application. Do not create a package, interface, factory, service, and mapper for every class. Add abstractions at real boundaries: engine execution, storage, clock, and random source.

Dependency direction: server depends on the Java engine; the engine depends on neither Spring, H2, nor Vue. Vue components depend on small view stores/API use cases, not JDBC or raw fetch calls scattered across components. Schemas/fixtures form the Java/TypeScript boundary. Neither frontend nor new Java code imports legacy UI classes.

Both new Java modules compile and run on Java 25; do not constrain the engine to Java 17 syntax/bytecode/APIs. Prefer stable features, no previews. Keep Spring/Jackson annotations, filesystem/database IO, UI globals, and executor management out of pure algorithms. Supply random/clock/budget/cancellation inputs through small testable boundaries; the server owns threads and storage.

## 4. Domain And Contracts

### Immutable Puzzle

Use flat row-major arrays: `cellIndex = row * columns + column`. Define this once in the schema and fixtures. The current Java arrays use row/column conventions that must be adapted carefully.

| Field | Contract |
| --- | --- |
| `schemaVersion` | Integer, initially 1 |
| `puzzleId` | Stable content-derived identifier or assigned immutable ID; never an array reference |
| `engineVersion` / `generatorVersion` | Explicit versions; generator changes do not reinterpret old puzzles |
| `seed` | Decimal string, not a JavaScript numeric 64-bit value |
| `columns`, `rows` | Approved preset dimensions initially; maximum 240 cells |
| `requestedDifficulty`, `maxRegionSize` | Generation settings actually used |
| `regionIds` | One nonnegative region ID per cell; each region orthogonally connected |
| `terrainByRegion` | Decorative biome assignment; never used to infer constraints |
| `givens` | 0 for blank, otherwise valid region value |
| `solution` | Canonical solution retained by Java/database; omitted from ordinary play DTOs, used for reveal/mistake features |
| `uniquenessStatus` | Must be `PROVEN_UNIQUE` for a newly generated normal puzzle |
| `generationStats` | Actual checks/nodes/retries/durations as available; not a fabricated percentage |

Preset dimensions are fixed for v1: `SUPERMINI=3x3`, `MINI=4x4`, `SMALL=5x5`, `MEDIUM=7x5`, `BIG=9x5`, `UBER=15x10`, `TITAN=20x12` (columns x rows). Normalize region IDs to consecutive integers in first-row-major-appearance order, and index `terrainByRegion` by those IDs. All cell arrays must have exactly `rows * columns` entries. Notes use bit `value - 1`; reject bits beyond the cell's region size.

Region identity is not terrain identity. Two separated forest regions are different constraints. Rendering must not accidentally merge them. The number of biome styles is independent of the maximum region size.

The local solution is ultimately inspectable by the machine's user, just as local code/storage is. This is acceptable for a private puzzle. Omitting it from play DTOs avoids accidental disclosure, not cheating. A future verified online competition needs a separate trust model.

### Mutable Attempt

Store on Java/H2: `attemptId`, `puzzleId`, immutable puzzle reference, `values`, pencil-mark bitmasks, elapsed active milliseconds, status, assistance counters, replay flag, checkpoint snapshot, bounded undo/redo state, record revision, and schema version. Pinia keeps the latest acknowledged snapshot plus ephemeral selected cell, hover, note-entry mode, open dialogs, request/pending state, and optional pending preview. Selection is not a puzzle mutation and needs no HTTP request.

Store settings separately: locale, theme, reduced detail, show-conflicts preference, default puzzle settings, and last local player selection. Never recompute active puzzle metadata from current menu settings.

### Result And Scoring v1

New results store `resultId` (equal to `attemptId` for newly completed attempts), puzzle/settings snapshot, completion UTC epoch milliseconds, elapsed active milliseconds, nullable player tag, replay flag, assistance counters, and `scoringVersion="direct-entry-v1"`. Show time, assistance, and entry counts rather than inventing a new accuracy percentage. Imported records use independent IDs and `scoringVersion="legacy-clicks-v1"`, preserving their percentage/counters with null for unavailable metadata.

Count `valueEntryCount` only when a user assigns a different nonzero editable value; clearing, selecting, notes, givens, and no-op entries do not increment it. An optional `wrongEntryCount` compares such assignments with the canonical solution for end-of-attempt statistics, without revealing correctness during play unless enabled. Undo/checkpoint do not subtract counters. Track candidate requests, explanation requests, value reveals, and whether automatic conflict display was ever enabled as separate fields; expose filterable assistance labels rather than one unexplained score penalty. No automatic cross-version accuracy averages.

For new tags allow empty/anonymous or 1-3 ASCII letters, normalized uppercase, matching actual legacy input behavior. Preserve imported legacy strings as inert text even when outside this format. Do not add a separate profile database.

### Attempt State Machine

```text
Generation: Idle -> Queued -> Running -> Succeeded | Failed | Cancelled | BudgetExceeded
Attempt:    Ready -> Playing -> Completed
              |        |
              +--------+-> Suspended -> Ready or Playing (previous state)
```

Generation and attempt states are independent. A successful job offers a verified puzzle; an explicit idempotent accept command transactionally creates a Ready attempt. There may be no attempt while the first generation runs. Keep the previous attempt until the new puzzle is verified and accepted. A failed or cancelled generation must not destroy it. The browser distinguishes pending, saved, failed, and outcome-unknown requests; an unacknowledged final move is not a durable win. While completion is pending/unknown freeze further edits and timer display until reconciliation.

- `Ready`: timer has never started; selecting a cell, opening help, and editing pencil marks do not start it by default.
- `Playing`: first actual editable value change starts timing; entry, clear, undo, and restore follow documented command semantics.
- `Suspended`: leaving the play route, hiding the document, or explicit pause stops active time; lease expiry handles device lock or missed lifecycle events. On reload, never count time while closed.
- `Completed`: checked against all constraints, persisted exactly once, immutable elapsed time and move statistics. Editing name does not duplicate the result.
- Restart creates a new attempt against the same immutable puzzle, clears values to givens, notes, checkpoint, and history, and marks the attempt as replayed.
- Undo/redo or checkpoint restore changes values/notes only; it never rolls back elapsed time, assistance counts, or an already completed result.
- Checkpoint is a user-created snapshot, independent of automatic crash recovery. Replacing/restoring it needs clear wording; restore is one undoable command.

While Suspended, reject board mutations, undo/redo, checkpoint restore, and puzzle-specific help; Resume is an explicit action before accepting them. While Completed, reject all commands that change board/progress/statistics; allow name editing, export, navigation, New Puzzle, or Restart into a new replay attempt. Closing a help dialog does not change attempt status. Resuming an unstarted Ready attempt does not start the timer until a real value change.

Use one Java command application path for value changes, clears, notes, undo/redo, checkpoint/restore, help, pause/resume, and restart. It validates status, fixed cells/ranges, revision and lease ownership, updates state/counters, checks all completion constraints, and commits the resulting state. Rendering is a projection, never the source of game rules.

### Rule Checks

Java is authoritative for generation, exact solving, candidates/conflicts, move validation, completion, and assistance counters. TypeScript may disable visibly fixed/out-of-range controls from server metadata, but cannot accept a move, declare completion, or compute a competing rules state. One HTTP request per logical move/help action is acceptable over localhost, not one per frame, key-repeat tick, selection, or timer repaint. No TypeScript solver/generator is needed.

Candidates mean values legal under current visible entries, not guaranteed globally solvable choices. Conflicts are not the same as a valid-but-wrong value versus the canonical solution. Use distinct language and flags for these concepts.

Default conflict policy preserves current play: highlight duplicates on a full invalid board or an explicit Check action. Offer continuous conflict display as an opt-in setting and record its use separately. Existing pencil-button filtering is based on original clues, not computed legal candidates; the new numeric manual-note editor must not silently present those two concepts as equivalent.

### HTTP And Command Contracts

Version API/DTO schemas; responses include `requestId`, relevant `engineVersion`, and authoritative revisions. Use structured errors with stable code, safe message, retryability, and current revision where applicable. Do not expose stack traces or paths from arbitrary requests.

| Endpoint family | Contract |
| --- | --- |
| `GET /api/v1/bootstrap`, `GET /api/v1/capabilities` | Safe same-origin bootstrap, process instance ID, schema/API versions, limits, supported presets, current attempt summary |
| `POST /api/v1/generation-jobs` | Validated spec/seed/request ID; 202 with opaque job ID, no blocking search on HTTP thread |
| `GET /api/v1/generation-jobs/{id}`, `DELETE .../{id}` | Phase/progress/terminal status; cooperative cancel; idempotent cancellation |
| `POST /api/v1/attempts` | Accept verified job output or restart a stored puzzle, with idempotency; never accept a client-supplied proof as trusted |
| `GET /api/v1/attempts/{id}` | Latest acknowledged snapshot, revision, status and editor/lease metadata |
| `POST /api/v1/attempts/{id}/commands` | `commandId`, `expectedRevision`, editor session, type/payload, optional active elapsed proposal; atomic application |
| `GET /api/v1/attempts/{id}/commands/{commandId}` | Resolve an uncertain commit; receipt or not-found plus current revision |
| History/settings/import/export | Versioned, bounded requests, pagination, revision checks, confirmed destructive actions; data streams rather than arbitrary filesystem paths |

Use one in-flight mutation per attempt, including help and heartbeat/time changes. Initially allow **no additional queued board commands** while it is pending (queue capacity zero); keep selection/navigation responsive and indicate pending input. This simple bounded queue avoids accidental unbounded click/key-repeat accumulation. If measurements justify a short queue later, explicitly cap it and base subsequent commands on acknowledged revisions only. Pause immediately stops the client clock, then sends its captured proposal once the current mutation resolves; do not resume accepting edits in the meantime.

Each command ID identifies one immutable payload. In a JDBC transaction, serialize on the attempt row (row lock or tested compare-and-set), check an existing receipt first, then expected revision/editor lease, apply the Java command, update the gameplay revision, insert any completion result, and insert the receipt. Unique constraints and transaction rollback cover duplicate concurrent requests, not just a check-then-write in Java. Same ID/same payload returns the original outcome without reapplying; same ID/different payload returns conflict. A stale expected revision returns 409 with reconciliation instructions, never last-write-wins. Store receipts durably for the retained attempt lifetime (compact identity/hash/outcome/revision records; bound history/import size and expose storage failures, never silently evict live deduplication keys). Creation and other non-attempt mutations also need stable idempotency keys where retry can duplicate data.

After a timeout, keep the command ID and mark outcome unknown, not failed-and-undone. Query the receipt/current attempt or retry exactly that payload/ID; never mint a new ID for an uncertain mutation. If receipt revision is older than current state, fetch the latest snapshot rather than overwriting it. After reload, read the attempt and never blindly replay browser state. Ignore stale responses by request ID, attempt ID, revision, and process instance; old generation handles do not survive a server restart. Refresh bootstrap/token after restart before resuming. A failed response after successful commit must converge to the saved server state, including exactly one completed result.

Use an active-editor lease per attempt with explicit takeover/read-only UI for another tab. Revision checks remain the final authority; takeover invalidates the old editor lease, and an old command cannot overwrite newer state. BroadcastChannel notifications are optional hints, not locks or persistence. Backend unavailable means pause, disable/reject mutations, retain only an unsaved indication, and offer reconnect. Do not keep playing into a durable browser queue or create a second source of truth.

### Timer Contract

This is pragmatic local, unverified timing, not anti-cheat. The client uses `performance.now()` to propose cumulative active elapsed milliseconds for an editor lease, while Java validates and persists accepted increments. The first real value change starts timing; notes/help alone do not. Render at one-second cadence without sending per-frame HTTP requests.

Baseline: heartbeat every **5 seconds while visible and Playing**, editor lease **15 seconds**, with elapsed proposals also attached to commands and explicit pause/completion. Proposals must be finite, nonnegative, monotonic within the lease; their increments are bounded by server-observed monotonic time since the last accepted proposal plus a small documented scheduling tolerance (initially 1 second). Reject or clamp out-of-bound proposals deterministically and report correction; do not silently create extra time. Persist cumulative totals, not retry-additive deltas. Use a lease-scoped monotonic timing sequence separate from gameplay revision: heartbeat commits must not advance gameplay revision. Ignore duplicate/older timing sequences, and never let a stale heartbeat revive a paused/completed attempt or expired lease. Serialize/coalesce heartbeats with mutations in the client; a move containing a proposal replaces a due heartbeat, and pause/completion consumes the final sequence atomically with its command. Use fake clocks for tolerance/ordering tests.

Persist only proposed active intervals, never automatically add wall time until lease expiry. Hidden/route-exit/pause captures an immediate stop; a best-effort pause request is helpful but unload is not a save guarantee. No heartbeat/expired lease or backend loss suspends the attempt at its last committed elapsed value. A new server process invalidates all old leases and resumes neither timing nor edits automatically. Resume establishes a fresh baseline, with no accounting for the closed/disconnected interval.

Crash-loss target is at most the **5-second unsaved heartbeat interval plus an in-flight request** during healthy acknowledged operation; do not claim a hard 5-second bound during stalled networking/scheduling. Pause after an unresolved request/heartbeat timeout (initially 5 seconds); the 15-second lease bounds continued normal-session submissions and server ownership, not fabricated closed-time accrual. On crash/reload reconcile to the committed server total, retaining any timing committed despite a lost response; discard only client-only uncommitted timing and document the possible undercount. Verify the practical loss window in P02/P04 rather than building a tamper-proof timing protocol.

### Generation Jobs

Start conservatively with one running CPU job and at most two queued jobs globally, one active job per client; expose bounded configurable limits measured in P02. Reject excess with 429/503 and retry guidance. Use a dedicated bounded CPU executor, not unbounded virtual threads. One job owns its mutable solver instance; no shared Dlx state. CPU search must not hold JDBC transactions or storage locks.

Pass cancellation token, monotonic deadline, node/retry/retained-solution budgets, and throttled progress through **every recursive search, region coloring, clue removal, and restart loop**. Check at bounded work intervals, restore solver state in `finally` as needed, and publish only one terminal result. `Future.cancel(true)` alone is insufficient; interruption is a supplementary signal that algorithms must check. Never use `Thread.stop`. Keep CPU cancellation separate from JDBC IO threads; do not interrupt a database transaction as a way to cancel generation.

Poll only active queued/running/cancel-requested jobs every **250-500 ms**, with at most one poll in flight. Stop on terminal outcome, route disposal, or unavailable backend; use explicit reconnect/backoff instead of perpetual background polling. Progress is phase/checks/nodes/retries, not a fabricated percentage. On cancellation display acknowledgement promptly, but do not declare CPU work stopped until cooperative termination. Late results for cancelled/replaced request IDs cannot replace the active attempt. Job handles/results expire after a documented bounded retention (initially 10 minutes after terminal state, capped count); process restart loses in-memory jobs, not committed attempts. A succeeded unaccepted puzzle must be accepted before expiry or regenerated.

Job-result publication uses a short guarded state transition after search, never a long database transaction. Accepting a result checks job status, request identity and the expected current attempt before transactionally installing it; cancellation/replacement racing with success cannot silently switch the board. Bound queue wait separately from execution deadlines (initially 30 seconds waiting); expire queued jobs rather than leaving them indefinitely pending.

Distinguish `ZERO_SOLUTIONS`, `ONE_SOLUTION`, `AT_LEAST_TWO`, and `UNKNOWN`. `ONE_SOLUTION` proves uniqueness only when remaining search is exhausted. Timeout/cancellation is unknown, never uniqueness. Return `BUDGET_EXCEEDED` without an uncertified puzzle; a separately verified recovery puzzle may be offered explicitly, not mislabeled as requested generation.

## 5. Java Engine Strategy

Preserve the useful ideas and measured behavior, not every existing class unchanged.

1. Extract the production `NewLevelCreate -> FillAndUniqueCheck -> UniqueSolution -> Solve` flow, and the sparse `famiru` solver used for initial solutions, behind tests.
2. Replace the few `GridPoint2` dependencies with a small engine coordinate type or cell indices; remove unused UI imports.
3. Separate immutable topology/solution/givens from mutable attempt data currently held by `Level`.
4. Introduce deterministic RNG, explicit iteration order, node/retry budgets, clock abstraction, and progress callback. Use a named stable PRNG with published reference vectors; do not assume `Math.random`, collection shuffle order, or thread scheduling is reproducible.
5. Correct full-board validation and other proven defects before using old solver output as an oracle.
6. Consolidate exact uniqueness checking around a tested sparse solver with fixed-clue support and a stop-after-two API. Retain the existing logical candidate solver only where it adds human-solvability/difficulty information.
7. Differential-test the consolidated path against independent tiny-board enumeration and known fixtures. Do not blindly replace one solver with another and call that verification.
8. Preserve existing generation shape strategies initially, including the large-board branch. Benchmark before replacing them. Do not promise optimal/minimal clue sets.
9. Later derive difficulty from explainable solving steps, branching depth, and technique use, with a new rating version. Avoid a false claim that clue percentage alone measures human difficulty.

Replace the small-board path's up-to-one-million retained initial solutions with bounded witness selection/sampling. Precompute immutable region membership and eight-neighbor lists before micro-optimizing allocations. Preserve the constructive UBER/TITAN branch, but apply global budgets to its coloring/restarts too. Validate dimension bounds instead of allowing byte-coordinate/counter overflow, defensively copy escaping arrays, and isolate mutable Dlx instances. The audit proves full-input/encoding defects, not that all ordinary generated puzzles are nonunique; do not overstate that finding.

Do not optimize into parallel search before deterministic correctness and cancellation are tested. Retain efficient plain arrays. Java 25 records are useful for immutable boundary data; do not rewrite every helper just to use newer syntax. Same seed/version/iteration order and deterministic node budget should reproduce successful fixtures; wall deadlines can change whether a run succeeds, not justify accepting partial proof.

## 6. Visual And Interaction Design

### Direction: A Quiet Topographic Atlas

Use the existing Andean exploration identity without imitating a generic SaaS dashboard. The puzzle is the main artifact: an atlas sheet with crisp boundaries, restrained natural textures, readable numbers, and a compact instrument-like toolbar. Avoid excessive cards, glass effects, gradients behind every control, and decorative UI competing with the board.

Proposed tokens, subject to measured contrast: warm paper `#F4F0E7`, dark ink `#24352F`, muted forest `#D7E2CE`, sand `#EADBB9`, mountain `#D9DEE3`, water `#D2E3E9`, selection `#176A62`, error `#B13D35`. Use locally bundled licensed fonts: a restrained serif for title only and a highly legible sans-serif with tabular digits for the game. Existing Open Sans can remain if its license is bundled and provenance confirmed.

### Terrain Rendering

1. Compute regions from `regionIds`, not colors or image selection.
2. Render one SVG beneath the HTML grid with a coordinate system in cell units.
3. Give each region a union silhouette: a path or a clip path built from its cell rectangles. Region-border segments are emitted wherever adjacent IDs differ or an outer edge is reached.
4. Fill with a flat biome color plus a small seamless vector/raster pattern anchored in board coordinates using `patternUnits="userSpaceOnUse"`. Do not restart the texture at every cell.
5. Add sparse, seeded decoration inside the region if needed: contour lines for mountain, curved dune marks for sand, small canopy marks for forest, thin wave marks for water. Texture variation is deterministic and presentation-only.
6. Render thin internal cell separators and stronger region boundaries above the terrain. Keep the square logical grid and corner contacts unambiguous.
7. Put numbers, pencil marks, focus, and selected/conflict indicators in the HTML layer. Use a calm backing patch/halo where necessary to guarantee contrast.
8. Offer plain/high-contrast and reduced-detail modes. Do not rely on biome color, texture, or red/green alone to communicate state.

The SVG is `aria-hidden` and `pointer-events: none`. Alignment comes from the same CSS container and row/column geometry as the controls, not separately calculated pixel offsets. Test concave regions, holes if supported, corners, adjacent equal-looking terrain, zoom, and fractional device-pixel ratios. Do not add animated water, per-cell SVG filters, or a continuous requestAnimationFrame loop.

This is still texture rendering: a texture can be a small repeated vector pattern or raster noise tile. The improvement is coherent region-level art direction and crisp overlays, not a blanket prohibition on image files. WebGL is a later option only if measured requirements justify it.

### Layout

- Desktop: narrow top header, board taking primary space, 260-320 px inspector for keypad, selected-region information, help, and history actions.
- Tablet: compact inspector below or beside the board based on available width, not user-agent detection.
- Narrow/phone-sized viewport: full-width board, sticky bottom keypad/actions respecting safe areas, compact status header, accessible help/settings dialog. This is responsive layout coverage, not a standalone phone distribution promise.
- Do not scale a 1400x800 scene to fit the screen. Layout elements reflow; text remains readable.
- Large boards cannot have 44 px targets and fit 20 columns into a phone width. Default to a readable scrollable/zoomable board with a mini overview/fit button; support normal page/browser zoom. Never present 15 px cells as accessible touch targets.
- At fit-to-board scale, selecting an imprecise tiny cell must have an alternative: zoom-to-selection and keyboard navigation. The default interactive view should use roughly 36-48 px cells and 44 px toolbar/keypad targets.

### Inputs And Accessibility

Click/tap selects. Digits 1-9 set a value, Delete/Backspace clears, arrows move, Home/End navigate rows, Tab exits the board rather than visiting 240 cells, and a documented shortcut toggles note mode. Ignore game shortcuts while editing a form. All shortcuts also have visible controls. Escape closes dialogs or exits transient mode; it must not abandon a puzzle unexpectedly.

Implement an ARIA grid with rows/gridcells and roving focus on the interactive cells. Announce row, column, region, given/editable state, value, notes, and conflict without reading the entire board after every move. Fixed clues remain discoverable, even if not editable. Use a polite status region for meaningful move/help feedback, not per-frame timer/progress output. Test with keyboard and an actual screen reader in addition to automated checks.

Pencil marks are numerals at every region size, not color-only circles. Candidate and manual-note presentation must be visually distinct. Respect `prefers-reduced-motion`, contrast modes, browser text scaling, touch scrolling, and focus visibility.

### Screens

| Route / surface | Purpose and acceptance |
| --- | --- |
| Home / setup | Continue existing attempt; size, difficulty, maximum region controls; clear New Puzzle action |
| Play | Board, keypad, timer, notes, help, undo/redo, checkpoint, pause; responsive states |
| Completion dialog | Result already saved; show time and assistance; select/create tag; next puzzle/replay/history |
| Local history | Sort/filter by puzzle settings/player/assistance; filtered count/known total time; legacy-only mean accuracy; scrollable; edit tag; confirmed deletion/export |
| Settings / help | Locale, visual/accessibility preferences, explicit rule examples including diagonal conflict |
| Algorithm Explorer | Advanced, lazy-loaded; solver events and small examples; no legacy dense matrix dependency |

Use native text input with visible validation for player tags instead of custom key handling. Preserve legacy three-letter tags and allow their selection; widening tag format is a separately versioned UX decision, not an accidental schema change. Small unstyled accessible primitives (for example, selectively adopted Reka UI dialogs/menus) are acceptable after dependency review; they do not replace atlas art direction or manual accessibility testing.

## 7. Persistence And Migration

### Backend Data Ownership

Embedded H2 file-mode holds `puzzles`, `attempts`, `results`, `settings`, command receipts, and migration/import metadata. Use JDBC with parameterized statements and a small repository boundary; Flyway owns ordered SQL schema migrations. No ORM is required. Store queryable history/identity fields as columns and bounded versioned board/undo snapshots as appropriate, not one unbounded whole-app blob. Apply foreign keys, unique result-per-attempt and command-ID constraints. Bound undo depth (initially 200 commands); trim oldest undo entries without changing counters or deduplication receipts.

Default data directory: `${user.home}/.tiwanaku`, overridden only by **`TIWANAKU_DATA_DIR`** (absolute path, validated at startup). Database, backups, and bounded logs live beneath it, never under the working directory, repository, JAR directory, browser profile, or `target/`. Print the resolved canonical directory on startup. An executable/JAR update must not reset it. Development/tests use explicitly isolated temporary directories; never point test cleanup at the user's default data.

Acquire an exclusive application lock in this data directory before opening H2; H2 file locking remains a second guard. Disable H2 `AUTO_SERVER`, TCP server, and web console. One backend process per data directory, even when ports differ. Lock contention reports an already-running instance and does not silently start another process or choose a fresh empty database. Release the lock on shutdown; stale lock-file presence alone is not proof of a live owner. Concurrent distinct directories require deliberate separate launches, not automatic fallback.

Commit each logical mutation before acknowledging Saved. A Pending preview must be distinguishable from the last saved snapshot, and failures/unknown outcomes use the command reconciliation contract. Configure and test H2 durability deliberately (including disabling delayed writes with `WRITE_DELAY=0` for acknowledged-save semantics); filesystem/drive failure can still violate guarantees beyond the app. Do not promise lossless saves on broken storage. Gracefully shut down jobs then datasource without interrupting JDBC IO threads.

Completion changes the attempt, fixes elapsed/statistics, inserts the unique result, and stores the command receipt in one transaction. Only after commit show the saved completion dialog; tag selection updates that same result. On SQL/commit failure roll back, retain last committed state, show an actionable error and reconcile ambiguous outcomes before retry. Disk full, read-only directory, permission denial, corrupt files, unsupported future schema, and migration failure must not cause a silent empty database reset. Destructive resets/deletions require confirmation and offer export first.

### Schema, Backup, And Recovery

Pin compatible H2/Flyway versions with Spring Boot 4/Java 25 in P02 and test real file databases, not only mocks/in-memory H2. Do not edit applied migrations. Detect unsupported future schema before writes; refuse normal startup with recovery instructions rather than attempting a downgrade. Flyway migrations and import transactions are different concerns. Test partial migration failure using the selected H2 DDL semantics; do not assume every schema upgrade is atomically rolled back.

Before any schema upgrade, hold the exclusive data lock and create a recoverable, timestamped/version-labeled backup. Use a cleanly closed database copy or H2's supported consistent backup operation, never blindly copy live `.mv.db` files. On first launch there is no old database to back up. Keep a bounded automatic backup rotation (initially five successful snapshots), avoid deleting the only pre-upgrade backup on failure, and allow explicit user-exported backups. Backups need space/error handling; if a required pre-upgrade backup fails, stop the upgrade.

Provide versioned logical JSON export/import for portability and history recovery, with size limits and checksum/manifest metadata. For physical recovery, stop Java, preserve the damaged directory, restore a known compatible backup into a separate directory, then validate before replacing the active data. Never run destructive repair automatically. A previous application binary may not read the upgraded schema: rollback means a compatible JAR plus matching pre-upgrade backup, with explicit warning about later edits. Test actual restore and logical export/import, not merely backup-file existence. Raw legacy sources remain untouched.

Browser reload/profile clearing or changing the configured port must not erase server history. Pinia is not persisted as an authority. Browser storage may hold disposable UI hints only; no IndexedDB attempt database, offline move log, or automatic replay of uncommitted moves.

### Legacy Import

The current source writes Preferences `TiwanakuScoreboard`, entry `scoreboard_results`. Records are separated by semicolons (`;`), not newlines. Each record is:

```text
mapSize|timeMillis|accuracyPercent|dateMillis|playerTrigram|totalClicks|totalMissClicks
```

Also support the existing 3-, 4-, and 5-field historical forms according to the old parser. Three fields contain map/time/accuracy, four add date, and five add player. Missing dates/counters were defaulted to zero; import them as not recorded and preserve the stored accuracy. Preserve unknown metadata as unknown; do not invent difficulty, maximum region size, or assistance flags for old records.

For the pinned gdx-teavm 1.5.3 defaults, upstream inspection indicates the browser key is `pref:app:TiwanakuScoreboard:scoreboard_resultss`, including the type suffix and double final `s`. Confirm this with a real old-browser fixture before shipping migration. Do not guess JSON or `localStorage['TiwanakuScoreboard']`.

Desktop defaults store a Java-properties XML file named `TiwanakuScoreboard` under the user's `.prefs` directory. Java can offer an **explicit, confirmed import from the known `${user.home}/.prefs/TiwanakuScoreboard` path**, with preview and raw backup; do not silently scan directories or accept arbitrary filesystem paths from HTTP. Alternatively accept a user-uploaded export file. Bound and safely parse the known properties format, disable external resource/entity resolution, and test an actual libGDX XML fixture. Native Android private storage requires an in-app exporter/update bridge; neither this browser nor a desktop JVM can automatically read another device's private data. Preserve Android application ID/signing continuity if an update performs export.

Browser access to legacy localStorage is possible only at the exact same origin, including scheme and port; path changes alone do not change origin. A new localhost app cannot read arbitrary remote-origin localStorage, and Java cannot bypass that browser boundary. Supply an exporter run at the old origin or accept an explicit user-exported file/paste. Same-origin import is an optional convenience only when genuinely applicable, not a universal migration promise. Generated `docs/` appears older than score support, so do not assume every installed version has a score blob.

Import transactionally, validate lengths/ranges/numbers, retain raw source backup, report rejected records without discarding good ones, and record a migration fingerprint so retries do not duplicate results. Never delete old preferences on first launch. Handle duplicate legacy records without relying on their non-unique size/time/date tuple. Display their scoring method as `legacy-clicks-v1` and unknown settings as unknown.

## 8. Delivery, Tooling, And Security

### Toolchain

The audit verified Java 25.0.4.1, Maven 3.10.0, Node 22.22.3, and npm 10.9.8. Use `npm.cmd`/`npx.cmd` in this Windows environment because execution policy blocks the `npm.ps1` shim; do not change machine execution policy.

Pin Maven wrapper/compiler/test/frontend-integration plugins, Spring Boot 4, H2/Flyway, frontend dependencies, Node build version, and npm lockfile. Earlier research mentioned Spring Boot 4.1.2 as a candidate, not an installed/tested project dependency; verify an actual compatible stable set in P02. Record resolved versions/licenses and validate plugins on Java 25. No new TeaVM compiler/module is part of this architecture.

The legacy wrapper is Gradle 8.13, not the README's 8.12. Java 25 support for running Gradle is documented from 9.1 onward. Core compilation happened to pass on 25, but the test task failed during configuration; JDK 17 works. Keep legacy verification under JDK 17 and the new Maven build under 25. Do not upgrade Android/Gradle and redesign the frontend in the same task.

### Local Launch And Packaging

Stable artifact contract: set the server Maven `finalName` to `tiwanaku`, repackage the executable JAR as **`server/target/tiwanaku.jar`**, and verify the exact filename in CI. From the repository root run **`java -jar server/target/tiwanaku.jar`** with Java 25. A distributed copy may be placed outside the repo; it still uses the stable user-home data directory. A Java 25 installation is required; an installer bundling a runtime is a later packaging choice, not required for the first release.

Default port is **8080**, configurable via `TIWANAKU_PORT` or documented `--server.port=<port>` (command-line precedence). Validate range and fail with actionable port-conflict advice; do not kill another program, silently increment the port, spawn a second process, or choose another database. Bind loopback, never `0.0.0.0` or a wildcard IPv6 address. Test `localhost` resolution on supported OSes and print the actual reachable URL (use a verified loopback literal if required by binding).

After successful migration, socket bind, and readiness, print the browser URL, Java/app version, and resolved data directory without tokens. An optional `--open-browser` attempts to open that URL only, not another JVM; failure leaves the printed instructions usable. Ctrl+C gracefully stops the process; closing a browser tab does not stop Java. Closing Java makes the UI unavailable until restart, with existing committed data preserved.

The Maven **release profile** integrates the frontend: provision/use pinned Node for build only, run `npm ci` from `package-lock.json`, typecheck/test as prescribed, build Vite, copy generated output into server generated classpath static resources, and repackage. Define ordering so fresh frontend assets precede server packaging; missing/stale frontend output fails the release. Do not check generated bundles into authored source or manually edit `docs/app.js`. Root release command: `./mvnw -Prelease clean verify` (`.\mvnw.cmd -Prelease clean verify` on Windows). Base `verify` can run Java tests without requiring a complete frontend toolchain; only the release profile produces a distributable full UI JAR.

Serve all fonts, textures, icons, scripts, locales, and any verified recovery puzzle from the same local origin; no CDN calls or required service worker. Installation/build may download dependencies, but installed runtime must operate with internet disconnected and an empty browser cache. Vue Router can use history mode: server fallback serves index only for recognized client routes and HTML GET navigation. Never turn `/api/**`, missing `/assets/**`, arbitrary files, or non-GET errors into a 200 SPA page. Use hashed assets, revalidated/no-cache HTML and no-store bootstrap/API responses. Check client/server version compatibility; after server update an old tab must pause/reload at a safe acknowledged boundary, not continue issuing incompatible commands.

Development: Vite runs on an explicitly configured loopback port and proxies `/api` to Java. Java remains mandatory for development play. Configure the allowed dev Origin/Host and proxy header behavior explicitly, scoped to a dev profile; do not enable wildcard CORS or ship dev origins in production. Production has no Vite or Node process.

### Native Targets

Keep old desktop/Android/web targets during migration; the new local JAR does not automatically replace an APK or a browser-only hosted site. Phone localhost points to the phone, not the desktop server. Do not promise modern standalone mobile, LAN hosting, or a mobile wrapper as part of this plan. Responsive small-window/touch tests do not change this constraint. Any native retirement or separate mobile product requires explicit approval. Do not introduce Electron/Tauri/Capacitor in the scaffold; verify Android store requirements only if later maintaining that distribution.

### Security And Privacy

Loopback is not enough on its own: reject unexpected Host authorities (including DNS-rebinding names) and Origins before serving sensitive bootstrap/API content. Allow only configured localhost/loopback hosts with the actual port; handle IPv6 syntax explicitly. Ignore untrusted forwarded-host headers. Use a per-start unpredictable CSRF/capability token obtained through a safe same-origin bootstrap response, never a URL/query/log/static-bundle secret. Require it as a custom header for state-changing requests with JSON content type; protect imports, deletes, cancellation, and settings as well as moves. Validate Origin/Fetch Metadata for bootstrap and mutations; reject foreign/null origins and define the narrow treatment of missing Origin for legitimate same-origin GETs/nonbrowser clients. Safe GETs must not mutate state.

Do not enable wildcard CORS, accept arbitrary filesystem endpoints, expose H2 console/actuator internals, trust arbitrary redirect URLs, or allow dev origins in release mode. Test hostile HTML form/fetch requests and DNS-rebinding Host values. Tokens/allowlists mitigate cross-site access; they do not protect a user from malicious code already running under that local OS account.

Treat imports/stored names as untrusted. Bound schemas, body sizes, arrays, SQL query/page sizes, generation and import resources; render names as text, never `v-html`. Use parameterized SQL, no arbitrary database connection strings from the browser. Restrictive CSP with same-origin scripts/assets/connect targets, `frame-ancestors 'none'`, and no object embedding; no required eval. Avoid logging keystrokes/player tags/whole puzzles or secrets, and rotate bounded local logs. No unnecessary device permissions, analytics, remote crash reporting, or hidden remote fallback. Document OS/user backup separately from application uploads.

Retain copyright notices, establish asset/font provenance, and bundle applicable licenses. A code-header BSD-style license does not automatically establish rights to every bitmap/font or embedded solver. Verify the origin/license of `famiru` code before redistribution. Do not replace uncertain assets with other unlicensed images.

### CI

New Java engine/server/storage tests, frontend typecheck/lint/unit/build, and Playwright smoke tests run from clean dependencies. Use Windows and Linux at least for launch/build/data-path tests; define macOS support from actual launch checks. Browser checks cover Chromium, Firefox, and WebKit against the running Java server, including small-window/touch emulation; no standalone mobile installation gate. Release smoke test uses the packaged JAR with Node absent, empty browser cache, isolated data directory, and internet disconnected while loopback works. Produce release artifacts in CI; keep the old published site intact until deliberate cutover and rollback artifacts exist.

## 9. Implementation Packets

Execute one packet at a time. A scaffold is not a completed rewrite. Each packet must list changed files, commands run, outcomes, and known gaps. Preserve unrelated changes. Do not commit/push or deploy unless separately requested.

### P00: Baseline And Characterization

**Depends on:** none. **Scope:** test fixtures/harness and baseline records only; no visual rewrite.

Read the audit and capture representative source-build screenshots and interaction flows. Add a new isolated test harness able to exercise legacy engine code without starting OpenGL; do not reorganize the legacy app just for tests. Characterize presets, note handling, help, score serialization, generation, and the known solver defects. For test fixtures, distinguish current behavior tests from intended correctness tests so bugs are not frozen as requirements. Establish tiny-board independent enumeration.

Minimal harness default: add JUnit configuration to `core/build.gradle` and characterization sources under `core/src/test/java`; because legacy main sources use `src/`, explicitly exclude `test/**` from the main source set. Run this harness with JDK 17. Stub only the platform preference boundary if needed; do not invoke asset loading or renderers. This is a narrowly allowed legacy build edit. P01 introduces the separate Maven harness for extracted code. Keep prototype schemas provisional during P01/P02; P03 freezes them without replacing already passing fixtures.

**Acceptance:** real tests execute (not `NO-SOURCE`); valid/invalid/full/ambiguous boards covered; failure fixtures demonstrate defects before corrections; baseline provenance records source version and toolchain. Existing application still builds under JDK 17.

### P01: Pure Java Extraction

**Depends on:** P00. **Scope:** `pom.xml`, wrapper, `puzzle-engine`, contract fixtures; legacy adapter only if needed for comparison.

Extract production engine and sparse exact-cover code. Remove libGDX coordinate/UI references from the extracted copy, split puzzle/attempt concerns, introduce immutable boundary values, deterministic RNG and bounded execution. Correct full-board validation and exact-cover edge cases with focused tests. Do not delete the old engine or refactor every class cosmetically.

**Acceptance:** Maven verify compiling/running at Java 25; dependency tree has no gdx/Spring/graphics; stop-after-two has explicit unknown status; recursion/loop cancellation and repeated-seed fixtures pass; retained-solution memory is bounded. Legacy/extracted differences are explained, not ignored. No Java 17 portable subset or browser compilation gate.

### P02: Mandatory Local Server And Durable Slice

**Depends on:** P01. **Scope:** `server`, minimal `web` Vue harness, provisional HTTP schemas/fixtures, H2/Flyway storage and baseline launch security.

Create Java 25/Spring Boot 4 MVC server using the real engine, bounded generation jobs, cooperative cancellation, loopback binding, Host/Origin/token checks, and H2 file-mode repositories via JDBC/Flyway. Implement minimal bootstrap/generate/accept/read/move/pause/resume/completion operations with revisions, idempotency receipts and editor ownership. Wire a simple same-origin Vue board to prove generation, saved moves, reload, completion, and server restart. Establish stable data-directory/lock behavior now, not only during packaging. Include an initial bundled UI for the gate; P08 makes the full release pipeline reproducible.

Use isolated test/development data directories, not real legacy/user history. Provisional schemas may evolve until P03 freezes v1, with an explicit disposal/migration policy for test data only. Carry forward passing engine/API/storage tests instead of throwing away the slice. Do not delete real data or create a second frontend database.

**Acceptance:** section 1 architecture gate passes, with the user's 2026-10-27 waiver of the unrun internet-disconnected cold-launch test; backend-down edits are blocked; committed moves/results survive Java restart; lost response after commit deduplicates; stale revisions/second-tab edits conflict. H2 file locking and data isolation tested. Cancel/deadline checks work inside hard recursion, overflow is bounded, and timeout never certifies a puzzle. Measure all presets including maximum region size 9, command latency, JVM heap, CPU and cancel latency. Failure stops expansion; no alternate browser engine fallback.

### P03: Application Scaffold And Contract Freeze

**Depends on:** P02 passes. **Scope:** Vue app shell, routes, stores, schemas, test/build scripts; no full terrain polish.

Use Vue/TS/Vite, Pinia, Vue Router, i18n, Vitest, Playwright, and lint/format checks. Establish directories only as needed. Freeze v1 puzzle/attempt/result/command/job contracts and initial H2 schema; add strict runtime validation and serialization fixtures. Create setup/play/history/settings routes with real empty/loading/error/disconnected/pending states, small CSS tokens, and the actual HTTP adapter. Pinia stores server snapshots and ephemeral view state, not authoritative move reducers or persisted attempts.

**Acceptance:** clean `npm.cmd ci`; typecheck/lint/unit/build pass; real Java-generated puzzle reaches Play; no fake solver or placeholder success paths. Test keyboard shell, same-origin routing fallback excluding API/assets, dev proxy origin policy, one-in-flight mutations, bounded/no input queue, stale responses, and version mismatch/reconnect.

Establish concrete future validation entry points: root `.\mvnw.cmd verify`; `web/` commands `npm.cmd run typecheck`, `npm.cmd run lint`, `npm.cmd run test:unit -- --run`, `npm.cmd run build`, and `npm.cmd run test:e2e` against a test Java server with isolated H2 data. P08 finalizes `.\mvnw.cmd -Prelease clean verify`. These commands do not yet exist in the legacy repository. Add a root verification script only to remove CI duplication. Use standard Maven source/test layout and fail if tests are not discovered. Test mocks cannot replace the real server E2E gate.

### P04: Playable State And Input

**Depends on:** P03. **Scope:** Java game commands and Vue view store, board controls, keypad, timer, notes/help, completion.

Implement the attempt state machine, immutable givens, direct entries/clear, eight-neighbor/region checks, numeric notes, help distinction, undo/redo, checkpoint, pause/resume, New Puzzle versus Restart, assistance/replay tracking, and durable completion hookup. Keep layout simple but responsive.

**Acceptance:** solve by keyboard and touch with Java validating each logical move; timer lease/proposal/pause/reload/reentry rules pass with fake clocks; no counting closed time; documented crash loss measured. Failed generation retains previous attempt; menu settings never relabel old boards; completion and response-loss retry are idempotent. Undo does not reduce elapsed/assistance. Backend unavailable stops edits, pending/saved feedback is accurate, reconnect uses server state.

### P05: Durable Data And Legacy Migration

**Depends on:** P04; mandatory storage exists from P02. **Scope:** H2 repositories/migrations/recovery, history/player/settings UI, legacy import/export.

Finish history/filter/sort/edit, settings persistence, confirmed deletion, player selection, legacy parser/importer, schema upgrade/backup/restore and logical export. Exercise transactional completion, receipts, lease takeover and storage-error UI. Add known desktop-preferences import with explicit confirmation and same-origin old-key fixture; require old-origin export for remote localStorage. Document Android export dependency without pretending automatic access.

**Acceptance:** refresh/Java restart/lost response at every completion step creates exactly one result; disk-full/read-only/migration/future-schema errors never silently reset data; backup restore and logical export/import round trips pass; import twice is idempotent; corrupt legacy records do not discard good records; raw sources untouched. Browser storage clearing and port changes preserve backend history. Filtered history preserves count, sum of known elapsed times, and arithmetic mean of stored legacy percentages only among compatible legacy records. Missing values remain unknown; legacy/new accuracy metrics are never averaged together.

### P06: Terrain And Responsive Visual Rework

**Depends on:** P04; can overlap P05 only with separate file ownership. **Scope:** board presentation, CSS tokens, approved assets, responsive shell.

Implement atlas direction, board-space region textures, clear boundaries, selection/conflicts/notes, desktop inspector and narrow-window keypad. Test all supported board sizes, low-detail and high-contrast modes. Add deterministic fixture screenshots, not random visual baselines. Keep server-derived rules and Pending/Saved states legible through the visual rework.

**Acceptance:** one coherent texture per region; no stretched per-cell atlas sprites; aligned SVG/HTML at zoom/DPR changes; readable numeric notes; 390 px portrait UI usable on TITAN through explicit viewport controls; keyboard/screen-reader path retained; no UI-thread generation or continuous rendering loop.

### P07: Robustness, Security, And Accessibility

**Depends on:** P05 and P06. **Scope:** local lifecycle/error handling, browser coverage, security/a11y/license checks, CI regression tests.

**User-approved lightweight amendment (2026-10-27):** This is a localhost-only game, not a hosted service. Retain the existing loopback/Host/Origin/process-token and safe-import boundaries. Focus on concrete disconnect/reconnect, old-tab/server restart, explicit pause/lease handling, cancellation and saved-state regressions; add inexpensive response headers and focused automated accessibility checks on the existing UI. Reuse existing backup/failure/keyboard/contrast tests rather than redesigning them. Record direct dependency/artwork provenance and measured local browser smoke timings, not an exhaustive audit/performance program. Keep checks runnable in the existing local test suite; new hosted CI infrastructure and a broad penetration test are not requirements for this packet.

**Amended acceptance:** server-down play blocks edits; an old tab reconnects safely after a real restart; cancellation and stale outcomes retain the existing game; no external runtime requests during the tested local journey; existing hostile Host/Origin/missing-token and import boundaries remain verified; no serious/critical automated accessibility findings on tested routes/states, with focused keyboard/contrast regression coverage. Actual screen-reader listening, disconnected-internet cold launch, physical-device/cross-browser matrix, exhaustive dependency/license investigation and broad manual accessibility certification are explicitly deferred, not passed. User confirmed the screen-reader and disconnected-internet deferrals. Packaging remains P08.

### P08: Runnable Packaging

**Depends on:** P07. **Scope:** Maven release profile, generated static-resource integration, executable JAR, launch/release smoke tests and verified instructions.

Integrate locked frontend build into Maven's release lifecycle and package the full Vue app with the mandatory server as `server/target/tiwanaku.jar`. Verify startup URL/data-dir printing, Java 25 prerequisite, configurable port and conflict diagnostics, exclusive data-directory lock, optional browser launch, graceful shutdown and schema-compatible rollback. Node/npm are build/dev only. Run the actual JAR from a different working directory against the same data directory to prove data is not repository-relative.

**Acceptance:** clean `.\mvnw.cmd -Prelease clean verify` emits the stable artifact; `java -jar server/target/tiwanaku.jar` serves the UI/API with no Node, internet, or prewarmed browser cache. Fresh user-home launch, custom `TIWANAKU_DATA_DIR`, port collision, second-instance refusal, existing-data upgrade, failed-migration recovery, route fallback and old-tab compatibility tests pass on supported OSes. No background second server, checked-in generated UI, or missing assets. Sections 10/11 release gates pass or have explicit product-approved deferrals.

### P09: Solver Education And Difficulty Calibration

**Depends on:** P08. **Scope:** explainable Java solver events, advanced explorer, rated fixture corpus.

Rebuild the reachable Algorithm screen as an advanced view using the tested Java engine/event model, not the legacy dense matrix implementation. Begin with a small bounded example and step/run/reset controls. Return bounded event batches/snapshots over HTTP; animate locally without a request per rendered frame. Apply the same job cancellation/deadline/retention limits and cap retained events. Add single-candidate explanations before more techniques. Separate human difficulty from exact uniqueness proof and version ratings.

**Acceptance:** explorer can be paused/cancelled and does not block play; explanations match a valid deduction; medium/hard claims are supported by documented rating behavior or remain labeled presets. Preserve access to the old explorer until replacement or approved retirement.

### P10: Cutover And Legacy Retirement

**Depends on:** P08; P09 or explicit approval for Explorer deferral; legacy distribution decisions documented.

Publish a separate downloadable JAR preview, validate migration on actual old storage, retain compatible binary/data rollback artifacts, then update the public entry point with explicit local-Java launch instructions. Do not replace the hosted browser game with a broken static Vue bundle expecting an absent server. Update README/build instructions only to verified behavior. Retire libGDX launchers, unused widgets, stale archive/GWT metadata, and generated runtime only after explicit feature/platform/migration approval. Preserve legal notices and fixtures; retain old native targets unless their retirement is approved.

**Acceptance:** no accidental lost score history, no mandatory online dependency, no unsupported native target silently removed, reproducible CI release, and clean ownership of generated versus authored assets. Use normal version control history instead of maintaining source ZIP snapshots.

## 10. Verification Matrix

| Layer | Required cases |
| --- | --- |
| Rules | Diagonal equal values; region duplicate not adjacent; orthogonal connectivity; disconnected same biome; ranges; fixed clues; full invalid board |
| Solver | 0/1/2+ solutions; invalid fully filled input; early-stop restoration; budgets return unknown; deterministic vectors; concurrent independent JVM calls |
| Generator | Every preset x difficulty x max-region option; 1..N regional permutation; all 8-neighbor constraints; bounds/connectivity; clues match solution; independently checked uniqueness on tractable boards |
| Rendering | Every preset, region concavity/corners, 9-value notes, equal-looking separated regions, selection/error/given overlays, zoom and DPR |
| Interaction | Keyboard-only solve; touch select/keypad; input fields suppress shortcuts; undo/redo/checkpoint; disabled/fixed cells; modal focus return |
| State | Back/reenter; settings cannot relabel puzzle; restart versus new; transactional completion once; cancellation retains old puzzle; stale IDs/revisions; pending vs saved |
| Commands | One in flight; bounded/no queue; duplicate ID/payload replay; changed payload rejected; lost response after commit; stale revision 409; multi-tab takeover; reload/restart reconciliation |
| Timer | Starts once; no note/help-only start; pause/hidden/closed excluded; bounded monotonic proposals; lease/heartbeat expiry; fake clocks; scheduling/request delay and documented crash-loss window |
| Storage | Real H2 file DB; empty/corrupt/legacy/future schema; disk full/permissions; interrupted transaction; one-process guard; migration failure; consistent backup/restore; export/import twice; browser clearing harmless |
| Internet Disconnected | Cold launch with empty browser cache and Java running; fresh local generation/history; no external runtime assets; Java stopped disables edits; reconnect loads saved state |
| Mandatory API | Validation, bounded CPU/command queues, recursive cancel/deadline, retained-solution memory, job expiry/restart, no shared solver mutation, Host/Origin/CSRF/dev proxy protections |
| Packaging | Stable JAR filename; Maven integrated frontend; Node absent at runtime; data dir independent of cwd/JAR; port conflict; safe route fallback; old tab/new API mismatch; binary+schema rollback |

Use an initial fast deterministic corpus (at least one case per configuration plus adversarial fixtures), and a larger scheduled corpus. Treat generation benchmarks separately from brittle wall-clock unit assertions. Cover all 7 x 3 x 5 = 105 supported option combinations in scheduled verification; multiple seeds are needed for meaningful confidence.

### Provisional Budgets

These are engineering targets to validate in P02, not measured current/new performance claims.

- Input/selection feedback: ideally one frame; acknowledged logical move plus rendering under 100 ms p95 on a stated local-machine baseline, measured under generation load too.
- Generation contributes no browser main-thread long task over 50 ms; bounded JVM CPU work must leave HTTP/DB responsive.
- Cancel acknowledgement visible within 250 ms; cooperative CPU termination target within 500 ms, measured inside worst recursive phases. Acknowledgement is not proof work stopped.
- Starter/recovery puzzle shown within 1 second once app assets are ready.
- Trial generation deadlines: 10 seconds for ordinary presets and 30 seconds for UBER/TITAN, with deterministic node/retry budgets and clear timeout UI. Tune only from measurements; never weaken uniqueness to meet a timer.
- Initial app-shell JavaScript target: at most 250 KiB compressed, excluding lazy explorer/fonts/textures. Java engine is not a browser payload; report JAR size, startup latency, heap and database size separately after P02/P08 measurements.
- No continuous redraw or timer updates while hidden. UI timer updates once per second, not every animation frame.
- Verify largest-board JVM memory, repeated cancellation and bounded job retention on a stated desktop baseline; record median/p95 generation, CPU/heap, API latency and restart behavior before release. Test small browser windows separately from standalone mobile claims.

## 11. Release Gates

- [ ] With internet disconnected and mandatory Java 25 running, cold-launch and complete a newly generated puzzle, not only a cached demonstration.
- [ ] Every generated puzzle is certified unique; interrupted proof is rejected.
- [ ] All board presets and max-region options supported or explicitly product-approved as deferred.
- [ ] Native distributions retained until a documented replacement/retirement decision.
- [ ] Keyboard, touch, screen reader, zoom, notes, and high-contrast modes verified.
- [ ] Attempt survives refresh, pause, menu navigation, Java restart and safe app update; backend-down edits rejected and reconnect reconciles unknown outcomes.
- [ ] One-in-flight/revision/idempotency protocol handles failed response after commit, multi-tab takeover and exactly-once completion effect.
- [ ] Timer excludes closed intervals, bounds proposals, expires leases and documents measured crash-loss window.
- [ ] H2 data survives browser clearing/port/cwd changes; exclusive directory guard, disk-full handling, Flyway upgrade, consistent backup/restore and schema-aware rollback verified.
- [ ] Results persisted before player selection; migration/import/export tested on genuine legacy examples.
- [ ] Legacy and new scoring semantics visibly separated; no false online/anti-cheat claim.
- [ ] Assets/fonts/borrowed code have redistribution notices and verified provenance.
- [ ] No new analytics, mandatory accounts, or hidden remote fallback.
- [ ] CI produces `server/target/tiwanaku.jar` via integrated locked frontend release build; Java 25 launch works without Node or internet and with empty browser cache.
- [ ] Loopback binding, port conflicts, Host/Origin/CSRF protection, safe bootstrap, restrictive CSP, dev-only proxy policy and API/asset routing exclusions tested.
- [ ] All runtime assets local; no service worker, hosted fallback, or independent phone-localhost claim required for release.
- [ ] Documentation describes implemented behavior, not disconnected or aspirational features.

## 12. Clean-Context Packet Handoff

Execute one bounded packet per clean context, using this template and updating the progress block below in this existing plan. A fresh session must inspect evidence rather than assume the previous model's summary proves acceptance. Model ranking/speculation is not part of the implementation contract.

Use a new chat in the same workspace, not necessarily a new Git worktree. These planning files may still be untracked and would not automatically appear in a new worktree. Do not commit or copy them elsewhere without the appropriate user request. Keep one chat through a packet's implementation and verification; do not reset context after every response. Start the next clean chat from the repository, revised documents, and recorded evidence, not the superseded discussion.

```text
Read .kilo/plans/modernization-plan.md and .kilo/plans/codebase-audit.md.
Revision 2 is authoritative: mandatory local Java 25 + Spring Boot 4 + H2/JDBC/Flyway,
Vue 3 TS/Vite served same-origin in one executable JAR. Node is build/dev only.
Implement packet <Pxx> only. Allowed files/directories: <explicit packet ownership>.
First inspect git status, current code, progress below and dependency evidence.
If prerequisites are unverified, stop implementation and report concrete gaps.
Preserve unrelated changes and the legacy application; use apply_patch for edits.
Do not implement later packets, replace the solver with a stub, or weaken uniqueness.
Do not create browser-engine/PWA/second browser database or silent remote fallbacks.
Do not start Agent Manager sessions/worktrees, commit, push or deploy without authorization.
Use stated contracts/tests/acceptance criteria; flag any necessary deviation before expanding scope.
Run applicable checks with isolated test data, never the real user-home database.
Report changed files, exact commands/outcomes, acceptance evidence and remaining gaps.
Update only this plan's progress block for handoff; do not create new tracking documents.
If blocked, record evidence and the next bounded action; never label an unrun check passed.
```

### Progress State

| Field | Current value |
| --- | --- |
| Plan revision | 2, final localhost clarification, 2026-10-26 |
| Implementation completed | P00-P07 (P02 offline check waived; P07 lightweight acceptance explicitly amended by user, deferred checks not passed) |
| Active packet | None; lightweight P07 verified and recorded |
| Next packet | P08 — runnable packaging |
| Packet status | P00-P07 complete under amended acceptance; P08-P10 pending |
| Allowed files for next execution | P08 Maven/frontend release integration, runnable-JAR/launch smoke checks and release instructions; inspect existing build and launch behavior before editing. P09 explorer and legacy retirement remain outside scope. |
| P00 changed files | `core/build.gradle`; `core/src/test/java/com/apogames/settler/characterization/PuzzleRulesOracle.java`; `LegacyEngineCharacterizationTest.java`; `ScoreboardCharacterizationTest.java`; this plan's Progress State |
| P00 evidence | Baseline revision `04f497337b0ea72997267414c1b2230098871cef` (2026-10-21); JDK 17.0.20.1, Gradle 8.13, libGDX 1.14.0. `.\gradlew.bat :core:test --no-daemon --offline --rerun-tasks` (JDK 17): BUILD SUCCESSFUL, 10 tests / 0 failures / 0 errors / 0 skipped. `.\gradlew.bat :teavm:buildRelease --no-daemon` (JDK 17): BUILD SUCCESSFUL, 1,162 classes. `.\gradlew.bat :desktop:compileJava :teavm:compileJava --no-daemon --offline` (JDK 17): BUILD SUCCESSFUL. Fixtures cover presets, notes/restart, diagonal conflicts, tiny independent enumeration, valid/invalid/full/ambiguous boards, two known legacy solver defects, score formats and malformed scores. User certified the current game launches correctly; browser-flow interaction was not independently captured, as documented above. |
| P01 changed files | `pom.xml`; `.mvn/wrapper/maven-wrapper.properties`; `mvnw`; `mvnw.cmd`; `puzzle-engine/pom.xml`; `puzzle-engine/src/main/java/com/apogames/tiwanaku/engine/{PuzzleDefinition,BoardRules,AttemptState,CancellationToken,SolverOptions,UniquenessStatus,SearchTermination,SearchResult,ExactCoverSolver}.java`; `puzzle-engine/src/test/java/com/apogames/tiwanaku/engine/EngineSolverTest.java` |
| P01 evidence | `.\mvnw.cmd -B clean verify` on Java 25.0.4.1 / Maven 3.10.0: BUILD SUCCESS, 9 tests / 0 failures / 0 errors / 0 skipped; clean compile at `--release 25`. `.\mvnw.cmd -B -pl puzzle-engine org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree`: only test-scoped JUnit 5.13.4; no gdx, Spring, or graphics dependency. Wrapper reports Maven 3.10.0. Independent exhaustive tiny-board counts are 1, 2, 0, and 24; exact search returns the correct unique/multiple/none statuses, never retains over two solutions, reports unknown on cancellation/budget, and repeats seeded search ordering. |
| P01 deviations and differences | The new engine uses immutable flat row-major puzzle/attempt values and an independent sparse exact-cover implementation instead of importing libGDX-backed `Level`, `Solve`, or `famiru.Dlx`; these old classes remain unchanged. Invalid full boards are rejected, and interrupted uniqueness proofs return `UNKNOWN`, unlike the legacy early completion path. The legacy `NewLevelCreate` / `FillAndUniqueCheck` pipeline remains unported; P02 adds an independent seeded Java generator without a browser or legacy fallback. |
| P02 changed files | `.gitignore`; root `pom.xml`; `puzzle-engine/src/main/java/com/apogames/tiwanaku/engine/{Difficulty,GenerationSpec,GenerationStatus,GeneratedPuzzle,PuzzleGenerator}.java`; `puzzle-engine/src/test/java/com/apogames/tiwanaku/engine/PuzzleGeneratorTest.java`; `server/pom.xml`; server Java sources `ApiException`, `AttemptCommand`, `AttemptController`, `AttemptService`, `AttemptSnapshot`, `BootstrapController`, `CreateAttemptRequest`, `DataDirectoryLock`, `GenerationController`, `GenerationJobService`, `GenerationRequest`, `LocalDataConfiguration`, `LocalRequestSecurityFilter`, `LocalRequestToken`, `LocalServerReadyListener`, `LoopbackServerConfiguration`, `TiwanakuServerApplication`; `server/src/main/resources/{application.properties,db/migration/V1__initial_local_store.sql}`; server tests `DataDirectoryLockTest`, `LocalRequestSecurityFilterTest`, `LocalServerBootstrapIntegrationTest`, and `server/src/test/resources/application-test.properties`; `web/{index.html,package.json,package-lock.json,tsconfig.json,tsconfig.app.json,vite.config.ts}` and `web/src/{App.vue,main.ts,style.css}`; this plan's Progress State. Generated `web/dist`, `web/node_modules`, and `server/target` outputs are ignored. |
| P02 evidence | Java 25.0.4.1 / Maven 3.10.0; Node 22.22.3 / npm 10.9.8. `Set-Location web; npm.cmd run typecheck` and `Set-Location web; npm.cmd run build`: pass; Vite emits local hashed JS/CSS (71.82 kB JS, 5.23 kB CSS). `.\mvnw.cmd -o -B test`: BUILD SUCCESS, 23 tests (14 engine, 9 server), 0 failures/errors/skips; this includes file-lock/security checks and HTTP generation, attempt completion, editor takeover/read-only snapshot, revision/idempotency and cancellation. Dependency tree pins H2 2.3.232 runtime with Spring Boot 4.1.2/Flyway; the first attempt to resolve it through the configured internal Nexus failed with PKIX certificate validation. The public Maven Central JAR SHA-1 was verified (`4fcc05d966ccdb2812ae8b9a718f69226c0cf4e2`) and cached locally; subsequent Maven checks were offline. `.\mvnw.cmd -o -B -DskipTests package` succeeds; the Spring Boot artifact is copied to `server/target/tiwanaku.jar`. Isolated final-JAR smoke: root HTML/JS and bootstrap served 200, API is `no-store`, real Java generation and completion succeed; after stopping/restarting with the same temporary `TIWANAKU_DATA_DIR`, the attempt restores at revision 1 and replaying its saved command remains revision 1. Direct H2 2.3.232 query after shutdown returned one attempt and one result. A second process using the locked directory failed startup. A raw TCP request whose response was deliberately not read still committed; subsequent snapshot and command retry returned the one completed revision. |
| P02 performance evidence | Final H2 2.3.232 JAR on Windows: Intel Core i7-1185G7, 32 GiB RAM, Java 25.0.4.1. With `maxRegionSize=9`, all seven presets generated (`server ms / wall ms / visited nodes`): 3x3 `5/214/128`; 4x4 `8/66/345`; 5x5 `13/79/757`; 7x5 `26/80/1,398`; 9x5 `13/72/2,228`; 15x10 `282/299/23,500`; 20x12 `846/875/59,342`. One saved move took 51 ms; ten idempotent retries averaged 11.21 ms. Cancellation of a 20x12 HARD search reached CANCELLED in 44 ms; the integration test also checks it never completes as a certified puzzle. A rapid burst of up to eight 20x12 HARD submissions accepted three and returned HTTP 429 on the fourth; accepted jobs were cancelled and reached terminal states. Benchmark process CPU-time delta was 2.70 s; working set 205.2 MiB before / 209.0 MiB after; `jcmd GC.heap_info` reported 24,758 KiB used and 294,912 KiB committed after generation. These are local baseline observations, not cross-platform budgets. |
| P02 remaining acceptance gaps | The real-browser journeys and Vite proxy have been exercised (see evidence below). A genuinely internet-disconnected cold launch/generation remains unverified and was explicitly waived by the user; it is not claimed passed. Broader schema-upgrade/recovery and disk-full/read-only behavior remain later-packet work; the explicit prototype migration has separate backup/restore tests. `git diff --check -- . ':!puzzle-engine/target/**' ':!server/target/**'` passes; generated tracked test reports remain outside that source check and were not reverted. |
| P02 launch recovery, 2026-10-27 | User reported H2 authentication failure and requested preserving the original data. Read-only inspection of a closed-database copy identified the earlier prototype's empty username, five tables with `attempts.note_masks`, no Flyway history, and 3 puzzles / 3 attempts / 0 results. Added `server/src/main/java/com/apogames/tiwanaku/server/LegacyStoreMigration.java`, `LegacyStoreMigrationTest.java`, explicit `--migrate-legacy-store` dispatch in `TiwanakuServerApplication`, actionable credential errors in `LocalDataConfiguration`, and recovery/build instructions in `README.md`. Migration refuses missing/unrecognized databases, locks the data directory, creates an H2-consistent ZIP backup before schema/login changes, retains historical notes/rows, sets a default for legacy-only notes, baselines Flyway v1, and normalizes the empty login to `sa` with its corresponding empty-password hash. Initial migration test caught the need to reset the password hash after renaming the user; corrected and retested. `.\mvnw.cmd -o -B verify`: BUILD SUCCESS, 26 tests (14 engine / 12 server), no failures/errors/skips; rebuilt `server/target/tiwanaku.jar`. Migration tests verify data/note preservation, physical backup restoration with the old login, new-attempt insertion, and refusal of missing/unknown databases. `java -jar server\target\tiwanaku.jar --migrate-legacy-store` succeeded on the isolated copy of the user's database; normal launch at `--server.port=18089` served UI 200 / bootstrap v1, and SQL counts remained 3 / 3 / 0. Original database SHA-256 remained unchanged. No migration has been run on the original user database; user must run the explicit one-time command before normal launch. General schema-upgrade/rollback support remains deferred; this narrowly scoped prototype compatibility repair was explicitly user-approved. |
| P02 browser launch diagnosis, 2026-10-27 | User confirmed Java was stopped when localhost:8080 returned ERR_CONNECTION_REFUSED. Started `java -jar server\target\tiwanaku.jar --server.port=18090` with `TIWANAKU_DATA_DIR` set to an isolated temporary directory. Actual integrated browser at `http://localhost:18090/` rendered the Vue setup form, board placeholder and JAVA SERVER CONNECTED state. The earlier black canvas requesting `app.js` matches the legacy HTML, not this bundled page; the user's earlier failing response/cache was not captured, so its precise cause remains unconfirmed. Browser generation with board `3x3`, then `7x5`, at default max region 5/Easy displayed `Generation ended: EXHAUSTED`; waits for `.cell` timed out after 10/15 seconds. No playable-board/browser-entry acceptance is claimed. Changed `README.md` to distinguish stopped-server errors, the printed localhost URL and legacy-page/cache troubleshooting; no application code changed for this diagnosis. |
| P02 user-server confirmation, 2026-10-27 | With the user's own Java process running on 8080, `Invoke-WebRequest -Uri 'http://localhost:8080/' -UseBasicParsing` returned 200 and Vue HTML referencing `/assets/index-3P_lHv5c.js`, not `app.js`. Navigated the shared integrated-browser tab with `page.goto('http://localhost:8080/')`: HTTP 200, no failed requests observed, Vue setup rendered with JAVA SERVER CONNECTED, and the same hashed script. No service-worker controller was reported in this browser. The external browser's stale HTML/cache remains the likely explanation, not a proven cause; no external-browser cache was inspected or cleared. No mutations or database changes were performed for this check. |
| P02 generation/diagnostics repair, 2026-10-27 | User confirmed the black screen was a browser cache issue and requested continued sequential work plus file diagnostics. Changes: `PuzzleGenerator.java` and `PuzzleGeneratorTest.java`; `ApiExceptionHandler.java` (new); `AttemptController.java`; `GenerationJobService.java`; `LocalServerReadyListener.java`; `application.properties`; `logback-spring.xml` (new); `LocalServerBootstrapIntegrationTest.java`; `web/src/App.vue`; `README.md`; this Progress State. The new default-size fixture first failed `3x3 seed 0: EXHAUSTED` under `.\mvnw.cmd -o -B -pl puzzle-engine '-Dtest=PuzzleGeneratorTest#generatesDefaultRegionSizeAcrossEveryPresetAndMultipleSeeds' test`. Odd block perimeters created touching small regions incompatible with the eight-neighbor rule. Added seeded connected serpentine partitions of size 4..maximum for odd boards with maximum below 8. An initial repair then hit UNKNOWN on 15x10 seed 2026; capped each initial partition solve at 20,000 nodes so a hard candidate does not consume the entire global budget, while retaining the global budget/deadline/cancellation and final uniqueness proof. `.\mvnw.cmd -o -B -pl puzzle-engine '-Dtest=PuzzleGeneratorTest' test` passed after that repair. Expanded coverage to all seven presets x five maxima x three seeds = 105 certified EASY puzzles plus global-node-exhaustion refusal. |
| P02 diagnostics verification, 2026-10-27 | `Push-Location web; npm.cmd run build; Pop-Location` passed Vue typecheck/Vite bundle. Final `.\mvnw.cmd -o -B verify` passed 28 tests (16 engine, 12 server), no failures/errors/skips, and packaged the executable JAR. HTTP tests assert explicit ProblemDetail reasons for stale revision and editor ownership 409s. API rejections now log status/path/reason; generation logs include seed/settings/outcome; accepted command logs include IDs, cell/value and revisions. Dated rolling `logs/game_2026-10-27.0.log` was inspected with `Get-Content -LiteralPath <isolated-data>\logs\game_2026-10-27.0.log -Tail 12` and `Select-String`: it contained generation, accepted moves, the rule-conflict reason, completion and two process startup entries after restart. Request tokens/full snapshots are not logged. Retention configured at 10 MB per segment, daily rotation, 14 days / 100 MB retained archives; rollover/retention thresholds were not stress-tested. The user's earlier generic 409 cannot be diagnosed retroactively. |
| P02 browser verification, 2026-10-27 | Ran `java -jar server\target\p02-build\tiwanaku.jar --server.port=18091` with an isolated `TIWANAKU_DATA_DIR`, then real Playwright actions against the Vue page: default 7x5/max5 generation produced 35 cells; deliberately invalid entry returned 409 and displayed `The move violates a puzzle rule`; valid keypad entry saved revision 1 and survived reload; a second browser tab loaded the same attempt, took control (revision 2), cleared and entered by keyboard (revision 4), and the original tab displayed takeover with entry disabled. A clear-button auto-scroll attempt timed out; explicit `scrollIntoViewIfNeeded()` followed by normal click succeeded, not a forced click. Stopped the test Java process: browser displayed SERVER DISCONNECTED and disabled edits. Restarted with the same isolated directory, reloaded: value 1/revision 4/move count 3 restored. Generated a 3x3 board, filled four open cells via actual keyboard events using an independent tiny test-only enumeration, reached COMPLETED at revision 4, and reload retained completion with all cells disabled. Reconnect currently requires reload; automatic reconnect remains later scaffold work. No real user database was used. |
| P02 installed artifact / dev proxy, 2026-10-27 | User confirmed stopping their Java server before replacement. `Copy-Item -LiteralPath 'server\target\p02-build\tiwanaku.jar' -Destination 'server\target\tiwanaku.jar'` succeeded; `Get-FileHash -Algorithm SHA256` confirmed identical artifacts. Started installed `java -jar server\target\tiwanaku.jar` on default 8080 with the same isolated directory; startup printed log directory and existing Flyway v1 schema. `Set-Location web; npm.cmd run dev -- --port 5173 --strictPort` started the actual configured Vite proxy. Browser `http://localhost:5173/` connected, generated 3x3/max5, accepted a keyboard move with HTTP 200/revision 1, and its dated backend log recorded cell/value. Test processes were stopped; isolated data cleaned up. Terrain artwork (P06) and legacy left/right-click cycling (P04) remain deliberately unimplemented, not claimed complete. |
| P02 acceptance decision, 2026-10-27 | User explicitly declined the internet-disconnected cold-launch test and instructed proceeding to the next step. That test remains unrun, not passed; architecture/P02 acceptance above is amended to record the waiver. P02 accepted on the recorded engine/API/storage/browser/security/measurement evidence and this explicit waiver. Backend remains mandatory; no offline fallback or service worker is added. |
| P03 changed files | `web/package.json`, lockfile, `eslint.config.js`, `vitest.config.ts`, `playwright.config.ts`, `tsconfig.json`, `tsconfig.tools.json`; `web/src/{App.vue,main.ts,api.ts,api.test.ts,i18n.ts,router.ts,style.css}`; `web/src/stores/{game.ts,game.test.ts}`; `web/src/views/{SetupView,PlayView,HistoryView,SettingsView,NotFoundView}.vue`; `web/e2e/{server.mjs,scaffold.spec.ts}`; server Java `SpaController`, `ResultSnapshot`, `AttemptController`, `AttemptService`, `LocalRequestSecurityFilter`; server `application.properties` and `LocalServerBootstrapIntegrationTest`; `.gitignore`; `README.md`; this Progress State. |
| P03 implementation / contracts | Added real setup/play/history/settings routes, Pinia authoritative-snapshot/view state, EN/DE shell i18n and basic CSS tokens. History/full localization/durable preferences remain honestly labelled future work. Strict runtime Zod v1 schemas cover bootstrap, generation/job, creation, command, embedded puzzle/attempt and result; real Java responses round-trip against them in E2E. Exposed the existing durable result through `GET /api/v1/attempts/{id}/result`. Java rejects unknown JSON request fields. V1 SQL was not changed and is now frozen; future changes require forward Flyway migrations. Added explicit reconnect/fresh token reconciliation and version/process mismatch pause, one mutation in flight/no input queue, stale poll epoch/revision guards, 409 refresh without replay, per-selected-region keypad limits and scoped keyboard input. Saved-game pointers are retained and explicitly reported if absent in the chosen server directory. Only known SPA routes forward to index; API/assets/unknown paths retain 404. HTML is no-store and static Last-Modified validation disabled to avoid serving stale HTML as 304. |
| P03 frontend verification | Final clean `Set-Location web; npm.cmd ci` succeeded; audit reported 0 vulnerabilities. `npm.cmd run typecheck`, `npm.cmd run lint`, `npm.cmd run format:check`, `npm.cmd run test:unit -- --run`, `npm.cmd run build` all passed; 20 unit tests cover strict/invalid serialization, version/bad JSON, one in-flight mutation, stale responses, offline edits/reconnect/fresh token, conflict reconciliation, missing saved IDs and failed-generation retention. Typecheck includes UI, test and build/E2E TypeScript configs. Final Vite 7.3.7 output: JS 256.95 kB (86.85 kB gzip), CSS 5.90 kB. Pinned compatible patched Vue 3.5.44/Zod 4.6.7/Vitest 4.1.12 rather than leaving the initial npm advisories. npm 10's lock resolver initially crashed on Vitest 4 optional peers; `npx.cmd --yes npm@11 install --package-lock-only` resolved it, and subsequent ordinary npm 10 `ci` passed. ESLint environment errors were fixed before acceptance. Remaining non-failing warnings: ESLint 9 deprecation metadata, vue-i18n's explicitly mistaken deprecation metadata, and Zod dependency PURE-comment warnings during bundling. |
| P03 Java/browser verification | Final `.\mvnw.cmd -o -B verify`: BUILD SUCCESS, 30 tests (16 engine, 14 server), 0 failures/errors/skips. Includes unknown-request rejection, exact result fields and routes/API/assets exclusions; a conditional root GET with a future If-Modified-Since still returned 200/current HTML. Initial `npm.cmd run test:e2e` lacked Playwright Chromium (2 failed browser launches, one API-only test passed); reran using installed Edge with `$env:PLAYWRIGHT_CHANNEL='msedge'`. Final `npm.cmd run test:e2e`: 4 passed against the actual packaged Java server and unique temporary H2 directory. Covered real v1 generation/create/complete/result serialization; actual Vue generation/keyboard save/reload; simulated network failure blocks cells, explicit Reconnect restores saved state; keyboard route activation/focus and German shell; incompatible version blocks generation; SPA fallback exclusions. An initial Windows webServer teardown left two test directories; replaced it with a global setup/teardown owning the exact child process/directory, then repeated runs verified no new leftovers/listeners. Earlier two exact scratch directories were cleaned separately. No real user database was used. |
| P03 final artifact | After the last CSS-token-only adjustment: `npm.cmd run format:check`, `npm.cmd run build`, `.\mvnw.cmd -o -B -DskipTests package`, and `$env:PLAYWRIGHT_CHANNEL='msedge'; npm.cmd run test:e2e` passed again (4 E2E tests). The skipTests packaging command is not counted as a test run; full Java verify above was already green with identical Java sources. Copied to `server/target/tiwanaku.jar`; both JAR SHA256 values match `104101CD4E74E6A9F53E14C79E5C433644D400F2C68C47F756387BA39F3BA3EF`. All owned servers stopped and disposable data cleaned. No commits/push/deploy or legacy edits. |
| P04 changed files | Server Java `AttemptCommand`, `AttemptService`, `AttemptSnapshot`, `CreateAttemptRequest`, `ResultSnapshot`, new `GameProgress` and `GameplayClock`; new `db/migration/V2__playable_progress.sql` (V1 unchanged); `LocalServerBootstrapIntegrationTest`, new `PlayableAttemptTest` and `PlayablePersistenceTest`; `web/src/{App.vue,api.ts,api.test.ts,testFixtures.ts,style.css}`; `web/src/stores/{game.ts,game.test.ts}`; `web/src/views/PlayView.vue`; new `web/e2e/gameplay.spec.ts`; `README.md`; this Progress State. Running engine checks also updated already-tracked generated `puzzle-engine/target/surefire-reports` timestamps; no legacy application sources were edited. |
| P04 implementation | Java owns notes/value/cycle/clear/history/checkpoint/help commands, bounded 100-frame undo, immutable givens, allowed-conflicting-entry policy, full-board validation and atomic immutable completion/results/receipts. Restart is idempotent and creates a new READY replay against the same puzzle. Candidate/explanation/reveal and continuous-conflict-ever-used counters remain separate; wrong-entry totals are hidden until completion. Vue adds number pad/keyboard/arrows/N, optional left+/right- cycle, confirmations, pause/resume, help/history/replay controls, pending/acknowledged status and responsive selection without an input queue. A forward V2 migration persists progress. Browser storage retains unresolved IDs/payloads only; reconnect resolves receipts/current state, explicit same-ID retry handles unreceived mutations, and edits remain blocked while outcome is unknown. Previous-process/expired leases suspend without adding wall time. |
| P04 frontend verification | `Set-Location web; npx.cmd prettier --write src e2e` formatted changes. Initial `npm.cmd run typecheck` caught a possibly undefined snapshot dereference; fixed using the accepted response. Final `npm.cmd run typecheck`, `npm.cmd run lint`, `npm.cmd run format:check`, `npm.cmd run test:unit -- --run`, and `npm.cmd run build` passed. Final unit total 25, 0 failures; covers prior P03 contracts plus immutable retry payload/ID, no recursive rejected-pause retry, pending-move-then-pause without queued input, given/absent-selection guards and visible-active-time proposals with a one-hour paused interval adding zero. Build produced JS 270.55 kB (91.19 kB gzip), CSS 6.23 kB; existing dependency PURE-comment warnings remain non-failing. No dependency changes/install needed. |
| P04 Java verification | Focused `.\mvnw.cmd -o -B -pl server -am '-Dtest=PlayableAttemptTest,LocalServerBootstrapIntegrationTest,LegacyStoreMigrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`: 19 tests passed initially. Added bounded history/assistance, crash-loss/startup, actual V1-to-V2 file migration and database reopen tests; focused `.\mvnw.cmd -o -B -pl server -am '-Dtest=PlayableAttemptTest,PlayablePersistenceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`: 13 passed before the additional concurrent completion test. Final `.\mvnw.cmd -o -B verify`: BUILD SUCCESS, 44 tests (16 engine, 28 server), 0 failures/errors/skips. Covers given/range/status rejection; manual notes versus candidates; history/checkpoint/count monotonicity; timer bounds/duplicate sequence/heartbeat revision; pause/lease/takeover/process restart; idempotent replay; simultaneous final-command retries; exactly one immutable result; file shutdown/reopen restoring values/notes/checkpoint/undo/time and final receipt/result. Prototype migration backup/row/note preservation remains green. |
| P04 timing evidence and limits | Fake clock: save 5,000 ms at the five-second heartbeat, lose the next 4,000 ms on process restart, and close for one hour; recovered total remains exactly 5,000 ms, then resume plus 2,000 ms saves 7,000 ms. This measures a four-second unsaved loss, not a hard five-second guarantee during stalled IO/scheduling. Heartbeats every 5 s while visible/PLAYING, 15 s editor lease, 1,000 ms server-observed monotonic tolerance; cumulative proposals with separate sequences do not advance gameplay revision or create unbounded heartbeat receipts. Pause consumes the captured final proposal; no wall-time accrual. Pending request latency conservatively undercounts time. Historical pre-P04 attempts have no recorded elapsed/assistance history; zero is the new timer baseline, not a reconstructed duration. |
| P04 real-server/browser evidence | `$env:PLAYWRIGHT_CHANNEL='msedge'; npm.cmd run test:e2e` against the packaged Java JAR and unique temporary H2 directory: final 8 passed, repeated successfully after final full Java verify. Includes real generation/Vue save; keyboard completion; a 390px touch-enabled Edge keypad completion; manual notes/help/cycle/undo/redo/checkpoint; pause/reload/explicit resume/route exit; fresh replay preserving original; missing-response completion both committed and never received with identical-payload retry; disconnect/reconnect/version/fallback regressions. First expanded run had 7 pass/1 failure because the test's final replay GET omitted required editorSessionId (HTTP 400); corrected the test, not the application. Touch is browser emulation, not a physical-device certification; full visual/device/a11y testing remains P06/P07. No real user database or network settings were changed. |
| P04 final artifact and cleanup | `git --no-pager diff --check` passed (line-ending warnings only). No running Tiwanaku process/listener or `tiwanaku-e2e-*` disposable directory remained. After final 8-test E2E pass and an exact Java command-line check, `Copy-Item server\target\p02-build\tiwanaku.jar server\target\tiwanaku.jar` installed the verified JAR; both SHA256 values match `748814AFCB834E168A1C6C3BA1A91282BF56A40A1ACE5D691D71B03CC998FD60`. No commit/push/deploy/worktree or legacy retirement. |
| P05 changed files | Server Java `AttemptService`, `GenerationJobService`, `LocalDataConfiguration`, `ApiExceptionHandler`; new `HistoryEntry`, `HistoryService`, `LocalSettings`, `LegacyScores`, `StoreJson`, `StoreBackups`, `DataController`, `BackupController`; new `db/migration/V3__history_and_settings.sql` (V1/V2 unchanged); new `HistoryServiceTest`, `StoreBackupsTest`, `StorageFailureIntegrationTest` and actual libGDX fixture `server/src/test/resources/fixtures/TiwanakuScoreboard.xml`; `web/src/{App.vue,dataApi.ts,dataApi.test.ts,api.test.ts,testFixtures.ts,i18n.ts,style.css}`; new `web/src/stores/{data.ts,data.test.ts}`; `web/src/views/{HistoryView,SettingsView}.vue`; new `web/e2e/{data.spec.ts,recovery.spec.ts}`, typed/shared `web/e2e/server.ts` replacing `server.mjs`, updated `scaffold.spec.ts` and `playwright.config.ts`; `README.md`; this Progress State. Already-tracked generated engine test reports changed through verification; legacy application sources/assets were not edited. User's preceding commit is `6b1d324 WIP P04 complete`; this packet made no commit. |
| P05 implementation / data semantics | V3 backfills existing completed results without inventing missing elapsed/assistance/settings, and adds durable settings, history/import provenance and deletion tombstones. New completion atomically writes attempt/result/history/receipt and snapshots actual generation metadata plus current selected player. History supports player/board/scoring/assistance filters, date/time sort, normalized new tags, and confirmed deletion from history/export while retaining completed attempts/results/receipts. Filtered summary counts all selected rows, sums/counts known elapsed values only, and averages stored percentages only for compatible `legacy-clicks-v1` records. Settings use revision conflicts and server snapshots; defaults never relabel old puzzles. Full theme/detail polish remains P06. Logical v1 SHA-256-envelope export/import is explicitly history/settings portability; full physical ZIP backup covers playable/unfinished attempts and all receipts/raw sources. Limits: 4 MB upload/export, 4 million API source characters, 10000 records per import/management view; limits fail explicitly rather than truncate. |
| P05 legacy/source verification | Bounded parser supports actual semicolon-separated 3/4/5/7-field records, preserves duplicate occurrences by source fingerprint/ordinal, reports malformed records independently, keeps missing dates/counters/settings unknown, and retains exact confirmed raw sources/rejection reports transactionally. Same exact source/format import is idempotent, including simultaneous retries; changing its settings-restore choice is rejected. Imported player text stays inert. A real XML fixture was generated using cached libGDX 1.14.0 `Lwjgl3Preferences(FileHandle)` via `jshell --class-path "$gdx;$backend"` with `putString("scoreboard_results", ...)` and `flush()` into the test resource, never the user's preferences. Tests parse its real XML declaration/DOCTYPE without external resource/entity resolution, reject internal entities, and preserve escaped text. Real Edge executes the existing compiled `docs/app.js` WebPreferences constructor through a test-only in-memory reader exposure, confirming prefix/key decoding for `pref:app:TiwanakuScoreboard:scoreboard_resultss`; the legacy bundle on disk is unchanged. This is a genuine compiled preference-reader/storage fixture, not a claim that the older docs game has scoreboard UI. Known desktop HTTP preview/import was verified with `-Duser.home=<isolated test home>`, explicit confirmation, that same libGDX XML and byte-identical original source afterwards. README supplies an old-origin local-only exporter and documents Android's required in-app exporter/bridge. |
| P05 backup/upgrade/failure verification | Production Flyway strategy rejects unsupported future schemas/checksum failures, creates/verifies an H2-consistent version-labelled pre-upgrade ZIP before DDL, stops if backup creation fails, and rotates only successful automatic snapshots to newest five while keeping manual backups. Fresh stores need no pre-upgrade backup. Tests restore an actual V1 ZIP into a different directory and upgrade to V3, preserving an old completed record with unknown time/settings/assistance and inert tag. A real deliberately failing H2 DDL migration leaves a partially created table plus `upgrade-incomplete.txt` and preserved backup; subsequent startup guard refuses automatic repair/retry. A filesystem obstruction proves required-backup failure prevents schema advancement; a fake future Flyway version is not downgraded. Genuine H2 `ACCESS_MODE_DATA=r` refuses writes without resetting settings. Disk-full handling is tested by a deterministic H2 trigger throwing a disk-full SQLException, not by exhausting this shared machine's real drive: HTTP 503 is explicit, final attempt/result/history/receipts and failed imports roll back, then identical final-command retry commits one result/history row after removing the fault. No physical-drive-exhaustion claim is made. |
| P05 Java commands / outcomes | Initial `.\mvnw.cmd -o -B -pl server -am '-Dtest=HistoryServiceTest,StoreBackupsTest,PlayablePersistenceTest,PlayableAttemptTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`: 21 passed / 2 failed because standalone Flyway validate rejected expected pending migrations. Fixed validation to explicitly permit pending migrations while still checking checksums/future/missing schemas; focused `.\mvnw.cmd -o -B -pl server -am '-Dtest=StoreBackupsTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`: 4 passed. `.\mvnw.cmd -o -B -pl server -am '-Dtest=StorageFailureIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`: passed. Additional focused/full runs passed as coverage grew. Final `.\mvnw.cmd -o -B verify`: BUILD SUCCESS, 55 tests (16 engine, 39 server), 0 failures/errors/skips, including 6 history/import/settings, 4 backup/upgrade/readonly and 1 storage-fault HTTP test plus all prior engine/security/gameplay/persistence regressions. Intermediate `.\mvnw.cmd -o -B -DskipTests package` was packaging only, not counted as test evidence. No dependency changes/install or TLS workarounds. |
| P05 frontend commands / outcomes | `Set-Location web; npx.cmd prettier --write src e2e`; final `npm.cmd run typecheck`, `npm.cmd run lint`, `npm.cmd run format:check`, `npm.cmd run test:unit -- --run`, `npm.cmd run build`: all pass. Initial test-only unknown browser-evaluation type was corrected with explicit Zod parsing. Final 30 frontend unit tests / 0 failures cover frozen game contracts plus legacy-null metadata, strict data/version/settings schemas, acknowledged normalized settings, stale revisions and lost-save response followed by authoritative reload. Vite output JS 289.02 kB (96.97 kB gzip), CSS 7.02 kB; existing Zod PURE-comment warnings remain non-failing. README documents controls, import provenance/limits, storage error reconciliation, backup retention and safe separate-directory recovery/rollback. |
| P05 real Java/Edge journeys | `$env:PLAYWRIGHT_CHANNEL='msedge'; npm.cmd run test:e2e`: final 13 passed against packaged Java and isolated H2/home directories. Covers legacy preview/partial rejection/raw-source download, filtering count/sum/mean, inert tags/edit/delete, logical JSON download and file-upload reimport without resurrecting tombstones, real compiled same-origin legacy reader, explicit storage-error UI, saved settings/player after reload and a fresh browser profile, plus all P04 keyboard/touch/disconnect/lost-response journeys. Recovery E2E launches exact owned Java children on 18094 then 18095 with the same database, confirms stable results/settings/raw imports, repeats final move/import without duplication, and proves menu changes do not relabel original generation metadata. It downloads an actual full ZIP, extracts only `tiwanaku.mv.db` into a separate directory and launches on 18096, verifying identical history/settings/raw sources. Known-desktop import uses a scratch home and leaves its source unchanged. No real user database/preferences/network configuration was touched. |
| P05 browser failures and fixes | First expanded 12-test run: 9 passed / 3 failed. Real HTTP testing caught unnamed new MVC query/path bindings (500) and primitive optional preview flags (400); fixed explicit parameter names and nullable optional flags with explicit import confirmation. A label-text locator was changed to the actual combobox accessible name. The legacy backend reader assertions already passed in that failed import journey; subsequent complete imports passed. Initial targeted selectors with Windows backslashes matched no tests; a pipe selector was interpreted by npm's Windows command shell. Correct focused command `npm.cmd run test:e2e -- data.spec recovery.spec` passed 5 tests; repeated final full suite passed 13. The shared typed runner retains diagnostic logs in ignored test-results and closes exact child processes/streams before deleting its own disposable data. |
| P05 final artifact / cleanup | Final source `git --no-pager diff --check -- . ':!puzzle-engine/target/**' ':!server/target/**'` passes (line-ending warnings only). No listeners on 18093-18096, running Tiwanaku JAR children, or `tiwanaku-e2e-*` / `tiwanaku-recovery-*` disposable directories remained. After an exact running-command check, `Copy-Item server\target\p02-build\tiwanaku.jar server\target\tiwanaku.jar` installed the fully tested artifact. `Get-FileHash server\target\p02-build\tiwanaku.jar,server\target\tiwanaku.jar` matches SHA256 `85BFCB95F66CCA6DECA6165388757C3C1FEAB1000C8261F65FC8C36FA0938852`. Original user storage is untouched and will receive its required pre-upgrade backup on the user's next V3 launch. No commits/push/deploy/worktrees or legacy retirement. |
| P06 changed files / boundaries | `web/src/components/TerrainBoard.vue` (new SVG/HTML board and viewport controls); `web/src/terrain.ts` and `terrain.test.ts` (new presentation-only geometry); `web/src/views/PlayView.vue` (inspector/help, keyboard guards/roving focus wiring); `web/src/App.vue` (compact play shell); `web/src/style.css` (atlas/responsive/theme/focus tokens); `web/e2e/terrain.spec.ts` (deterministic presentation fixtures/screenshots); `README.md`; this Progress State. No server, schema, game-store, legacy code or asset changes. Existing staged P05 work was preserved; tests updated already-tracked generated engine reports without reverting/unstaging them. |
| P06 rendering / provenance | One SVG in cell-unit coordinates beneath HTML grid/rows/gridcells. Each region has one multi-rectangle union silhouette, flat biome fill and a board-anchored `userSpaceOnUse` pattern; shared edges follow IDs, not biome colors. Tests cover a concave eight-cell ring/hole and its same-biome center, plus all four terrain motifs. Original vector canopy/dune/peak/wave marks replace proposed legacy artwork with unverified provenance. No legacy PNG or font was bundled. System sans-serif and Georgia/serif fallbacks deliberately replace proposed bundled fonts until provenance is verified; no external font requests or new binary dependencies. Legacy assets remain untouched. Numeric notes, underlined givens, dashed focus and solid selection, `!` conflicts and labelled candidates are distinct. High contrast removes textures and uses white regions/stronger black boundaries; reduced detail removes marks only. |
| P06 frontend commands / outcomes | From `web`: `npx.cmd prettier --write src` and focused `npx.cmd prettier --write src\style.css src\views\PlayView.vue e2e\terrain.spec.ts`; `npm.cmd run typecheck`; `npm.cmd run lint`; `npm.cmd run format:check`; `npm.cmd run test:unit -- --run`; `npm.cmd run build`. Final gates pass; 40 unit tests (10 new terrain cases plus 30 existing). Initial test typecheck rejected iterable `NodeList` under the existing DOM library; corrected to `Array.from`, without weakening types/configuration. Final assets `index-dBrnBy53.js` 297.40 kB / 100.24 kB gzip and `index-BM9K47j0.css` 12.55 kB / 3.58 kB gzip. Existing non-failing Zod PURE-comment warnings remain. |
| P06 Java / regression commands | With `$env:JAVA_HOME='C:\Users\ho.jchatron\Documents\local_bin\jdk-25.0.4.1'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"`, `.\mvnw.cmd -o -B verify` passed 55 Java tests (16 engine, 39 server), zero failures/errors/skips, using isolated test storage. After CSS/keyboard-only refinements and rebuilding Vue, `.\mvnw.cmd -o -B package -DskipTests` repackaged the final UI successfully; this latter command skipped tests, not claimed to rerun them. From `web`, `$env:PLAYWRIGHT_CHANNEL='msedge'; npm.cmd run test:e2e` passed all 17 tests twice, including the final artifact: all 13 existing real-Java gameplay/completion/lost-response/settings/import/restart/physical-restore journeys plus four P06 presentation tests. All data/home directories were disposable, not the user's storage. |
| P06 measured browser / screenshot evidence | `npx.cmd playwright test terrain.spec.ts` exercised presentation-only snapshots; final full-suite tests cover all seven approved presets at 1440x900 and 390x900. Default cells measure 44 px, overview has no horizontal scroll, SVG bounds and every HTML cell align within 0.1 CSS px, and the page has at most 1 px horizontal overflow. TITAN retains an intentionally scrollable board; zoom-to-selection reveals/focuses its last cell in both axes. Viewport/keypad targets measure at least 44x44 px; narrow keypad is actually inside the viewport and sticky. DPR 1.25 with CSS zoom 1.1 and DPR 1.5 with CSS zoom 1.25 pass; synthetic 200% text scaling at 390 px retains controls without page overflow. Notes 1-9 have stable 3x3 slots, at least 10 px font at default scale, and measured note/value backing contrast at least 4.5:1 in every theme. Screenshots for 14 size/viewport combinations and light/dark/high-contrast/reduced-detail fixtures are written under ignored `web/test-results`; desktop themes and 390 px TITAN were visually inspected. These are deterministic screenshots, not randomized baselines or solver certification. |
| P06 failures / fixes | First focused browser run: 3 passed / 1 failed because a special nine-cell fixture ID collided with an ordinary UBER row group, correctly rejected by the unchanged schema; assigned a disjoint fixture ID. Second focused run: 3 passed / 1 failed with measured 0.11875 px fit-view cell drift. Fixed accumulated fractional track rounding by sharing the outer grid width/height with `minmax(0,1fr)` rows/columns, rather than loosening the 0.1 px assertion. Subsequent full runs passed 17/17. Screenshot review led to opaque calm numeral backings and focus rings outside the note glyphs; final browser run includes these refinements. |
| P06 keyboard / remaining verification boundaries | Automated real-browser assertions verify one roving stop, row endpoints, arrow movement, Tab exit, given/region/value/note/conflict accessible labels, N/Escape note mode (including checkbox focus), guarded editable-form shortcuts, native modal Escape and focus return. SVG is hidden from accessibility and pointer events; snapshots and Pending/Saved/error messages remain Java-authoritative. Source search `rg "requestAnimationFrame|WebGL|Solver|PuzzleGenerator|https?://" web/src` finds only the existing one-shot router focus callback, no continuous render loop or browser generation/solver. CSS zoom/text-size simulation is not an OS/browser-toolbar zoom or physical-device certification. Actual screen-reader listening, cross-browser/physical-device coverage, dependency/license inventory, and broader a11y/performance checks remain P07 work, not reported passed. |
| P06 final artifact / cleanup | After confirming no Tiwanaku JAR process via `Get-CimInstance Win32_Process`, `Copy-Item -LiteralPath 'server\target\p02-build\tiwanaku.jar' -Destination 'server\target\tiwanaku.jar'` installed the tested artifact. `Get-FileHash` source/destination SHA256 match: `AE7EEF6B3A02C5FD33A2FC46F056532676F126A443A8AC7D670FFCE4762C781B`. Java `jar tf` verifies final HTML and the exact hashed CSS/JS above are bundled. `Get-ChildItem $env:TEMP -Directory -Filter 'tiwanaku-*'` and exact Java-child check returned no leftovers. `git --no-pager diff --check -- . ':!puzzle-engine/target/**' ':!server/target/**'` passes (line-ending warnings only). No user database/preferences changed, commits, pushes, deployment, worktrees, legacy removal or later-packet implementation. |
| P07 scope amendment / changed files | User requested lighter local-only security/accessibility work and explicitly approved deferring actual screen-reader listening and disconnected-internet cold launch. Amended P07 scope/acceptance above before implementation. Changed `server/src/main/java/com/apogames/tiwanaku/server/LocalRequestSecurityFilter.java` and its test; `web/src/{App.vue,style.css,views/SetupView.vue}`; `web/package.json` and lockfile; new `web/e2e/robustness.spec.ts`; `web/e2e/data.spec.ts` (test-only legacy script fixture compatibility); `README.md`; this Progress State. User's preceding commit is `e9bb42f P06`; initial worktree was clean. Test runs updated already-tracked generated engine reports, not reverted. No gameplay-store/rules/schema/legacy-source changes. |
| P07 small fixes / retained boundaries | Retained loopback binding, Host/Origin/process-token and safe-import boundaries. Added self-only scripts/connections, no objects/framing/base override and self-only form action CSP, `nosniff` and `no-referrer`; inline styles remain allowed for Vue reactive geometry, not inline scripts/eval. Added malformed authority/wrong port/foreign or null origin/non-loopback/stale-process-token cases and header assertions. Repaired malformed generation-cancel button binding/text so its disabled and Cancelling states are real. Axe-driven fixes only: setup-label and LOCAL-wordmark color use existing theme tokens; home accessible name includes the visible LOCAL text. No login/TLS service, broad audit, accessibility redesign or new hosted CI infrastructure. |
| P07 dependency / provenance command | From `web`, `npm.cmd install --save-dev --save-exact @axe-core/playwright` added only the integration and axe-core 4.14.0, both MPL-2.0 and test-only; npm's install audit reported 0 known vulnerabilities, not a penetration-test claim. Type/build support verified on Node 22.22.3 (integration requires >=22.12). Read local direct runtime package manifests and LICENSE files: Vue 3.5.44, Pinia 3.0.3, Vue Router 4.6.3, Vue I18n 11.1.12 and Zod 4.6.7 declare MIT. Local Maven POMs declare Spring Boot 4.1.2 Apache-2.0, H2 2.3.232 MPL-2.0/EPL-1.0, Flyway Core 12.4.0 parent Apache-2.0. Bounded inventory recorded in README, not exhaustive transitive/legal review or resolution of old image/font provenance. P06 original art/system-font policy remains. |
| P07 focused checks / failures / fixes | `.\mvnw.cmd -o -B '-Dtest=LocalRequestSecurityFilterTest' '-Dsurefire.failIfNoSpecifiedTests=false' package` passed 5 filter tests. Initial `npx.cmd playwright test robustness.spec.ts`: 1 passed / 2 failed. Axe caught dark setup-label contrast 2.91:1 and visible/accessibility home-name mismatch; corrected existing tokens/name. Restart test wrongly assumed Resume before resolving a failed heartbeat: actual unknown-payload preservation was correct, so the test now explicitly resolves it before resuming; no production fallback/discard was added. Focused 3/3 then passed. Expanding route tests to all themes caught light LOCAL-wordmark contrast 2.65:1; corrected the second token. First expanded full suite 19/20 passed: CSP correctly blocked an old test's inline legacy-reader injection. Changed only that fixture to a Playwright-served same-origin script URL, preserving production CSP and unchanged legacy bundle; `npx.cmd playwright test data.spec.ts` then passed 4/4. |
| P07 final frontend / Java commands | From `web`: `npx.cmd prettier --write src\App.vue src\style.css src\views\SetupView.vue e2e\robustness.spec.ts e2e\data.spec.ts` (executed as focused batches); `npm.cmd run typecheck`; `npm.cmd run lint`; `npm.cmd run format:check`; `npm.cmd run test:unit -- --run`; `npm.cmd run build`. Final gates pass, 40 frontend unit tests. With `$env:JAVA_HOME='C:\Users\ho.jchatron\Documents\local_bin\jdk-25.0.4.1'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"`, `.\mvnw.cmd -o -B verify` passed 57 Java tests (16 engine, 41 server), zero failures/errors/skips, using isolated data. Intermediate `.\mvnw.cmd -o -B package -DskipTests` rebuilds skipped tests explicitly. Final assets: JS `index-BkRtHnGp.js` 297.40 kB / 100.23 kB gzip, CSS `index-CiCGMHvM.css` 12.57 kB / 3.57 kB gzip. Existing non-failing Zod comment warnings remain. |
| P07 final real-browser acceptance | From `web`, `$env:PLAYWRIGHT_CHANNEL='msedge'; $env:PLAYWRIGHT_JSON_OUTPUT_NAME='test-results\p07-results.json'; npm.cmd run test:e2e -- --reporter=list,json`: 20 passed, 0 skipped/flaky/unexpected, 90.76 s. Preserves all existing generation, completion/lost responses, keyboard/touch, saved settings/import/history, physical restore/restart, API mismatch and P06 preset/zoom/contrast checks. New real restart test owns port 18097 and scratch H2: open tab edits freeze when Java stops, keyboard sends zero new mutations, restarted process gets a new identity/token, old token returns 403, same values/attempt restore suspended (observed revision 1 -> 2), pending heartbeat is explicitly reconciled with its existing immutable metadata, then Resume succeeds. Synthetic held queued/cancelled responses test the cancellation UI race/previous-game preservation; actual Java job cancellation remains verified by the integration suite, not by that synthetic fixture. |
| P07 automated a11y / local timing evidence | Final JSON report attachments decoded with Node show empty violation arrays for all 13 axe WCAG A/AA states: setup/settings/history in light/dark/high-contrast (9), ready grid, help dialog, playing grid, paused grid (4). Thus no serious/critical findings on tested states; no rules/nodes excluded. Existing P06 tests verify roving keyboard/form guards and note/value contrast. Runtime request capture returned no external-origin requests and no CSP violation console messages during the route/play journey; actual HTML response headers also asserted. One local Edge navigation sample was 53.7 ms with 11 resource entries; this is a smoke observation, not a benchmark/budget claim. `web/test-results/p07-results.json` retains axe/timing/restart attachments and deterministic P06 screenshots remain ignored. No disconnected-network simulation was substituted for a real disconnected cold launch. |
| P07 explicit deferrals / limits | Screen-reader listening and genuinely internet-disconnected cold start explicitly deferred with user approval, not passed; physical-device/cross-browser matrix, exhaustive transitive license/security/performance work and new hosted CI infrastructure outside amended scope. Automated checks and existing keyboard tests are not manual accessibility certification. No broad security review/penetration-test claim. Future distribution still needs appropriate release notices. Existing P05 isolated write-failure/read-only/upgrade/backup tests were rerun, not replaced with success-shaped fallbacks. |
| P07 final artifact / cleanup | Checked exact `Get-CimInstance Win32_Process` Tiwanaku JAR matches: none running. `Copy-Item -LiteralPath 'server\target\p02-build\tiwanaku.jar' -Destination 'server\target\tiwanaku.jar'` installed final tested JAR; `Get-FileHash` source/destination SHA256 match `BD8260983EC598C5A23DD52AAE224439A38C3468DBFCEF91E9DCDFBAA99694B4`. Java `jar tf` verifies final HTML and exact CSS/JS above. Exact process check, `Get-ChildItem $env:TEMP -Directory -Filter 'tiwanaku-*'`, and listeners 18093-18097 returned no leftovers. Source `git --no-pager diff --check -- . ':!puzzle-engine/target/**' ':!server/target/**'` passes with line-ending warnings only. Original user data/preferences and legacy application untouched; no commits/push/deploy/worktrees or P08 implementation. |
| Last handoff / next bounded action | Lightweight P07 accepted on amended actual-command evidence above. Next is P08 only: inspect build/resource integration and launch instructions, then implement reproducible runnable packaging and isolated final-JAR launch checks. Full rework is not complete; P08-P10 remain. |

On each packet completion replace these values with packet ID/status, actual owned/changed paths, exact tests/results, deviations, blocker evidence and next packet. Keep concise evidence for completed dependencies in the same block/adjacent list; do not erase failed checks when they still matter. For first execution use **P00**, not "implement the whole plan". P01 and P02 require correctness review before expanding the UI. P03 freezes contracts after the real Java/server/storage slice. Avoid concurrent ownership of stores/schemas/build files.

### Remaining Decisions

- P02/P07: browser-rendered acceptance is verified; internet-disconnected cold-start acceptance was explicitly waived by the user on 2026-10-27 and reconfirmed deferred for lightweight P07. Actual screen-reader listening also explicitly deferred; neither is reported passed.
- P06/P07: original vectors and system-font fallbacks avoid redistributing legacy art/fonts with unverified provenance. Numeric contrast and automated route/theme a11y checks pass. Direct dependency declarations recorded; exhaustive transitive/legal investigation and manual accessibility certification deferred. Native HTML dialog is used; no unstyled component dependency was added.
- P08: P05 verified five-snapshot automatic backup rotation, required-backup failure, real restore/upgrade and retention semantics; final release/OS launch packaging remains P08.
- P09/P10: calibrate difficulty or keep honest preset labels, decide Explorer deferral if necessary, and obtain explicit approval for any native/hosted legacy retirement. A bundled Java runtime installer or separate mobile product is outside the initial JAR target.

## 13. Evidence And Uncertainty

The accompanying audit covers all 78 tracked Java source files through subsystem reviews, build configuration, source assets/localization, platform launchers, and deployment artifacts. Generated/minified third-party runtime and the archived ZIP were classified/inspected structurally rather than exhaustively audited line by line. This is not a penetration test or a formal solver proof.

Verified during planning: core compilation on JDK 25; core test task failure on JDK 25/Gradle 8.13; successful JDK 17 core test task with `NO-SOURCE`; desktop/TeaVM compile tasks; full source TeaVM release build. No browser automation, interactive screen-reader/device testing, Android release build, or new-architecture prototype was performed in this planning task. Proposed budgets and target-stack compatibility must therefore be validated during implementation.

External references checked on 2026-10-26:

- Vue setup and supported tooling: https://vuejs.org/guide/quick-start.html
- Vue client-side SPA use: https://vuejs.org/guide/extras/ways-of-using-vue.html
- React SPA alternative: https://react.dev/learn/build-a-react-app-from-scratch
- Svelte static deployment alternative: https://svelte.dev/docs/kit/adapter-static
- Selective unstyled Vue accessibility primitives: https://reka-ui.com/docs/overview/introduction
- Vite runtime requirements and static build: https://vite.dev/guide/
- Spring Boot Java/Maven support: https://docs.spring.io/spring-boot/system-requirements.html
- Spring executable packaging: https://docs.spring.io/spring-boot/maven-plugin/packaging.html
- Lighter Java server alternative: https://javalin.io/documentation
- Gradle Java compatibility: https://docs.gradle.org/current/userguide/compatibility.html

Revision-2 reference: embedded H2 behavior/backup/IO cancellation cautions, https://h2database.com/html/features.html . P02 has verified Spring Boot 4.1.2, H2 2.3.232, Flyway startup/migration, and file-mode restart on Java 25; backup/restore, upgrade rollback, and failure-mode semantics remain for their owning packets. Historical TeaVM release-build evidence above is legacy validation only, not a new-stack requirement. The companion audit's deployment note/recommendations were aligned without changing historical findings.

The measured Java/server/storage slice, not the freshness of a dependency version, validates this design's implementation.
