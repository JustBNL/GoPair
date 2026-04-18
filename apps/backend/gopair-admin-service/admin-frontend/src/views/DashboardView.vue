<script setup lang="ts">
import { ref, onMounted } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatCard from '@/components/common/StatCard.vue'
import { dashboardApi } from '@/api/dashboard'
import { formatDuration } from '@/utils/format'
import type { DashboardStats } from '@/types'

const stats   = ref<DashboardStats | null>(null)
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const { data } = await dashboardApi.getStats()
    stats.value = data
  } finally {
    loading.value = false
  }
})

const statItems = [
  { label: '用户总数',       key: 'totalUsers' },
  { label: '今日新增用户',   key: 'todayNewUsers' },
  { label: '活跃房间',       key: 'activeRooms' },
  { label: '今日新增房间',   key: 'todayNewRooms' },
  { label: '今日消息',       key: 'todayMessages' },
  { label: '今日通话时长',   key: 'todayVoiceCallDuration' },
]
</script>

<template>
  <div class="dashboard-view">
    <PageHeader title="仪表盘" description="系统运行状态一览" />

    <div v-if="loading" class="dashboard-view__loading">
      <a-spin size="large" />
    </div>

    <div v-else-if="stats" class="dashboard-view__stats">
      <StatCard
        v-for="item in statItems"
        :key="item.key"
        :label="item.label"
        :value="item.key === 'todayVoiceCallDuration'
          ? formatDuration(stats[item.key as keyof DashboardStats] as number)
          : (stats[item.key as keyof DashboardStats] as number).toLocaleString()"
      />
    </div>
  </div>
</template>

<style scoped>
.dashboard-view__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
}

.dashboard-view__stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--space-4);
}
</style>
