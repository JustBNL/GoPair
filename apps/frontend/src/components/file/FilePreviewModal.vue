<template>
  <a-modal
    v-model:open="visible"
    :title="file?.fileName || '文件预览'"
    width="90%"
    style="max-width: 1200px;"
    :footer="null"
    centered
  >
    <div v-if="file" class="file-preview-modal">
      <!-- 预览工具栏 -->
      <div class="preview-toolbar">
        <div class="file-info">
          <span class="file-name">{{ file.fileName }}</span>
          <span class="file-size">{{ file.fileSizeFormatted }}</span>
        </div>
        <div class="preview-actions">
          <a-button @click="downloadFile">
            <download-outlined />
            下载
          </a-button>
          <a-button @click="openInNewTab">
            <export-outlined />
            新窗口打开
          </a-button>
          <a-button @click="visible = false">
            <close-outlined />
            关闭
          </a-button>
        </div>
      </div>

      <!-- 预览内容 -->
      <div class="preview-content">
        <a-spin :spinning="loading" tip="加载中...">
          <!-- 图片预览 -->
          <div v-if="isImageFile" class="image-preview">
            <div class="image-container">
              <img
                :src="previewUrl"
                :alt="file.fileName"
                class="preview-image"
                :style="{ transform: `scale(${zoomLevel / 100})` }"
                @load="handleImageLoad"
                @error="handleImageError"
              />
            </div>
            <div class="image-controls">
              <a-button-group>
                <a-button @click="zoomOut" :disabled="zoomLevel <= MIN_ZOOM" aria-label="缩小">
                  <zoom-out-outlined />
                </a-button>
                <a-button disabled style="min-width: 60px;">{{ zoomLevel }}%</a-button>
                <a-button @click="zoomIn" :disabled="zoomLevel >= MAX_ZOOM" aria-label="放大">
                  <zoom-in-outlined />
                </a-button>
                <a-button @click="resetZoom" aria-label="适应窗口">
                  <compress-outlined />
                  适应窗口
                </a-button>
              </a-button-group>
            </div>
          </div>

          <!-- PDF预览 -->
          <div v-else-if="isPdfFile" class="pdf-preview">
            <iframe
              :src="previewUrl"
              style="width: 100%; height: 70vh; border: none;"
              @load="handlePdfLoad"
              @error="handlePdfError"
            />
          </div>

          <!-- 文本文件预览 -->
          <div v-else-if="isTextFile" class="text-preview">
            <a-textarea
              v-model:value="textContent"
              :rows="20"
              readonly
              style="font-family: monospace; font-size: 14px; line-height: 1.6;"
            />
          </div>

          <!-- 音频预览 -->
          <div v-else-if="isAudioFile" class="audio-preview">
            <div class="audio-container">
              <audio
                controls
                style="width: 100%;"
                @loadeddata="handleAudioLoad"
                @error="handleAudioError"
              >
                <source :src="previewUrl" :type="file.contentType">
                您的浏览器不支持音频播放。
              </audio>
            </div>
            <div class="audio-info">
              <p>音频文件：{{ file.fileName }}</p>
              <p>文件大小：{{ file.fileSizeFormatted }}</p>
            </div>
          </div>

          <!-- 视频预览 -->
          <div v-else-if="isVideoFile" class="video-preview">
            <video
              controls
              style="max-width: 100%; max-height: 70vh;"
              @loadeddata="handleVideoLoad"
              @error="handleVideoError"
            >
              <source :src="previewUrl" :type="file.contentType">
              您的浏览器不支持视频播放。
            </video>
          </div>

          <!-- 不支持预览的文件类型 -->
          <div v-else class="unsupported-preview">
            <a-result
              status="warning"
              title="无法预览该文件类型"
              :sub-title="`${file.fileName} (${getFileTypeDesc(file.fileType)}) 不支持在线预览`"
            >
              <template #extra>
                <a-button type="primary" @click="downloadFile">
                  <download-outlined />
                  下载文件
                </a-button>
              </template>
            </a-result>
          </div>
        </a-spin>
      </div>

      <!-- 预览错误提示 -->
      <div v-if="error" class="preview-error">
        <a-alert
          :message="error"
          type="error"
          show-icon
          closable
          @close="error = ''"
        />
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import {
  DownloadOutlined,
  ExportOutlined,
  CloseOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
  CompressOutlined
} from '@ant-design/icons-vue'
import { FileAPI } from '@/api/file'
import type { FileVO } from '@/types/api'

