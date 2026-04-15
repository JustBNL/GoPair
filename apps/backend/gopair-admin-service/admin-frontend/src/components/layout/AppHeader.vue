<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const app   = useAppStore()
const auth  = useAuthStore()

const pageTitle = computed(() => {
  const meta = route.meta
  return (meta?.title as string) || 'GoPair 管理后台'
})
</script>

<template>
  <header class="app-header">
    <div class="app-header__left">
      <h1 class="app-header__title">{{ pageTitle }}</h1>
    </div>
    <div class="app-header__right">
      <span class="app-header__greeting">{{ auth.nickname || '管理员' }}</span>
      <button class="app-header__theme-btn" @click="app.toggleTheme">
        {{ app.isDark ? '亮色' : '暗色' }}
      </button>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-5);
  background-color: var(--color-header-bg);
  border-bottom: 1px solid var(--color-border);
  transition: background-color var(--transition-normal), border-color var(--transition-normal);
}

.app-header__left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.app-header__title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
  letter-spacing: -0.01em;
}

.app-header__right {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.app-header__greeting {
  font-size: 13px;
  color: var(--color-text-muted);
}

.app-header__theme-btn {
  font-size: 13px;
  color: var(--color-text-secondary);
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--space-1) var(--space-3);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.app-header__theme-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}
</style>
