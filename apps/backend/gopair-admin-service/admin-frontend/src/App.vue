<script setup lang="ts">
import { computed } from 'vue'
import { RouterView } from 'vue-router'
import { ConfigProvider } from 'ant-design-vue'
import { useAppStore } from '@/stores/app'

const app = useAppStore()

const themeToken = computed(() => ({
  colorPrimary: '#0d9488',
  borderRadius: 8,
  fontFamily: "'Noto Sans SC', sans-serif",
}))
</script>

<template>
  <ConfigProvider :theme="{ token: themeToken }">
    <RouterView />
  </ConfigProvider>
</template>
