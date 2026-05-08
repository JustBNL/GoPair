<script setup lang="ts">
import { computed } from 'vue'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import VChart from 'vue-echarts'
import type { DailyStats } from '@/types'

use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

interface Props {
  data: DailyStats[]
  isDark?: boolean
}

const props = defineProps<Props>()

const option = computed(() => {
  const dates = props.data.map(d => d.date)
  const textColor = props.isDark ? '#A8B5C7' : '#566476'
  const splitLineColor = props.isDark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)'
  const tooltipBg = props.isDark ? '#1F2633' : '#FFFFFF'

  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: tooltipBg,
      borderColor: props.isDark ? '#2A3344' : '#E2E8F0',
      borderWidth: 1,
      textStyle: { color: props.isDark ? '#E6EDF3' : '#1E2432', fontSize: 12 },
      axisPointer: {
        type: 'cross',
        crossStyle: { color: textColor },
        lineStyle: { color: splitLineColor },
      },
    },
    legend: {
      data: ['新增用户', '新增房间', '消息数'],
      top: 4,
      right: 16,
      textStyle: { color: textColor, fontSize: 12 },
      itemWidth: 14,
      itemHeight: 6,
    },
    grid: {
      top: 44,
      left: 16,
      right: 16,
      bottom: 12,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: splitLineColor } },
      axisTick: { show: false },
      axisLabel: { color: textColor, fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: splitLineColor } },
      axisLabel: { color: textColor, fontSize: 11 },
    },
    series: [
      {
        name: '新增用户',
        type: 'line',
        data: props.data.map(d => d.newUsers),
        smooth: 0.4,
        lineStyle: { width: 2, color: '#0d9488' },
        itemStyle: { color: '#0d9488' },
        symbol: 'circle',
        symbolSize: 5,
      },
      {
        name: '新增房间',
        type: 'line',
        data: props.data.map(d => d.newRooms),
        smooth: 0.4,
        lineStyle: { width: 2, color: '#E07850' },
        itemStyle: { color: '#E07850' },
        symbol: 'circle',
        symbolSize: 5,
      },
      {
        name: '消息数',
        type: 'line',
        data: props.data.map(d => d.messages),
        smooth: 0.4,
        lineStyle: { width: 2, color: '#5B87BD', type: 'dashed' },
        itemStyle: { color: '#5B87BD' },
        symbol: 'circle',
        symbolSize: 5,
      },
    ],
  }
})
</script>

<template>
  <div class="chart-wrapper">
    <div class="chart-title">7日趋势</div>
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
