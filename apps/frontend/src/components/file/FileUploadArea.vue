<template>
  <div class="file-upload-area">
    <!-- 拖拽上传区域 -->
    <a-upload-dragger
      v-model:file-list="fileList"
      :multiple="true"
      :before-upload="beforeUpload"
      :show-upload-list="false"
      @drop="handleDrop"
      @change="handleChange"
      class="upload-dragger"
    >
      <div class="upload-content">
        <div class="upload-icon">
          <cloud-upload-outlined />
        </div>
        <div class="upload-text">
          <h4>点击或拖拽文件到此区域上传</h4>
          <p>支持单个或批量上传，最大文件大小100MB</p>
        </div>
      </div>
    </a-upload-dragger>

    <!-- 上传列表 -->
    <div v-if="uploadingFiles.length > 0" class="upload-list">
      <h4>上传中的文件</h4>
      <div class="upload-items">
        <div
          v-for="file in uploadingFiles"
          :key="file.uid"
          class="upload-item"
        >
          <div class="file-info">
            <div class="file-icon">
              <file-icon :file-type="getFileType(file.name)" />
            </div>
            <div class="file-details">
              <div class="file-name">{{ file.name }}</div>
              <div class="file-size">{{ formatFileSize(file.size) }}</div>
            </div>
          </div>
          
          <div class="upload-progress">
            <a-progress
              :percent="file.percent"
              :status="file.status === 'error' ? 'exception' : 'active'"
              size="small"
            />
          </div>

          <div class="upload-actions">
            <a-button
              v-if="file.status === 'uploading'"
              type="text"
              size="small"
              @click="cancelUpload(file)"
            >
              <stop-outlined />
            </a-button>
            <a-button
              v-else-if="file.status === 'error'"
              type="text"
              size="small"
              @click="retryUpload(file)"
            >
              <reload-outlined />
            </a-button>
            <a-button
              v-else
              type="text"
              size="small"
              @click="removeFile(file)"
            >
              <delete-outlined />
            </a-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 上传限制提示 -->
    <div class="upload-tips">
      <a-alert
        message="上传提示"
        type="info"
        show-icon
        closable
      >
        <template #description>
          <ul class="tips-list">
            <li>单个文件最大支持100MB</li>
            <li>房间文件总容量限制1GB</li>
            <li>支持的文件类型：{{ allowedTypes.join('、') }}</li>
            <li>房间关闭后所有文件将被自动删除</li>
          </ul>
        </template>
      </a-alert>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import {
  CloudUploadOutlined,
  StopOutlined,
  ReloadOutlined,
  DeleteOutlined
} from '@ant-design/icons-vue'
import { FileAPI, type UploadProgressCallback } from '@/api/file'
import type { FileVO } from '@/types/api'
import FileIcon from './FileIcon.vue'

interface Props {
  roomId: number
  disabled?: boolean
  maxFileSize?: number // MB
  allowedTypes?: string[]
}

interface Emits {
  (e: 'upload-success', file: FileVO): void
  (e: 'upload-error', error: string): void
  (e: 'upload-progress', progress: { file: string; percent: number }): void
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  maxFileSize: 100,
  allowedTypes: () => [
    'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx',
    'txt', 'jpg', 'jpeg', 'png', 'gif', 'mp4', 'mp3',
    'zip', 'rar', '7z', 'tar', 'gz'
  ]
})

const emit = defineEmits<Emits>()

// 文件列表和上传状态
const fileList = ref<any[]>([])
const uploadingFiles = ref<any[]>([])

/**
 * 上传前检查
 */
const beforeUpload = (file: File) => {
  // 检查文件大小
  if (file.size > props.maxFileSize * 1024 * 1024) {
    antMessage.error(`文件大小不能超过${props.maxFileSize}MB`)
    return false
  }

  // 检查文件类型
  const fileType = getFileType(file.name)
  if (!props.allowedTypes.includes(fileType)) {
    antMessage.error(`不支持的文件类型：${fileType}`)
    return false
  }

  // 检查是否禁用
  if (props.disabled) {
    antMessage.error('当前无法上传文件')
    return false
  }

  // 开始上传
  startUpload(file)
  return false // 阻止默认上传
}

/**
 * 开始上传文件
 */
