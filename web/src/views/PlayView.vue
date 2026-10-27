<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { onBeforeRouteLeave } from "vue-router";
import { useI18n } from "vue-i18n";
import { useGameStore } from "../stores/game";
import { useDataStore } from "../stores/data";
import TerrainBoard from "../components/TerrainBoard.vue";
import { assignRegionBiomes, biomes } from "../terrain";
const game = useGameStore();
const data = useDataStore();
const terrainBoard = ref<InstanceType<typeof TerrainBoard>>();
const selectedRegion = computed(
  () => game.attempt?.regionIds[game.selectedCell ?? -1],
);
const selectedBiome = computed(() => {
  const attempt = game.attempt;
  const region = selectedRegion.value;
  if (!attempt || region === undefined) return undefined;
  const biome = assignRegionBiomes(
    attempt.columns,
    attempt.rows,
    attempt.regionIds,
  ).get(region);
  return biome === undefined ? undefined : biomes[biome];
});
const help = ref<HTMLDialogElement>();
const { t } = useI18n();
const time = computed(() => {
  const seconds = Math.floor(game.elapsedMillis / 1_000);
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
});
const boardDisabled = computed(() => !game.editable);

function select(index: number, event: MouseEvent) {
  game.selectedCell = index;
  if (game.cycleMode && event.detail > 0) void game.cycleValue(1);
}
function secondary(index: number) {
  game.selectedCell = index;
  if (game.cycleMode) void game.cycleValue(-1);
}
async function onKeydown(event: KeyboardEvent) {
  if (event.target instanceof HTMLElement && event.target.closest("dialog"))
    return;
  if (event.key === "Escape") {
    game.noteMode = false;
    return;
  }
  if (
    event.target instanceof HTMLElement &&
    (event.target.matches("input,select,textarea") ||
      event.target.isContentEditable)
  )
    return;
  if (event.ctrlKey || event.metaKey || event.altKey || event.repeat) return;
  if (/^[1-9]$/.test(event.key)) {
    event.preventDefault();
    void game.enterValue(Number(event.key));
  } else if (["Backspace", "Delete", "0"].includes(event.key)) {
    event.preventDefault();
    void game.command("SET_VALUE", 0);
  } else if (event.key.toLowerCase() === "n") {
    event.preventDefault();
    game.noteMode = !game.noteMode;
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
      "Restore checkpoint values and notes? Time and assistance will not be reset. You can undo this restore.",
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
function reveal() {
  if (
    window.confirm(
      "Reveal this cell's answer? This assistance will be recorded permanently for this attempt.",
    )
  ) {
    void game.command("REVEAL");
  }
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
          >{{ game.attempt.columns }} × {{ game.attempt.rows }} ·
          {{ game.attempt.status }}
          {{ game.attempt.progress.replay ? " · REPLAY" : "" }}</span
        >
        <span
          >REV {{ game.attempt.revision }} ·
          {{ game.attempt.moveCount }} ENTRIES ·
          <time aria-label="Active elapsed time">{{ time }}</time></span
        >
      </div>
      <p v-if="game.pending" role="status">
        Saving to Java... additional input is not queued.
      </p>
      <div class="play-toolbar">
        <RouterLink to="/setup">New puzzle</RouterLink>
        <button
          :disabled="!game.connected || game.pending || game.outcomeUnknown"
          @click="restart"
        >
          Restart this puzzle
        </button>
        <button
          v-if="game.connected && !game.ownsEditor"
          :disabled="game.pending"
          @click="game.takeControl"
        >
          Take control of this game
        </button>
        <button
          v-if="game.attempt.status === 'SUSPENDED'"
          :disabled="
            !game.connected ||
            !game.ownsEditor ||
            game.pending ||
            game.outcomeUnknown
          "
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
          @select="select"
          @secondary="secondary"
          @focus-cell="game.selectedCell = $event"
        />
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
              <span
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
              ><input v-model="game.noteMode" type="checkbox" />Manual notes
              (N)</label
            >
            <label
              ><input v-model="game.cycleMode" type="checkbox" />Click to change
              cycle (left + / right −)</label
            >
            <label
              ><input
                type="checkbox"
                :checked="game.attempt.progress.showConflicts"
                :disabled="boardDisabled"
                @change="
                  game.command(
                    'SHOW_CONFLICTS',
                    ($event.target as HTMLInputElement).checked ? 1 : 0,
                  )
                "
              />Continuous conflicts (assisted)</label
            >
          </div>
          <div class="number-pad" aria-label="Number entry">
            <button
              v-for="number in game.maxEntry"
              :key="number"
              :aria-label="`${game.noteMode ? 'Toggle note' : 'Enter'} ${number}`"
              :disabled="
                boardDisabled ||
                game.selectedCell === undefined ||
                game.attempt.givens[game.selectedCell] !== 0
              "
              @click="game.enterValue(number)"
            >
              {{ number }}
            </button>
            <button
              class="clear-button"
              aria-label="Clear selected cell"
              :disabled="
                boardDisabled ||
                !game.selectedValue ||
                game.attempt.givens[game.selectedCell ?? -1] !== 0
              "
              @click="game.command('SET_VALUE', 0)"
            >
              CLEAR
            </button>
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
            <button :disabled="boardDisabled" @click="game.command('CHECK')">
              Check conflicts
            </button>
            <button
              :disabled="
                boardDisabled ||
                game.selectedCell === undefined ||
                game.attempt.givens[game.selectedCell] !== 0
              "
              @click="game.command('CANDIDATES')"
            >
              Show candidates
            </button>
            <button :disabled="boardDisabled" @click="game.command('EXPLAIN')">
              Explain a single
            </button>
            <button
              :disabled="
                boardDisabled ||
                game.selectedCell === undefined ||
                game.attempt.givens[game.selectedCell] !== 0
              "
              @click="reveal"
            >
              Reveal selected value
            </button>
          </div>
          <p v-if="game.attempt.progress.helpMessage" class="help-message">
            {{ game.attempt.progress.helpMessage }}
          </p>
          <p v-if="game.attempt.progress.candidates.length">
            Server candidates:
            {{ game.attempt.progress.candidates.join(", ") }}. These are not
            manual notes.
          </p>
          <p class="assistance">
            Assistance: {{ game.attempt.progress.candidateRequests }} candidate
            requests,
            {{ game.attempt.progress.explanationRequests }} explanations,
            {{ game.attempt.progress.reveals }} reveals
            {{
              game.attempt.progress.continuousConflictsUsed
                ? " · continuous conflicts used"
                : ""
            }}.
          </p>
          <p v-if="game.attempt.progress.legacyAccuracy !== null">
            Click accuracy: {{ game.attempt.progress.legacyAccuracy }}% ({{
              game.attempt.progress.totalClicks
            }}
            clicks, {{ game.attempt.progress.totalMissClicks }} non-optimal)
          </p>
          <p v-else>
            Click accuracy is unavailable after direct value entry; the
            completed result will retain the entry/error counts instead.
          </p>
          <p v-if="game.attempt.status === 'COMPLETED'" class="completion">
            Result saved · {{ time }} active ·
            {{ game.attempt.progress.valueEntryCount }} entries ·
            {{ game.attempt.progress.wrongEntryCount }} noncanonical entries.
          </p>
          <p>
            Digits enter values; Delete clears; arrows move selection. Select a
            given to inspect it; it cannot be edited.
          </p>
        </aside>
      </div>
      <dialog ref="help" class="rules-dialog" aria-labelledby="rules-title">
        <h2 id="rules-title">Reading the atlas</h2>
        <p>
          Every bold-bordered region contains each number from 1 to its number
          of cells exactly once. Equal numbers cannot touch, including
          diagonally.
        </p>
        <p>
          Terrain is decorative. Two forest regions remain separate when a bold
          line divides them. Underlined numbers are fixed clues. A cell marked !
          has a server-reported duplicate conflict, not necessarily every wrong
          answer.
        </p>
        <p>
          Click/tap selects; digits enter; Delete, Backspace or 0 clears; arrows
          move; Home/End reach the first/last cell in a row. Tab exits the grid.
          N toggles manual notes; Escape exits note mode. Notes are not computed
          candidates.
        </p>
        <p>
          Large maps start with readable scrollable cells. Fit overview is for
          orientation; use Zoom to selection for precise entry. Browser zoom and
          touch scrolling remain available.
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
