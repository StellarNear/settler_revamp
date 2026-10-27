import { ref } from "vue";
import { defineStore } from "pinia";
import {
  dataRequest,
  settingsSnapshotSchema,
  type LocalSettings,
} from "../dataApi";
import { HttpError } from "../api";

export const useDataStore = defineStore("data", () => {
  const settings = ref<LocalSettings>();
  const revision = ref(0);
  const pending = ref(false);
  const error = ref("");
  const message = ref("");
  async function load() {
    if (pending.value) return;
    pending.value = true;
    error.value = "";
    try {
      const saved = await dataRequest(
        "/api/v1/settings",
        settingsSnapshotSchema,
      );
      settings.value = saved.settings;
      revision.value = saved.revision;
    } catch (reason) {
      error.value =
        reason instanceof Error ? reason.message : "Could not load settings.";
    } finally {
      pending.value = false;
    }
  }
  async function save(next: LocalSettings) {
    if (pending.value) return;
    pending.value = true;
    error.value = "";
    message.value = "";
    try {
      const saved = await dataRequest(
        "/api/v1/settings",
        settingsSnapshotSchema,
        {
          method: "POST",
          body: JSON.stringify({ revision: revision.value, settings: next }),
        },
      );
      settings.value = saved.settings;
      revision.value = saved.revision;
      message.value = "Settings saved to Java.";
    } catch (reason) {
      error.value =
        reason instanceof Error ? reason.message : "Settings save failed.";
      if (!(reason instanceof HttpError) || reason.status >= 500)
        error.value +=
          " Save outcome may be unknown. Reload settings before making another change.";
    } finally {
      pending.value = false;
    }
  }
  return { settings, revision, pending, error, message, load, save };
});
