<script setup lang="ts">
import { useI18n } from "vue-i18n";
const { t } = useI18n();
</script>
<template>
  <section class="page-panel">
    <h2>{{ t("notFound") }}</h2>
    <RouterLink to="/setup">{{ t("setup") }}</RouterLink>
  </section>
</template>
