<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatCard from '@/components/common/StatCard.vue'
import TrendLineChart from '@/components/dashboard/TrendLineChart.vue'
import RoomStatusPieChart from '@/components/dashboard/RoomStatusPieChart.vue'
import VoiceCallBarChart from '@/components/dashboard/VoiceCallBarChart.vue'
import RecentRoomTable from '@/components/dashboard/RecentRoomTable.vue'
import { dashboardApi } from '@/api/dashboard'
import { formatDuration } from '@/utils/format'
import { useAppStore } from '@/stores/app'
import type { DashboardStats, DashboardTrends, RecentRoom } from '@/types'

const app = useAppStore()

const stats      = ref<DashboardStats | null>(null)
const trends     = ref<DashboardTrends | null>(null)
const recentRooms = ref<RecentRoom[]>([])
const loading    = ref(false)
const error      = ref<string | null>(null)

onMounted(async () => {
  loading.value = true
  try {
    const [statsResp, trendsResp, roomsResp] = await Promise.all([
      dashboardApi.getStats(),
      dashboardApi.getTrends(),
      dashboardApi.getRecentRooms(5),
    ])
    stats.value        = statsResp
    trends.value       = trendsResp
    recentRooms.value  = roomsResp
  } catch (e: any) {
    error.value = e?.message || '加载数据失败，请刷新重试'
  } finally {
    loading.value = false
  }
})

function calcTrend(today: number, yesterday: number): { direction: 'up' | 'down'; percent: string } | null {
  if (yesterday === 0) return today > 0 ? { direction: 'up', percent: '新' } : null
  const diff = ((today - yesterday) / yesterday) * 100
  const direction: 'up' | 'down' = diff >= 0 ? 'up' : 'down'
  const percent = Math.abs(diff).toFixed(0) + '%'
  return { direction, percent }
}

const userTrend = computed(() => {
  if (!stats.value || !trends.value) return undefined
  const daily = trends.value.daily
  const today = daily[daily.length - 1]?.newUsers ?? 0
  const yesterday = daily[daily.length - 2]?.newUsers ?? 0
  return calcTrend(stats.value.todayNewUsers, yesterday)
})

const roomTrend = computed(() => {
  if (!stats.value || !trends.value) return undefined
  const daily = trends.value.daily
  const yesterday = daily[daily.length - 2]?.newRooms ?? 0
  return calcTrend(stats.value.todayNewRooms, yesterday)
})

const statItems = computed(() => [
  {
    label: '用户总数',
    key: 'totalUsers',
    value: stats.value?.totalUsers?.toLocaleString() ?? '—',
    color: 'primary',
    icon: 'users',
  },
  {
    label: '今日新增用户',
    key: 'todayNewUsers',
    value: stats.value?.todayNewUsers?.toLocaleString() ?? '—',
    color: 'success',
    icon: 'userPlus',
    trend: userTrend.value?.percent,
    trendDirection: userTrend.value?.direction,
  },
  {
    label: '活跃房间',
    key: 'activeRooms',
    value: stats.value?.activeRooms?.toLocaleString() ?? '—',
    color: 'info',
    icon: 'rooms',
  },
  {
    label: '今日新增房间',
    key: 'todayNewRooms',
    value: stats.value?.todayNewRooms?.toLocaleString() ?? '—',
    color: 'warning',
    icon: 'roomPlus',
    trend: roomTrend.value?.percent,
    trendDirection: roomTrend.value?.direction,
  },
  {
    label: '今日消息',
    key: 'todayMessages',
    value: stats.value?.todayMessages?.toLocaleString() ?? '—',
    color: 'info',
    icon: 'messages',
  },
  {
    label: '今日通话时长',
    key: 'todayVoiceCallDuration',
    value: formatDuration(stats.value?.todayVoiceCallDuration ?? null),
    color: 'voice',
    icon: 'voice',
  },
])
</script>

<template>
  <div class="dashboard-view">
    <PageHeader title="仪表盘" description="系统运行状态一览" />

    <div v-if="loading" class="dashboard-view__loading">
      <a-spin size="large" />
    </div>

    <div v-else-if="error" class="dashboard-view__error">
      <a-result status="error" :title="error" />
    </div>

    <div v-else-if="stats && trends" class="dashboard-view__content">
      <!-- 统计卡片行 -->
      <div class="dashboard-view__stats">
        <StatCard
          v-for="item in statItems"
          :key="item.key"
          :label="item.label"
          :value="item.value"
          :color="item.color"
          :icon="item.icon"
          :trend="item.trend"
          :trend-direction="item.trendDirection"
        />
      </div>

      <!-- 折线图：7日趋势 -->
      <div class="dashboard-view__row dashboard-view__row--full">
        <TrendLineChart
          v-if="trends.daily.length > 0"
          :data="trends.daily"
          :is-dark="app.isDark"
        />
      </div>

      <!-- 饼图 + 柱图 -->
      <div class="dashboard-view__row dashboard-view__row--split">
        <RoomStatusPieChart
          :data="trends.roomStatusDistribution"
          :is-dark="app.isDark"
        />
        <VoiceCallBarChart
          v-if="trends.daily.length > 0"
          :data="trends.daily"
          :is-dark="app.isDark"
        />
      </div>

      <!-- 最新房间列表 -->
      <div class="dashboard-view__row dashboard-view__row--full">
        <RecentRoomTable v-if="recentRooms.length > 0" :rooms="recentRooms" />
      </div>
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

.dashboard-view__error {
  padding: var(--space-8) 0;
}

.dashboard-view__content {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.dashboard-view__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
}

.dashboard-view__row {
  display: flex;
  gap: var(--space-4);
}

.dashboard-view__row--full {
  flex-direction: column;
}

.dashboard-view__row--split > * {
  flex: 1;
  min-width: 0;
}

@media (max-width: 1024px) {
  .dashboard-view__stats {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .dashboard-view__stats {
    grid-template-columns: 1fr;
  }

  .dashboard-view__row--split {
    flex-direction: column;
  }
}
</style>
