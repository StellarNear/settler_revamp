<script setup lang="ts">
import { onMounted, onUnmounted, watch } from "vue";
import { useI18n } from "vue-i18n";
import { useGameStore } from "./stores/game";
import { useDataStore } from "./stores/data";
import { useRoute } from "vue-router";

const route = useRoute();
const game = useGameStore();
const data = useDataStore();
const { t, locale } = useI18n();
watch(
  () => game.connected,
  (connected) => {
    if (connected) void data.load();
  },
);
watch(
  () => data.settings,
  (settings) => {
    if (!settings) return;
    locale.value = settings.locale;
    document.documentElement.lang = settings.locale;
    document.documentElement.dataset.theme = settings.theme;
    document.documentElement.dataset.reducedDetail = String(
      settings.reducedDetail,
    );
    game.preset = settings.preset;
    game.maxRegionSize = settings.maxRegionSize;
    game.difficulty = settings.difficulty;
  },
);
let refreshTimer: number | undefined;
let displayTimer: number | undefined;
onMounted(() => {
  void game.connect();
  refreshTimer = window.setInterval(() => void game.heartbeat(), 5_000);
  displayTimer = window.setInterval(game.tick, 1_000);
});
onUnmounted(() => {
  window.clearInterval(refreshTimer);
  window.clearInterval(displayTimer);
});
</script>

<template>
  <div class="shell" :class="{ 'play-shell': route.path === '/play' }">
    <a class="skip-link" href="#content">{{ t("skip") }}</a>
    <header class="topbar">
      <RouterLink class="wordmark" to="/setup" aria-label="Tiwanaku home"
        >TIWANAKU<span> / LOCAL</span></RouterLink
      >
      <div class="connection" :class="{ offline: !game.connected }">
        <span class="connection-dot"></span>
        {{ t(game.connected ? "connected" : "disconnected") }}
      </div>
    </header>
    <nav aria-label="Main navigation">
      <RouterLink
        v-for="name in ['setup', 'play', 'history', 'settings']"
        :key="name"
        :to="`/${name}`"
        >{{ t(name) }}</RouterLink
      >
    </nav>
    <section class="intro">
      <div class="eyebrow">A LANDSCAPE OF LOGIC</div>
      <h1>Find the pattern.<br /><em>Shape the whole.</em></h1>
      <p>
        Place one of each number in every connected region.<br />Equal numbers
        cannot touch, even diagonally.
      </p>
    </section>
    <div class="server-status" aria-live="polite">
      <p v-if="data.error" class="error" role="alert">{{ data.error }}</p>
      <p role="status">{{ game.statusMessage }}</p>
      <p v-if="game.errorMessage" class="error" role="alert">
        {{ game.errorMessage }}
      </p>
      <button
        v-if="!game.connected"
        :disabled="game.connecting || game.pending"
        @click="game.connect"
      >
        {{ t(game.connecting ? "connecting" : "reconnect") }}
      </button>
      <button
        v-if="game.outcomeUnknown && game.connected"
        :disabled="game.pending"
        @click="game.resolvePending"
      >
        Resolve pending action (retry same ID)
      </button>
    </div>
    <main id="content" tabindex="-1"><RouterView /></main>
    <footer>
      <span>ALL MOVES ARE VALIDATED AND SAVED BY JAVA.</span
      ><span>LOCAL JAVA REQUIRED.</span>
    </footer>
  </div>
</template>
