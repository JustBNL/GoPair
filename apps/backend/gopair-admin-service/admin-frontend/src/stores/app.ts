import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { usePreferredDark } from '@vueuse/core'

const THEME_KEY = 'admin_theme'

export const useAppStore = defineStore('app', () => {
  /* 跟随系统主题偏好，但允许用户手动切换 */
  const prefersDark = usePreferredDark()
  const isDark      = ref(localStorage.getItem(THEME_KEY) === 'dark' || (!localStorage.getItem(THEME_KEY) && prefersDark.value))

  /* 监听系统主题变化 */
  watch(prefersDark, (v) => {
    if (!localStorage.getItem(THEME_KEY)) {
      isDark.value = v.matches
      applyTheme()
    }
  })

  function toggleTheme() {
    isDark.value = !isDark.value
    localStorage.setItem(THEME_KEY, isDark.value ? 'dark' : 'light')
    applyTheme()
  }

  function setTheme(dark: boolean) {
    isDark.value = dark
    localStorage.setItem(THEME_KEY, dark ? 'dark' : 'light')
    applyTheme()
  }

  function applyTheme() {
    if (isDark.value) {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }

  return { isDark, toggleTheme, setTheme, applyTheme }
})
