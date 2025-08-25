<template>
  <a-modal
    v-model:open="visible"
    title="文件信息"
    width="600px"
    :footer="null"
  >
    <div v-if="file" class="file-info-modal">
      <!-- 文件基本信息 -->
      <div class="info-section">
        <div class="file-header">
          <div class="file-icon">
            <file-icon :file-type="file.fileType" />
          </div>
          <div class="file-basic">
            <h3 class="file-name">{{ file.fileName }}</h3>
            <p class="file-type">{{ getFileTypeDesc(file.fileType) }}</p>
          </div>
        </div>
      </div>

      <!-- 详细信息 -->
      <div class="info-section">
        <h4 class="section-title">详细信息</h4>
        <a-descriptions :column="2" size="small">
          <a-descriptions-item label="文件大小">
            {{ file.fileSizeFormatted }}
          </a-descriptions-item>
          <a-descriptions-item label="文件类型">
            {{ file.contentType }}
          </a-descriptions-item>
          <a-descriptions-item label="上传者">
            {{ file.uploaderNickname }}
          </a-descriptions-item>
          <a-descriptions-item label="上传时间">
            {{ formatTime(file.uploadTime) }}
          </a-descriptions-item>
          <a-descriptions-item label="下载次数">
            {{ file.downloadCount }} 次
          </a-descriptions-item>
          <a-descriptions-item label="是否可预览">
            <a-tag :color="file.previewable ? 'green' : 'orange'">
              {{ file.previewable ? '支持' : '不支持' }}
            </a-tag>
          </a-descriptions-item>
        </a-descriptions>
      </div>

      <!-- 文件操作 -->
      <div class="info-section">
        <h4 class="section-title">文件操作</h4>
        <div class="action-buttons">
          <a-button
            v-if="file.previewable"
            type="primary"
            @click="previewFile"
          >
            <eye-outlined />
            预览
          </a-button>
          <a-button @click="downloadFile">
            <download-outlined />
            下载
          </a-button>
          <a-button @click="shareFile">
            <share-alt-outlined />
            分享链接
          </a-button>
          <a-button danger @click="deleteFile">
            <delete-outlined />
            删除文件
          </a-button>
        </div>
      </div>

      <!-- 文件路径 -->
      <div class="info-section">
        <h4 class="section-title">文件路径</h4>
        <div class="file-path">
          <a-input
            :value="file.downloadUrl"
            readonly
            class="path-input"
          >
            <template #suffix>
              <a-tooltip title="复制链接">
                <copy-outlined 
                  @click="copyPath"
                  class="copy-icon"
                />
              </a-tooltip>
            </template>
          </a-input>
        </div>
      </div>

      <!-- 文件预览（如果支持） -->
      <div v-if="file.previewable && showPreview" class="info-section">
        <h4 class="section-title">文件预览</h4>
        <div class="file-preview">
          <!-- 图片预览 -->
          <div v-if="isImageFile" class="image-preview">
            <a-image
              :src="file.previewUrl || file.downloadUrl"
              :alt="file.fileName"
              style="max-width: 100%; max-height: 300px;"
            />
          </div>
          
          <!-- 文本文件预览 -->
          <div v-else-if="isTextFile" class="text-preview">
            <a-textarea
              v-model:value="fileContent"
              :rows="8"
              readonly
              placeholder="加载中..."
            />
          </div>
          
          <!-- 其他文件类型 -->
          <div v-else class="preview-placeholder">
            <a-result
              status="info"
              title="预览功能开发中"
              sub-title="该文件类型暂不支持在线预览，请下载后查看"
            />
          </div>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { message as antMessage, Modal } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  EyeOutlined,
  DownloadOutlined,
  ShareAltOutlined,
  DeleteOutlined,
  CopyOutlined
} from '@ant-design/icons-vue'
import { FileAPI } from '@/api/file'
import type { FileVO } from '@/types/api'
import FileIcon from './FileIcon.vue'

interface Props {
  open: boolean
  file: FileVO | null
}

interface Emits {
  (e: 'update:open', value: boolean): void
  (e: 'preview', file: FileVO): void
  (e: 'download', file: FileVO): void
  (e: 'delete', file: FileVO): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// 模态框可见性
const visible = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value)
})

// 文件内容预览
const showPreview = ref(false)
const fileContent = ref('')

/**
 * 是否为图片文件
 */
const isImageFile = computed(() => {
  if (!props.file) return false
  const imageTypes = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'svg', 'webp']
  return imageTypes.includes(props.file.fileType.toLowerCase())
})

/**
 * 是否为文本文件
 */
