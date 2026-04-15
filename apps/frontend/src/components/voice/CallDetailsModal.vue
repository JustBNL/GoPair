<template>
  <a-modal
    v-model:open="visible"
    :title="`通话详情 - ${call?.statusDescription || ''}`"
    width="700px"
    :footer="null"
  >
    <div v-if="call" class="call-details-modal">
      <!-- 通话基本信息 -->
      <div class="info-section">
        <h4 class="section-title">基本信息</h4>
        <a-descriptions :column="2" size="small">
          <a-descriptions-item label="通话ID">
            {{ call.callId }}
          </a-descriptions-item>
          <a-descriptions-item label="通话类型">
            {{ getCallTypeText(call.callType) }}
          </a-descriptions-item>
          <a-descriptions-item label="发起人">
            {{ call.initiatorNickname }}
          </a-descriptions-item>
          <a-descriptions-item label="开始时间">
            {{ formatTime(call.startTime) }}
          </a-descriptions-item>
          <a-descriptions-item label="结束时间">
            {{ call.endTime ? formatTime(call.endTime) : '进行中' }}
          </a-descriptions-item>
          <a-descriptions-item label="通话时长">
            {{ call.durationFormatted || calculateDuration() }}
          </a-descriptions-item>
          <a-descriptions-item label="参与人数">
            {{ call.participantCount }} 人
          </a-descriptions-item>
          <a-descriptions-item label="通话状态">
            <a-tag :color="getStatusColor(call.status)">
              {{ call.statusDescription }}
            </a-tag>
          </a-descriptions-item>
        </a-descriptions>
      </div>

      <!-- 参与者信息 -->
      <div class="info-section">
        <div class="section-header">
          <h4 class="section-title">参与者列表</h4>
          <a-button type="text" size="small" @click="loadParticipants">
            <reload-outlined />
            刷新
          </a-button>
        </div>
        
        <div class="participants-table">
          <a-table
            :columns="participantColumns"
            :data-source="participants"
            :pagination="false"
            size="small"
            :loading="loadingParticipants"
            :locale="{ emptyText: '暂无参与者数据' }"
            class="participants-table-inner"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'userNickname'">
                <div class="participant-name">
                  <a-avatar size="small">
                    {{ record.userNickname?.charAt(0) || 'U' }}
                  </a-avatar>
                  <span>{{ record.userNickname }}</span>
                  <a-tag v-if="record.isInitiator" color="gold" size="small">
                    发起人
                  </a-tag>
                </div>
              </template>
              
              <template v-else-if="column.key === 'joinTime'">
                {{ formatTime(record.joinTime) }}
              </template>
              
              <template v-else-if="column.key === 'leaveTime'">
                {{ record.leaveTime ? formatTime(record.leaveTime) : '在线' }}
              </template>
              
              <template v-else-if="column.key === 'duration'">
                {{ calculateParticipantDuration(record) }}
              </template>
              
              <template v-else-if="column.key === 'connectionStatus'">
                <a-tag :color="getConnectionStatusColor(record.connectionStatus)">
                  {{ getConnectionStatusText(record.connectionStatus) }}
                </a-tag>
              </template>
            </template>
          </a-table>
        </div>
      </div>

      <!-- 通话统计 -->
      <div class="info-section">
        <h4 class="section-title">通话统计</h4>
        <div class="stats-grid">
          <div class="stat-card">
            <div class="stat-icon">
              <user-outlined />
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ call.participantCount }}</div>
              <div class="stat-label">参与人数</div>
            </div>
          </div>
          
          <div class="stat-card">
            <div class="stat-icon">
              <clock-circle-outlined />
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ call.durationFormatted || calculateDuration() }}</div>
              <div class="stat-label">通话时长</div>
            </div>
          </div>
          
          <div class="stat-card">
            <div class="stat-icon">
              <calendar-outlined />
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ formatDate(call.startTime) }}</div>
              <div class="stat-label">开始日期</div>
            </div>
          </div>
          
          <div class="stat-card">
            <div class="stat-icon">
              <sound-outlined />
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ getCallQualityText() }}</div>
              <div class="stat-label">通话质量</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="actions-section">
        <a-space>
          <a-button v-if="call.status === 'IN_PROGRESS'" type="primary" @click="joinCall">
            <phone-outlined />
            加入通话
          </a-button>
          
          <a-button v-if="canEndCall" danger @click="endCall">
            <phone-outlined />
            结束通话
          </a-button>
          
          <a-button @click="exportCallData">
            <download-outlined />
            导出数据
          </a-button>
          
          <a-button @click="shareCall">
            <share-alt-outlined />
            分享通话
          </a-button>
        </a-space>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import dayjs from 'dayjs'
