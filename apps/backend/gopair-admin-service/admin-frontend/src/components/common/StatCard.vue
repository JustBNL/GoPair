<script setup lang="ts">
interface Props {
  label: string
  value: string | number
  sub?: string
  trend?: string
  trendDirection?: 'up' | 'down'
  color?: string
  icon?: string
}

withDefaults(defineProps<Props>(), {
  trendDirection: 'up',
  color: 'primary',
})

const iconMap: Record<string, string> = {
  users: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="7" r="4"/><path d="M3 21v-2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v2"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/><path d="M21 21v-2a4 4 0 0 0-3-3.87"/></svg>`,
  userPlus: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><line x1="19" y1="8" x2="19" y2="14"/><line x1="22" y1="11" x2="16" y2="11"/></svg>`,
  rooms: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>`,
  roomPlus: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><line x1="12" y1="10" x2="12" y2="16"/><line x1="9" y1="13" x2="15" y2="13"/></svg>`,
  messages: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>`,
  voice: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.15 14a19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 3.06 3h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L7.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 21 16.92z"/></svg>`,
}
</script>

<template>
  <div class="stat-card">
    <div class="stat-card__header">
      <div class="stat-card__icon" :class="`stat-card__icon--${color}`" v-html="iconMap[icon || 'users']" />
      <span v-if="trend" class="stat-card__trend" :class="`stat-card__trend--${trendDirection}`">
        {{ trendDirection === 'up' ? '↑' : '↓' }} {{ trend }}
      </span>
    </div>
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
  transition: background-color var(--transition-normal),
              border-color var(--transition-normal),
              box-shadow var(--transition-normal);
  cursor: default;
}

.stat-card:hover {
  box-shadow: var(--shadow-md);
}

.stat-card__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--space-3);
}

.stat-card__icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-card__icon--primary {
  background-color: oklch(52% 0.15 195 / 0.12);
  color: oklch(52% 0.15 195);
}

.stat-card__icon--success {
  background-color: oklch(62% 0.16 145 / 0.12);
  color: oklch(62% 0.16 145);
}

.stat-card__icon--warning {
  background-color: oklch(68% 0.17 70 / 0.12);
  color: oklch(68% 0.17 70);
}

.stat-card__icon--danger {
  background-color: oklch(58% 0.22 25 / 0.12);
  color: oklch(58% 0.22 25);
}

.stat-card__icon--info {
  background-color: oklch(55% 0.12 240 / 0.12);
  color: oklch(55% 0.12 240);
}

.stat-card__icon--voice {
  background-color: oklch(52% 0.18 280 / 0.12);
  color: oklch(52% 0.18 280);
}

.stat-card__trend {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  margin-top: 2px;
}

.stat-card__trend--up {
  color: oklch(62% 0.16 145);
  background-color: oklch(62% 0.16 145 / 0.10);
}

.stat-card__trend--down {
  color: oklch(58% 0.22 25);
  background-color: oklch(58% 0.22 25 / 0.10);
}

.stat-card__body {
  margin-top: var(--space-1);
}

.stat-card__value {
  font-family: var(--font-display);
  font-size: 26px;
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
