<script setup lang="ts">
import { ref, reactive } from 'vue'
import { message } from 'ant-design-vue'
import type { TableProps } from 'ant-design-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import { roomApi } from '@/api/rooms'
import { formatTime } from '@/utils/format'
import type { Room, RoomDetail, RoomQuery } from '@/types'

const loading    = ref(false)
const roomList  = ref<Room[]>([])
const pagination = reactive({ total: 0, current: 1, pageSize: 20 })
const searchKw  = ref('')
const statusFilter = ref<number | undefined>(undefined)

const drawerVisible  = ref(false)
const drawerLoading = ref(false)
const roomDetail    = ref<RoomDetail | null>(null)

const confirmOpen   = ref(false)
const confirmTarget = ref<Room | null>(null)
const confirmLoading = ref(false)

async function loadRooms() {
  loading.value = true
  try {
    const params: RoomQuery = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      status: statusFilter.value,
      keyword: searchKw.value || undefined,
    }
    const res = await roomApi.getPage(params)
    roomList.value   = res.records as Room[]
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

const columns = [
  { title: '房间ID',   dataIndex: 'roomId',      key: 'roomId',      width: 80 },
  { title: '房间码',   dataIndex: 'roomCode',     key: 'roomCode',    width: 100 },
  { title: '房间名称', dataIndex: 'roomName',     key: 'roomName',    ellipsis: true },
  { title: '成员',     key: 'members',           width: 100 },
  { title: '状态',     dataIndex: 'status',      key: 'status',      width: 90 },
  { title: '创建时间', dataIndex: 'createTime',   key: 'createTime',  width: 170 },
  { title: '操作',     key: 'actions',           width: 140 },
]

async function handleView(roomId: number) {
  drawerVisible.value  = true
  drawerLoading.value  = true
  roomDetail.value     = null
  try {
    const res = await roomApi.getDetail(roomId)
    roomDetail.value = res
  } catch {
    message.error('获取房间详情失败')
  } finally {
    drawerLoading.value = false
  }
}

function openCloseConfirm(room: Room) {
  confirmTarget.value = room
  confirmOpen.value   = true
}

async function handleConfirm() {
  if (!confirmTarget.value) return
  confirmLoading.value = true
  try {
    await roomApi.close(confirmTarget.value.roomId)
    message.success('房间已关闭')
    loadRooms()
  } catch (e: unknown) {
    const err = e as { message?: string }
    message.error(err?.message || '操作失败')
  } finally {
    confirmLoading.value = false
  }
}

const onPageChange: TableProps['onChange'] = (pag) => {
  pagination.current = pag.current as number
  pagination.pageSize = pag.pageSize as number
  loadRooms()
}

let searchTimer: ReturnType<typeof setTimeout>
function onSearch(value: string) {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchKw.value = value
    pagination.current = 1
    loadRooms()
  }, 350)
}

loadRooms()
</script>

