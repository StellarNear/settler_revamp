<script setup lang="ts">
import { useRouter } from "vue-router";
import { computed } from "vue";
import { useGameStore } from "../stores/game";
import { useDataStore } from "../stores/data";
const game = useGameStore();
const router = useRouter();
const data = useDataStore();
// Saved defaults arrive after connecting; editing earlier would be silently overwritten.
const choicesLocked = computed(
  () => game.pending || (game.connected && !data.settings && !data.error),
);
async function generate() {
  if (await game.startPuzzle()) await router.push("/play");
}
</script>

<template>
  <section class="setup setup-page" aria-labelledby="setup-title">
    <h2 id="setup-title">New puzzle</h2>
    <label
      ><span>BOARD</span
      ><select v-model="game.preset" :disabled="choicesLocked">
        <option value="3x3">Supermini · 3 × 3</option>
        <option value="4x4">Mini · 4 × 4</option>
        <option value="5x5">Small · 5 × 5</option>
        <option value="7x5">Medium · 7 × 5</option>
        <option value="9x5">Big · 9 × 5</option>
        <option value="15x10">Uber · 15 × 10</option>
        <option value="20x12">Titan · 20 × 12</option>
      </select></label
    >
    <label
      ><span>MAX REGION</span
      ><select v-model.number="game.maxRegionSize" :disabled="choicesLocked">
        <option v-for="size in [5, 6, 7, 8, 9]" :key="size" :value="size">
          {{ size }} cells
        </option>
      </select></label
    >
    <label
      ><span>CLUE DENSITY</span
      ><select v-model="game.difficulty" :disabled="choicesLocked">
        <option value="EASY">Easy</option>
        <option value="MEDIUM">Medium</option>
        <option value="HARD">Hard</option>
      </select></label
    >
    <button
      class="primary-button"
      :disabled="!game.connected || game.pending"
      @click="generate"
    >
      {{
        game.pending
          ? `WORKING... ${game.job?.progressPercent ?? 0}%`
          : "GENERATE PUZZLE"
      }}
    </button>
    <button
      v-if="game.job"
      class="text-button"
      :disabled="game.cancelling || !game.connected"
      @click="game.cancelGeneration"
    >
      {{ game.cancelling ? "Cancelling..." : "Cancel generation" }}
    </button>
    <RouterLink v-if="game.attempt" to="/play">Return to saved game</RouterLink>
  </section>
</template>
