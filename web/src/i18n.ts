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
      historyEmpty:
        "History is not available in this scaffold yet. Saved attempts remain in the Java database.",
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
      historyEmpty:
        "Der Verlauf ist noch nicht verfügbar. Gespeicherte Spiele bleiben in der Java-Datenbank.",
      noGame: "Kein Spiel geöffnet. Erstelle ein neues Rätsel.",
      notFound: "Seite nicht gefunden.",
    },
  },
});
