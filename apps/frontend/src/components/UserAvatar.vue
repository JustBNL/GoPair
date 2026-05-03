<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  userId: number
  nickname?: string
  avatar?: string
  size?: number | string
  clickable?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  size: 32,
  clickable: true
})

const emit = defineEmits<{
  (e: 'click', userId: number): void
}>()

const initial = computed(() => {
  const name = props.nickname || 'U'
  const trimmed = name.trim()
  if (!trimmed) return '?'
  const parts = trimmed.split(/\s+/)
  if (parts.length >= 2) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
  }
  return trimmed.slice(0, 2).toUpperCase()
})
</script>

<template>
  <a-avatar
    :src="avatar"
    :alt="nickname"
    :size="size"
    :style="{ cursor: clickable !== false ? 'pointer' : 'default' }"
    @click="clickable !== false && emit('click', userId)"
  >
    <template v-if="!avatar">
      {{ initial }}
    </template>
  </a-avatar>
</template>
