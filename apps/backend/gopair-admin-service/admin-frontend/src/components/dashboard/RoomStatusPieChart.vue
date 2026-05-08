<script setup lang="ts">
import { computed } from 'vue'
import { use } from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import VChart from 'vue-echarts'
import type { RoomStatusDistribution } from '@/types'

use([PieChart, TooltipComponent, LegendComponent, CanvasRenderer])

interface Props {
  data: RoomStatusDistribution
  isDark?: boolean
}

const props = defineProps<Props>()

const option = computed(() => {
  const textColor = props.isDark ? '#A8B5C7' : '#566476'
  const tooltipBg = props.isDark ? '#1F2633' : '#FFFFFF'

  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: tooltipBg,
      borderColor: props.isDark ? '#2A3344' : '#E2E8F0',
      borderWidth: 1,
      textStyle: { color: props.isDark ? '#E6EDF3' : '#1E2432', fontSize: 12 },
      formatter: '{b}: {c} ({d}%)',
    },
    legend: {
      orient: 'vertical',
      right: 16,
      top: 'middle',
      textStyle: { color: textColor, fontSize: 12 },
      itemWidth: 10,
      itemHeight: 10,
      itemGap: 12,
    },
    series: [
      {
        type: 'pie',
        radius: ['45%', '72%'],
        center: ['38%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderColor: props.isDark ? '#161B22' : '#FFFFFF',
          borderWidth: 2,
        },
        label: { show: false },
        emphasis: {
          label: { show: false },
          scaleSize: 6,
        },
        data: [
          { value: props.data.active, name: '活跃', itemStyle: { color: '#0d9488' } },
          { value: props.data.closed, name: '已关闭', itemStyle: { color: '#E07850' } },
          { value: props.data.expired, name: '已过期', itemStyle: { color: '#8896AA' } },
        ],
      },
    ],
  }
})
</script>

<template>
  <div class="chart-wrapper">
    <div class="chart-title">房间状态分布</div>
    <v-chart :option="option" autoresize />
  </div>
</template>

<style scoped>
.chart-wrapper {
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
  height: 300px;
  display: flex;
  flex-direction: column;
  transition: background-color var(--transition-normal), border-color var(--transition-normal);
}

.chart-title {
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--space-4);
  letter-spacing: -0.01em;
}

.v-chart {
  flex: 1;
  width: 100%;
  min-height: 0;
}
</style>
