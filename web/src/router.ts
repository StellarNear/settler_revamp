import { createRouter, createWebHistory } from "vue-router";
import SetupView from "./views/SetupView.vue";
import PlayView from "./views/PlayView.vue";
import HistoryView from "./views/HistoryView.vue";
import SettingsView from "./views/SettingsView.vue";
import NotFoundView from "./views/NotFoundView.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: "/setup" },
    { path: "/setup", component: SetupView },
    { path: "/play", component: PlayView },
    { path: "/history", component: HistoryView },
    { path: "/settings", component: SettingsView },
    { path: "/:pathMatch(.*)*", component: NotFoundView },
  ],
});

router.afterEach(() => {
  requestAnimationFrame(() => document.getElementById("content")?.focus());
});
