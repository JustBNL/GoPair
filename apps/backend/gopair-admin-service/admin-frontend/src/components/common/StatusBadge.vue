<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  status: string | number
  type: 'user' | 'room' | 'call' | 'connection' | 'message' | 'file' | 'callType'
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
    if (s === '2') return { label: '已注销', color: 'warning' }
  }
  if (props.type === 'room') {
    if (s === '0') return { label: '活跃', color: 'success' }
    if (s === '1') return { label: '已关闭', color: 'default' }
    if (s === '2') return { label: '已过期', color: 'warning' }
    if (s === '3') return { label: '已归档', color: 'default' }
    if (s === '4') return { label: '已禁用', color: 'error' }
  }
  if (props.type === 'call') {
    if (s === '1') return { label: '进行中', color: 'processing' }
    if (s === '2') return { label: '已结束', color: 'success' }
    if (s === '3') return { label: '已取消', color: 'warning' }
  }
  if (props.type === 'connection') {
    if (s === '1') return { label: '已连接', color: 'success' }
    if (s === '2') return { label: '已断开', color: 'error' }
  }
  if (props.type === 'message') {
    if (s === '1') return { label: '文本', color: 'default' }
    if (s === '2') return { label: '图片', color: 'processing' }
    if (s === '3') return { label: '文件', color: 'warning' }
    if (s === '4') return { label: '语音', color: 'voice' }
    if (s === '5') return { label: 'Emoji', color: 'purple' }
  }
  if (props.type === 'file') {
    const ext = s.toLowerCase()
    if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'].includes(ext)) return { label: '图片', color: 'processing' }
    if (ext === 'pdf') return { label: 'PDF', color: 'error' }
    if (['doc', 'docx'].includes(ext)) return { label: '文档', color: 'purple' }
    if (['xls', 'xlsx'].includes(ext)) return { label: '表格', color: 'success' }
    if (['mp3', 'wav', 'ogg'].includes(ext)) return { label: '音频', color: 'warning' }
    if (['mp4', 'avi', 'mov'].includes(ext)) return { label: '视频', color: 'error' }
    return { label: '其他', color: 'default' }
  }
  if (props.type === 'callType') {
    if (s === '0') return { label: '语音', color: 'processing' }
    if (s === '1') return { label: '视频', color: 'purple' }
    return { label: s, color: 'default' }
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
