<script setup lang="ts">
import { ref, reactive } from 'vue'
import type { TableProps } from 'ant-design-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { messageApi } from '@/api/messages'
import { formatTime } from '@/utils/format'
import type { Message, MessageQuery } from '@/types'

const loading    = ref(false)
const msgList   = ref<Message[]>([])
const pagination = reactive({ total: 0, current: 1, pageSize: 20 })
const searchKw  = ref('')

async function loadMessages() {
  loading.value = true
  try {
    const params: MessageQuery = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      keyword: searchKw.value || undefined,
    }
    const res = await messageApi.getPage(params)
    msgList.value    = res.records as Message[]
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

const columns = [
  { title: '消息ID',   dataIndex: 'messageId',  key: 'messageId',  width: 90 },
  { title: '房间ID',   dataIndex: 'roomId',       key: 'roomId',      width: 90 },
  { title: '发送者ID', dataIndex: 'senderId',     key: 'senderId',    width: 100 },
  { title: '类型',     dataIndex: 'messageType', key: 'messageType', width: 90 },
  { title: '内容',     dataIndex: 'content',      key: 'content',     ellipsis: true },
  { title: '发送时间', dataIndex: 'createTime',   key: 'createTime',  width: 170 },
]

const onPageChange: TableProps['onChange'] = (pag) => {
  pagination.current = pag.current as number
  pagination.pageSize = pag.pageSize as number
  loadMessages()
}

let searchTimer: ReturnType<typeof setTimeout>
function onSearch(value: string) {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchKw.value = value
    pagination.current = 1
    loadMessages()
  }, 350)
}

loadMessages()
</script>

<template>
  <div class="message-manage-view">
    <PageHeader title="消息管理" description="查看所有房间消息记录" />

    <div class="message-manage-view__toolbar">
      <a-input-search
        v-model:value="searchKw"
        aria-label="搜索消息内容"
        placeholder="搜索消息内容"
        @search="onSearch"
        @change="() => { pagination.current = 1; loadMessages() }"
        style="width: 280px;"
      />
    </div>

    <a-table
      :columns="columns"
      :data-source="msgList"
      :loading="loading"
      :pagination="{ ...pagination, showSizeChanger: true, showTotal: (total: number) => `共 ${total} 条` }"
      :scroll="{ x: 700 }"
      class="message-manage-view__table"
      @change="onPageChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'messageId' || column.key === 'roomId' || column.key === 'senderId'">
          <span class="message-manage-view__mono">{{ record[column.dataIndex as keyof Message] }}</span>
        </template>
        <template v-else-if="column.key === 'messageType'">
          <StatusBadge :status="record.messageType" type="message" />
        </template>
        <template v-else-if="column.key === 'createTime'">
          <span class="message-manage-view__muted">{{ formatTime(record.createTime) }}</span>
        </template>
      </template>
    </a-table>
  </div>
</template>

<style scoped>
.message-manage-view__toolbar {
  margin-bottom: var(--space-4);
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.message-manage-view__table {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.message-manage-view__mono { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); }
.message-manage-view__muted { font-size: 13px; color: var(--color-text-muted); }
</style>
