<script setup lang="ts">
import {
  computed,
  nextTick,
  onMounted,
  onUnmounted,
  ref,
  useId,
  watch,
} from "vue";
import type { AttemptSnapshot } from "../api";
import { biomes, terrainGeometry } from "../terrain";

const props = defineProps<{
  attempt: AttemptSnapshot;
  selected?: number;
  disabled: boolean;
  reducedDetail: boolean;
}>();
const emit = defineEmits<{
  select: [index: number, event: MouseEvent];
  secondary: [index: number];
  focusCell: [index: number];
}>();
const id = useId().replace(/:/g, "");
const viewport = ref<HTMLDivElement>();
const board = ref<HTMLDivElement>();
const size = ref(44);
const fitted = ref(false);
const available = ref(0);
const geometry = computed(() =>
  terrainGeometry(
    props.attempt.columns,
    props.attempt.rows,
    props.attempt.regionIds,
  ),
);
const selectedRegion = computed(() =>
  props.selected === undefined
    ? undefined
    : props.attempt.regionIds[props.selected],
);
const selectedBoundary = computed(
  () =>
    geometry.value.regions.find((region) => region.id === selectedRegion.value)
      ?.boundary,
);
const cellSize = computed(() =>
  fitted.value
    ? Math.min(44, Math.max(12, (available.value - 8) / props.attempt.columns))
    : size.value,
);
const rows = computed(() =>
  Array.from({ length: props.attempt.rows }, (_, row) =>
    Array.from(
      { length: props.attempt.columns },
      (_, column) => row * props.attempt.columns + column,
    ),
  ),
);
let observer: ResizeObserver | undefined;
onMounted(() => {
  observer = new ResizeObserver((entries) => {
    available.value = entries[0].contentRect.width;
  });
  if (viewport.value) observer.observe(viewport.value);
});
onUnmounted(() => observer?.disconnect());
watch(
  () => props.attempt.attemptId,
  () => {
    fitted.value = false;
    size.value = 44;
  },
);
function noteValues(index: number) {
  const mask = props.attempt.progress.notes[index];
  return Array.from({ length: 9 }, (_, i) => (mask & (1 << i) ? i + 1 : null));
}
function label(index: number) {
  const current = props.attempt;
  const notes = noteValues(index)
    .filter((value) => value !== null)
    .join(", ");
  return `Row ${Math.floor(index / current.columns) + 1}, column ${(index % current.columns) + 1}, region ${current.regionIds[index] + 1}, ${current.givens[index] ? "given" : "editable"}, ${current.values[index] ? `value ${current.values[index]}` : "empty"}${notes ? `, notes ${notes}` : ""}${current.progress.conflicts.includes(index) ? ", conflict" : ""}`;
}
async function focus(index: number) {
  await nextTick();
  const cell = board.value?.querySelectorAll<HTMLButtonElement>(".cell")[index];
  cell?.focus({ preventScroll: true });
  cell?.scrollIntoView({ block: "nearest", inline: "nearest" });
}
async function zoomSelection() {
  fitted.value = false;
  size.value = 44;
  emit("focusCell", props.selected ?? 0);
  await focus(props.selected ?? 0);
}
function zoom(delta: number) {
  fitted.value = false;
  size.value = Math.min(72, Math.max(36, size.value + delta));
}
watch(
  () => props.selected,
  async (index) => {
    if (
      index !== undefined &&
      document.activeElement?.classList.contains("cell")
    )
      await focus(index);
  },
);
defineExpose({ focus });
</script>
<template>
  <div class="atlas-sheet">
    <div class="viewport-controls" aria-label="Board viewport controls">
      <button
        :disabled="!fitted && size <= 36"
        aria-label="Zoom out board"
        @click="zoom(-8)"
      >
        −
      </button>
      <button
        :disabled="!fitted && size >= 72"
        aria-label="Zoom in board"
        @click="zoom(8)"
      >
        +
      </button>
      <button :aria-pressed="fitted" @click="fitted = !fitted">
        {{ fitted ? "Readable size" : "Fit overview" }}
      </button>
      <button @click="zoomSelection">Zoom to selection</button>
    </div>
    <p class="viewport-guidance">
      {{
        fitted
          ? "Overview: cells may be small. Zoom to selection for precise entry."
          : "Readable cells. Scroll the board in either direction; arrows move selection."
      }}
    </p>
    <div
      ref="viewport"
      class="board-viewport"
      tabindex="0"
      aria-label="Scrollable puzzle viewport"
    >
      <div
        ref="board"
        class="board"
        :class="{ 'reduced-terrain': reducedDetail, 'fit-overview': fitted }"
        :style="{
          '--columns': attempt.columns,
          '--rows': attempt.rows,
          '--cell-size': `${cellSize}px`,
        }"
        role="grid"
        :aria-rowcount="attempt.rows"
        :aria-colcount="attempt.columns"
        aria-label="Tiwanaku number grid"
      >
        <svg
          class="terrain-layer"
          :viewBox="`0 0 ${attempt.columns} ${attempt.rows}`"
          preserveAspectRatio="none"
          aria-hidden="true"
          focusable="false"
        >
          <defs>
            <pattern
              v-for="(biome, index) in biomes"
              :id="`${id}-biome-${index}`"
              :key="biome.name"
              patternUnits="userSpaceOnUse"
              width="1.7"
              height="1.3"
            >
              <g
                :stroke="biome.ink"
                stroke-width=".026"
                fill="none"
                stroke-linecap="round"
                stroke-linejoin="round"
                opacity=".76"
              >
                <template v-if="index === 0">
                  <path
                    :fill="biome.ink"
                    opacity=".28"
                    d="M.35 .32 .25 .55 .31 .53 .18 .74 .25 .72 .07 .96 .63 .96 .45 .72 .52 .74 .39 .53 .45 .55ZM1.12 .59 1.02 .78 1.08 .76 .95 .94 1.02 .92 .84 1.16 1.4 1.16 1.22 .92 1.29 .94 1.16 .76 1.22 .78Z"
                  />
                  <path
                    fill="none"
                    :stroke="biome.color"
                    stroke-width=".035"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    d="M.35 .4 .27 .55M.35 .4 .43 .55M.35 .53 .24 .69M.35 .53 .46 .69M.35 .69 .15 .9M.35 .69 .55 .9M1.12 .67 1.04 .79M1.12 .67 1.2 .79M1.12 .8 1.01 .9M1.12 .8 1.23 .9M1.12 .95 .92 1.1M1.12 .95 1.32 1.1"
                  />
                  <path
                    fill="none"
                    :stroke="biome.ink"
                    stroke-width=".028"
                    d="M.35 .92v.12M1.12 1.12v.1M.03 .99h.67M.85 1.19h.55"
                  />
                </template>
                <path
                  v-if="index === 1"
                  d="M.1 .48Q.45 .12.82 .36T1.58 .38M.2 .61Q.55 .25.92 .49T1.48 .51M.25 1.13q.4-.35.8 0"
                />
                <path
                  v-if="index === 2"
                  d="m.08 .66.34-.48.35.48M.25 .43l.17.06.12-.13M.91 1.2l.3-.51.36.51M1.08 .98l.13.04.12-.12"
                />
                <path
                  v-if="index === 3"
                  d="M.1 .27q.18-.13.36 0t.36 0t.36 0M.25 .43q.18-.13.36 0t.36 0M.35 .94q.18-.13.36 0t.36 0t.36 0M.5 1.1q.18-.13.36 0t.36 0"
                />
                <template v-if="index === 1">
                  <path
                    :fill="biome.ink"
                    opacity=".14"
                    d="M-.1 .72Q.27 .22 .68 .58T1.55 .43V.78Q1.12 .91 .73 .78T-.1 1.02Z"
                  />
                  <circle cx=".28" cy="1.05" r=".018" :fill="biome.ink" />
                  <circle cx="1.1" cy=".92" r=".018" :fill="biome.ink" />
                  <circle cx="1.34" cy="1.25" r=".018" :fill="biome.ink" />
                </template>
                <template v-if="index === 2">
                  <path
                    :fill="biome.ink"
                    opacity=".16"
                    d="m.08 .66 .34-.48 .35 .48-.2-.23-.15 .06-.12-.1ZM.91 1.2l.3-.51.36 .51-.2-.21-.14 .05-.13-.1Z"
                  />
                  <path d="M.42 .47l.17 .06 .12-.13M1.08 .98l.13 .04 .12-.12" />
                </template>
                <template v-if="index === 3">
                  <path
                    :fill="biome.ink"
                    opacity=".14"
                    d="M.1 .27q.18-.13.36 0t.36 0t.36 0t.36 0v.14q-.18-.13-.36 0t-.36 0t-.36 0t-.36 0Z"
                  />
                  <path d="M.24 .68l.08 .04M1.08 .72l.08 .04M1.35 .2l.08 .04" />
                </template>
              </g>
            </pattern>
          </defs>
          <g
            v-for="region in geometry.regions"
            :key="region.id"
            class="terrain-region"
            :data-region="region.id"
            :data-biome="biomes[region.biome].name"
          >
            <path
              class="biome-fill"
              :d="region.path"
              :fill="biomes[region.biome].color"
            />
            <path
              class="biome-texture"
              :d="region.path"
              :fill="`url(#${id}-biome-${region.biome})`"
            />
          </g>
          <path class="cell-separators" :d="geometry.separators" fill="none" />
          <path
            class="region-boundary-halo"
            :d="geometry.boundaries"
            fill="none"
          />
          <path
            class="region-boundaries"
            :d="geometry.boundaries"
            fill="none"
          />
          <path
            v-if="selectedBoundary"
            class="selected-region-boundary-halo"
            :d="selectedBoundary"
            fill="none"
          />
          <path
            v-if="selectedBoundary"
            class="selected-region-boundary"
            :d="selectedBoundary"
            fill="none"
          />
        </svg>
        <div
          v-for="(row, rowIndex) in rows"
          :key="rowIndex"
          role="row"
          class="board-row"
        >
          <div
            v-for="index in row"
            :key="index"
            role="gridcell"
            :aria-rowindex="rowIndex + 1"
            :aria-colindex="(index % attempt.columns) + 1"
          >
            <button
              class="cell"
              :class="{
                given: attempt.givens[index] !== 0,
                selected: selected === index,
                conflict: attempt.progress.conflicts.includes(index),
              }"
              :aria-label="label(index)"
              :aria-pressed="selected === index"
              :tabindex="index === (selected ?? 0) ? 0 : -1"
              :disabled="disabled"
              @focus="emit('focusCell', index)"
              @click="emit('select', index, $event)"
              @contextmenu.prevent="emit('secondary', index)"
            >
              <span
                v-if="attempt.values[index]"
                class="cell-value"
                :class="`cell-value-${attempt.values[index]}`"
                >{{ attempt.values[index] }}</span
              >
              <span v-else class="manual-notes"
                ><span v-for="(value, slot) in noteValues(index)" :key="slot">{{
                  value
                }}</span></span
              >
              <span
                v-if="attempt.progress.conflicts.includes(index)"
                class="conflict-mark"
                aria-hidden="true"
                >!</span
              >
            </button>
          </div>
        </div>
      </div>
    </div>
    <p class="atlas-legend">
      <span v-for="biome in biomes" :key="biome.name"
        ><i :style="{ background: biome.color }"></i>{{ biome.name }}</span
      >
    </p>
    <p class="atlas-caption">
      Outlines trace each connected region. Red rings mark fixed clues; terrain
      is decorative and never changes the rules.
    </p>
  </div>
</template>