import type { TableColumnsType } from 'ant-design-vue'
import {
  ReloadOutlined,
  UserOutlined,
  ClockCircleOutlined,
  CalendarOutlined,
  SoundOutlined,
  PhoneOutlined,
  DownloadOutlined,
  ShareAltOutlined
} from '@ant-design/icons-vue'
import { VoiceAPI } from '@/api/voice'
import type { CallVO, CallParticipantVO } from '@/types/api'

interface Props {
  open: boolean
  call: CallVO | null
}

interface Emits {
  (e: 'update:open', value: boolean): void
  (e: 'join-call', call: CallVO): void
  (e: 'end-call', call: CallVO): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// 模态框状态
const visible = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value)
})

// 参与者数据
const participants = ref<CallParticipantVO[]>([])
const loadingParticipants = ref(false)

// 表格列定义
const participantColumns: TableColumnsType = [
  {
    title: '参与者',
    dataIndex: 'userNickname',
    key: 'userNickname',
    width: 200
  },
  {
    title: '加入时间',
    dataIndex: 'joinTime',
    key: 'joinTime',
    width: 150
  },
  {
    title: '离开时间',
    dataIndex: 'leaveTime',
    key: 'leaveTime',
    width: 150
  },
  {
    title: '通话时长',
    key: 'duration',
    width: 120
  },
  {
    title: '连接状态',
    dataIndex: 'connectionStatus',
    key: 'connectionStatus',
    width: 100
  }
]

/**
 * 是否可以结束通话
 */
const canEndCall = computed(() => {
  // TODO: 检查权限，这里简化处理
  return props.call?.status === 'IN_PROGRESS'
})

/**
 * 加载参与者信息
 */
const loadParticipants = async () => {
  if (!props.call) return
  
  try {
    loadingParticipants.value = true
    const response = await VoiceAPI.getCallParticipants(props.call.callId)
    participants.value = response.data
  } catch (error) {
    console.error('加载参与者失败:', error)
  } finally {
    loadingParticipants.value = false
  }
}

/**
 * 获取通话类型文本
 */
const getCallTypeText = (callType: string): string => {
  const typeMap: Record<string, string> = {
    '1': '一对一通话',
    '2': '多人通话',
    'ONE_TO_ONE': '一对一通话',
    'MULTI_USER': '多人通话'
  }
  return typeMap[callType] || callType
}

/**
 * 获取状态颜色
 */
const getStatusColor = (status: string): string => {
  const colorMap: Record<string, string> = {
    IN_PROGRESS: 'green',
    ENDED: 'blue',
    CANCELLED: 'orange'
  }
  return colorMap[status] || 'default'
}

/**
 * 获取连接状态颜色
 */
const getConnectionStatusColor = (status: string): string => {
  const colorMap: Record<string, string> = {
    CONNECTED: 'green',
    DISCONNECTED: 'red',
    CONNECTING: 'orange'
  }
  return colorMap[status] || 'default'
}

/**
 * 获取连接状态文本
 */
const getConnectionStatusText = (status: string): string => {
  const statusMap: Record<string, string> = {
    CONNECTED: '已连接',
    DISCONNECTED: '已断开',
    CONNECTING: '连接中'
  }
  return statusMap[status] || status
}

/**
 * 计算通话时长
 */