interface Props {
  open: boolean
  file: FileVO | null
}

interface Emits {
  (e: 'update:open', value: boolean): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// 模态框状态
const visible = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value)
})

// 预览状态
const loading = ref(false)
const error = ref('')
const textContent = ref('')
const previewUrl = ref('')
const zoomLevel = ref(100)

const MIN_ZOOM = 25
const MAX_ZOOM = 400
const ZOOM_STEP = 25

/**
 * 文件类型判断
 */
const isImageFile = computed(() => {
  if (!props.file) return false
  const imageTypes = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'svg', 'webp']
  return imageTypes.includes(props.file.fileType.toLowerCase())
})

const isPdfFile = computed(() => {
  if (!props.file) return false
  return props.file.fileType.toLowerCase() === 'pdf'
})

const isTextFile = computed(() => {
  if (!props.file) return false
  const textTypes = ['txt', 'md', 'json', 'xml', 'csv', 'log', 'js', 'ts', 'html', 'css']
  return textTypes.includes(props.file.fileType.toLowerCase())
})

const isAudioFile = computed(() => {
  if (!props.file) return false
  const audioTypes = ['mp3', 'wav', 'flac', 'aac', 'ogg', 'wma']
  return audioTypes.includes(props.file.fileType.toLowerCase())
})

const isVideoFile = computed(() => {
  if (!props.file) return false
  const videoTypes = ['mp4', 'avi', 'mov', 'wmv', 'flv', 'mkv', 'webm']
  return videoTypes.includes(props.file.fileType.toLowerCase())
})

/**
 * 获取文件类型描述
 */
const getFileTypeDesc = (fileType: string): string => {
  const typeMap: Record<string, string> = {
    pdf: 'PDF 文档',
    doc: 'Word 文档',
    docx: 'Word 文档',
    txt: '文本文件',
    jpg: 'JPEG 图片',
    jpeg: 'JPEG 图片',
    png: 'PNG 图片',
    mp4: 'MP4 视频',
    mp3: 'MP3 音频'
  }
  
  return typeMap[fileType.toLowerCase()] || `${fileType.toUpperCase()} 文件`
}

/**
 * 加载预览内容
 */
const loadPreview = async () => {
  if (!props.file) return

  try {
    loading.value = true
    error.value = ''

    if (isImageFile.value) {
      // 图片文件直接使用预览URL
      previewUrl.value = props.file.previewUrl || FileAPI.getPreviewUrl(props.file.fileId)
    } else if (isPdfFile.value) {
      // PDF文件使用预览URL
      previewUrl.value = FileAPI.getPreviewUrl(props.file.fileId)
    } else if (isTextFile.value) {
      // 文本文件需要下载内容
      const blob = await FileAPI.previewFile(props.file.fileId)
      textContent.value = await blob.text()
    } else if (isAudioFile.value || isVideoFile.value) {
      // 音视频文件使用下载URL
      previewUrl.value = FileAPI.getDownloadUrl(props.file.fileId)
    }

  } catch (err: any) {
    error.value = err.message || '预览加载失败'
  } finally {
    loading.value = false
  }
}

/**
 * 下载文件
 */
const downloadFile = async () => {
  if (!props.file) return

  try {
    const blob = await FileAPI.downloadFile(props.file.fileId)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = props.file.fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    
    antMessage.success('下载成功')
  } catch (error) {
    antMessage.error('下载失败')
  }
}

/**
 * 在新窗口打开
 */
const openInNewTab = () => {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank')
  }
}

/**
 * 图片缩放控制
 */
