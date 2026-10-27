<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import type { z } from "zod";
import {
  archiveSchema,
  dataRequest,
  download,
  historySchema,
  outcomeSchema,
  previewSchema,
  sourceSchema,
  type HistoryEntry,
} from "../dataApi";
import { loadBootstrap, HttpError } from "../api";
import { useDataStore } from "../stores/data";
import { formatElapsed } from "../historyPresentation";
const { t } = useI18n();
const settings = useDataStore();
const history = ref<z.infer<typeof historySchema>>();
const busy = ref(false),
  error = ref(""),
  message = ref("");
const player = ref(""),
  mapSize = ref(""),
  scoring = ref(""),
  assistance = ref(""),
  sort = ref("date-desc");
const source = ref(""),
  format = ref("legacy"),
  restoreSettings = ref(false);
const preview = ref<z.infer<typeof previewSchema>>();
const tags = ref<Record<string, string>>({});
function scoringLabel(version: HistoryEntry["scoringVersion"]) {
  switch (version) {
    case "legacy-clicks-v1":
      return "Legacy clicks";
    case "direct-entry-v1":
      return "Direct entry";
    case "unrecorded-v0":
      return "Unrecorded";
  }
}
function assistanceLabel(entry: HistoryEntry) {
  const assistance = entry.assistance;
  if (!assistance) return "Unknown";
  const used: string[] = [];
  if (assistance.candidates)
    used.push(`${assistance.candidates} candidate checks`);
  if (assistance.explanations)
    used.push(`${assistance.explanations} explanations`);
  if (assistance.reveals) used.push(`${assistance.reveals} reveals`);
  if (assistance.continuousConflicts) used.push("conflicts");
  if (assistance.replay) used.push("replay");
  return used.length ? used.join(" · ") : "No hints or reveals";
}
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
  const query = new URLSearchParams({
    player: player.value,
    mapSize: mapSize.value,
    scoring: scoring.value,
    assistance: assistance.value,
    sort: sort.value,
  });
  history.value = await dataRequest(`/api/v1/history?${query}`, historySchema);
  tags.value = Object.fromEntries(
    history.value.entries.map((entry) => [
      entry.recordId,
      entry.playerTag ?? "",
    ]),
  );
}
function refresh() {
  return operation(fetchHistory);
}
function rename(entry: HistoryEntry) {
  return operation(async () => {
    await dataRequest(
      `/api/v1/history/${entry.recordId}/player`,
      historySchema,
      {
        method: "POST",
        body: JSON.stringify({ playerTag: tags.value[entry.recordId] }),
      },
    );
    await fetchHistory();
    message.value = "Player tag saved.";
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
      <label>Player filter<input v-model="player" maxlength="128" /></label>
      <label
        >Board filter<input
          v-model="mapSize"
          maxlength="64"
          placeholder="e.g. 7x5 or SMALL"
      /></label>
      <label
        >Scoring filter<select v-model="scoring">
          <option value="">All methods</option>
          <option value="direct-entry-v1">Direct entries</option>
          <option value="legacy-clicks-v1">Legacy clicks</option>
          <option value="unrecorded-v0">Unrecorded</option>
        </select></label
      >
      <label
        >Assistance filter<select v-model="assistance">
          <option value="">All assistance</option>
          <option value="assisted">Assisted</option>
          <option value="unassisted">Unassisted</option>
          <option value="unknown">Unknown</option>
          <option value="replay">Replay</option>
        </select></label
      >
      <label
        >Sort<select v-model="sort">
          <option value="date-desc">Newest</option>
          <option value="date-asc">Oldest</option>
          <option value="time-asc">Fastest</option>
          <option value="time-desc">Slowest</option>
        </select></label
      >
      <button :disabled="busy">Apply filters</button>
    </form>
    <div v-if="history" class="history-summary" aria-label="History summary">
      <strong>{{ history.summary.count }} results</strong>
      <span
        >Total time: {{ formatElapsed(history.summary.elapsedSum) }} across
        {{ history.summary.knownElapsedCount }} timed records</span
      >
      <span
        >Mean legacy accuracy:
        {{
          history.summary.legacyAccuracyMean === null
            ? "Unknown"
            : `${history.summary.legacyAccuracyMean.toFixed(2)}%`
        }}
        across {{ history.summary.legacyAccuracyCount }} compatible
        records</span
      >
      <small
        >Legacy accuracy excludes newer entry-count scoring; the two are not
        averaged together.</small
      >
    </div>
    <p v-if="history && !history.entries.length">
      No history matches these filters.
    </p>
    <div v-if="history?.entries.length" class="history-table-wrap">
      <table class="history-table">
        <caption>
          Saved completion history; unavailable metadata is shown as unknown.
        </caption>
        <thead>
          <tr>
            <th>Board &amp; date</th>
            <th>Time &amp; score</th>
            <th>Method</th>
            <th>Assistance</th>
            <th>Player &amp; actions</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="entry in history.entries"
            :key="entry.recordId"
            :data-record-id="entry.recordId"
          >
            <td class="history-board">
              <strong>{{ entry.mapSize }}</strong>
              <span>{{
                entry.completedMillis === null
                  ? "Date unknown"
                  : new Date(entry.completedMillis).toLocaleString()
              }}</span>
              <small v-if="entry.puzzleSettings">
                {{ entry.puzzleSettings.difficulty }} · max region
                {{ entry.puzzleSettings.maxRegionSize }}
              </small>
              <small v-else>Puzzle settings unknown</small>
            </td>
            <td class="history-result">
              <strong>{{ formatElapsed(entry.elapsedMillis) }}</strong>
              <span>{{
                entry.legacyAccuracy === null
                  ? `${entry.valueEntries ?? "Unknown"} entries`
                  : `${entry.legacyAccuracy}% legacy accuracy`
              }}</span>
              <small v-if="entry.totalClicks !== null">
                {{ entry.totalClicks }} clicks ·
                {{ entry.totalMissClicks }} misses
              </small>
            </td>
            <td>{{ scoringLabel(entry.scoringVersion) }}</td>
            <td>{{ assistanceLabel(entry) }}</td>
            <td class="history-player">
              <span class="saved-player">{{
                entry.playerTag || "Anonymous"
              }}</span>
              <div class="history-actions">
                <form @submit.prevent="rename(entry)">
                  <label
                    >New player tag<input
                      v-model="tags[entry.recordId]"
                      maxlength="128"
                      pattern="[A-Za-z]{0,3}" /></label
                  ><button :disabled="busy">Save player tag</button>
                </form>
                <button :disabled="busy" @click="remove(entry)">
                  Delete history entry
                </button>
              </div>
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
