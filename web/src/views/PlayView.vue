<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { useGameStore } from "../stores/game";
const game = useGameStore();
const { t } = useI18n();
function onKeydown(event: KeyboardEvent) {
  if (
    event.ctrlKey ||
    event.metaKey ||
    event.altKey ||
    !/^[1-9]$/.test(event.key)
  )
    return;
  event.preventDefault();
  void game.enterValue(Number(event.key));
}
</script>

<template>
  <section class="board-area" aria-label="Puzzle board" @keydown="onKeydown">
    <template v-if="game.attempt">
      <div class="board-meta">
        <span
          >{{ game.attempt.columns }} × {{ game.attempt.rows }} ·
          {{ game.attempt.status }}</span
        >
        <span
          >REV {{ game.attempt.revision }} ·
          {{ game.attempt.moveCount }} MOVES</span
        >
      </div>
      <button
        v-if="game.connected && !game.ownsEditor"
        class="text-button"
        :disabled="game.pending"
        @click="game.takeControl"
      >
        Take control of this game
      </button>
      <div class="board" :style="{ '--columns': game.attempt.columns }">
        <button
          v-for="(value, index) in game.attempt.values"
          :key="index"
          class="cell"
          :class="{
            given: game.attempt.givens[index] !== 0,
            selected: game.selectedCell === index,
            'region-edge-right':
              index % game.attempt.columns < game.attempt.columns - 1 &&
              game.attempt.regionIds[index] !==
                game.attempt.regionIds[index + 1],
            'region-edge-bottom':
              index < game.attempt.values.length - game.attempt.columns &&
              game.attempt.regionIds[index] !==
                game.attempt.regionIds[index + game.attempt.columns],
          }"
          :aria-label="`Row ${Math.floor(index / game.attempt.columns) + 1}, column ${(index % game.attempt.columns) + 1}, ${value ? `value ${value}` : 'empty'}`"
          :aria-pressed="game.selectedCell === index"
          :disabled="
            !game.connected ||
            game.pending ||
            game.attempt.givens[index] !== 0 ||
            game.attempt.status === 'COMPLETED'
          "
          @click="game.selectedCell = index"
        >
          {{ value || "" }}
        </button>
      </div>
      <div class="number-pad" aria-label="Number entry">
        <button
          v-for="number in game.maxEntry"
          :key="number"
          :aria-label="`Enter ${number}`"
          :disabled="
            !game.connected ||
            !game.ownsEditor ||
            game.pending ||
            game.attempt.status === 'COMPLETED'
          "
          @click="game.enterValue(number)"
        >
          {{ number }}
        </button>
        <button
          class="clear-button"
          aria-label="Clear selected cell"
          :disabled="
            !game.connected ||
            !game.ownsEditor ||
            game.pending ||
            !game.selectedValue ||
            game.attempt.status === 'COMPLETED'
          "
          @click="game.enterValue(0)"
        >
          CLEAR
        </button>
      </div>
    </template>
    <div v-else class="empty-board">
      <p>{{ t("noGame") }}</p>
      <RouterLink to="/setup">{{ t("setup") }}</RouterLink>
    </div>
  </section>
</template>
