<script setup lang="ts">
import { ref, reactive } from 'vue'
import { message } from 'ant-design-vue'
import type { TableProps } from 'ant-design-vue'
import type { Dayjs } from 'dayjs'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { voiceCallApi } from '@/api/voiceCalls'
import { formatTime, formatDuration } from '@/utils/format'
import type { VoiceCall, VoiceCallParticipant, CallQuery } from '@/types'

const loading    = ref(false)
const callList  = ref<VoiceCall[]>([])
const pagination = reactive({ total: 0, current: 1, pageSize: 20 })
const searchKw  = ref('')

const filters = reactive({
  roomId: undefined as number | undefined,
  initiatorId: undefined as number | undefined,
  callType: undefined as number | undefined,
  status: undefined as number | undefined,
})

const dateRange = ref<[Dayjs | null, Dayjs | null]>([null, null])

const callTypeOptions = [
  { label: '全部类型', value: undefined },
  { label: '语音', value: 0 },
  { label: '视频', value: 1 },
]

const statusOptions = [
  { label: '全部状态', value: undefined },
  { label: '进行中', value: 1 },
  { label: '已结束', value: 2 },
  { label: '已取消', value: 3 },
]

const drawerVisible    = ref(false)
const drawerLoading  = ref(false)
const callDetail     = ref<VoiceCall | null>(null)
const participants   = ref<VoiceCallParticipant[]>([])

async function loadCalls() {
  loading.value = true
  try {
    const [startDate, endDate] = dateRange.value
    const params: CallQuery = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      ...filters,
      keyword: searchKw.value || undefined,
      startTime: startDate ? startDate.format('YYYY-MM-DD') : undefined,
      endTime: endDate ? endDate.format('YYYY-MM-DD') : undefined,
    }
    const res = await voiceCallApi.getPage(params)
    callList.value   = res.records as VoiceCall[]
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

const columns = [
  { title: '通话ID',   dataIndex: 'callId',          key: 'callId',          width: 90 },
  { title: '房间号',   dataIndex: 'roomId',            key: 'roomId',          width: 90 },
  { title: '房间名称', dataIndex: 'roomName',          key: 'roomName',        width: 160, ellipsis: true },
  { title: '发起人ID', dataIndex: 'initiatorId',       key: 'initiatorId',     width: 100 },
  { title: '发起人',   dataIndex: 'initiatorNickname', key: 'initiatorNickname', width: 110, ellipsis: true },
  { title: '类型',     dataIndex: 'callType',         key: 'callType',        width: 90 },
  { title: '状态',     dataIndex: 'status',           key: 'status',          width: 90 },
  { title: '参与人数', dataIndex: 'participantCount',  key: 'participantCount', width: 100 },
  { title: '时长',     dataIndex: 'duration',          key: 'duration',        width: 90 },
  { title: '开始时间', dataIndex: 'startTime',         key: 'startTime',       width: 170 },
  { title: '操作',     key: 'actions',                width: 80 },
]

async function handleView(callId: number) {
  drawerVisible.value = true
  drawerLoading.value = true
  callDetail.value    = null
  participants.value  = []
  try {
    const [detailRes, partRes] = await Promise.all([
      voiceCallApi.getDetail(callId),
      voiceCallApi.getParticipants(callId),
    ])
    callDetail.value   = detailRes
    participants.value = partRes
  } catch {
    message.error('获取通话详情失败')
  } finally {
    drawerLoading.value = false
  }
}

const onPageChange: TableProps['onChange'] = (pag) => {
  pagination.current = pag.current as number
  pagination.pageSize = pag.pageSize as number
  loadCalls()
}

let searchTimer: ReturnType<typeof setTimeout>
function onSearch(value: string) {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchKw.value = value
    pagination.current = 1
    loadCalls()
  }, 350)
}

function onFilterChange() {
  pagination.current = 1
  loadCalls()
}

function onReset() {
  searchKw.value = ''
  filters.roomId = undefined
  filters.initiatorId = undefined
  filters.callType = undefined
  filters.status = undefined
  dateRange.value = [null, null]
  pagination.current = 1
  loadCalls()
}

const connectedCount = (parts: VoiceCallParticipant[]) =>
  parts.filter(p => p.connectionStatus === 1).length

loadCalls()
</script>

