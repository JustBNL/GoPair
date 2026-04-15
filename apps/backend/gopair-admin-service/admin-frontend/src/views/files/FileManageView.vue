<script setup lang="ts">
import { ref, reactive } from 'vue'
import { message } from 'ant-design-vue'
import type { TableProps } from 'ant-design-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import { fileApi } from '@/api/files'
import { formatTime, formatFileSize } from '@/utils/format'
import type { RoomFile, FileQuery } from '@/types'

const loading    = ref(false)
const fileList  = ref<RoomFile[]>([])
const pagination = reactive({ total: 0, current: 1, pageSize: 20 })
const searchKw  = ref('')
const viewMode  = ref<'table' | 'grid'>('table')

const drawerVisible  = ref(false)
const drawerLoading = ref(false)
const fileDetail    = ref<RoomFile | null>(null)

const confirmOpen    = ref(false)
const confirmTarget  = ref<RoomFile | null>(null)
const confirmLoading = ref(false)

async function loadFiles() {
  loading.value = true
  try {
    const params: FileQuery = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      keyword: searchKw.value || undefined,
    }
    const { data } = await fileApi.getPage(params)
    fileList.value   = data.records as RoomFile[]
    pagination.total = data.total
  } finally {
    loading.value = false
  }
}

const isImage = (file: RoomFile) =>
  ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'].includes(file.fileType.toLowerCase())

const columns = [
  { title: '文件ID',   dataIndex: 'fileId',          key: 'fileId',          width: 80 },
  { title: '文件名',   dataIndex: 'fileName',        key: 'fileName',        ellipsis: true },
  { title: '类型',     dataIndex: 'fileType',        key: 'fileType',        width: 80 },
  { title: '大小',     dataIndex: 'fileSize',        key: 'fileSize',        width: 100 },
  { title: '上传者',   dataIndex: 'uploaderNickname', key: 'uploaderNickname', width: 100 },
  { title: '下载次数', dataIndex: 'downloadCount',    key: 'downloadCount',    width: 100 },
  { title: '上传时间', dataIndex: 'uploadTime',       key: 'uploadTime',      width: 170 },
  { title: '操作',     key: 'actions',               width: 120 },
]

async function handleView(fileId: number) {
  drawerVisible.value  = true
  drawerLoading.value  = true
  fileDetail.value     = null
  try {
    const { data } = await fileApi.getDetail(fileId)
    fileDetail.value = data
  } catch {
    message.error('获取文件详情失败')
  } finally {
    drawerLoading.value = false
  }
}

function openDeleteConfirm(file: RoomFile) {
  confirmTarget.value = file
  confirmOpen.value    = true
}

async function handleConfirm() {
  if (!confirmTarget.value) return
  confirmLoading.value = true
  try {
    await fileApi.deleteFile(confirmTarget.value.fileId)
    message.success('文件记录已删除')
    loadFiles()
  } catch (e: unknown) {
    const err = e as { message?: string }
    message.error(err?.message || '删除失败')
  } finally {
    confirmLoading.value = false
  }
}

const onPageChange: TableProps['onChange'] = (pag) => {
  pagination.current = pag.current as number
  pagination.pageSize = pag.pageSize as number
  loadFiles()
}

let searchTimer: ReturnType<typeof setTimeout>
function onSearch(value: string) {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchKw.value = value
    pagination.current = 1
    loadFiles()
  }, 350)
}

loadFiles()
</script>