const startUpload = async (file: File) => {
  const uploadFile = {
    uid: Date.now().toString(),
    name: file.name,
    size: file.size,
    status: 'uploading',
    percent: 0,
    file: file
  }

  uploadingFiles.value.push(uploadFile)

  try {
    // 进度回调
    const onProgress: UploadProgressCallback = (progressEvent) => {
      const percent = progressEvent.total 
        ? Math.round((progressEvent.loaded / progressEvent.total) * 100)
        : 0
      
      uploadFile.percent = percent
      emit('upload-progress', { file: file.name, percent })
    }

    // 调用上传API
    const response = await FileAPI.uploadFile({
      roomId: props.roomId,
      file: file
    }, onProgress)

    // 上传成功
    uploadFile.status = 'done'
    uploadFile.percent = 100
    
    emit('upload-success', response.data)
    antMessage.success(`${file.name} 上传成功`)

    // 延迟移除上传项
    setTimeout(() => {
      removeFile(uploadFile)
    }, 2000)

  } catch (error: any) {
    // 上传失败
    uploadFile.status = 'error'
    uploadFile.percent = 0
    
    const errorMsg = error.response?.data?.msg || '上传失败'
    emit('upload-error', errorMsg)
    antMessage.error(`${file.name} 上传失败：${errorMsg}`)
  }
}

/**
 * 处理文件变化
 */
const handleChange = (info: any) => {
  // 文件列表变化处理
  console.log('File list changed:', info)
}

/**
 * 处理拖拽上传
 */
const handleDrop = (event: DragEvent) => {
  console.log('Files dropped:', event.dataTransfer?.files)
}

/**
 * 取消上传
 */
const cancelUpload = (file: any) => {
  file.status = 'removed'
  removeFile(file)
  antMessage.info(`已取消上传 ${file.name}`)
}

/**
 * 重试上传
 */
const retryUpload = (file: any) => {
  if (file.file) {
    removeFile(file)
    startUpload(file.file)
  }
}

/**
 * 移除文件
 */
const removeFile = (file: any) => {
  const index = uploadingFiles.value.findIndex(f => f.uid === file.uid)
  if (index > -1) {
    uploadingFiles.value.splice(index, 1)
  }
}

/**
 * 获取文件类型
 */
const getFileType = (fileName: string): string => {
  const extension = fileName.split('.').pop()?.toLowerCase()
  return extension || 'unknown'
}

/**
 * 格式化文件大小
 */
const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

/**
 * 获取支持的文件类型列表
 */
const allowedTypes = computed(() => props.allowedTypes)
</script>

<style scoped lang="scss">
.file-upload-area {
  .upload-dragger {
    :deep(.ant-upload-drag) {
      border: 2px dashed #d9d9d9;
      border-radius: 8px;
      background: #fafafa;
      transition: all 0.3s ease;

      &:hover {
        border-color: #1890ff;
        background: #f0f8ff;
      }

      &.ant-upload-drag-hover {
        border-color: #1890ff;
        background: #f0f8ff;
      }
    }

    .upload-content {
      padding: 40px 20px;
      text-align: center;

      .upload-icon {
        font-size: 48px;
        color: #d9d9d9;
        margin-bottom: 16px;
      }

      .upload-text {
        h4 {
          margin-bottom: 8px;
          color: #262626;
          font-size: 16px;
        }

        p {
          margin: 0;
          color: #8c8c8c;
          font-size: 14px;
        }
      }
    }
  }

  .upload-list {
    margin-top: 24px;
    padding: 16px;
    background: #fafafa;
    border-radius: 8px;

    h4 {
      margin-bottom: 16px;
      color: #262626;
      font-size: 14px;
      font-weight: 500;
    }

    .upload-items {
      .upload-item {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 12px;
        background: white;
        border-radius: 6px;
        margin-bottom: 8px;
        border: 1px solid #f0f0f0;

        &:last-child {
          margin-bottom: 0;
        }

        .file-info {
          display: flex;
          align-items: center;
          gap: 8px;
          flex: 1;

          .file-icon {
            font-size: 24px;
          }

          .file-details {
            .file-name {
              font-weight: 500;
              margin-bottom: 2px;
              word-break: break-all;
            }

            .file-size {
              font-size: 12px;
              color: #8c8c8c;
            }
          }
        }

        .upload-progress {
          flex: 1;
          max-width: 200px;
        }

        .upload-actions {
          flex-shrink: 0;
        }
      }
    }
  }

  .upload-tips {
    margin-top: 16px;

    .tips-list {
      margin: 0;
      padding-left: 16px;

      li {
        margin-bottom: 4px;
        font-size: 12px;
        color: #595959;

        &:last-child {
          margin-bottom: 0;
        }
      }
    }
  }
}

@media (max-width: 768px) {
  .file-upload-area {
    .upload-dragger {
      .upload-content {
        padding: 20px 16px;

        .upload-icon {
          font-size: 36px;
        }

        .upload-text {
          h4 {
            font-size: 14px;
          }

          p {
            font-size: 12px;
          }
        }
      }
    }

    .upload-list {
      .upload-items {
        .upload-item {
          flex-direction: column;
          align-items: stretch;
          gap: 8px;

          .file-info {
            justify-content: flex-start;
          }

          .upload-progress {
            max-width: none;
          }

          .upload-actions {
            align-self: flex-end;
          }
        }
      }
    }
  }
}
</style> 