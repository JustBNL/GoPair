<template>
  <div class="file-list">
    <!-- 文件统计信息 -->
    <div class="file-stats">
      <div class="stats-item">
        <span class="stats-label">文件总数：</span>
        <span class="stats-value">{{ pagination.total }}</span>
      </div>
      <div class="stats-item">
        <span class="stats-label">已用空间：</span>
        <span class="stats-value">{{ formatFileSize(totalSize) }}</span>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="file-toolbar">
      <!-- 搜索框 -->
      <a-input-search
        v-model:value="searchKeyword"
        placeholder="搜索文件名..."
        :loading="loading"
        @search="handleSearch"
        class="search-input"
      />

      <!-- 文件类型筛选 -->
      <a-select
        v-model:value="selectedFileType"
        placeholder="文件类型"
        :options="fileTypeOptions"
        @change="handleTypeFilter"
        class="type-filter"
        allowClear
      />

      <!-- 排序方式 -->
      <a-select
        v-model:value="sortBy"
        :options="sortOptions"
        @change="handleSort"
        class="sort-select"
      />

      <!-- 批量操作 -->
      <a-dropdown v-if="selectedFiles.length > 0" :trigger="['click']">
        <a-button type="primary">
          批量操作 ({{ selectedFiles.length }})
          <down-outlined />
        </a-button>
        <template #overlay>
          <a-menu>
            <a-menu-item key="download" @click="batchDownload">
              <download-outlined />
              批量下载
            </a-menu-item>
            <a-menu-divider />
            <a-menu-item key="delete" danger @click="batchDelete">
              <delete-outlined />
              批量删除
            </a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </div>

    <!-- 文件列表 -->
    <div class="file-content">
      <a-spin :spinning="loading">
        <div v-if="fileList.length === 0" class="empty-state">
          <a-empty description="暂无文件">
            <template #image>
              <folder-open-outlined style="font-size: 64px; color: #d9d9d9;" />
            </template>
          </a-empty>
        </div>

        <div v-else class="file-items">
          <div
            v-for="file in fileList"
            :key="file.fileId"
            :class="[
              'file-item',
              { 'selected': selectedFiles.includes(file.fileId) }
            ]"
            @click="selectFile(file)"
          >
            <!-- 选择框 -->
            <div class="file-select">
              <a-checkbox
                :checked="selectedFiles.includes(file.fileId)"
                @change="toggleSelect(file.fileId)"
                @click.stop
              />
            </div>

            <!-- 文件图标 -->
            <div class="file-icon">
              <file-icon :file-type="file.fileType" />
            </div>

            <!-- 文件信息 -->
            <div class="file-info">
              <div class="file-name" :title="file.fileName">
                {{ file.fileName }}
              </div>
              <div class="file-meta">
                <span class="file-size">{{ file.fileSizeFormatted }}</span>
                <span class="file-uploader">{{ file.uploaderNickname }}</span>
                <span class="file-time">{{ formatTime(file.uploadTime) }}</span>
              </div>
              <div class="file-stats">
                <span class="download-count">下载 {{ file.downloadCount }} 次</span>
              </div>
            </div>

            <!-- 文件操作 -->
            <div class="file-actions" @click.stop>
              <!-- 预览按钮 -->
              <a-tooltip title="预览">
                <a-button
                  v-if="file.previewable"
                  type="text"
                  size="small"
                  @click="previewFile(file)"
                >
                  <eye-outlined />
                </a-button>
              </a-tooltip>

              <!-- 下载按钮 -->
              <a-tooltip title="下载">
                <a-button
                  type="text"
                  size="small"
                  @click="downloadFile(file)"
                >
                  <download-outlined />
                </a-button>
              </a-tooltip>

              <!-- 更多操作 -->
              <a-dropdown :trigger="['click']">
                <a-button type="text" size="small">
                  <more-outlined />
                </a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item key="info" @click="showFileInfo(file)">
                      <info-circle-outlined />
                      文件信息
                    </a-menu-item>
                    <a-menu-item key="share" @click="shareFile(file)">
                      <share-alt-outlined />
                      分享链接
                    </a-menu-item>
                    <a-menu-divider v-if="canDelete(file)" />
                    <a-menu-item 
                      v-if="canDelete(file)"
                      key="delete" 
                      danger 
                      @click="deleteFile(file)"
                    >
                      <delete-outlined />
                      删除
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </div>
          </div>
        </div>

        <!-- 分页器 -->
        <div v-if="fileList.length > 0" class="file-pagination">
          <a-pagination
            v-model:current="pagination.current"
            v-model:page-size="pagination.pageSize"
            :total="pagination.total"
            :show-size-changer="false"
            :show-quick-jumper="true"
            @change="handlePageChange"
          />
        </div>
      </a-spin>
    </div>

    <!-- 文件信息模态框 -->
    <file-info-modal
      v-model:open="showInfoModal"
      :file="selectedFileInfo"
    />

    <!-- 文件预览模态框 -->
    <file-preview-modal
      v-model:open="showPreviewModal"
      :file="previewFileInfo"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { message as antMessage, Modal } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  DownOutlined,
  DownloadOutlined,
  DeleteOutlined,
  FolderOpenOutlined,
  EyeOutlined,
  MoreOutlined,
  InfoCircleOutlined,
  ShareAltOutlined
} from '@ant-design/icons-vue'
import { FileAPI, type RoomFileStats } from '@/api/file'
import type { FileVO, PageResult } from '@/types/api'
import FileIcon from './FileIcon.vue'
import FileInfoModal from './FileInfoModal.vue'
import FilePreviewModal from './FilePreviewModal.vue'

