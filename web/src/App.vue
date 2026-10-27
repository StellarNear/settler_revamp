<script setup lang="ts">
import { onMounted, onUnmounted } from "vue";
import { useI18n } from "vue-i18n";
import { useGameStore } from "./stores/game";

const game = useGameStore();
const { t } = useI18n();
let refreshTimer: number | undefined;
onMounted(() => {
  void game.connect();
  refreshTimer = window.setInterval(() => void game.refreshAttempt(), 5_000);
});
onUnmounted(() => window.clearInterval(refreshTimer));
</script>

<template>
  <div class="shell">
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
    </div>
    <main id="content" tabindex="-1"><RouterView /></main>
    <footer>
      <span>ALL MOVES ARE VALIDATED AND SAVED BY JAVA.</span
      ><span>LOCAL JAVA REQUIRED.</span>
    </footer>
  </div>
</template>
