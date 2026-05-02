<template>
  <button
    class="theme-toggle"
    :title="themeStore.isDark ? '切换到浅色模式' : '切换到暗色模式'"
    @click="themeStore.toggle()"
  >
    <BulbOutlined v-if="themeStore.isDark" class="toggle-icon" />
    <BulbFilled v-else class="toggle-icon" />
  </button>
</template>

<script setup lang="ts">
import { BulbOutlined, BulbFilled } from '@ant-design/icons-vue'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()
</script>

<style scoped>
.theme-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  cursor: pointer;
  color: var(--text-secondary);
  transition: color 0.2s, border-color 0.2s, background-color 0.2s;
}

.theme-toggle:hover {
  color: var(--brand-primary);
  border-color: var(--brand-primary);
  background: var(--brand-primary-light);
}

.toggle-icon {
  font-size: 16px;
}
</style>
