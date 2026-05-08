<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { RecentRoom } from '@/types'
import { useRoomDetailStore } from '@/stores/roomDetail'

interface Props {
  rooms: RecentRoom[]
}

const props = defineProps<Props>()
const router = useRouter()
const roomDetailStore = useRoomDetailStore()

const statusMap: Record<number, { text: string; color: string }> = {
  0: { text: '活跃', color: '#0d9488' },
  1: { text: '已关闭', color: '#E07850' },
  2: { text: '已过期', color: '#8896AA' },
}

const columns = [
  { title: '房间名称', dataIndex: 'roomName', key: 'roomName' },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status',
    width: 100,
  },
  {
    title: '成员',
    dataIndex: 'members',
    key: 'members',
    width: 100,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    key: 'createTime',
    width: 160,
  },
  {
    title: '操作',
    key: 'action',
    width: 80,
  },
]

function getStatusInfo(status: number) {
  return statusMap[status] || { text: '未知', color: '#8896AA' }
}

function goToRoom(roomId: number) {
  roomDetailStore.setPendingRoomId(roomId)
  router.push({ name: 'rooms', query: { keyword: String(roomId) } })
}
</script>

<template>
  <div class="recent-rooms-wrapper">
    <div class="recent-rooms-header">
      <span class="chart-title">最新活跃房间</span>
      <a-button type="link" size="small" @click="router.push({ name: 'rooms' })">
        查看全部 →
      </a-button>
    </div>
    <a-table
      :columns="columns"
      :data-source="props.rooms"
      :pagination="false"
      :row-key="(record: RecentRoom) => record.roomId"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'roomName'">
          <span class="room-name">{{ record.roomName }}</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="getStatusInfo(record.status).color" style="margin: 0">
            {{ getStatusInfo(record.status).text }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'members'">
          <span class="members-text">
            {{ record.currentMembers }} / {{ record.maxMembers }}
          </span>
        </template>
        <template v-else-if="column.key === 'createTime'">
          <span class="time-text">{{ record.createTime }}</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-button type="link" size="small" @click="goToRoom(record.roomId)">
            查看
          </a-button>
        </template>
      </template>
    </a-table>
  </div>
</template>

<style scoped>
.recent-rooms-wrapper {
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
  transition: background-color var(--transition-normal), border-color var(--transition-normal);
}

.recent-rooms-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-4);
}

.chart-title {
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  letter-spacing: -0.01em;
}

.room-name {
  font-weight: 500;
  color: var(--color-text-primary);
}

.members-text {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.time-text {
  color: var(--color-text-muted);
  font-size: 13px;
}
</style>
