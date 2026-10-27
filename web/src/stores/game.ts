import { computed, ref } from "vue";
import { defineStore } from "pinia";
import {
  attemptSchema,
  commandSchema,
  ContractError,
  creationSchema,
  generationSchema,
  HttpError,
  jobSchema,
  jsonBody,
  loadBootstrap,
  request,
} from "../api";
import type { AttemptSnapshot, Bootstrap, GenerationJob } from "../api";
import type { CommandType } from "../api";

const sessionKey = "tiwanaku.editorSessionId";
const attemptKey = "tiwanaku.attemptId";
const uncertainKey = "tiwanaku.uncertainMutation";

export const useGameStore = defineStore("game", () => {
  const editorSessionId =
    sessionStorage.getItem(sessionKey) ?? crypto.randomUUID();
  sessionStorage.setItem(sessionKey, editorSessionId);
  const bootstrap = ref<Bootstrap>();
  const attempt = ref<AttemptSnapshot>();
  const job = ref<GenerationJob>();
  const selectedCell = ref<number>();
  const preset = ref("7x5");
  const maxRegionSize = ref(5);
  const difficulty = ref("EASY");
  const statusMessage = ref("Connect to the local game server.");
  const errorMessage = ref("");
  const connected = ref(false);
  const connecting = ref(false);
  const pending = ref(false);
  const cancelling = ref(false);
  const noteMode = ref(false);
  const cycleMode = ref(true);
  const playVisible = ref(false);
  const pauseRequested = ref(false);
  const outcomeUnknown = ref(false);
  const clockTick = ref(0);
  let localElapsed = 0;
  let localStartedAt: number | undefined;
  let timingSequence = 0;
  let uncertain: { path: string; body: string } | undefined;
  let epoch = 0;
  let refreshing = false;
  const ownsEditor = computed(
    () => attempt.value?.editorSessionId === editorSessionId,
  );
  const selectedValue = computed(
    () => attempt.value?.values[selectedCell.value ?? -1] ?? 0,
  );
  const maxEntry = computed(() => {
    const current = attempt.value;
    if (!current || selectedCell.value === undefined) return 0;
    return current.regionIds.filter(
      (id) => id === current.regionIds[selectedCell.value ?? -1],
    ).length;
  });
  const editable = computed(
    () =>
      connected.value &&
      ownsEditor.value &&
      !pending.value &&
      !pauseRequested.value &&
      !outcomeUnknown.value &&
      ["READY", "PLAYING"].includes(attempt.value?.status ?? ""),
  );
  const elapsedMillis = computed(() => {
    void clockTick.value;
    return Math.floor(
      localElapsed +
        (localStartedAt === undefined ? 0 : performance.now() - localStartedAt),
    );
  });
  function tick() {
    clockTick.value++;
  }
  function stopClock() {
    if (localStartedAt !== undefined)
      localElapsed += Math.max(0, performance.now() - localStartedAt);
    localStartedAt = undefined;
    tick();
  }
  function adopt(snapshot: AttemptSnapshot, resetClock = false) {
    const previous = attempt.value;
    attempt.value = snapshot;
    timingSequence = Math.max(timingSequence, snapshot.progress.timingSequence);
    if (
      resetClock ||
      previous?.attemptId !== snapshot.attemptId ||
      snapshot.status !== "PLAYING"
    ) {
      localStartedAt = undefined;
      localElapsed = snapshot.progress.elapsedMillis;
      timingSequence = snapshot.progress.timingSequence;
    }
    if (
      localStartedAt === undefined &&
      snapshot.status === "PLAYING" &&
      connected.value &&
      playVisible.value &&
      !pauseRequested.value &&
      !pending.value
    ) {
      localStartedAt = performance.now();
    }
    tick();
  }
  function clearUncertain() {
    uncertain = undefined;
    outcomeUnknown.value = false;
    sessionStorage.removeItem(uncertainKey);
  }
  async function mutate(path: string, body: string) {
    if (!bootstrap.value) throw new Error("Connect to the local server first.");
    uncertain = { path, body };
    sessionStorage.setItem(uncertainKey, JSON.stringify(uncertain));
    try {
      const snapshot = await request(bootstrap.value, path, attemptSchema, {
        method: "POST",
        body,
      });
      clearUncertain();
      return snapshot;
    } catch (error) {
      if (error instanceof HttpError && error.status < 500) {
        clearUncertain();
      } else {
        outcomeUnknown.value = true;
        connected.value = false;
        stopClock();
      }
      throw error;
    }
  }

  function report(error: unknown) {
    errorMessage.value =
      error instanceof Error
        ? error.message
        : "Unexpected local application error.";
    if (
      error instanceof TypeError ||
      error instanceof DOMException ||
      (error instanceof HttpError && error.status === 403) ||
      error instanceof ContractError
    ) {
      connected.value = false;
      stopClock();
      statusMessage.value =
        "Edits are paused. Reconnect to restore the authoritative saved game.";
    }
  }

  async function loadAttempt(id: string) {
    if (!bootstrap.value) throw new Error("Connect to the local server first.");
    return request(
      bootstrap.value,
      `/api/v1/attempts/${id}?editorSessionId=${encodeURIComponent(editorSessionId)}`,
      attemptSchema,
    );
  }

  async function connect() {
    if (connecting.value || pending.value) return;
    connecting.value = true;
    connected.value = false;
    errorMessage.value = "";
    const currentEpoch = ++epoch;
    try {
      const next = await loadBootstrap();
      if (currentEpoch !== epoch) return;
      bootstrap.value = next;
      const savedMutation = sessionStorage.getItem(uncertainKey);
      if (savedMutation) {
        const parsed: unknown = JSON.parse(savedMutation);
        if (
          typeof parsed !== "object" ||
          parsed === null ||
          !("path" in parsed) ||
          !("body" in parsed) ||
          typeof parsed.path !== "string" ||
          typeof parsed.body !== "string"
        ) {
          throw new ContractError(
            "The pending action metadata is invalid; keep it for diagnosis.",
          );
        }
        const payload: unknown = JSON.parse(parsed.body);
        const match = /^\/api\/v1\/attempts\/([a-f0-9-]{36})\/commands$/.exec(
          parsed.path,
        );
        if (match) {
          const command = commandSchema.parse(payload);
          uncertain = { path: parsed.path, body: parsed.body };
          try {
            const resolved = await request(
              next,
              `${parsed.path}/${encodeURIComponent(command.commandId)}?editorSessionId=${encodeURIComponent(editorSessionId)}`,
              attemptSchema,
            );
            sessionStorage.setItem(attemptKey, resolved.attemptId);
            clearUncertain();
          } catch (error) {
            if (!(error instanceof HttpError) || error.status !== 404)
              throw error;
            outcomeUnknown.value = true;
          }
        } else if (parsed.path === "/api/v1/attempts") {
          creationSchema.parse(payload);
          uncertain = { path: parsed.path, body: parsed.body };
          outcomeUnknown.value = true;
        } else {
          throw new ContractError(
            "The pending action path is invalid; keep it for diagnosis.",
          );
        }
      }
      const savedId = sessionStorage.getItem(attemptKey);
      let snapshot: AttemptSnapshot | undefined;
      if (savedId) {
        try {
          snapshot = await loadAttempt(savedId);
        } catch (error) {
          if (!(error instanceof HttpError) || error.status !== 404)
            throw error;
          errorMessage.value =
            "The saved game is not in this Java data directory. Restore its original directory or generate a new puzzle; no data was deleted.";
        }
      }
      if (currentEpoch !== epoch) return;
      if (
        snapshot?.status === "PLAYING" &&
        snapshot.editorSessionId === editorSessionId &&
        !outcomeUnknown.value
      ) {
        snapshot = await mutate(
          `/api/v1/attempts/${snapshot.attemptId}/commands`,
          jsonBody(commandSchema, {
            commandId: crypto.randomUUID(),
            expectedRevision: snapshot.revision,
            editorSessionId,
            type: "PAUSE",
          }),
        );
      }
      selectedCell.value = undefined;
      connected.value = true;
      pauseRequested.value = false;
      if (snapshot) adopt(snapshot, true);
      else attempt.value = undefined;
      statusMessage.value = snapshot
        ? "Saved game restored from the local server."
        : "Connected. Start a puzzle to play.";
      if (outcomeUnknown.value)
        statusMessage.value =
          "An action's outcome is unknown. Resolve it before editing.";
    } catch (error) {
      report(error);
      statusMessage.value =
        "Could not reconnect. Saved data was kept; retry when the server is ready.";
    } finally {
      connecting.value = false;
    }
  }

  async function refreshAttempt() {
    if (!connected.value || pending.value || connecting.value || refreshing)
      return;
    refreshing = true;
    const currentEpoch = epoch;
    const current = attempt.value;
    try {
      const instance = await loadBootstrap();
      if (currentEpoch !== epoch) return;
      if (instance.processInstanceId !== bootstrap.value?.processInstanceId) {
        connected.value = false;
        stopClock();
        statusMessage.value =
          "The Java server restarted. Reconnect before editing.";
        return;
      }
      if (!current) return;
      const snapshot = await loadAttempt(current.attemptId);
      if (
        currentEpoch === epoch &&
        attempt.value?.attemptId === snapshot.attemptId &&
        snapshot.revision >= attempt.value.revision
      ) {
        adopt(snapshot);
      }
    } catch (error) {
      if (currentEpoch === epoch) report(error);
    } finally {
      refreshing = false;
    }
  }

  async function startPuzzle(): Promise<boolean> {
    if (
      !connected.value ||
      pending.value ||
      outcomeUnknown.value ||
      !bootstrap.value
    )
      return false;
    pending.value = true;
    ++epoch;
    errorMessage.value = "";
    try {
      const [columns, rows] = preset.value.split("x").map(Number);
      job.value = await request(
        bootstrap.value,
        "/api/v1/generation-jobs",
        jobSchema,
        {
          method: "POST",
          body: jsonBody(generationSchema, {
            columns,
            rows,
            maxRegionSize: maxRegionSize.value,
            difficulty: difficulty.value,
            seed: crypto.getRandomValues(new Uint32Array(1))[0],
          }),
        },
      );
      while (["QUEUED", "CANCELLING"].includes(job.value.status)) {
        statusMessage.value = "Building and certifying a puzzle in Java...";
        await new Promise((resolve) => setTimeout(resolve, 250));
        job.value = await request(
          bootstrap.value,
          `/api/v1/generation-jobs/${job.value.jobId}`,
          jobSchema,
        );
      }
      if (job.value.status === "CANCELLED") {
        statusMessage.value =
          "Generation cancelled. Your current game is unchanged.";
        return false;
      }
      if (job.value.status !== "GENERATED") {
        throw new Error(
          job.value.error ??
            `Generation ended: ${job.value.status} (${job.value.termination})`,
        );
      }
      const created = await mutate(
        "/api/v1/attempts",
        jsonBody(creationSchema, {
          requestId: crypto.randomUUID(),
          generationJobId: job.value.jobId,
          editorSessionId,
        }),
      );
      adopt(created, true);
      sessionStorage.setItem(attemptKey, created.attemptId);
      selectedCell.value = undefined;
      statusMessage.value =
        "Puzzle saved. Select an open cell and enter a number.";
      return true;
    } catch (error) {
      report(error);
      if (connected.value)
        statusMessage.value =
          "Generation failed. Your previous game is unchanged.";
      return false;
    } finally {
      pending.value = false;
      job.value = undefined;
    }
  }

  async function cancelGeneration() {
    if (!bootstrap.value || !job.value || cancelling.value) return;
    cancelling.value = true;
    try {
      // The polling request remains authoritative; a late DELETE response must not replace a terminal job.
      await request(
        bootstrap.value,
        `/api/v1/generation-jobs/${job.value.jobId}`,
        jobSchema,
        { method: "DELETE" },
      );
    } catch (error) {
      report(error);
    } finally {
      cancelling.value = false;
    }
  }

  async function command(type: CommandType, value?: number) {
    const current = attempt.value;
    const cell = selectedCell.value;
    if (
      !connected.value ||
      pending.value ||
      outcomeUnknown.value ||
      !bootstrap.value ||
      !current
    )
      return;
    if (type !== "TAKEOVER" && !ownsEditor.value) return;
    if (
      !["TAKEOVER", "RESUME", "PAUSE", "HEARTBEAT"].includes(type) &&
      !editable.value
    )
      return;
    if (
      ["SET_VALUE", "TOGGLE_NOTE", "CANDIDATES", "REVEAL"].includes(type) &&
      (cell === undefined || current.givens[cell] !== 0)
    )
      return;
    if (
      type === "SET_VALUE" &&
      (!ownsEditor.value ||
        cell === undefined ||
        current.givens[cell] !== 0 ||
        current.status === "COMPLETED" ||
        current.values[cell] === value)
    )
      return;
    pending.value = true;
    stopClock();
    const proposal =
      current.status === "PLAYING" && type !== "TAKEOVER"
        ? {
            timingSequence: ++timingSequence,
            elapsedMillis: Math.floor(localElapsed),
          }
        : {};
    ++epoch;
    errorMessage.value = "";
    try {
      const snapshot = await mutate(
        `/api/v1/attempts/${current.attemptId}/commands`,
        jsonBody(commandSchema, {
          commandId: crypto.randomUUID(),
          expectedRevision: current.revision,
          editorSessionId,
          type,
          ...proposal,
          ...([
            "SET_VALUE",
            "CYCLE_VALUE",
            "TOGGLE_NOTE",
            "CANDIDATES",
            "REVEAL",
          ].includes(type)
            ? { cellIndex: cell }
            : {}),
          ...(value !== undefined ? { value } : {}),
        }),
      );
      adopt(snapshot, true);
      statusMessage.value =
        snapshot.status === "COMPLETED"
          ? "Puzzle complete. Your result has been saved."
          : type === "TAKEOVER"
            ? "This tab now controls the saved game."
            : type === "PAUSE"
              ? "Game paused. Closed and hidden time is not counted."
              : type === "RESUME"
                ? "Game resumed."
                : type === "HEARTBEAT"
                  ? "Active time saved."
                  : "Move saved to the local server.";
    } catch (error) {
      report(error);
      if (error instanceof HttpError && error.status === 409) {
        try {
          adopt(await loadAttempt(current.attemptId), true);
          statusMessage.value = ownsEditor.value
            ? "Saved game refreshed. Review it and retry your move."
            : "Another tab controls this game. Take control before editing.";
        } catch (refreshError) {
          report(refreshError);
        }
      }
    } finally {
      pending.value = false;
      if (pauseRequested.value) {
        if (
          type !== "PAUSE" &&
          connected.value &&
          !outcomeUnknown.value &&
          attempt.value &&
          ["READY", "PLAYING"].includes(attempt.value.status)
        ) {
          void command("PAUSE");
        } else if (connected.value && !outcomeUnknown.value)
          pauseRequested.value = false;
      } else if (attempt.value) adopt(attempt.value);
    }
  }

  async function pause() {
    playVisible.value = false;
    stopClock();
    if (
      !attempt.value ||
      !ownsEditor.value ||
      !["READY", "PLAYING"].includes(attempt.value.status)
    )
      return;
    pauseRequested.value = true;
    if (!pending.value) await command("PAUSE");
  }
  async function resume() {
    playVisible.value = true;
    pauseRequested.value = false;
    await command("RESUME");
  }
  async function heartbeat() {
    if (
      playVisible.value &&
      editable.value &&
      attempt.value?.status === "PLAYING"
    ) {
      await command("HEARTBEAT");
    } else await refreshAttempt();
  }
  async function resolvePending() {
    if (!uncertain || !connected.value || pending.value) return;
    pending.value = true;
    ++epoch;
    stopClock();
    try {
      const snapshot = await mutate(uncertain.path, uncertain.body);
      sessionStorage.setItem(attemptKey, snapshot.attemptId);
      adopt(snapshot, true);
      pauseRequested.value = snapshot.status === "PLAYING";
      statusMessage.value =
        "Pending action resolved against the Java database.";
    } catch (error) {
      report(error);
      if (error instanceof HttpError && error.status === 409 && attempt.value) {
        try {
          adopt(await loadAttempt(attempt.value.attemptId), true);
          pauseRequested.value = attempt.value?.status === "PLAYING";
        } catch (refreshError) {
          report(refreshError);
        }
      }
    } finally {
      pending.value = false;
      if (pauseRequested.value && connected.value && !outcomeUnknown.value)
        await command("PAUSE");
      else if (attempt.value) adopt(attempt.value);
    }
  }
  async function restart() {
    const current = attempt.value;
    if (!connected.value || pending.value || outcomeUnknown.value || !current)
      return;
    await pause();
    if (!connected.value || pending.value || outcomeUnknown.value) return;
    pending.value = true;
    ++epoch;
    try {
      const snapshot = await mutate(
        "/api/v1/attempts",
        jsonBody(creationSchema, {
          requestId: crypto.randomUUID(),
          restartAttemptId: current.attemptId,
          editorSessionId,
        }),
      );
      adopt(snapshot, true);
      sessionStorage.setItem(attemptKey, snapshot.attemptId);
      selectedCell.value = undefined;
      pauseRequested.value = false;
      playVisible.value = true;
      statusMessage.value =
        "Restarted as a new replay attempt. Original history is unchanged.";
    } catch (error) {
      report(error);
    } finally {
      pending.value = false;
    }
  }

  return {
    attempt,
    job,
    selectedCell,
    selectedValue,
    preset,
    maxRegionSize,
    difficulty,
    statusMessage,
    errorMessage,
    connected,
    connecting,
    pending,
    cancelling,
    ownsEditor,
    maxEntry,
    editable,
    noteMode,
    cycleMode,
    playVisible,
    elapsedMillis,
    outcomeUnknown,
    pauseRequested,
    tick,
    heartbeat,
    pause,
    resume,
    command,
    restart,
    resolvePending,
    connect,
    refreshAttempt,
    startPuzzle,
    cancelGeneration,
    enterValue: (value: number) =>
      command(
        noteMode.value && value !== 0 ? "TOGGLE_NOTE" : "SET_VALUE",
        value,
      ),
    cycleValue: (direction: 1 | -1) => command("CYCLE_VALUE", direction),
    takeControl: () => command("TAKEOVER"),
  };
});
