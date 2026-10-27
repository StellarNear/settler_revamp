<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useDataStore } from "../stores/data";
import type { LocalSettings } from "../dataApi";
const { t } = useI18n();
const data = useDataStore();
const draft = ref<LocalSettings>();
watch(
  () => data.settings,
  (settings) => {
    if (settings) draft.value = { ...settings };
  },
  { immediate: true },
);
onMounted(() => void data.load());
</script>
<template>
  <section class="page-panel">
    <h2>{{ t("settings") }}</h2>
    <p v-if="data.pending" role="status">Loading or saving settings...</p>
    <p v-if="data.error" role="alert" class="error">{{ data.error }}</p>
    <p v-if="data.message" role="status">{{ data.message }}</p>
    <button :disabled="data.pending" @click="data.load">Reload settings</button>
    <form v-if="draft" class="data-form" @submit.prevent="data.save(draft)">
      <label
        >{{ t("language")
        }}<select v-model="draft.locale" :disabled="data.pending">
          <option value="en">English</option>
          <option value="de">Deutsch</option>
        </select></label
      >
      <label
        >Theme<select v-model="draft.theme" :disabled="data.pending">
          <option value="dark">Dark</option>
          <option value="light">Light</option>
          <option value="high-contrast">High contrast</option>
        </select></label
      >
      <label
        ><input v-model="draft.reducedDetail" type="checkbox" />Reduced
        detail</label
      >
      <label
        ><input v-model="draft.showConflicts" type="checkbox" />Continuous
        conflicts for new attempts (assisted)</label
      >
      <label
        >Default board<select v-model="draft.preset">
          <option
            v-for="size in [
              '3x3',
              '4x4',
              '5x5',
              '7x5',
              '9x5',
              '15x10',
              '20x12',
            ]"
            :key="size"
          >
            {{ size }}
          </option>
        </select></label
      >
      <label
        >Default maximum region<select v-model.number="draft.maxRegionSize">
          <option v-for="size in [5, 6, 7, 8, 9]" :key="size" :value="size">
            {{ size }}
          </option>
        </select></label
      >
      <label
        >Default clue density<select v-model="draft.difficulty">
          <option>EASY</option>
          <option>MEDIUM</option>
          <option>HARD</option>
        </select></label
      >
      <label
        >Current player (empty or 1–3 letters)<input
          v-model="draft.playerTag"
          maxlength="3"
          pattern="[A-Za-z]{0,3}"
      /></label>
      <button type="submit" :disabled="data.pending">Save settings</button>
      <p>
        Defaults apply to new puzzles only. Existing puzzle metadata and
        assistance history never change. The selected player is attached when a
        result completes; history tags can be edited separately.
      </p>
    </form>
  </section>
</template>