interface Props {
  roomId: number
  refresh?: boolean
}

interface Emits {
  (e: 'file-selected', file: FileVO): void
  (e: 'file-deleted', fileId: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// 数据状态
const loading = ref(false)
const fileList = ref<FileVO[]>([])
const selectedFiles = ref<number[]>([])
const totalSize = ref(0)

// 搜索和筛选
const searchKeyword = ref('')
const selectedFileType = ref<string>()
const sortBy = ref('uploadTime_desc')

// 分页
const pagination = ref({
  current: 1,
  pageSize: 20,
  total: 0
})

// 模态框
const showInfoModal = ref(false)
const showPreviewModal = ref(false)
const selectedFileInfo = ref<FileVO | null>(null)
const previewFileInfo = ref<FileVO | null>(null)

// 文件类型选项
const fileTypeOptions = [
  { label: '图片', value: 'image' },
  { label: '文档', value: 'document' },
  { label: '视频', value: 'video' },
  { label: '音频', value: 'audio' },
  { label: '压缩包', value: 'archive' },
  { label: '其他', value: 'other' }
]

// 排序选项
const sortOptions = [
  { label: '上传时间↓', value: 'uploadTime_desc' },
  { label: '上传时间↑', value: 'uploadTime_asc' },
  { label: '文件大小↓', value: 'fileSize_desc' },
  { label: '文件大小↑', value: 'fileSize_asc' },
  { label: '文件名↓', value: 'fileName_desc' },
  { label: '文件名↑', value: 'fileName_asc' }
]

/**
 * 加载文件列表
 */
const loadFileList = async () => {
  try {
    loading.value = true

    const response = await FileAPI.getRoomFiles(
      props.roomId,
      pagination.value.current,
      pagination.value.pageSize
    )

    fileList.value = response.data.records
    pagination.value.total = response.data.total

    // 加载统计信息
    await loadFileStats()

  } catch (error) {
    antMessage.error('加载文件列表失败')
  } finally {
    loading.value = false
  }
}

/**
 * 加载文件统计信息
 */
const loadFileStats = async () => {
  try {
    const response = await FileAPI.getRoomFileStats(props.roomId)
    totalSize.value = response.data.totalSize
  } catch (error) {
    console.error('加载文件统计失败:', error)
  }
}

/**
 * 处理搜索
 */
const handleSearch = () => {
  pagination.value.current = 1
  loadFileList()
}

/**
 * 处理类型筛选
 */
const handleTypeFilter = () => {
  pagination.value.current = 1
  loadFileList()
}

/**
 * 处理排序
 */
const handleSort = () => {
  pagination.value.current = 1
  loadFileList()
}

/**
 * 处理分页变化
 */
const handlePageChange = (page: number) => {
  pagination.value.current = page
  loadFileList()
}

/**
 * 选择文件
 */
const selectFile = (file: FileVO) => {
  emit('file-selected', file)
}

/**
 * 切换选择状态
 */
const toggleSelect = (fileId: number) => {
  const index = selectedFiles.value.indexOf(fileId)
  if (index > -1) {
    selectedFiles.value.splice(index, 1)
  } else {
    selectedFiles.value.push(fileId)
  }
}

/**
 * 预览文件
 */
const previewFile = (file: FileVO) => {
  previewFileInfo.value = file
  showPreviewModal.value = true
}

/**
 * 下载文件
 */
const downloadFile = async (file: FileVO) => {
  try {
    const blob = await FileAPI.downloadFile(file.fileId)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = file.fileName
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
 * 显示文件信息
 */
const showFileInfo = (file: FileVO) => {
  selectedFileInfo.value = file
  showInfoModal.value = true
}

/**
 * 分享文件
 */
const shareFile = async (file: FileVO) => {
  try {
    const response = await FileAPI.createShareLink(file.fileId)
    await navigator.clipboard.writeText(response.data.shareUrl)
    antMessage.success('分享链接已复制到剪贴板')
  } catch (error) {
    antMessage.error('创建分享链接失败')
  }
}

/**
 * 删除文件
 */
const deleteFile = (file: FileVO) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除文件 "${file.fileName}" 吗？`,
    onOk: async () => {
      try {
        await FileAPI.deleteFile(file.fileId)
        antMessage.success('删除成功')
        emit('file-deleted', file.fileId)
        loadFileList()
      } catch (error) {
        antMessage.error('删除失败')
      }
    }
  })
}

/**
 * 批量下载
 */
const batchDownload = async () => {
  try {
    const blob = await FileAPI.batchDownloadFiles(selectedFiles.value)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `files_${Date.now()}.zip`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    
    antMessage.success('批量下载成功')
    selectedFiles.value = []
  } catch (error) {
    antMessage.error('批量下载失败')
  }
}

/**
 * 批量删除
 */
const batchDelete = () => {
  Modal.confirm({
    title: '确认批量删除',
    content: `确定要删除选中的 ${selectedFiles.value.length} 个文件吗？`,
    onOk: async () => {
      try {
        await FileAPI.batchDeleteFiles(selectedFiles.value)
        antMessage.success('批量删除成功')
        selectedFiles.value = []
        loadFileList()
      } catch (error) {
        antMessage.error('批量删除失败')
      }
    }
  })
}

/**
 * 检查是否可以删除
 */
const canDelete = (file: FileVO): boolean => {
  // TODO: 检查权限，这里简化处理
  return true
}

/**
 * 格式化时间
 */
const formatTime = (timeStr: string) => {
  return dayjs(timeStr).format('MM-DD HH:mm')
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

// 监听刷新标志
watch(() => props.refresh, () => {
  if (props.refresh) {
    loadFileList()
  }
})

// 组件挂载时加载数据
onMounted(() => {
  loadFileList()
})
</script>

<style scoped lang="scss">
.file-list {
  .file-stats {
    display: flex;
    gap: 24px;
    padding: 16px;
    background: #fafafa;
    border-radius: 8px;
    margin-bottom: 16px;

    .stats-item {
      .stats-label {
        color: #8c8c8c;
        font-size: 12px;
      }

      .stats-value {
        color: #262626;
        font-weight: 500;
        margin-left: 4px;
      }
    }
  }

  .file-toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;

    .search-input {
      width: 200px;
    }

    .type-filter {
      width: 120px;
    }

    .sort-select {
      width: 140px;
    }
  }

  .file-content {
    .empty-state {
      padding: 40px;
      text-align: center;
    }

    .file-items {
      .file-item {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 12px;
        border: 1px solid #f0f0f0;
        border-radius: 8px;
        margin-bottom: 8px;
        cursor: pointer;
        transition: all 0.2s;

        &:hover {
          background: #fafafa;
          border-color: #d9d9d9;
        }

        &.selected {
          background: #e6f7ff;
          border-color: #91d5ff;
        }

        .file-select {
          flex-shrink: 0;
        }

        .file-icon {
          flex-shrink: 0;
          font-size: 32px;
        }

        .file-info {
          flex: 1;
          min-width: 0;

          .file-name {
            font-weight: 500;
            margin-bottom: 4px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }

          .file-meta {
            display: flex;
            align-items: center;
            gap: 12px;
            font-size: 12px;
            color: #8c8c8c;
            margin-bottom: 2px;

            .file-size {
              font-weight: 500;
              color: #595959;
            }
          }

          .file-stats {
            font-size: 12px;
            color: #8c8c8c;

            .download-count {
              color: #1890ff;
            }
          }
        }

        .file-actions {
          display: flex;
          align-items: center;
          gap: 4px;
          opacity: 0;
          transition: opacity 0.2s;
        }

        &:hover .file-actions {
          opacity: 1;
        }
      }
    }

    .file-pagination {
      margin-top: 24px;
      text-align: center;
    }
  }
}

@media (max-width: 768px) {
  .file-list {
    .file-toolbar {
      flex-direction: column;
      align-items: stretch;
      gap: 8px;

      .search-input,
      .type-filter,
      .sort-select {
        width: 100%;
      }
    }

    .file-content {
      .file-items {
        .file-item {
          .file-info {
            .file-meta {
              flex-direction: column;
              align-items: flex-start;
              gap: 2px;
            }
          }

          .file-actions {
            opacity: 1;
          }
        }
      }
    }
  }
}
</style> 