const isTextFile = computed(() => {
  if (!props.file) return false
  const textTypes = ['txt', 'md', 'json', 'xml', 'csv', 'log']
  return textTypes.includes(props.file.fileType.toLowerCase())
})

/**
 * 获取文件类型描述
 */
const getFileTypeDesc = (fileType: string): string => {
  const typeMap: Record<string, string> = {
    pdf: 'PDF 文档',
    doc: 'Word 文档',
    docx: 'Word 文档',
    xls: 'Excel 表格',
    xlsx: 'Excel 表格',
    ppt: 'PowerPoint 演示文稿',
    pptx: 'PowerPoint 演示文稿',
    txt: '文本文件',
    jpg: 'JPEG 图片',
    jpeg: 'JPEG 图片',
    png: 'PNG 图片',
    gif: 'GIF 图片',
    mp4: 'MP4 视频',
    mp3: 'MP3 音频',
    zip: 'ZIP 压缩包',
    rar: 'RAR 压缩包'
  }
  
  return typeMap[fileType.toLowerCase()] || `${fileType.toUpperCase()} 文件`
}

/**
 * 格式化时间
 */
const formatTime = (timeStr: string) => {
  return dayjs(timeStr).format('YYYY-MM-DD HH:mm:ss')
}

/**
 * 预览文件
 */
const previewFile = () => {
  if (props.file) {
    emit('preview', props.file)
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
    emit('download', props.file)
  } catch (error) {
    antMessage.error('下载失败')
  }
}

/**
 * 分享文件
 */
const shareFile = async () => {
  if (!props.file) return
  
  try {
    const response = await FileAPI.createShareLink(props.file.fileId)
    await navigator.clipboard.writeText(response.data.shareUrl)
    antMessage.success('分享链接已复制到剪贴板')
  } catch (error) {
    antMessage.error('创建分享链接失败')
  }
}

/**
 * 删除文件
 */
const deleteFile = () => {
  if (!props.file) return
  
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除文件 "${props.file.fileName}" 吗？`,
    onOk: () => {
      if (props.file) {
        emit('delete', props.file)
        visible.value = false
      }
    }
  })
}

/**
 * 复制文件路径
 */
const copyPath = async () => {
  if (!props.file) return
  
  try {
    await navigator.clipboard.writeText(props.file.downloadUrl)
    antMessage.success('文件路径已复制到剪贴板')
  } catch (error) {
    antMessage.error('复制失败')
  }
}

/**
 * 加载文件内容预览
 */
const loadFilePreview = async () => {
  if (!props.file || !isTextFile.value) return
  
  try {
    const blob = await FileAPI.previewFile(props.file.fileId)
    const text = await blob.text()
    fileContent.value = text
  } catch (error) {
    fileContent.value = '预览加载失败'
  }
}

// 监听文件变化
watch(() => props.file, (newFile) => {
  if (newFile) {
    showPreview.value = false
    fileContent.value = ''
    
    // 如果是文本文件，自动加载预览
    if (isTextFile.value) {
      showPreview.value = true
      loadFilePreview()
    } else if (isImageFile.value) {
      showPreview.value = true
    }
  }
})
</script>

<style scoped lang="scss">
.file-info-modal {
  .info-section {
    margin-bottom: 24px;

    .section-title {
      margin-bottom: 12px;
      color: #262626;
      font-size: 14px;
      font-weight: 500;
    }

    .file-header {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 16px;
      background: #fafafa;
      border-radius: 8px;

      .file-icon {
        font-size: 48px;
      }

      .file-basic {
        .file-name {
          margin-bottom: 4px;
          font-size: 18px;
          font-weight: 500;
          color: #262626;
        }

        .file-type {
          margin: 0;
          color: #8c8c8c;
          font-size: 14px;
        }
      }
    }

    .action-buttons {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }

    .file-path {
      .path-input {
        font-family: monospace;

        .copy-icon {
          cursor: pointer;
          color: #1890ff;

          &:hover {
            color: #40a9ff;
          }
        }
      }
    }

    .file-preview {
      .image-preview {
        text-align: center;
        padding: 16px;
        background: #fafafa;
        border-radius: 8px;
      }

      .text-preview {
        :deep(.ant-input) {
          font-family: monospace;
          font-size: 12px;
          line-height: 1.6;
          resize: none;
        }
      }

      .preview-placeholder {
        text-align: center;
        padding: 40px 16px;
        background: #fafafa;
        border-radius: 8px;
      }
    }
  }
}

@media (max-width: 768px) {
  .file-info-modal {
    .info-section {
      .file-header {
        flex-direction: column;
        text-align: center;

        .file-icon {
          font-size: 64px;
        }
      }

      .action-buttons {
        justify-content: center;
      }
    }
  }
}
</style> 