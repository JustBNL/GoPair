<template>
  <div class="file-icon" :class="`file-type-${fileType}`">
    <component :is="iconComponent" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  FileTextOutlined,
  FilePdfOutlined,
  FileImageOutlined,
  FileExcelOutlined,
  FileWordOutlined,
  FilePptOutlined,
  FileZipOutlined,
  VideoCameraOutlined,
  AudioOutlined,
  FileOutlined
} from '@ant-design/icons-vue'

interface Props {
  fileType: string
  size?: 'small' | 'default' | 'large'
}

const props = withDefaults(defineProps<Props>(), {
  size: 'default'
})

/**
 * 根据文件类型返回对应图标组件
 */
const iconComponent = computed(() => {
  const type = props.fileType.toLowerCase()
  
  // 文档类型
  if (['pdf'].includes(type)) {
    return FilePdfOutlined
  }
  
  if (['doc', 'docx'].includes(type)) {
    return FileWordOutlined
  }
  
  if (['xls', 'xlsx'].includes(type)) {
    return FileExcelOutlined
  }
  
  if (['ppt', 'pptx'].includes(type)) {
    return FilePptOutlined
  }
  
  if (['txt', 'md', 'json', 'xml', 'csv'].includes(type)) {
    return FileTextOutlined
  }
  
  // 图片类型
  if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'svg', 'webp'].includes(type)) {
    return FileImageOutlined
  }
  
  // 压缩文件
  if (['zip', 'rar', '7z', 'tar', 'gz', 'bz2'].includes(type)) {
    return FileZipOutlined
  }
  
  // 视频文件
  if (['mp4', 'avi', 'mov', 'wmv', 'flv', 'mkv', 'webm'].includes(type)) {
    return VideoCameraOutlined
  }
  
  // 音频文件
  if (['mp3', 'wav', 'flac', 'aac', 'ogg', 'wma'].includes(type)) {
    return AudioOutlined
  }
  
  // 默认文件图标
  return FileOutlined
})

/**
 * 获取文件类型（用于样式）
 */
const fileType = computed(() => {
  const type = props.fileType.toLowerCase()
  
  // 文档类型
  if (['pdf'].includes(type)) return 'pdf'
  if (['doc', 'docx'].includes(type)) return 'word'
  if (['xls', 'xlsx'].includes(type)) return 'excel'
  if (['ppt', 'pptx'].includes(type)) return 'powerpoint'
  if (['txt', 'md', 'json', 'xml', 'csv'].includes(type)) return 'text'
  
  // 媒体类型
  if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'svg', 'webp'].includes(type)) return 'image'
  if (['mp4', 'avi', 'mov', 'wmv', 'flv', 'mkv', 'webm'].includes(type)) return 'video'
  if (['mp3', 'wav', 'flac', 'aac', 'ogg', 'wma'].includes(type)) return 'audio'
  
  // 压缩文件
  if (['zip', 'rar', '7z', 'tar', 'gz', 'bz2'].includes(type)) return 'archive'
  
  // 代码文件
  if (['js', 'ts', 'jsx', 'tsx', 'vue', 'html', 'css', 'scss', 'less'].includes(type)) return 'code'
  if (['java', 'py', 'cpp', 'c', 'h', 'php', 'rb', 'go', 'rs'].includes(type)) return 'code'
  
  return 'default'
})
</script>

<style scoped lang="scss">
.file-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;

  &.file-type-pdf    { color: var(--file-pdf); }
  &.file-type-word   { color: var(--file-word); }
  &.file-type-excel  { color: var(--file-excel); }
  &.file-type-powerpoint { color: var(--file-powerpoint); }
  &.file-type-text   { color: var(--file-text); }
  &.file-type-image  { color: var(--file-image); }
  &.file-type-video  { color: var(--file-video); }
  &.file-type-audio  { color: var(--file-audio); }
  &.file-type-archive { color: var(--file-archive); }
  &.file-type-code   { color: var(--file-code); }
  &.file-type-default { color: var(--file-default); }
}
</style> 