<template>
  <div class="file-manage-view">
    <PageHeader title="文件管理" description="查看和管理所有上传文件" />

    <div class="file-manage-view__toolbar">
      <a-input-search
        v-model:value="searchKw"
        placeholder="搜索文件名"
        @search="onSearch"
        @change="() => { pagination.current = 1; loadFiles() }"
        style="width: 280px;"
      />
      <a-radio-group v-model:value="viewMode">
        <a-radio-button value="table">列表</a-radio-button>
        <a-radio-button value="grid">网格</a-radio-button>
      </a-radio-group>
    </div>

    <template v-if="viewMode === 'table'">
      <a-table
        :columns="columns"
        :data-source="fileList"
        :loading="loading"
        :pagination="{ ...pagination, showSizeChanger: true, showTotal: (total: number) => `共 ${total} 条` }"
        :scroll="{ x: 800 }"
        class="file-manage-view__table"
        @change="onPageChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'fileId'">
            <span class="file-manage-view__mono">{{ record.fileId }}</span>
          </template>
          <template v-else-if="column.key === 'fileSize'">
            <span class="file-manage-view__muted">{{ formatFileSize(record.fileSize) }}</span>
          </template>
          <template v-else-if="column.key === 'uploadTime'">
            <span class="file-manage-view__muted">{{ formatTime(record.uploadTime) }}</span>
          </template>
          <template v-else-if="column.key === 'actions'">
            <div class="file-manage-view__actions">
              <a-button type="link" size="small" @click="handleView(record.fileId)">详情</a-button>
              <a-button type="link" size="small" danger @click="openDeleteConfirm(record)">删除</a-button>
            </div>
          </template>
        </template>
      </a-table>
    </template>

    <template v-else>
      <div class="file-manage-view__grid">
        <div v-for="file in fileList" :key="file.fileId" class="file-card">
          <template v-if="isImage(file)">
            <a-image :src="file.filePath" :alt="file.fileName" class="file-card__image" :preview-mask="false" />
          </template>
          <template v-else>
            <div class="file-card__icon">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/>
              </svg>
              <span class="file-card__ext">{{ file.fileType.toUpperCase() }}</span>
            </div>
          </template>
          <div class="file-card__info">
            <div class="file-card__name" :title="file.fileName">{{ file.fileName }}</div>
            <div class="file-card__meta">{{ formatFileSize(file.fileSize) }} · {{ formatTime(file.uploadTime, 'MM-DD HH:mm') }}</div>
            <div class="file-card__actions">
              <a v-if="isImage(file)" :href="file.filePath" target="_blank">
                <a-button type="link" size="small">预览</a-button>
              </a>
              <a :href="file.filePath" target="_blank" class="file-card__link" download>
                <a-button type="link" size="small">下载</a-button>
              </a>
              <a-button type="link" size="small" danger @click="openDeleteConfirm(file)">删除</a-button>
            </div>
          </div>
        </div>
      </div>
      <div class="file-manage-view__pagination">
        <a-pagination
          v-model:current="pagination.current"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :show-size-changer="true"
          :show-total="(total: number) => `共 ${total} 条`"
          @change="() => loadFiles()"
        />
      </div>
    </template>

    <a-drawer v-model:open="drawerVisible" title="文件详情" :width="480" :loading="drawerLoading">
      <template v-if="fileDetail">
        <template v-if="isImage(fileDetail)">
          <a-image :src="fileDetail.filePath" :alt="fileDetail.fileName" style="width: 100%; border-radius: var(--radius-md);" />
        </template>
        <a-descriptions :column="1" bordered size="small" style="margin-top: var(--space-4);">
          <a-descriptions-item label="文件名">{{ fileDetail.fileName }}</a-descriptions-item>
          <a-descriptions-item label="文件类型">{{ fileDetail.fileType }}</a-descriptions-item>
          <a-descriptions-item label="文件大小">{{ formatFileSize(fileDetail.fileSize) }}</a-descriptions-item>
          <a-descriptions-item label="上传者">{{ fileDetail.uploaderNickname }}</a-descriptions-item>
          <a-descriptions-item label="下载次数">{{ fileDetail.downloadCount }}</a-descriptions-item>
          <a-descriptions-item label="上传时间">{{ formatTime(fileDetail.uploadTime) }}</a-descriptions-item>
          <a-descriptions-item label="文件路径">
            <a :href="fileDetail.filePath" target="_blank" download style="font-size: 12px; word-break: break-all;">{{ fileDetail.filePath }}</a>
          </a-descriptions-item>
        </a-descriptions>
      </template>
    </a-drawer>

    <ConfirmModal
      v-model:open="confirmOpen"
      title="删除文件记录"
      :content="`确定删除文件「${confirmTarget?.fileName}」的元数据记录？实际文件不会删除。`"
      confirm-text="删除记录"
      danger
      :loading="confirmLoading"
      @confirm="handleConfirm"
    />
  </div>
</template>

<style scoped>
.file-manage-view__toolbar {
  margin-bottom: var(--space-4);
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.file-manage-view__table {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.file-manage-view__mono { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); }
.file-manage-view__muted { font-size: 13px; color: var(--color-text-muted); }
.file-manage-view__actions { display: flex; gap: var(--space-2); align-items: center; }

.file-manage-view__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: var(--space-4);
}

.file-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: border-color var(--transition-fast);
}
.file-card:hover { border-color: var(--color-border-hover); }

.file-card__image {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  display: block;
}

.file-card__icon {
  width: 100%;
  aspect-ratio: 4 / 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background-color: var(--color-surface-hover);
  color: var(--color-text-muted);
  gap: var(--space-2);
}

.file-card__ext {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.file-card__info { padding: var(--space-3); }
.file-card__name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: var(--space-1);
}
.file-card__meta {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-bottom: var(--space-2);
}
.file-card__actions { display: flex; gap: var(--space-2); }
.file-card__link { text-decoration: none; }

.file-manage-view__pagination {
  margin-top: var(--space-4);
  display: flex;
  justify-content: flex-end;
}
</style>
