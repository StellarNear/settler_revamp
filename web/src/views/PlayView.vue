<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { onBeforeRouteLeave, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { useGameStore } from "../stores/game";
import { useDataStore } from "../stores/data";
import TerrainBoard from "../components/TerrainBoard.vue";
import PlayerPicker from "../components/PlayerPicker.vue";
import { biomes } from "../terrain";
import { contactRuleDescription, shapeLabels } from "../shapes";
import { assignPlayer, loadPlayers } from "../dataApi";
const game = useGameStore();
const data = useDataStore();
const router = useRouter();
const terrainBoard = ref<InstanceType<typeof TerrainBoard>>();
const selectedRegion = computed(
  () => game.attempt?.regionIds[game.selectedCell ?? -1],
);
const selectedBiome = computed(() => {
  if (!terrainBoard.value) return undefined;
  const biome = terrainBoard.value.selectedBiome;
  return biome === undefined ? undefined : biomes[biome];
});
const help = ref<HTMLDialogElement>();
const { t } = useI18n();
const time = computed(() => {
  const seconds = Math.floor(game.elapsedMillis / 1_000);
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
});
const clueCount = computed(
  () => game.attempt?.givens.filter((value) => value !== 0).length ?? 0,
);
const presetNames: Record<string, string> = {
  "3x3": "Tiny",
  "4x4": "Small",
  "5x5": "Compact",
  "7x5": "Medium",
  "9x5": "Large",
  "15x10": "Huge",
  "20x12": "Titan",
};
const boardName = computed(() => {
  const current = game.attempt;
  if (!current) return "";
  const name = presetNames[`${current.columns}x${current.rows}`];
  const tiles = shapeLabels[current.puzzleSettings?.shape ?? "SQUARE"];
  return `${name ? `${name} · ` : ""}${current.columns} × ${current.rows} · ${tiles}`;
});
const win = ref<HTMLDialogElement>();
const winPlayer = ref("");
const winPlayers = ref<string[]>([]);
const winMessage = ref("");
const winBusy = ref(false);
watch(
  () => [game.attempt?.attemptId, game.attempt?.status] as const,
  async ([id, status], previous) => {
    if (
      status !== "COMPLETED" ||
      !previous ||
      previous[0] !== id ||
      previous[1] === "COMPLETED"
    )
      return;
    winPlayer.value = data.settings?.playerTag ?? "";
    winMessage.value = "";
    if (!win.value?.open) win.value?.showModal();
    try {
      winPlayers.value = await loadPlayers([winPlayer.value]);
    } catch {
      winPlayers.value = winPlayer.value ? [winPlayer.value] : [];
    }
  },
);
async function pickWinner(tag: string) {
  const attemptId = game.attempt?.attemptId;
  if (!attemptId || winBusy.value) return;
  winBusy.value = true;
  winMessage.value = "";
  try {
    await assignPlayer(attemptId, tag);
    winPlayer.value = tag;
    if (tag && !winPlayers.value.includes(tag))
      winPlayers.value = [...winPlayers.value, tag].sort();
    if (data.settings && data.settings.playerTag !== tag)
      await data.save({ ...data.settings, playerTag: tag });
    winMessage.value = tag ? `Result saved for ${tag}.` : "Player cleared.";
  } catch (reason) {
    winMessage.value =
      reason instanceof Error ? reason.message : "Could not save the player.";
  } finally {
    winBusy.value = false;
  }
}
function newPuzzle() {
  win.value?.close();
  void router.push("/setup");
}
const boardDisabled = computed(() => !game.editable);
const slowSave = ref(false);
let slowSaveTimer: number | undefined;
watch(
  () => game.saving,
  (saving) => {
    window.clearTimeout(slowSaveTimer);
    if (saving)
      slowSaveTimer = window.setTimeout(() => (slowSave.value = true), 400);
    else slowSave.value = false;
  },
);

function select(index: number, event: MouseEvent) {
  game.selectedCell = index;
  if (game.cycleMode && event.detail > 0) {
    game.clearWheelNote(index);
    void game.cycleValue(1);
  }
}
function secondary(index: number) {
  game.selectedCell = index;
  if (game.cycleMode) {
    game.clearWheelNote(index);
    void game.cycleValue(-1);
  }
}
const confetti = Array.from({ length: 36 }, (_, index) => ({
  left: `${(index * 37) % 100}%`,
  delay: `${(index % 9) * 0.12}s`,
  duration: `${2.2 + (index % 5) * 0.35}s`,
  color: ["#f4c8bd", "#efe0a5", "#bfe0ce", "#c4deec", "#e0cbe5", "#ffb703"][
    index % 6
  ],
  turn: `${index % 2 ? "" : "-"}${360 + (index % 4) * 180}deg`,
}));
async function onKeydown(event: KeyboardEvent) {
  if (event.target instanceof HTMLElement && event.target.closest("dialog"))
    return;
  if (
    event.target instanceof HTMLElement &&
    (event.target.matches("input,select,textarea") ||
      event.target.isContentEditable)
  )
    return;
  if (event.ctrlKey || event.metaKey || event.altKey || event.repeat) return;
  if (/^[1-9]$/.test(event.key)) {
    event.preventDefault();
    game.clearWheelNote(game.selectedCell);
    void game.enterValue(Number(event.key));
  } else if (["Backspace", "Delete", "0"].includes(event.key)) {
    event.preventDefault();
    game.clearWheelNote(game.selectedCell);
    void game.command("SET_VALUE", 0);
  } else if (
    (event.key.startsWith("Arrow") || ["Home", "End"].includes(event.key)) &&
    game.attempt
  ) {
    event.preventDefault();
    const current = game.selectedCell ?? 0;
    const columns = game.attempt.columns;
    const delta =
      event.key === "Home"
        ? -(current % columns)
        : event.key === "End"
          ? columns - 1 - (current % columns)
          : {
              ArrowLeft: -1,
              ArrowRight: 1,
              ArrowUp: -columns,
              ArrowDown: columns,
            }[event.key];
    if (delta === undefined) return;
    const next = current + delta;
    if (
      next < 0 ||
      next >= game.attempt.values.length ||
      (Math.abs(delta) === 1 &&
        Math.floor(next / columns) !== Math.floor(current / columns))
    )
      return;
    game.selectedCell = next;
    await terrainBoard.value?.focus(next);
  }
}
function checkpoint() {
  if (
    !game.attempt?.progress.hasCheckpoint ||
    window.confirm("Replace your saved checkpoint with this board and notes?")
  ) {
    void game.command("CHECKPOINT");
  }
}
function restore() {
  if (
    window.confirm(
      "Restore checkpoint values and notes? Time will not be reset. You can undo this restore.",
    )
  ) {
    void game.command("RESTORE_CHECKPOINT");
  }
}
function restart() {
  if (
    window.confirm(
      "Restart this same puzzle as a new replay attempt? The original attempt is kept.",
    )
  )
    void game.restart();
}
function visibility() {
  if (document.hidden) void game.pause();
}
function pageHide() {
  void game.pause();
}
onMounted(() => {
  game.playVisible = !document.hidden;
  document.addEventListener("visibilitychange", visibility);
  window.addEventListener("pagehide", pageHide);
});
onBeforeRouteLeave(async () => {
  await game.pause();
});
onUnmounted(() => {
  window.clearTimeout(slowSaveTimer);
  void game.pause();
  document.removeEventListener("visibilitychange", visibility);
  window.removeEventListener("pagehide", pageHide);
});
</script>

<template>
  <section class="board-area" aria-label="Puzzle board" @keydown="onKeydown">
    <template v-if="game.attempt">
      <div class="board-meta">
        <span
          >{{ boardName }} ·
          {{ game.attempt.status }}
          {{ game.attempt.progress.replay ? " · REPLAY" : "" }}</span
        >
        <span
          class="save-indicator"
          :class="{ visible: slowSave }"
          role="status"
          :aria-hidden="!slowSave"
          >Saving to Java…</span
        >
        <span
          >REV {{ game.attempt.revision }} ·
          {{ game.attempt.moveCount }} ENTRIES</span
        >
      </div>
      <div class="play-toolbar">
        <RouterLink to="/setup">New puzzle</RouterLink>
        <button
          :disabled="!game.connected || game.outcomeUnknown"
          @click="restart"
        >
          Restart this puzzle
        </button>
        <button
          v-if="game.connected && !game.ownsEditor"
          @click="game.takeControl"
        >
          Take control of this game
        </button>
        <button
          v-if="game.attempt.status === 'SUSPENDED'"
          :disabled="!game.connected || !game.ownsEditor || game.outcomeUnknown"
          @click="game.resume"
        >
          Resume game
        </button>
        <button
          v-else-if="game.attempt.status !== 'COMPLETED'"
          :disabled="boardDisabled"
          @click="game.pause"
        >
          Pause game
        </button>
      </div>
      <p v-if="game.attempt.status === 'SUSPENDED'">
        Paused. Resume explicitly; hidden and closed time is not counted.
      </p>
      <div class="play-layout">
        <TerrainBoard
          ref="terrainBoard"
          :attempt="game.attempt"
          :selected="game.selectedCell"
          :disabled="
            !game.connected ||
            game.attempt.status === 'SUSPENDED' ||
            game.attempt.status === 'COMPLETED'
          "
          :reduced-detail="data.settings?.reducedDetail ?? false"
          :wheel-notes="game.wheelNotes"
          @select="select"
          @secondary="secondary"
          @focus-cell="game.selectedCell = $event"
          @note="game.stepWheelNote"
        >
          <template #hud>
            <dl class="hud" aria-label="Game information">
              <div>
                <dt>Time</dt>
                <dd>
                  <time aria-label="Active elapsed time">{{ time }}</time>
                </dd>
              </div>
              <div>
                <dt>Clicks</dt>
                <dd aria-label="Total clicks">
                  {{ game.attempt.progress.totalClicks }}
                </dd>
              </div>
              <div>
                <dt>Board</dt>
                <dd>{{ boardName }}</dd>
              </div>
              <div v-if="game.attempt.puzzleSettings">
                <dt>Difficulty</dt>
                <dd>
                  {{ game.attempt.puzzleSettings.difficulty }} · max
                  {{ game.attempt.puzzleSettings.maxRegionSize }}
                  · {{ clueCount }} clues
                </dd>
              </div>
            </dl>
          </template>
        </TerrainBoard>
        <aside class="play-inspector" aria-label="Puzzle tools and selection">
          <h2>Field notes</h2>
          <div
            v-if="selectedRegion !== undefined"
            class="selected-region"
            aria-label="Selected region details"
          >
            <div class="region-inspector-heading">
              <i
                aria-hidden="true"
                :style="{
                  background: selectedBiome?.color,
                }"
              ></i>
              <div>
                <span>REGION {{ selectedRegion + 1 }}</span>
                <strong>{{ selectedBiome?.name }}</strong>
              </div>
              <b>{{ game.maxEntry }} cells</b>
            </div>
            <div class="region-cell-meta">
              <span v-if="game.attempt.cellPolygons"
                >Tile {{ (game.selectedCell ?? 0) + 1 }}</span
              >
              <span v-else
                >Row
                {{
                  Math.floor((game.selectedCell ?? 0) / game.attempt.columns) +
                  1
                }}, column
                {{
                  ((game.selectedCell ?? 0) % game.attempt.columns) + 1
                }}</span
              >
              <span>{{
                game.attempt.givens[game.selectedCell ?? -1]
                  ? "Fixed clue"
                  : "Editable cell"
              }}</span>
            </div>
          </div>
          <p v-else class="selected-region">
            Select a cell to inspect its region and enter values.
          </p>
          <button @click="help?.showModal()">Rules and keyboard help</button>
          <div class="play-toolbar">
            <label
              ><input v-model="game.cycleMode" type="checkbox" />Click to change
              cycle (left + / right −)</label
            >
          </div>
          <div class="play-toolbar">
            <button
              :disabled="boardDisabled || !game.attempt.progress.canUndo"
              @click="game.command('UNDO')"
            >
              Undo
            </button>
            <button
              :disabled="boardDisabled || !game.attempt.progress.canRedo"
              @click="game.command('REDO')"
            >
              Redo
            </button>
            <button :disabled="boardDisabled" @click="checkpoint">
              Save checkpoint
            </button>
            <button
              :disabled="boardDisabled || !game.attempt.progress.hasCheckpoint"
              @click="restore"
            >
              Restore checkpoint
            </button>
          </div>
          <p v-if="game.attempt.status === 'COMPLETED'" class="completion">
            Result saved · {{ time }} active ·
            {{ game.attempt.progress.valueEntryCount }} entries ·
            {{ game.attempt.progress.wrongEntryCount }} noncanonical entries.
          </p>
          <p>
            Left/right click changes a value. Mouse wheel over a cell writes a
            note that does not count as a click. Digits enter values; Delete
            clears; arrows move selection.
          </p>
        </aside>
      </div>
      <dialog
        ref="win"
        class="win-dialog"
        aria-labelledby="win-title"
        @click.self="win?.close()"
      >
        <div class="win-confetti" aria-hidden="true">
          <i
            v-for="(piece, index) in confetti"
            :key="index"
            :style="{
              left: piece.left,
              background: piece.color,
              animationDelay: piece.delay,
              animationDuration: piece.duration,
              '--turn': piece.turn,
            }"
          ></i>
        </div>
        <div class="win-card">
          <div class="win-trophy" aria-hidden="true">★</div>
          <p class="win-kicker">Puzzle solved</p>
          <h2 id="win-title">Well played!</h2>
          <p class="win-board">
            {{ boardName }}
            <template v-if="game.attempt.puzzleSettings">
              · {{ game.attempt.puzzleSettings.difficulty }} · max region
              {{ game.attempt.puzzleSettings.maxRegionSize }}
            </template>
          </p>
          <dl class="win-stats">
            <div>
              <dt>Time</dt>
              <dd>{{ time }}</dd>
            </div>
            <div>
              <dt>Accuracy</dt>
              <dd>
                {{
                  game.attempt.progress.legacyAccuracy === null
                    ? "—"
                    : `${game.attempt.progress.legacyAccuracy}%`
                }}
              </dd>
            </div>
            <div>
              <dt>Clicks</dt>
              <dd>{{ game.attempt.progress.totalClicks }}</dd>
            </div>
            <div>
              <dt>Missed</dt>
              <dd>{{ game.attempt.progress.totalMissClicks }}</dd>
            </div>
          </dl>
          <div class="win-player">
            <span>Player</span>
            <PlayerPicker
              :current="winPlayer"
              :players="winPlayers"
              :disabled="winBusy"
              label="Player for this result"
              @pick="pickWinner"
            />
          </div>
          <p v-if="winMessage" class="win-message" role="status">
            {{ winMessage }}
          </p>
          <div class="win-actions">
            <button class="primary" @click="newPuzzle">New puzzle</button>
            <button @click="win?.close()">Close</button>
          </div>
        </div>
      </dialog>
      <dialog ref="help" class="rules-dialog" aria-labelledby="rules-title">
        <h2 id="rules-title">Reading the atlas</h2>
        <p>
          Every bold-bordered region contains each number from 1 to its number
          of cells exactly once.
          {{
            contactRuleDescription(
              game.attempt.puzzleSettings?.shape ?? "SQUARE",
            )
          }}
        </p>
        <p>
          Terrain is decorative. Two forest regions remain separate when a bold
          line divides them. Numbers with a thick red ring are fixed clues.
        </p>
        <p>
          Left-click a cell to increase its number, right-click to decrease.
          Digits also enter values; Delete, Backspace or 0 clears; arrows move.
          Scroll the mouse wheel over a cell to pencil a small note (up counts
          up, down counts down); notes never count as clicks and vanish when you
          click that cell.
        </p>
        <p>
          Large maps start with readable scrollable cells. Use Fit overview for
          orientation and Fullscreen map to just play.
        </p>
        <form method="dialog"><button>Close help</button></form>
      </dialog>
    </template>
    <div v-else class="empty-board">
      <p>{{ t("noGame") }}</p>
      <RouterLink to="/setup">{{ t("setup") }}</RouterLink>
    </div>
  </section>
</template>
