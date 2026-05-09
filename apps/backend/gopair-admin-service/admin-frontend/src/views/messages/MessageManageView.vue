<script setup lang="ts">
import { ref, reactive } from 'vue'
import type { TableProps } from 'ant-design-vue'
import type { Dayjs } from 'dayjs'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { messageApi } from '@/api/messages'
import { formatTime } from '@/utils/format'
import type { Message, MessageQuery } from '@/types'

const loading    = ref(false)
const msgList   = ref<Message[]>([])
const pagination = reactive({ total: 0, current: 1, pageSize: 20 })
const searchKw   = ref('')

const filters = reactive({
  roomId: undefined as number | undefined,
  senderId: undefined as number | undefined,
  ownerId: undefined as number | undefined,
  messageType: undefined as number | undefined,
  isRecalled: undefined as boolean | undefined,
})

const dateRange = ref<[Dayjs | null, Dayjs | null]>([null, null])

const messageTypeOptions = [
  { label: '全部类型', value: undefined },
  { label: '文本',     value: 1 },
  { label: '图片',     value: 2 },
  { label: '文件',     value: 3 },
  { label: '语音',     value: 4 },
  { label: 'Emoji',   value: 5 },
]

const recalledOptions = [
  { label: '全部',   value: undefined },
  { label: '未撤回', value: false },
  { label: '已撤回', value: true },
]

async function loadMessages() {
  loading.value = true
  try {
    const [startDate, endDate] = dateRange.value
    const params: MessageQuery = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      ...filters,
      keyword: searchKw.value || undefined,
      startTime: startDate ? startDate.format('YYYY-MM-DD') : undefined,
      endTime: endDate ? endDate.format('YYYY-MM-DD') : undefined,
    }
    const res = await messageApi.getPage(params)
    msgList.value    = res.records as Message[]
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

const columns = [
  { title: '消息ID',     dataIndex: 'messageId',      key: 'messageId',      width: 90 },
  { title: '房间号',     dataIndex: 'roomId',          key: 'roomId',          width: 90 },
  { title: '房间名称',   dataIndex: 'roomName',        key: 'roomName',        width: 130, ellipsis: true },
  { title: '房主ID',    dataIndex: 'ownerId',          key: 'ownerId',         width: 90 },
  { title: '发送者ID',   dataIndex: 'senderId',        key: 'senderId',        width: 90 },
  { title: '发送者昵称', dataIndex: 'senderNickname',  key: 'senderNickname',  width: 110, ellipsis: true },
  { title: '类型',       dataIndex: 'messageType',    key: 'messageType',     width: 90 },
  { title: '内容',       dataIndex: 'content',         key: 'content',          ellipsis: true },
  { title: '是否撤回',   dataIndex: 'isRecalled',      key: 'isRecalled',      width: 80 },
  { title: '发送时间',   dataIndex: 'createTime',      key: 'createTime',      width: 170 },
]

const onPageChange: TableProps['onChange'] = (pag) => {
  pagination.current = pag.current as number
  pagination.pageSize = pag.pageSize as number
  loadMessages()
}

function onKeywordChange(value: string) {
  searchKw.value = value
  pagination.current = 1
}

function onFilterChange() {
  pagination.current = 1
}

function onReset() {
  searchKw.value = ''
  filters.roomId = undefined
  filters.senderId = undefined
  filters.ownerId = undefined
  filters.messageType = undefined
  filters.isRecalled = undefined
  dateRange.value = [null, null]
  pagination.current = 1
  loadMessages()
}

loadMessages()
</script>

<template>
  <div class="message-manage-view">
    <PageHeader title="消息管理" description="查看所有房间消息记录" />

    <div class="message-manage-view__toolbar">
      <a-input-number
        v-model:value="filters.roomId"
        placeholder="房间号"
        :min="1"
        class="message-manage-view__number-input"
        @change="onFilterChange"
      />
      <a-input-number
        v-model:value="filters.senderId"
        placeholder="发送者ID"
        :min="1"
        class="message-manage-view__number-input"
        @change="onFilterChange"
      />
      <a-input-number
        v-model:value="filters.ownerId"
        placeholder="房主ID"
        :min="1"
        class="message-manage-view__number-input"
        @change="onFilterChange"
      />
      <a-select
        v-model:value="filters.messageType"
        placeholder="消息类型"
        :options="messageTypeOptions"
        allow-clear
        class="message-manage-view__select"
        @change="onFilterChange"
      />
      <a-select
        v-model:value="filters.isRecalled"
        placeholder="是否撤回"
        :options="recalledOptions"
        allow-clear
        class="message-manage-view__select message-manage-view__select--sm"
        @change="onFilterChange"
      />
      <a-range-picker
        v-model:value="dateRange"
        value-format="YYYY-MM-DD"
        class="message-manage-view__date-range"
        @change="onFilterChange"
      />
      <a-input
        v-model:value="searchKw"
        aria-label="搜索消息内容"
        placeholder="搜索消息内容"
        @change="onKeywordChange"
        class="message-manage-view__search"
        allow-clear
      />
      <a-button type="primary" @click="loadMessages">查询</a-button>
      <a-button @click="onReset">重置</a-button>
    </div>

    <a-table
      :columns="columns"
      :data-source="msgList"
      :loading="loading"
      :pagination="{ ...pagination, showSizeChanger: true, showTotal: (total: number) => `共 ${total} 条` }"
      :scroll="{ x: 1300 }"
      class="message-manage-view__table"
      @change="onPageChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'messageId' || column.key === 'roomId' || column.key === 'senderId' || column.key === 'ownerId'">
          <span class="message-manage-view__mono">{{ record[column.dataIndex as keyof Message] }}</span>
        </template>
        <template v-else-if="column.key === 'messageType'">
          <StatusBadge :status="record.messageType" type="message" />
        </template>
        <template v-else-if="column.key === 'content'">
          <span class="message-manage-view__content">
            <span v-if="record.replyToId && record.replyToContent" class="message-manage-view__reply">
              <span class="message-manage-view__reply-sender">{{ record.replyToSenderNickname || '未知' }}</span>
              {{ ': ' + record.replyToContent }}
            </span>
            {{ record.content }}
          </span>
        </template>
        <template v-else-if="column.key === 'isRecalled'">
          <a-tag v-if="record.isRecalled" color="error">已撤回</a-tag>
          <a-tag v-else color="success">未撤回</a-tag>
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
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.message-manage-view__table {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.message-manage-view__number-input {
  width: 120px;
}

.message-manage-view__select {
  width: 120px;
}

.message-manage-view__select--sm {
  width: 100px;
}

.message-manage-view__date-range {
  width: 260px;
}

.message-manage-view__search {
  width: 200px;
}

.message-manage-view__mono {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--color-text-secondary);
}

.message-manage-view__muted {
  font-size: 13px;
  color: var(--color-text-muted);
}

.message-manage-view__content {
  max-width: 300px;
}

.message-manage-view__reply {
  display: block;
  color: var(--color-text-muted);
  font-size: 12px;
  margin-bottom: 2px;
  border-left: 2px solid var(--color-border);
  padding-left: 6px;
}

.message-manage-view__reply-sender {
  font-weight: 500;
  color: var(--color-primary);
}
</style>
