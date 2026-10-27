import { createI18n } from "vue-i18n";

export const i18n = createI18n({
  legacy: false,
  locale: "en",
  fallbackLocale: "en",
  messages: {
    en: {
      setup: "New puzzle",
      play: "Play",
      history: "History",
      settings: "Settings",
      reconnect: "Reconnect",
      connecting: "Connecting...",
      connected: "JAVA SERVER CONNECTED",
      disconnected: "SERVER DISCONNECTED",
      language: "Language",
      skip: "Skip to content",
      noGame: "No saved game is open. Generate a puzzle to start.",
      notFound: "Page not found.",
    },
    de: {
      setup: "Neues Rätsel",
      play: "Spielen",
      history: "Verlauf",
      settings: "Einstellungen",
      reconnect: "Neu verbinden",
      connecting: "Verbindung...",
      connected: "JAVA-SERVER VERBUNDEN",
      disconnected: "SERVER GETRENNT",
      language: "Sprache",
      skip: "Zum Inhalt",
      noGame: "Kein Spiel geöffnet. Erstelle ein neues Rätsel.",
      notFound: "Seite nicht gefunden.",
    },
  },
});
