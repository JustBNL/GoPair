<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { ConfigProvider, theme } from 'ant-design-vue'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()

const themeConfig = ref({
  algorithm: theme.darkAlgorithm,
})

onMounted(() => {
  themeStore.initTheme()
  themeConfig.value.algorithm = themeStore.isDark ? theme.darkAlgorithm : theme.defaultAlgorithm
  // Apply .dark class to <html> so CSS variable overrides cascade to all elements including <body>
  document.documentElement.classList.toggle('dark', themeStore.isDark)
})

watch(() => themeStore.isDark, (isDark) => {
  themeConfig.value.algorithm = isDark ? theme.darkAlgorithm : theme.defaultAlgorithm
  document.documentElement.classList.toggle('dark', isDark)
})
</script>

<template>
  <ConfigProvider :theme="themeConfig">
    <div id="app">
      <RouterView />
    </div>
  </ConfigProvider>
</template>

<style>
/* 全局样式重置 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen',
    'Ubuntu', 'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue',
    sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

#app {
  min-height: 100vh;
  position: relative;
  background: var(--surface-bg);
}

/* 滚动条样式 */
::-webkit-scrollbar {
  width: 6px;
}

::-webkit-scrollbar-track {
  background: var(--scrollbar-track);
}

::-webkit-scrollbar-thumb {
  background: var(--scrollbar-thumb);
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: var(--scrollbar-thumb-hover);
}
</style>
