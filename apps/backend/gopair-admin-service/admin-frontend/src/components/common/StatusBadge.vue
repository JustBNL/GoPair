<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  status: string | number
  type: 'user' | 'room' | 'call' | 'connection' | 'message'
}

const props = defineProps<Props>()

interface BadgeConfig {
  label: string
  color: string
}

const badge = computed<BadgeConfig>(() => {
  const s = String(props.status)

  if (props.type === 'user') {
    if (s === '0') return { label: '正常', color: 'success' }
    if (s === '1') return { label: '停用', color: 'error' }
  }
  if (props.type === 'room') {
    if (s === '0') return { label: '活跃', color: 'success' }
    if (s === '1') return { label: '已关闭', color: 'default' }
  }
  if (props.type === 'call') {
    if (s === '0') return { label: '进行中', color: 'processing' }
    if (s === '1') return { label: '已取消', color: 'warning' }
    if (s === '2') return { label: '已结束', color: 'success' }
  }
  if (props.type === 'connection') {
    if (s === '0') return { label: '断线', color: 'error' }
    if (s === '1') return { label: '正常', color: 'success' }
  }
  if (props.type === 'message') {
    if (s === '1') return { label: '文本', color: 'default' }
    if (s === '2') return { label: '图片', color: 'processing' }
    if (s === '3') return { label: '文件', color: 'warning' }
    if (s === '4') return { label: '语音', color: 'voice' }
  }
  return { label: s, color: 'default' }
})

const isVoice = computed(() => badge.value.color === 'voice')
const tagStyle = computed(() =>
  isVoice.value ? { backgroundColor: 'var(--color-voice)', borderColor: 'var(--color-voice)' } : {}
)
</script>

<template>
  <a-tag :color="isVoice ? undefined : badge.color" :style="tagStyle" class="status-badge">{{ badge.label }}</a-tag>
</template>

<style scoped>
.status-badge {
  border-radius: var(--radius-sm);
  font-size: 12px;
  line-height: 1.4;
}
</style>