<template>
  <div class="voice-call-view">
    <PageHeader title="通话记录" description="查看语音通话记录和参与者" />

    <div class="voice-call-view__toolbar">
      <a-input-number
        v-model:value="filters.roomId"
        placeholder="房间号"
        :min="1"
        class="voice-call-view__number-input"
        @change="onFilterChange"
      />
      <a-input-number
        v-model:value="filters.initiatorId"
        placeholder="发起人ID"
        :min="1"
        class="voice-call-view__number-input"
        @change="onFilterChange"
      />
      <a-select
        v-model:value="filters.callType"
        placeholder="通话类型"
        :options="callTypeOptions"
        allow-clear
        class="voice-call-view__select"
        @change="onFilterChange"
      />
      <a-select
        v-model:value="filters.status"
        placeholder="状态"
        :options="statusOptions"
        allow-clear
        class="voice-call-view__select"
        @change="onFilterChange"
      />
      <a-range-picker
        v-model:value="dateRange"
        value-format="YYYY-MM-DD"
        class="voice-call-view__date-range"
        @change="onFilterChange"
      />
      <a-input-search
        v-model:value="searchKw"
        aria-label="搜索房间名/发起人"
        placeholder="搜索房间名/发起人"
        @search="onSearch"
        @change="() => { pagination.current = 1; loadCalls() }"
        class="voice-call-view__search"
      />
      <a-button @click="onReset">重置</a-button>
    </div>

    <a-table
      :columns="columns"
      :data-source="callList"
      :loading="loading"
      :pagination="{ ...pagination, showSizeChanger: true, showTotal: (total: number) => `共 ${total} 条` }"
      :scroll="{ x: 1300 }"
      class="voice-call-view__table"
      @change="onPageChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'callId' || column.key === 'roomId' || column.key === 'initiatorId'">
          <span class="voice-call-view__mono">{{ record[column.dataIndex as keyof VoiceCall] }}</span>
        </template>
        <template v-else-if="column.key === 'callType'">
          <StatusBadge :status="record.callType" type="callType" />
        </template>
        <template v-else-if="column.key === 'status'">
          <StatusBadge :status="record.status" type="call" />
        </template>
        <template v-else-if="column.key === 'participantCount'">
          <span class="voice-call-view__participant">
            <span class="voice-call-view__online">{{ record.connectedCount ?? 0 }}</span>
            <span class="voice-call-view__sep">/</span>
            <span>{{ record.participantCount ?? 0 }}</span>
          </span>
        </template>
        <template v-else-if="column.key === 'duration'">
          <span class="voice-call-view__muted">{{ formatDuration(record.duration) }}</span>
        </template>
        <template v-else-if="column.key === 'startTime'">
          <span class="voice-call-view__muted">{{ formatTime(record.startTime) }}</span>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-button type="link" size="small" @click="handleView(record.callId)">详情</a-button>
        </template>
      </template>
    </a-table>

    <a-drawer v-model:open="drawerVisible" title="通话详情" :width="520" :loading="drawerLoading">
      <template v-if="callDetail">
        <a-descriptions :column="1" bordered size="small">
          <a-descriptions-item label="通话ID">{{ callDetail.callId }}</a-descriptions-item>
          <a-descriptions-item label="房间号">{{ callDetail.roomId }}</a-descriptions-item>
          <a-descriptions-item label="房间名称">{{ callDetail.roomName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="发起人ID">{{ callDetail.initiatorId }}</a-descriptions-item>
          <a-descriptions-item label="发起人昵称">{{ callDetail.initiatorNickname || '—' }}</a-descriptions-item>
          <a-descriptions-item label="通话类型">
            <StatusBadge :status="callDetail.callType" type="callType" />
          </a-descriptions-item>
          <a-descriptions-item label="状态">
            <StatusBadge :status="callDetail.status" type="call" />
          </a-descriptions-item>
          <a-descriptions-item label="通话时长">{{ formatDuration(callDetail.duration) }}</a-descriptions-item>
          <a-descriptions-item label="开始时间">{{ formatTime(callDetail.startTime) }}</a-descriptions-item>
          <a-descriptions-item v-if="callDetail.endTime" label="结束时间">{{ formatTime(callDetail.endTime) }}</a-descriptions-item>
          <a-descriptions-item label="系统自动创建">{{ callDetail.isAutoCreated ? '是' : '否' }}</a-descriptions-item>
        </a-descriptions>

        <div class="voice-call-view__section-title">
          <span>参与者列表</span>
          <span class="voice-call-view__participant-summary">
            共 {{ participants.length }} 人参与，当前 {{ connectedCount(participants) }} 人在线
          </span>
        </div>
        <a-table
          :columns="[
            { title: '用户ID', dataIndex: 'userId', key: 'userId', width: 90 },
            { title: '加入时间', dataIndex: 'joinTime', key: 'joinTime', width: 160 },
            { title: '离开时间', dataIndex: 'leaveTime', key: 'leaveTime', width: 160 },
            { title: '连接状态', key: 'connectionStatus', width: 90 },
          ]"
          :data-source="participants"
          :pagination="false"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'userId'">
              <span class="voice-call-view__mono">{{ record.userId }}</span>
            </template>
            <template v-else-if="column.key === 'joinTime'">
              <span class="voice-call-view__muted">{{ formatTime(record.joinTime) }}</span>
            </template>
            <template v-else-if="column.key === 'leaveTime'">
              <span class="voice-call-view__muted">{{ formatTime(record.leaveTime) || '—' }}</span>
            </template>
            <template v-else-if="column.key === 'connectionStatus'">
              <StatusBadge :status="record.connectionStatus" type="connection" />
            </template>
          </template>
        </a-table>
      </template>
    </a-drawer>
  </div>
</template>

<style scoped>
.voice-call-view__toolbar {
  margin-bottom: var(--space-4);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.voice-call-view__number-input {
  width: 120px;
}

.voice-call-view__select {
  width: 120px;
}

.voice-call-view__date-range {
  width: 260px;
}

.voice-call-view__search {
  width: 200px;
}

.voice-call-view__table {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.voice-call-view__mono { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); }
.voice-call-view__muted { font-size: 13px; color: var(--color-text-muted); }

.voice-call-view__participant {
  font-size: 13px;
}

.voice-call-view__online {
  color: var(--color-success, #52c41a);
  font-weight: 600;
}

.voice-call-view__sep {
  color: var(--color-text-muted);
  margin: 0 2px;
}

.voice-call-view__section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: var(--space-5) 0 var(--space-3);
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.voice-call-view__participant-summary {
  font-size: 13px;
  font-weight: 400;
  color: var(--color-text-secondary);
}
</style>
