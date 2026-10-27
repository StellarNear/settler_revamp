<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted } from "vue";
import { onBeforeRouteLeave } from "vue-router";
import { useI18n } from "vue-i18n";
import { useGameStore } from "../stores/game";
const game = useGameStore();
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
function notes(index: number) {
  const mask = game.attempt?.progress.notes[index] ?? 0;
  return Array.from({ length: 9 }, (_, i) => i + 1)
    .filter((value) => mask & (1 << (value - 1)))
    .join(" ");
}
async function onKeydown(event: KeyboardEvent) {
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
  } else if (event.key.startsWith("Arrow") && game.attempt) {
    event.preventDefault();
    const current = game.selectedCell ?? 0;
    const columns = game.attempt.columns;
    const delta = {
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
    await nextTick();
    document.querySelectorAll<HTMLButtonElement>(".cell")[next]?.focus();
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
      <div class="play-toolbar">
        <label
          ><input v-model="game.noteMode" type="checkbox" />Manual notes
          (N)</label
        >
        <label
          ><input v-model="game.cycleMode" type="checkbox" />Legacy click cycle
          (left + / right −)</label
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
      <div class="board" :style="{ '--columns': game.attempt.columns }">
        <button
          v-for="(value, index) in game.attempt.values"
          :key="index"
          class="cell"
          :class="{
            given: game.attempt.givens[index] !== 0,
            selected: game.selectedCell === index,
            conflict: game.attempt.progress.conflicts.includes(index),
            'region-edge-right':
              index % game.attempt.columns < game.attempt.columns - 1 &&
              game.attempt.regionIds[index] !==
                game.attempt.regionIds[index + 1],
            'region-edge-bottom':
              index < game.attempt.values.length - game.attempt.columns &&
              game.attempt.regionIds[index] !==
                game.attempt.regionIds[index + game.attempt.columns],
          }"
          :aria-label="`Row ${Math.floor(index / game.attempt.columns) + 1}, column ${(index % game.attempt.columns) + 1}, ${value ? `value ${value}` : 'empty'}${game.attempt.givens[index] ? ', given' : ''}${notes(index) ? `, notes ${notes(index)}` : ''}${game.attempt.progress.conflicts.includes(index) ? ', conflict' : ''}`"
          :aria-pressed="game.selectedCell === index"
          :disabled="
            !game.connected ||
            game.attempt.status === 'SUSPENDED' ||
            game.attempt.status === 'COMPLETED'
          "
          @click="select(index, $event)"
          @contextmenu.prevent="secondary(index)"
        >
          <span v-if="value">{{ value }}</span
          ><span v-else class="manual-notes">{{ notes(index) }}</span>
        </button>
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
        Server candidates: {{ game.attempt.progress.candidates.join(", ") }}.
        These are not manual notes.
      </p>
      <p class="assistance">
        Assistance: {{ game.attempt.progress.candidateRequests }} candidate
        requests, {{ game.attempt.progress.explanationRequests }} explanations,
        {{ game.attempt.progress.reveals }} reveals
        {{
          game.attempt.progress.continuousConflictsUsed
            ? " · continuous conflicts used"
            : ""
        }}.
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
    </template>
    <div v-else class="empty-board">
      <p>{{ t("noGame") }}</p>
      <RouterLink to="/setup">{{ t("setup") }}</RouterLink>
    </div>
  </section>
</template>
