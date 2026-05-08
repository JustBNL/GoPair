<script setup lang="ts">
import { computed } from 'vue'
import { use } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import VChart from 'vue-echarts'
import type { DailyStats } from '@/types'

use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

interface Props {
  data: DailyStats[]
  isDark?: boolean
}

const props = defineProps<Props>()

function formatDuration(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  if (h > 0) return `${h}h${m}m`
  return `${m}m`
}

const option = computed(() => {
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
      formatter: (params: any[]) => {
        const item = params[0]
        return `${item.name}<br/>通话时长: ${formatDuration(item.value)}`
      },
    },
    grid: {
      top: 16,
      left: 16,
      right: 16,
      bottom: 12,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: props.data.map(d => d.date),
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
        type: 'bar',
        data: props.data.map(d => d.voiceCallDuration),
        barWidth: '50%',
        itemStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: '#5B87BD' },
              { offset: 1, color: '#0d9488' },
            ],
          },
          borderRadius: [4, 4, 0, 0],
        },
      },
    ],
  }
})
</script>

<template>
  <div class="chart-wrapper">
    <div class="chart-title">7日通话时长</div>
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
