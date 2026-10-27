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

const sessionKey = "tiwanaku.editorSessionId";
const attemptKey = "tiwanaku.attemptId";

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
      attempt.value = snapshot;
      selectedCell.value = undefined;
      connected.value = true;
      statusMessage.value = snapshot
        ? "Saved game restored from the local server."
        : "Connected. Start a puzzle to play.";
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
        attempt.value = snapshot;
      }
    } catch (error) {
      if (currentEpoch === epoch) report(error);
    } finally {
      refreshing = false;
    }
  }

  async function startPuzzle(): Promise<boolean> {
    if (!connected.value || pending.value || !bootstrap.value) return false;
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
      const created = await request(
        bootstrap.value,
        "/api/v1/attempts",
        attemptSchema,
        {
          method: "POST",
          body: jsonBody(creationSchema, {
            requestId: crypto.randomUUID(),
            generationJobId: job.value.jobId,
            editorSessionId,
          }),
        },
      );
      attempt.value = created;
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

  async function command(type: "SET_VALUE" | "TAKEOVER", value?: number) {
    const current = attempt.value;
    const cell = selectedCell.value;
    if (!connected.value || pending.value || !bootstrap.value || !current)
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
    ++epoch;
    errorMessage.value = "";
    try {
      attempt.value = await request(
        bootstrap.value,
        `/api/v1/attempts/${current.attemptId}/commands`,
        attemptSchema,
        {
          method: "POST",
          body: jsonBody(commandSchema, {
            commandId: crypto.randomUUID(),
            expectedRevision: current.revision,
            editorSessionId,
            type,
            ...(type === "SET_VALUE" ? { cellIndex: cell, value } : {}),
          }),
        },
      );
      statusMessage.value =
        attempt.value.status === "COMPLETED"
          ? "Puzzle complete. Your result has been saved."
          : type === "TAKEOVER"
            ? "This tab now controls the saved game."
            : "Move saved to the local server.";
    } catch (error) {
      report(error);
      if (error instanceof HttpError && error.status === 409) {
        try {
          attempt.value = await loadAttempt(current.attemptId);
          statusMessage.value = ownsEditor.value
            ? "Saved game refreshed. Review it and retry your move."
            : "Another tab controls this game. Take control before editing.";
        } catch (refreshError) {
          report(refreshError);
        }
      }
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
    connect,
    refreshAttempt,
    startPuzzle,
    cancelGeneration,
    enterValue: (value: number) => command("SET_VALUE", value),
    takeControl: () => command("TAKEOVER"),
  };
});
