<script setup lang="ts">
import { useRouter } from "vue-router";
import { computed } from "vue";
import { useGameStore } from "../stores/game";
import { useDataStore } from "../stores/data";
import { difficulties, tileShapes } from "../api";
import { shapeLabels } from "../shapes";
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
    <div class="board-heading">
      <label for="board-preset">BOARD</label>
      <details class="generation-info">
        <summary
          aria-label="How puzzle generation works"
          title="How puzzle generation works"
        >
          i
        </summary>
        <div class="generation-explanation">
          <strong>How your map is made</strong>
          <ol>
            <li>
              Board size sets the number of tiles: Titan has 240. Squares use
              the displayed rows and columns; other tile shapes use the same
              total in a compact arrangement. Bigger maps give you more to
              solve, not automatically harder individual moves.
            </li>
            <li>
              A random seed is the generator's starting number. It guides its
              choices, so the same seed and settings can reproduce a map with
              the same generator version.
            </li>
            <li>
              The generator builds connected regions and a complete, valid
              number filling. A region of five tiles contains 1 to 5, and equal
              numbers cannot touch, even at corners. Max region is a limit, not
              a requirement that every region be that size.
            </li>
            <li>
              It repeatedly reshapes neighbouring regions into random connected
              patches, keeping only changes that still allow a valid filling.
              The final shapes have no fixed width or height. Not every possible
              map is equally likely.
            </li>
            <li>
              It hides numbers according to your difficulty, checking that the
              clues still lead to exactly one solution. Extreme also checks that
              short, step-by-step reasoning can finish the puzzle.
            </li>
            <li>
              A final check proves there is exactly one solution before you
              play. If a try cannot finish its checks, a new seed is tried.
              Large maps can take about a minute in total; if verification
              cannot finish, generation reports failure instead of giving you an
              unchecked puzzle.
            </li>
          </ol>
        </div>
      </details>
    </div>
    <div class="board-choice">
      <select id="board-preset" v-model="game.preset" :disabled="choicesLocked">
        <option value="3x3">Supermini · 3 × 3</option>
        <option value="4x4">Mini · 4 × 4</option>
        <option value="5x5">Small · 5 × 5</option>
        <option value="7x5">Medium · 7 × 5</option>
        <option value="9x5">Big · 9 × 5</option>
        <option value="15x10">Uber · 15 × 10</option>
        <option value="20x12">Titan · 20 × 12</option>
      </select>
    </div>
    <label
      ><span>TILES</span
      ><select v-model="game.shape" :disabled="choicesLocked">
        <option v-for="shape in tileShapes" :key="shape" :value="shape">
          {{ shapeLabels[shape] }}
        </option>
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
      ><span>DIFFICULTY</span
      ><select v-model="game.difficulty" :disabled="choicesLocked">
        <option
          v-for="difficulty in difficulties"
          :key="difficulty"
          :value="difficulty"
        >
          {{ difficulty.charAt(0) + difficulty.slice(1).toLowerCase() }}
        </option>
      </select></label
    >
    <p v-if="game.difficulty === 'EASY'">
      More starting numbers to help you get going: the generator aims to leave
      about 35% of the tiles filled. Every puzzle has exactly one solution.
    </p>
    <p v-else-if="game.difficulty === 'MEDIUM'">
      A little less guidance: about 30% of the tiles start filled, leaving more
      connections for you to work out. Every puzzle has exactly one solution.
    </p>
    <p v-else-if="game.difficulty === 'HARD'">
      Fewer starting numbers: about 24% of the tiles start filled. You may need
      to test possibilities and follow their consequences. Every puzzle has
      exactly one solution.
    </p>
    <p v-else-if="game.difficulty === 'EXTREME'">
      As few clues as we can keep while checking a step-by-step way to solve it.
      Sometimes you must reason, "If this tile were 3, something would become
      impossible, so it cannot be 3." Each such check uses at most 10 forced
      placements, without making another guess inside it. Exactly one solution
      is guaranteed.
    </p>
    <p v-else-if="game.difficulty === 'GODLIKE'">
      Clues are removed until removing any one more would allow several
      solutions. There is still exactly one answer, but finding it may mean
      following a long "what if?" chain, including testing another possibility
      inside your first one. Unlike Extreme, there is no short-reasoning
      guarantee.
    </p>
    <p v-if="['EASY', 'MEDIUM', 'HARD'].includes(game.difficulty)">
      These percentages are targets: extra clues stay if removing them would
      allow several answers. They control starting help, not a guaranteed
      solving technique or an exact measure of how hard a map will feel.
    </p>
    <p v-if="game.difficulty === 'EXTREME' || game.difficulty === 'GODLIKE'">
      No clue is individually removable under this mode's rules: each remaining
      clue protects either the single answer or, in Extreme, the checked
      step-by-step solving path. A different choice of clues might use fewer;
      these modes can sometimes give the same puzzle. Large maps may need
      retries for about a minute.
    </p>
    <button
      class="primary-button"
      :disabled="!game.connected || game.pending"
      @click="generate"
    >
      {{
        game.pending
          ? `WORKING... ${game.job?.progressPercent ?? 0}% · ${Math.floor((game.job?.elapsedMillis ?? 0) / 1000)}s${(game.job?.tryNumber ?? 1) > 1 ? ` · try ${game.job?.tryNumber}` : ""}`
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

<style scoped>
.board-heading {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.board-heading > label {
  margin-bottom: 0;
}
.generation-info {
  position: relative;
}
.generation-info > summary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: 1px solid currentColor;
  border-radius: 50%;
  font-size: 12px;
  cursor: pointer;
  list-style: none;
}
.generation-info > summary::-webkit-details-marker {
  display: none;
}
.generation-explanation {
  position: absolute;
  z-index: 5;
  top: 26px;
  left: -65px;
  width: min(460px, calc(100vw - 100px));
  max-height: 60vh;
  overflow: auto;
  padding: 18px;
  border: 1px solid var(--line-color);
  border-radius: 8px;
  background: var(--panel-background);
  box-shadow: 0 8px 24px #0004;
  font-size: 14px;
  line-height: 1.5;
}
.generation-explanation ol {
  margin-bottom: 0;
  padding-left: 20px;
}
.generation-explanation li + li {
  margin-top: 10px;
}
</style>
