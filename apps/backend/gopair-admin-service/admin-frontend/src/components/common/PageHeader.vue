<script setup lang="ts">
interface Props {
  title: string
  description?: string
}

defineProps<Props>()
</script>

<template>
  <div class="page-header">
    <h2 class="page-header__title">{{ title }}</h2>
    <p v-if="description" class="page-header__desc">{{ description }}</p>
  </div>
</template>

<style scoped>
.page-header {
  margin-bottom: var(--space-5);
}

.page-header__title {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 600;
  color: var(--color-text-primary);
  letter-spacing: -0.02em;
  line-height: 1.3;
}

.page-header__desc {
  margin-top: var(--space-2);
  font-size: 14px;
  color: var(--color-text-muted);
}
</style>
