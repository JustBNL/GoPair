<script setup lang="ts">
import { computed } from 'vue'
import { RouterView } from 'vue-router'
import { ConfigProvider, theme } from 'ant-design-vue'
import { useAppStore } from '@/stores/app'

const app = useAppStore()

const themeConfig = computed(() => ({
  token: {
    colorPrimary: '#0d9488',
    borderRadius: 8,
    fontFamily: "'Noto Sans SC', sans-serif",
  },
  algorithm: app.isDark ? theme.darkAlgorithm : undefined,
}))
</script>

<template>
  <ConfigProvider :theme="themeConfig">
    <RouterView />
  </ConfigProvider>
</template>