const calculateDuration = (): string => {
  if (!props.call) return '0:00'
  
  const start = dayjs(props.call.startTime)
  const end = props.call.endTime ? dayjs(props.call.endTime) : dayjs()
  const duration = end.diff(start, 'second')
  
  const hours = Math.floor(duration / 3600)
  const minutes = Math.floor((duration % 3600) / 60)
  const seconds = duration % 60
  
  if (hours > 0) {
    return `${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
  }
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

/**
 * 计算参与者通话时长
 */
const calculateParticipantDuration = (participant: CallParticipantVO): string => {
  const start = dayjs(participant.joinTime)
  const end = participant.leaveTime ? dayjs(participant.leaveTime) : dayjs()
  const duration = end.diff(start, 'second')
  
  const minutes = Math.floor(duration / 60)
  const seconds = duration % 60
  
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

/**
 * 格式化时间
 */
const formatTime = (timeStr: string) => {
  return dayjs(timeStr).format('HH:mm:ss')
}

/**
 * 格式化日期
 */
const formatDate = (timeStr: string) => {
  return dayjs(timeStr).format('YYYY-MM-DD')
}

/**
 * 获取通话质量文本
 */
const getCallQualityText = (): string => {
  // TODO: 根据实际通话质量数据返回
  return '良好'
}

/**
 * 加入通话
 */
const joinCall = () => {
  if (props.call) {
    emit('join-call', props.call)
    visible.value = false
  }
}

/**
 * 结束通话
 */
const endCall = async () => {
  if (!props.call) return
  
  try {
    await VoiceAPI.endCall(props.call.callId)
    emit('end-call', props.call)
    antMessage.success('通话已结束')
    visible.value = false
  } catch (error: any) {
    antMessage.error(error.response?.data?.msg || '结束通话失败')
  }
}

/**
 * 导出通话数据
 */
const exportCallData = () => {
  if (!props.call) return
  
  const data = {
    call: props.call,
    participants: participants.value
  }
  
  const dataStr = JSON.stringify(data, null, 2)
  const blob = new Blob([dataStr], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  
  const link = document.createElement('a')
  link.href = url
  link.download = `call-${props.call.callId}-${dayjs().format('YYYY-MM-DD-HH-mm-ss')}.json`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  
  URL.revokeObjectURL(url)
  antMessage.success('通话数据已导出')
}

/**
 * 分享通话
 */
const shareCall = async () => {
  if (!props.call) return
  
  const shareText = `通话详情：\n通话ID：${props.call.callId}\n发起人：${props.call.initiatorNickname}\n开始时间：${formatTime(props.call.startTime)}\n参与人数：${props.call.participantCount}人`
  
  try {
    await navigator.clipboard.writeText(shareText)
    antMessage.success('通话信息已复制到剪贴板')
  } catch (error) {
    antMessage.error('复制失败')
  }
}

// 监听通话变化，自动加载参与者
watch(() => props.call, (newCall) => {
  if (newCall && props.open) {
    loadParticipants()
  }
})

// 监听模态框打开状态
watch(() => props.open, (isOpen) => {
  if (isOpen && props.call) {
    loadParticipants()
  }
})
</script>

<style scoped lang="scss">
.call-details-modal {
  .info-section {
    margin-bottom: 24px;

    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
    }

    .section-title {
      margin-bottom: 12px;
      color: var(--text-primary);
      font-size: 14px;
      font-weight: 500;
    }

    .participants-table {
      .participants-table-inner {
        :deep(.ant-table-placeholder) {
          background: var(--surface-bg);
          border-radius: 8px;
        }
      }

      .participant-name {
        display: flex;
        align-items: center;
        gap: 8px;

        span {
          font-weight: 500;
        }
      }
    }

    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
      gap: 16px;

      .stat-card {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 16px;
        background: var(--surface-bg);
        border-radius: 8px;
        border: 1px solid var(--border-light);

        .stat-icon {
          width: 40px;
          height: 40px;
          display: flex;
          align-items: center;
          justify-content: center;
          background: var(--color-info);
          color: white;
          border-radius: 8px;
          font-size: 18px;
        }

        .stat-content {
          .stat-value {
            font-size: 18px;
            font-weight: 600;
            color: var(--text-primary);
            margin-bottom: 2px;
          }

          .stat-label {
            font-size: 12px;
            color: var(--text-muted);
          }
        }
      }
    }
  }

  .actions-section {
    padding-top: 16px;
    border-top: 1px solid var(--border-light);
    text-align: center;
  }
}

@media (max-width: 768px) {
  .call-details-modal {
    .info-section {
      .stats-grid {
        grid-template-columns: repeat(2, 1fr);
      }

      .participants-table {
        :deep(.ant-table) {
          font-size: 12px;
        }

        .participant-name {
          flex-direction: column;
          gap: 4px;
          align-items: flex-start;
        }
      }
    }

    .actions-section {
      :deep(.ant-space) {
        width: 100%;
        justify-content: center;
        flex-wrap: wrap;
      }
    }
  }
}
</style> 