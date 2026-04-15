<script setup lang="ts">
interface Props {
  label: string
  value: string | number
  sub?: string
}

defineProps<Props>()
</script>

<template>
  <div class="stat-card">
    <div class="stat-card__body">
      <div class="stat-card__value">{{ value }}</div>
      <div class="stat-card__label">{{ label }}</div>
      <div v-if="sub" class="stat-card__sub">{{ sub }}</div>
    </div>
  </div>
</template>

<style scoped>
.stat-card {
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
  transition: background-color var(--transition-normal), border-color var(--transition-normal);
}

.stat-card__value {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text-primary);
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.stat-card__label {
  margin-top: var(--space-2);
  font-size: 13px;
  color: var(--color-text-muted);
  font-weight: 500;
}

.stat-card__sub {
  margin-top: var(--space-1);
  font-size: 12px;
  color: var(--color-primary);
}
</style>