<template>
  <div class="room-manage-view">
    <PageHeader title="房间管理" description="查看和管理所有房间" />

    <div class="room-manage-view__toolbar">
      <a-input-search
        v-model:value="searchKw"
        aria-label="搜索房间名或房间码"
        placeholder="搜索房间名或房间码"
        class="room-manage-view__search"
        @search="onSearch"
        @change="() => { pagination.current = 1; loadRooms() }"
        style="width: 280px;"
      />
      <a-select
        v-model:value="statusFilter"
        placeholder="状态筛选"
        allow-clear
        style="width: 140px;"
        @change="() => { pagination.current = 1; loadRooms() }"
      >
        <a-select-option :value="0">活跃</a-select-option>
        <a-select-option :value="1">已关闭</a-select-option>
      </a-select>
    </div>

    <a-table
      :columns="columns"
      :data-source="roomList"
      :loading="loading"
      :pagination="{ ...pagination, showSizeChanger: true, showTotal: (total: number) => `共 ${total} 条` }"
      :scroll="{ x: 800 }"
      class="room-manage-view__table"
      @change="onPageChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'roomId'">
          <span class="room-manage-view__mono">{{ record.roomId }}</span>
        </template>
        <template v-else-if="column.key === 'roomCode'">
          <a-tag class="room-manage-view__code">{{ record.roomCode }}</a-tag>
        </template>
        <template v-else-if="column.key === 'members'">
          <span class="room-manage-view__muted">{{ record.currentMembers }} / {{ record.maxMembers }}</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <StatusBadge :status="record.status" type="room" />
        </template>
        <template v-else-if="column.key === 'createTime'">
          <span class="room-manage-view__muted">{{ formatTime(record.createTime) }}</span>
        </template>
        <template v-else-if="column.key === 'actions'">
          <div class="room-manage-view__actions">
            <a-button type="link" size="small" @click="handleView(record.roomId)">详情</a-button>
            <a-button v-if="record.status === 0" type="link" size="small" danger @click="openCloseConfirm(record)">关闭</a-button>
          </div>
        </template>
      </template>
    </a-table>

    <a-drawer v-model:open="drawerVisible" title="房间详情" :width="520" :loading="drawerLoading">
      <template v-if="roomDetail">
        <a-descriptions :column="1" bordered size="small">
          <a-descriptions-item label="房间ID">{{ roomDetail.room.roomId }}</a-descriptions-item>
          <a-descriptions-item label="房间码"><a-tag>{{ roomDetail.room.roomCode }}</a-tag></a-descriptions-item>
          <a-descriptions-item label="房间名称">{{ roomDetail.room.roomName }}</a-descriptions-item>
          <a-descriptions-item label="描述">{{ roomDetail.room.description || '—' }}</a-descriptions-item>
          <a-descriptions-item label="状态"><StatusBadge :status="roomDetail.room.status" type="room" /></a-descriptions-item>
          <a-descriptions-item label="成员数">{{ roomDetail.room.currentMembers }} / {{ roomDetail.room.maxMembers }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ formatTime(roomDetail.room.createTime) }}</a-descriptions-item>
          <a-descriptions-item label="过期时间">{{ formatTime(roomDetail.room.expireTime) }}</a-descriptions-item>
        </a-descriptions>

        <div class="room-manage-view__members-title">成员列表（{{ roomDetail.members.length }}）</div>
        <a-table
          :columns="[
            { title: '用户ID', dataIndex: 'userId', key: 'userId', width: 90 },
            { title: '昵称', key: 'nickname', ellipsis: true },
            { title: '角色', dataIndex: 'role', key: 'role', width: 90 },
            { title: '加入时间', dataIndex: 'joinTime', key: 'joinTime', width: 160 },
          ]"
          :data-source="roomDetail.members"
          :pagination="false"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'userId'">
              <span class="room-manage-view__mono">{{ record.userId }}</span>
            </template>
            <template v-else-if="column.key === 'nickname'">
              {{ roomDetail.userMap[record.userId]?.nickname || '—' }}
            </template>
            <template v-else-if="column.key === 'role'">
              {{ record.role === 0 ? '房主' : record.role === 2 ? '管理员' : '成员' }}
            </template>
            <template v-else-if="column.key === 'joinTime'">
              <span class="room-manage-view__muted">{{ formatTime(record.joinTime) }}</span>
            </template>
          </template>
        </a-table>
      </template>
    </a-drawer>

    <ConfirmModal
      v-model:open="confirmOpen"
      title="关闭房间"
      :content="`确定关闭房间「${confirmTarget?.roomName}」？关闭后房间将无法访问。`"
      confirm-text="关闭房间"
      danger
      :loading="confirmLoading"
      @confirm="handleConfirm"
    />
  </div>
</template>

<style scoped>
.room-manage-view__toolbar {
  margin-bottom: var(--space-4);
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.room-manage-view__table {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.room-manage-view__mono { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); }
.room-manage-view__muted { font-size: 13px; color: var(--color-text-muted); }
.room-manage-view__code { font-family: var(--font-mono); font-size: 12px; letter-spacing: 0.05em; }
.room-manage-view__actions { display: flex; gap: var(--space-2); align-items: center; }
.room-manage-view__members-title {
  margin: var(--space-5) 0 var(--space-3);
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
}
</style>
