<script setup lang="ts">
import { ref, reactive } from 'vue'
import { message } from 'ant-design-vue'
import type { TableProps } from 'ant-design-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { voiceCallApi } from '@/api/voiceCalls'
import { formatTime, formatDuration } from '@/utils/format'
import type { VoiceCall, VoiceCallParticipant, CallQuery } from '@/types'

const loading    = ref(false)
const callList  = ref<VoiceCall[]>([])
const pagination = reactive({ total: 0, current: 1, pageSize: 20 })
const statusFilter = ref<number | undefined>()

const drawerVisible    = ref(false)
const drawerLoading  = ref(false)
const callDetail     = ref<VoiceCall | null>(null)
const participants   = ref<VoiceCallParticipant[]>([])

async function loadCalls() {
  loading.value = true
  try {
    const params: CallQuery = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      status: statusFilter.value,
    }
    const { data } = await voiceCallApi.getPage(params)
    callList.value   = data.records as VoiceCall[]
    pagination.total = data.total
  } finally {
    loading.value = false
  }
}

const columns = [
  { title: '通话ID',   dataIndex: 'callId',       key: 'callId',      width: 90 },
  { title: '房间ID',   dataIndex: 'roomId',         key: 'roomId',      width: 90 },
  { title: '发起人ID', dataIndex: 'initiatorId',   key: 'initiatorId',  width: 100 },
  { title: '状态',     dataIndex: 'status',         key: 'status',       width: 90 },
  { title: '时长',     dataIndex: 'duration',       key: 'duration',     width: 100 },
  { title: '开始时间', dataIndex: 'startTime',      key: 'startTime',    width: 170 },
  { title: '操作',     key: 'actions',              width: 100 },
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
    callDetail.value   = detailRes.data
    participants.value = partRes.data
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

loadCalls()
</script>

<template>
  <div class="voice-call-view">
    <PageHeader title="通话记录" description="查看语音通话记录和参与者" />

    <div class="voice-call-view__toolbar">
      <a-select
        v-model:value="statusFilter"
        placeholder="状态筛选"
        allow-clear
        style="width: 140px;"
        @change="() => { pagination.current = 1; loadCalls() }"
      >
        <a-select-option :value="0">进行中</a-select-option>
        <a-select-option :value="1">已取消</a-select-option>
        <a-select-option :value="2">已结束</a-select-option>
      </a-select>
    </div>

    <a-table
      :columns="columns"
      :data-source="callList"
      :loading="loading"
      :pagination="{ ...pagination, showSizeChanger: true, showTotal: (total: number) => `共 ${total} 条` }"
      :scroll="{ x: 700 }"
      class="voice-call-view__table"
      @change="onPageChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'callId' || column.key === 'roomId' || column.key === 'initiatorId'">
          <span class="voice-call-view__mono">{{ record[column.dataIndex as keyof VoiceCall] }}</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <StatusBadge :status="record.status" type="call" />
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

    <a-drawer v-model:open="drawerVisible" title="通话详情" :width="480" :loading="drawerLoading">
      <template v-if="callDetail">
        <a-descriptions :column="1" bordered size="small">
          <a-descriptions-item label="通话ID">{{ callDetail.callId }}</a-descriptions-item>
          <a-descriptions-item label="房间ID">{{ callDetail.roomId }}</a-descriptions-item>
          <a-descriptions-item label="发起人ID">{{ callDetail.initiatorId }}</a-descriptions-item>
          <a-descriptions-item label="状态"><StatusBadge :status="callDetail.status" type="call" /></a-descriptions-item>
          <a-descriptions-item label="通话时长">{{ formatDuration(callDetail.duration) }}</a-descriptions-item>
          <a-descriptions-item label="开始时间">{{ formatTime(callDetail.startTime) }}</a-descriptions-item>
          <a-descriptions-item v-if="callDetail.endTime" label="结束时间">{{ formatTime(callDetail.endTime) }}</a-descriptions-item>
          <a-descriptions-item label="系统自动创建">{{ callDetail.isAutoCreated ? '是' : '否' }}</a-descriptions-item>
        </a-descriptions>

        <div style="margin: var(--space-5) 0 var(--space-3); font-size: 15px; font-weight: 600; color: var(--color-text-primary);">参与者列表</div>
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
.voice-call-view__toolbar { margin-bottom: var(--space-4); }

.voice-call-view__table {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.voice-call-view__mono { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); }
.voice-call-view__muted { font-size: 13px; color: var(--color-text-muted); }
</style>
