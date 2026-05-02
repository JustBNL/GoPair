import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

const THEME_KEY = 'gopair-theme'
const DEFAULT_THEME = 'dark'

/**
 * 主题状态管理 Store
 * 支持暗色/浅色模式切换，持久化到 localStorage
 */
export const useThemeStore = defineStore('theme', () => {
  const isDark = ref(true)

  function toggle() {
    isDark.value = !isDark.value
  }

  function setTheme(theme: 'dark' | 'light') {
    isDark.value = theme === 'dark'
  }

  function initTheme() {
    const stored = localStorage.getItem(THEME_KEY)
    if (stored === 'light' || stored === 'dark') {
      isDark.value = stored === 'dark'
    } else {
      // 默认保持暗色
      isDark.value = true
    }
  }

  // 监听变化，持久化到 localStorage
  watch(isDark, (val) => {
    localStorage.setItem(THEME_KEY, val ? 'dark' : 'light')
  })

  return {
    isDark,
    toggle,
    setTheme,
    initTheme,
  }
})
