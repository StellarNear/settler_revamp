<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import type { z } from "zod";
import {
  archiveSchema,
  assignPlayer,
  dataRequest,
  download,
  historySchema,
  outcomeSchema,
  playersFrom,
  previewSchema,
  sourceSchema,
  type HistoryEntry,
} from "../dataApi";
import { loadBootstrap, HttpError } from "../api";
import { useDataStore } from "../stores/data";
import { formatElapsed } from "../historyPresentation";
import PlayerPicker from "../components/PlayerPicker.vue";
import { shapeLabels } from "../shapes";
import { tileShapes } from "../api";
const { t } = useI18n();
const settings = useDataStore();
const history = ref<z.infer<typeof historySchema>>();
const busy = ref(false),
  error = ref(""),
  message = ref("");
const player = ref(""),
  mapSize = ref(""),
  shape = ref(""),
  sort = ref("date-desc");
const source = ref(""),
  format = ref("legacy"),
  restoreSettings = ref(false);
const preview = ref<z.infer<typeof previewSchema>>();
const playerOptions = ref<string[]>([]);
const pickerPlayers = ref<string[]>([]);
const knownSizes = ref<string[]>([]);
const editing = ref<string>();
const presetSizes = [
  ["3x3", "Tiny · 3 × 3"],
  ["4x4", "Small · 4 × 4"],
  ["5x5", "Compact · 5 × 5"],
  ["7x5", "Medium · 7 × 5"],
  ["9x5", "Large · 9 × 5"],
  ["15x10", "Huge · 15 × 10"],
  ["20x12", "Titan · 20 × 12"],
] as const;
const sizeOptions = computed(() => [
  ...presetSizes.map(([value, label]) => ({ value, label })),
  ...knownSizes.value
    .filter((size) => !presetSizes.some(([value]) => value === size))
    .map((size) => ({ value: size, label: size })),
]);
function accuracy(entry: HistoryEntry) {
  return entry.legacyAccuracy === null ? "—" : `${entry.legacyAccuracy}%`;
}
function boardLabel(size: string) {
  return presetSizes.find(([value]) => value === size)?.[1] ?? size;
}
watch([player, mapSize, shape, sort], () => void refresh());
watch([source, format], () => {
  preview.value = undefined;
});
function failure(reason: unknown, mutation = false) {
  error.value =
    reason instanceof Error ? reason.message : "Local data operation failed.";
  if (mutation && (!(reason instanceof HttpError) || reason.status >= 500))
    error.value +=
      " Outcome may be unknown. Reload history/settings or preview the same source to reconcile; do not assume the action failed.";
}
async function operation(action: () => Promise<void>, mutation = false) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  message.value = "";
  try {
    await action();
  } catch (reason) {
    failure(reason, mutation);
  } finally {
    busy.value = false;
  }
}
async function fetchHistory() {
  const filters = {
    player: player.value,
    mapSize: mapSize.value,
    scoring: "",
    assistance: "",
    sort: sort.value,
    shape: shape.value,
  };
  const query = new URLSearchParams(filters);
  const allPlayersQuery = new URLSearchParams({
    player: "",
    mapSize: "",
    scoring: "",
    assistance: "",
    sort: "date-desc",
  });
  const filteredRequest = dataRequest(
    `/api/v1/history?${query}`,
    historySchema,
  );
  const allPlayersRequest =
    player.value || mapSize.value || shape.value || sort.value !== "date-desc"
      ? dataRequest(`/api/v1/history?${allPlayersQuery}`, historySchema)
      : filteredRequest;
  const [filtered, allPlayers] = await Promise.all([
    filteredRequest,
    allPlayersRequest,
  ]);
  history.value = filtered;
  playerOptions.value = [
    ...new Set(
      allPlayers.entries
        .map((entry) => entry.playerTag?.trim() ?? "")
        .filter(Boolean),
    ),
  ].sort((first, second) => first.localeCompare(second));
  pickerPlayers.value = playersFrom(allPlayers.entries, [
    settings.settings?.playerTag ?? "",
  ]);
  knownSizes.value = [
    ...new Set(allPlayers.entries.map((entry) => entry.mapSize)),
  ].sort();
}
let refreshAgain = false;
async function refresh(): Promise<void> {
  if (busy.value) {
    refreshAgain = true;
    return;
  }
  await operation(fetchHistory);
  if (refreshAgain) {
    refreshAgain = false;
    return refresh();
  }
}
function assign(entry: HistoryEntry, tag: string) {
  editing.value = undefined;
  return operation(async () => {
    await assignPlayer(entry.recordId, tag);
    await fetchHistory();
    message.value = tag ? `Result assigned to ${tag}.` : "Player removed.";
  }, true);
}
function remove(entry: HistoryEntry) {
  if (
    !window.confirm(
      "Delete this history entry? Export first if needed. The completed puzzle and command receipts are retained; this entry will no longer be listed or exported.",
    )
  )
    return;
  return operation(async () => {
    await dataRequest(
      `/api/v1/history/${entry.recordId}?confirmed=true`,
      historySchema,
      { method: "DELETE" },
    );
    await fetchHistory();
    message.value = "History entry deleted.";
  }, true);
}
function exportData() {
  return operation(async () => {
    const archive = await dataRequest("/api/v1/data/export", archiveSchema);
    download(JSON.stringify(archive), `tiwanaku-history-${Date.now()}.json`);
    message.value =
      "History/settings export downloaded. Active attempts require a physical backup.";
  });
}
function physicalBackup() {
  if (
    !window.confirm(
      "Create and download a consistent backup of the entire local database, including active attempts?",
    )
  )
    return;
  return operation(async () => {
    const bootstrap = await loadBootstrap();
    const response = await fetch("/api/v1/data/backup?confirmed=true", {
      method: "POST",
      headers: { "X-Tiwanaku-Token": bootstrap.requestToken },
      signal: AbortSignal.timeout(30_000),
    });
    if (!response.ok)
      throw new Error(
        `Physical backup failed (HTTP ${response.status}). Check server logs and disk space; no successful backup is claimed.`,
      );
    const blob = await response.blob();
    download(blob, `tiwanaku-backup-${Date.now()}.zip`, "application/zip");
    message.value =
      "Physical backup downloaded. Stop Java and restore into a separate directory; never overwrite a live database.";
  }, true);
}
function previewImport() {
  return operation(async () => {
    preview.value = await dataRequest("/api/v1/data/preview", previewSchema, {
      method: "POST",
      body: JSON.stringify({ source: source.value, format: format.value }),
    });
  });
}
function importData() {
  const current = preview.value;
  if (
    !current ||
    !window.confirm(
      `Import ${current.entries.length} valid records and retain the raw source? ${current.rejected.length} rejected records will be reported. ${restoreSettings.value ? "Saved settings will also be replaced." : "Settings will be preserved."}`,
    )
  )
    return;
  return operation(async () => {
    const outcome = await dataRequest("/api/v1/data/import", outcomeSchema, {
      method: "POST",
      body: JSON.stringify({
        source: source.value,
        format: format.value,
        fingerprint: current.fingerprint,
        confirmed: true,
        restoreSettings: restoreSettings.value && format.value === "logical",
      }),
    });
    await fetchHistory();
    if (restoreSettings.value) await settings.load();
    message.value = `${outcome.imported} records imported. ${outcome.alreadyImported ? "This exact source was already imported." : ""} ${outcome.rejected.length} rejected records retained in the report. Raw source fingerprint: ${outcome.fingerprint}`;
  }, true);
}
async function fileInput(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  await operation(async () => {
    if (file.size > 4_000_000) throw new Error("Import file exceeds 4 MB.");
    source.value = await file.text();
  });
}
function desktop() {
  if (
    !window.confirm(
      "Read only the known desktop file ~/.prefs/TiwanakuScoreboard for preview? No original data will be changed.",
    )
  )
    return;
  return operation(async () => {
    source.value = (
      await dataRequest(
        "/api/v1/data/desktop-preview?confirmed=true",
        sourceSchema,
        { method: "POST" },
      )
    ).source;
    format.value = "legacy";
    message.value =
      "Desktop source loaded. Preview, review rejections, then confirm import.";
  });
}
function sameOrigin() {
  if (
    !window.confirm(
      "Read only this origin's legacy Tiwanaku preference key? Data on other origins or devices is inaccessible.",
    )
  )
    return;
  return operation(async () => {
    const raw = localStorage.getItem(
      "pref:app:TiwanakuScoreboard:scoreboard_resultss",
    );
    if (raw === null)
      throw new Error(
        "No legacy scoreboard key exists at this exact origin. Export it from the old origin and upload/paste it here.",
      );
    source.value = raw;
    format.value = "legacy";
  });
}
function rawBackup() {
  const fingerprint = preview.value?.fingerprint;
  if (!fingerprint) return;
  return operation(async () => {
    const raw = await dataRequest(
      `/api/v1/data/imports/${fingerprint}`,
      sourceSchema,
    );
    download(raw.source, `tiwanaku-import-${fingerprint}.txt`, "text/plain");
  });
}
onMounted(() => void refresh());
</script>
<template>
  <section class="page-panel history-panel">
    <h2>{{ t("history") }}</h2>
    <p v-if="busy" role="status">Loading/saving local data...</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="message" role="status">{{ message }}</p>
    <form class="history-filters" @submit.prevent="refresh">
      <label
        >Player filter<select v-model="player" aria-label="Player filter">
          <option value="">All players</option>
          <option v-for="name in playerOptions" :key="name" :value="name">
            {{ name }}
          </option>
        </select></label
      >
      <label
        >Board filter<select v-model="mapSize" aria-label="Board filter">
          <option value="">All boards</option>
          <option
            v-for="size in sizeOptions"
            :key="size.value"
            :value="size.value"
          >
            {{ size.label }}
          </option>
        </select></label
      >
      <label
        >Tiles filter<select v-model="shape" aria-label="Tiles filter">
          <option value="">All tiles</option>
          <option v-for="tile in tileShapes" :key="tile" :value="tile">
            {{ shapeLabels[tile] }}
          </option>
        </select></label
      >
      <label
        >Sort<select v-model="sort" aria-label="Sort">
          <option value="date-desc">Newest</option>
          <option value="date-asc">Oldest</option>
          <option value="time-asc">Fastest</option>
          <option value="time-desc">Slowest</option>
        </select></label
      >
    </form>
    <div v-if="history" class="history-summary" aria-label="History summary">
      <div>
        <span>Games</span><strong>{{ history.summary.count }} results</strong>
      </div>
      <div>
        <span>Total time</span
        ><strong>{{ formatElapsed(history.summary.elapsedSum) }}</strong>
      </div>
      <div>
        <span>Mean accuracy</span
        ><strong>{{
          history.summary.legacyAccuracyMean === null
            ? "—"
            : `${history.summary.legacyAccuracyMean.toFixed(1)}%`
        }}</strong>
      </div>
    </div>
    <p v-if="history && !history.entries.length">
      No history matches these filters.
    </p>
    <div v-if="history?.entries.length" class="history-table-wrap">
      <table class="history-table">
        <caption>
          Completed games. Click a player to reassign the result.
        </caption>
        <thead>
          <tr>
            <th>Player</th>
            <th>Board</th>
            <th>Tiles</th>
            <th>Difficulty</th>
            <th class="num">Max region</th>
            <th class="num">Time</th>
            <th class="num">Accuracy</th>
            <th class="num">Clicks / misses</th>
            <th>Date</th>
            <th><span class="visually-hidden">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="entry in history.entries"
            :key="entry.recordId"
            :data-record-id="entry.recordId"
          >
            <td class="history-player">
              <PlayerPicker
                v-if="editing === entry.recordId"
                :current="entry.playerTag ?? ''"
                :players="pickerPlayers"
                :disabled="busy"
                label="Player for this result"
                @pick="assign(entry, $event)"
              />
              <button
                v-else
                class="saved-player"
                :class="{ unknown: !entry.playerTag }"
                title="Change player"
                :disabled="busy"
                @click="editing = entry.recordId"
              >
                {{ entry.playerTag || "N/A" }}
              </button>
            </td>
            <td>
              <strong>{{ boardLabel(entry.mapSize) }}</strong>
            </td>
            <td>
              {{ shapeLabels[entry.puzzleSettings?.shape ?? "SQUARE"] }}
            </td>
            <td>{{ entry.puzzleSettings?.difficulty ?? "—" }}</td>
            <td class="num">
              {{ entry.puzzleSettings?.maxRegionSize ?? "—" }}
            </td>
            <td class="num">
              <strong>{{ formatElapsed(entry.elapsedMillis) }}</strong>
            </td>
            <td class="num">{{ accuracy(entry) }}</td>
            <td class="num">
              {{
                entry.totalClicks === null
                  ? "—"
                  : `${entry.totalClicks} / ${entry.totalMissClicks}`
              }}
            </td>
            <td class="history-date">
              {{
                entry.completedMillis === null
                  ? "—"
                  : new Date(entry.completedMillis).toLocaleString([], {
                      dateStyle: "short",
                      timeStyle: "short",
                    })
              }}
            </td>
            <td>
              <button
                class="icon-button"
                :disabled="busy"
                aria-label="Delete history entry"
                title="Delete history entry"
                @click="remove(entry)"
              >
                ✕
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <h3>Backups and import</h3>
    <div class="play-toolbar">
      <button :disabled="busy" @click="exportData">
        Export history and settings</button
      ><button :disabled="busy" @click="physicalBackup">
        Download full database backup
      </button>
    </div>
    <p>
      Logical JSON exports contain history/settings, not playable boards or
      unfinished attempts. Full ZIP backups preserve the entire database. Keep
      originals; restore ZIPs into a separate directory with Java stopped.
    </p>
    <div class="data-form">
      <label
        >Import format<select v-model="format" :disabled="busy">
          <option value="legacy">
            Legacy scores (delimited or desktop XML)
          </option>
          <option value="logical">Tiwanaku history/settings JSON</option>
        </select></label
      >
      <label
        >Import file<input type="file" :disabled="busy" @change="fileInput"
      /></label>
      <label
        >Import source<textarea
          v-model="source"
          :disabled="busy"
          maxlength="4000000"
          rows="5"
        ></textarea>
      </label>
      <div class="play-toolbar">
        <button :disabled="busy" @click="desktop">
          Preview known desktop file</button
        ><button :disabled="busy" @click="sameOrigin">
          Read this origin's legacy key</button
        ><button :disabled="busy || !source" @click="previewImport">
          Preview import
        </button>
      </div>
      <template v-if="preview">
        <p class="import-preview">
          {{ preview.entries.length }} valid records;
          {{ preview.rejected.length }} rejected.
          {{
            preview.alreadyImported ? "Already imported." : "Not imported yet."
          }}
        </p>
        <ul>
          <li v-for="rejection in preview.rejected" :key="rejection">
            {{ rejection }}
          </li>
        </ul>
        <p v-for="entry in preview.entries.slice(0, 10)" :key="entry.recordId">
          {{ entry.mapSize }} · {{ entry.playerTag ?? "Anonymous" }} ·
          {{ entry.elapsedMillis ?? "unknown" }} ms · {{ entry.scoringVersion }}
        </p>
        <label v-if="format === 'logical'"
          ><input v-model="restoreSettings" type="checkbox" />Also replace saved
          settings</label
        >
        <button :disabled="busy" @click="importData">Confirm import</button>
        <button :disabled="busy" @click="rawBackup">
          Download retained raw import source
        </button>
      </template>
    </div>
    <p>
      Remote browser data must be exported at its old exact origin (scheme, host
      and port), then uploaded or pasted here. Android private preferences
      require an in-app exporter/update bridge; desktop Java cannot read another
      device. No source is deleted or modified.
    </p>
  </section>
</template>
