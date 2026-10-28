<script setup lang="ts">
import { computed, ref } from "vue";
import { playerTagPattern } from "../dataApi";

const props = defineProps<{
  current: string;
  players: string[];
  disabled?: boolean;
  label?: string;
}>();
const emit = defineEmits<{ pick: [tag: string] }>();
const NEW = "__new__";
const adding = ref(false);
const draft = ref("");
const options = computed(() =>
  [...new Set([...props.players, props.current.toUpperCase()])].filter((tag) =>
    playerTagPattern.test(tag),
  ),
);
const draftValid = computed(() => playerTagPattern.test(draft.value));
function change(event: Event) {
  const value = (event.target as HTMLSelectElement).value;
  if (value === NEW) {
    adding.value = true;
    draft.value = "";
    return;
  }
  adding.value = false;
  emit("pick", value);
}
function confirmNew() {
  if (!draftValid.value) return;
  emit("pick", draft.value.toUpperCase());
  adding.value = false;
}
</script>

<template>
  <span class="player-picker">
    <select
      :aria-label="label ?? 'Player'"
      :value="adding ? NEW : current.toUpperCase()"
      :disabled="disabled"
      @change="change"
    >
      <option value="">N/A</option>
      <option v-for="tag in options" :key="tag" :value="tag">{{ tag }}</option>
      <option :value="NEW">+ Add new player…</option>
    </select>
    <form v-if="adding" class="player-new" @submit.prevent="confirmNew">
      <input
        v-model="draft"
        aria-label="New player tag"
        maxlength="3"
        placeholder="ABC"
        autofocus
      />
      <button :disabled="disabled || !draftValid">Add</button>
      <button type="button" @click="adding = false">Cancel</button>
    </form>
  </span>
</template>
