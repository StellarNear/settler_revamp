<script setup lang="ts">
import { watch } from "vue";
import { useI18n } from "vue-i18n";
const { t, locale } = useI18n();
watch(locale, (value) => {
  document.documentElement.lang = value;
});
</script>
<template>
  <section class="page-panel">
    <h2>{{ t("settings") }}</h2>
    <label
      ><span>{{ t("language") }}</span
      ><select v-model="locale">
        <option value="en">English</option>
        <option value="de">Deutsch</option>
      </select></label
    >
    <p>
      Language applies to this session. Durable settings and full translations
      are later packets.
    </p>
  </section>
</template>