const zoomIn = () => {
  if (zoomLevel.value < MAX_ZOOM) {
    zoomLevel.value = Math.min(MAX_ZOOM, zoomLevel.value + ZOOM_STEP)
  }
}

const zoomOut = () => {
  if (zoomLevel.value > MIN_ZOOM) {
    zoomLevel.value = Math.max(MIN_ZOOM, zoomLevel.value - ZOOM_STEP)
  }
}

const resetZoom = () => {
  zoomLevel.value = 100
}

/**
 * 媒体文件加载处理
 */
const handleImageLoad = () => {
  loading.value = false
}

const handleImageError = () => {
  loading.value = false
  error.value = '图片加载失败'
}

const handlePdfLoad = () => {
  loading.value = false
}

const handlePdfError = () => {
  loading.value = false
  error.value = 'PDF 加载失败'
}

const handleAudioLoad = () => {
  loading.value = false
}

const handleAudioError = () => {
  loading.value = false
  error.value = '音频加载失败'
}

const handleVideoLoad = () => {
  loading.value = false
}

const handleVideoError = () => {
  loading.value = false
  error.value = '视频加载失败'
}

// 监听文件变化，自动加载预览
watch(() => props.file, (newFile) => {
  if (newFile && props.open) {
    zoomLevel.value = 100 // 重置缩放
    loadPreview()
  }
})

// 监听模态框打开状态
watch(() => props.open, (isOpen) => {
  if (isOpen && props.file) {
    zoomLevel.value = 100 // 重置缩放
    loadPreview()
  } else {
    // 清理状态
    loading.value = false
    error.value = ''
    textContent.value = ''
    previewUrl.value = ''
    zoomLevel.value = 100
  }
})
</script>

<style scoped lang="scss">
.file-preview-modal {
.preview-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: var(--surface-bg);
  border-radius: 8px;
  margin-bottom: 16px;

  .file-info {
    display: flex;
    align-items: center;
    gap: 12px;

    .file-name {
      font-weight: 500;
      color: var(--text-primary);
    }

    .file-size {
      color: var(--text-muted);
      font-size: 12px;
    }
  }

    .preview-actions {
      display: flex;
      gap: 8px;
    }
  }

  .preview-content {
    min-height: 400px;
    max-height: 80vh;
    overflow: auto;

    .image-preview {
      text-align: center;

      .image-container {
        margin-bottom: 16px;
        background: var(--surface-bg);
        border-radius: 8px;
        padding: 16px;
        overflow: auto;
        max-height: calc(70vh - 80px);
        display: flex;
        align-items: center;
        justify-content: center;

        .preview-image {
          max-width: 100%;
          max-height: calc(70vh - 80px);
          object-fit: contain;
          transition: transform 0.2s ease;
          transform-origin: center center;
        }
      }

      .image-controls {
        display: flex;
        justify-content: center;
        gap: 8px;
      }
    }

    .pdf-preview {
      border-radius: 8px;
      overflow: hidden;
    }

    .text-preview {
      :deep(.ant-input) {
        background: var(--surface-bg);
        border: none;
        resize: none;
      }
    }

    .audio-preview {
      text-align: center;
      padding: 40px 20px;

      .audio-container {
        margin-bottom: 24px;
      }

      .audio-info {
        color: var(--text-muted);
        font-size: 14px;

        p {
          margin: 4px 0;
        }
      }
    }

    .video-preview {
      text-align: center;
      padding: 20px;
      background: var(--ai-surface);
      border-radius: 8px;
    }

    .unsupported-preview {
      padding: 40px 20px;
      text-align: center;
    }
  }

  .preview-error {
    margin-top: 16px;
  }
}

@media (max-width: 768px) {
  .file-preview-modal {
    .preview-toolbar {
      flex-direction: column;
      gap: 12px;
      align-items: stretch;

      .file-info {
        justify-content: center;
      }

      .preview-actions {
        justify-content: center;
      }
    }

    .preview-content {
      .image-controls {
        :deep(.ant-btn-group) {
          flex-wrap: wrap;
          gap: 4px;
        }
      }
    }
  }
}
</style> 