<template>
  <a-modal
    v-model:open="visible"
    title="发起语音通话"
    width="500px"
    @ok="initiateCall"
    @cancel="handleCancel"
    :confirm-loading="loading"
    ok-text="发起通话"
    cancel-text="取消"
  >
    <div class="initiate-call-modal">
      <!-- 通话类型选择 -->
      <div class="form-section">
        <h4 class="section-title">通话类型</h4>
        <a-radio-group v-model:value="callType" class="call-type-group">
          <a-radio-button value="ONE_TO_ONE">一对一通话</a-radio-button>
          <a-radio-button value="MULTI_USER">多人通话</a-radio-button>
        </a-radio-group>
      </div>

      <!-- 邀请用户 -->
      <div class="form-section">
        <h4 class="section-title">
          邀请用户
          <span class="section-subtitle">
            ({{ callType === 'ONE_TO_ONE' ? '最多选择1人' : '可选择多人' }})
          </span>
        </h4>
        
        <!-- 搜索框 -->
        <a-input-search
          v-model:value="searchKeyword"
          placeholder="搜索房间成员..."
          @search="searchMembers"
          class="search-input"
        />

        <!-- 成员列表 -->
        <div class="members-list">
          <a-spin :spinning="loadingMembers">
            <div v-if="filteredMembers.length === 0" class="empty-members">
              <a-empty description="暂无可邀请的成员" />
            </div>
            <div
              v-for="member in filteredMembers"
              :key="member.userId"
              :class="[
                'member-item',
                { 'selected': selectedMembers.includes(member.userId) },
                { 'disabled': !canSelectMember(member) }
              ]"
              @click="toggleMember(member)"
            >
              <div class="member-avatar">
                <a-avatar :size="32">
                  {{ member.displayName?.charAt(0) || 'U' }}
                </a-avatar>
                <div v-if="member.status === 'online'" class="online-indicator"></div>
              </div>

              <div class="member-info">
                <div class="member-name">
                  {{ member.displayName }}
                  <span v-if="member.role === 'owner'" class="owner-badge">房主</span>
                </div>
                <div class="member-status">
                  <span :class="['status-text', member.status || 'offline']">
                    {{ getStatusText(member.status || 'offline') }}
                  </span>
                  <span class="last-active">
                    {{ formatTime(member.lastActiveTime || member.joinTime) }}
                  </span>
                </div>
              </div>

              <div class="member-select">
                <a-checkbox
                  :checked="selectedMembers.includes(member.userId)"
                  :disabled="!canSelectMember(member)"
                />
              </div>
            </div>
          </a-spin>
        </div>

        <!-- 已选择的成员 -->
        <div v-if="selectedMembers.length > 0" class="selected-members">
          <h5>已选择 {{ selectedMembers.length }} 人：</h5>
          <div class="selected-tags">
            <a-tag
              v-for="userId in selectedMembers"
              :key="userId"
              closable
              @close="removeMember(userId)"
              class="member-tag"
            >
              {{ getMemberName(userId) }}
            </a-tag>
          </div>
        </div>
      </div>

      <!-- 通话设置 -->
      <div class="form-section">
        <h4 class="section-title">通话设置</h4>
        
        <div class="setting-item">
          <a-checkbox v-model:checked="settings.autoRecord">
            自动录音
          </a-checkbox>
          <span class="setting-desc">通话过程将被自动录制</span>
        </div>

        <div class="setting-item">
          <a-checkbox v-model:checked="settings.allowInvite">
            允许参与者邀请他人
          </a-checkbox>
          <span class="setting-desc">通话参与者可以邀请其他人加入</span>
        </div>

        <div class="setting-item">
          <a-checkbox v-model:checked="settings.muteOnJoin">
            加入时自动静音
          </a-checkbox>
          <span class="setting-desc">新加入的参与者默认静音</span>
        </div>
      </div>

      <!-- 音频测试 -->
      <div class="form-section">
        <h4 class="section-title">音频测试</h4>
        <div class="audio-test">
          <a-button @click="testMicrophone" :loading="testingMic">
            <audio-outlined />
            测试麦克风
          </a-button>
          <a-button @click="testSpeaker" :loading="testingSpeaker">
            <sound-outlined />
            测试扬声器
          </a-button>
          
          <div v-if="audioTestResult" class="test-result">
            <a-alert
              :type="audioTestResult.type"
              :message="audioTestResult.message"
              show-icon
              closable
            />
          </div>
        </div>
      </div>

      <!-- 网络质量检测 -->
      <div class="form-section">
        <h4 class="section-title">网络状况</h4>
        <div class="network-status">
          <div class="network-info">
            <div class="network-quality">
              <span class="quality-label">网络质量：</span>
              <a-tag :color="getNetworkQualityColor(networkQuality)">
                {{ getNetworkQualityText(networkQuality) }}
              </a-tag>
            </div>
            <div class="network-details">
              <span>延迟：{{ networkStats.latency }}ms</span>
              <span>丢包率：{{ networkStats.packetLoss }}%</span>
            </div>
          </div>
          <a-button size="small" @click="testNetwork" :loading="testingNetwork">
            重新检测
          </a-button>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  AudioOutlined,
  SoundOutlined
} from '@ant-design/icons-vue'
import { VoiceAPI } from '@/api/voice'
import { getRoomMembers } from '@/api/room'
import type { CallInitiateDto, CallVO } from '@/types/api'
import { CallType } from '@/types/api'
import type { RoomMember } from '@/types/room'

interface Props {
  open: boolean
  roomId: number
}

interface Emits {
  (e: 'update:open', value: boolean): void
  (e: 'call-initiated', call: CallVO): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// 模态框状态
const visible = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value)
})

// 表单数据
const callType = ref<'ONE_TO_ONE' | 'MULTI_USER'>('MULTI_USER')
const selectedMembers = ref<number[]>([])
const settings = ref({
  autoRecord: false,
  allowInvite: true,
  muteOnJoin: false
})

// 成员数据
const roomMembers = ref<RoomMember[]>([])
const searchKeyword = ref('')
const loadingMembers = ref(false)

// 操作状态
const loading = ref(false)
const testingMic = ref(false)
const testingSpeaker = ref(false)
const testingNetwork = ref(false)

// 测试结果
const audioTestResult = ref<{type: 'success' | 'warning' | 'error', message: string} | null>(null)
const networkQuality = ref<'poor' | 'fair' | 'good' | 'excellent'>('good')
const networkStats = ref({
  latency: 45,
  packetLoss: 0.2
})

/**
 * 过滤后的成员列表
 */
const filteredMembers = computed(() => {
  if (!searchKeyword.value) {
    return roomMembers.value
  }
  
  return roomMembers.value.filter(member =>
    member.displayName?.toLowerCase().includes(searchKeyword.value.toLowerCase())
  )
})

/**
 * 加载房间成员
 */
const loadRoomMembers = async () => {
  try {
    loadingMembers.value = true
    const response = await getRoomMembers(props.roomId)
    // 过滤掉当前用户
    roomMembers.value = response.data.filter(member => {
      // TODO: 获取当前用户ID并过滤
      return true
    })
  } catch (error) {
    antMessage.error('加载房间成员失败')
  } finally {
    loadingMembers.value = false
  }
}

/**
 * 搜索成员
 */
const searchMembers = () => {
  // 搜索功能已通过computed实现
}

/**
 * 检查是否可以选择成员
 */
const canSelectMember = (member: RoomMember): boolean => {
  // 离线用户不能选择
  if (member.status !== 'online') return false
  
  // 一对一通话最多选择1人
  if (callType.value === 'ONE_TO_ONE' && selectedMembers.value.length >= 1) {
    return selectedMembers.value.includes(member.userId)
  }
  
  return true
}

/**
 * 切换成员选择状态
 */
const toggleMember = (member: RoomMember) => {
  if (!canSelectMember(member)) return
  
  const index = selectedMembers.value.indexOf(member.userId)
  if (index > -1) {
    selectedMembers.value.splice(index, 1)
  } else {
    // 一对一通话只能选择一个
    if (callType.value === 'ONE_TO_ONE') {
      selectedMembers.value = [member.userId]
    } else {
      selectedMembers.value.push(member.userId)
    }
  }
}

/**
 * 移除已选择的成员
 */
const removeMember = (userId: number) => {
  const index = selectedMembers.value.indexOf(userId)
  if (index > -1) {
    selectedMembers.value.splice(index, 1)
  }
}

/**
 * 获取成员名称
 */
const getMemberName = (userId: number): string => {
  const member = roomMembers.value.find(m => m.userId === userId)
  return member?.displayName || '未知用户'
}

/**
 * 获取状态文本
 */
const getStatusText = (status: string): string => {
  const statusMap: Record<string, string> = {
    online: '在线',
    offline: '离线',
    away: '离开'
  }
  return statusMap[status] || status
}

/**
 * 格式化时间
 */
const formatTime = (timeStr: string) => {
  return dayjs(timeStr).fromNow()
}

/**
 * 测试麦克风
 */
const testMicrophone = async () => {
  try {
    testingMic.value = true
    
    // 请求麦克风权限
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    
    // 简单的音频检测
    const audioContext = new AudioContext()
    const analyser = audioContext.createAnalyser()
    const microphone = audioContext.createMediaStreamSource(stream)
    microphone.connect(analyser)
    
    const dataArray = new Uint8Array(analyser.frequencyBinCount)
    analyser.getByteFrequencyData(dataArray)
    
    const volume = dataArray.reduce((sum, value) => sum + value, 0) / dataArray.length
    
    if (volume > 10) {
      audioTestResult.value = {
        type: 'success',
        message: '麦克风工作正常，音量检测正常'
      }
    } else {
      audioTestResult.value = {
        type: 'warning',
        message: '麦克风可能被静音或音量过低'
      }
    }
    
    // 清理资源
    stream.getTracks().forEach(track => track.stop())
    audioContext.close()
    
  } catch (error) {
    audioTestResult.value = {
      type: 'error',
      message: '麦克风访问失败，请检查权限设置'
    }
  } finally {
    testingMic.value = false
  }
}

/**
 * 测试扬声器
 */
const testSpeaker = async () => {
  try {
    testingSpeaker.value = true
    
    // 播放测试音频
    const audioContext = new AudioContext()
    const oscillator = audioContext.createOscillator()
    const gainNode = audioContext.createGain()
    
    oscillator.connect(gainNode)
    gainNode.connect(audioContext.destination)
    
    oscillator.frequency.setValueAtTime(440, audioContext.currentTime)
    gainNode.gain.setValueAtTime(0.1, audioContext.currentTime)
    
    oscillator.start()
    
    setTimeout(() => {
      oscillator.stop()
      audioContext.close()
      
      audioTestResult.value = {
        type: 'success',
        message: '扬声器测试完成，如果听到提示音说明工作正常'
      }
    }, 1000)
    
  } catch (error) {
    audioTestResult.value = {
      type: 'error',
      message: '扬声器测试失败'
    }
  } finally {
    testingSpeaker.value = false
  }
}

/**
 * 测试网络
 */
const testNetwork = async () => {
  try {
    testingNetwork.value = true
    
    // 简单的网络延迟测试
    const startTime = Date.now()
    await VoiceAPI.getNetworkQuality()
    const latency = Date.now() - startTime
    
    networkStats.value.latency = latency
    
    if (latency < 50) {
      networkQuality.value = 'excellent'
    } else if (latency < 100) {
      networkQuality.value = 'good'
    } else if (latency < 200) {
      networkQuality.value = 'fair'
    } else {
      networkQuality.value = 'poor'
    }
    
  } catch (error) {
    networkQuality.value = 'poor'
  } finally {
    testingNetwork.value = false
  }
}

/**
 * 获取网络质量颜色
 */
const getNetworkQualityColor = (quality: string): string => {
  const colorMap: Record<string, string> = {
    excellent: 'green',
    good: 'blue',
    fair: 'orange',
    poor: 'red'
  }
  return colorMap[quality] || 'default'
}

/**
 * 获取网络质量文本
 */
const getNetworkQualityText = (quality: string): string => {
  const textMap: Record<string, string> = {
    excellent: '极佳',
    good: '良好',
    fair: '一般',
    poor: '较差'
  }
  return textMap[quality] || quality
}

/**
 * 发起通话
 */
const initiateCall = async () => {
  if (selectedMembers.value.length === 0) {
    antMessage.warning('请至少选择一个用户')
    return
  }
  
  try {
    loading.value = true
    
    const callData: CallInitiateDto = {
      roomId: props.roomId,
      callType: callType.value === 'ONE_TO_ONE' ? CallType.ONE_TO_ONE : CallType.MULTI_USER,
      inviteUserIds: selectedMembers.value
    }
    
    const response = await VoiceAPI.initiateCall(callData)
    
    emit('call-initiated', response.data)
    handleCancel()
    
  } catch (error: any) {
    antMessage.error(error.response?.data?.msg || '发起通话失败')
  } finally {
    loading.value = false
  }
}

/**
 * 取消操作
 */
const handleCancel = () => {
  visible.value = false
  
  // 重置表单
  callType.value = 'MULTI_USER'
  selectedMembers.value = []
  settings.value = {
    autoRecord: false,
    allowInvite: true,
    muteOnJoin: false
  }
  searchKeyword.value = ''
  audioTestResult.value = null
}

// 监听通话类型变化
watch(callType, (newType) => {
  if (newType === 'ONE_TO_ONE' && selectedMembers.value.length > 1) {
    selectedMembers.value = selectedMembers.value.slice(0, 1)
  }
})

// 监听模态框打开状态
watch(visible, (isOpen) => {
  if (isOpen) {
    loadRoomMembers()
    testNetwork()
  }
})
</script>

<style scoped lang="scss">
.initiate-call-modal {
  .form-section {
    margin-bottom: 24px;

    .section-title {
      margin-bottom: 12px;
      color: #262626;
      font-size: 14px;
      font-weight: 500;

      .section-subtitle {
        color: #8c8c8c;
        font-weight: normal;
        font-size: 12px;
      }
    }

    .call-type-group {
      width: 100%;

      :deep(.ant-radio-button-wrapper) {
        flex: 1;
        text-align: center;
      }
    }

    .search-input {
      margin-bottom: 12px;
    }

    .members-list {
      max-height: 240px;
      overflow-y: auto;
      border: 1px solid #f0f0f0;
      border-radius: 6px;

      .empty-members {
        padding: 20px;
        text-align: center;
      }

      .member-item {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 12px;
        border-bottom: 1px solid #f0f0f0;
        cursor: pointer;
        transition: all 0.2s;

        &:last-child {
          border-bottom: none;
        }

        &:hover {
          background: #fafafa;
        }

        &.selected {
          background: #e6f7ff;
          border-color: #91d5ff;
        }

        &.disabled {
          opacity: 0.5;
          cursor: not-allowed;

          &:hover {
            background: transparent;
          }
        }

        .member-avatar {
          position: relative;

          .online-indicator {
            position: absolute;
            bottom: 0;
            right: 0;
            width: 8px;
            height: 8px;
            background: #52c41a;
            border: 2px solid white;
            border-radius: 50%;
          }
        }

        .member-info {
          flex: 1;

          .member-name {
            font-weight: 500;
            margin-bottom: 2px;

            .owner-badge {
              margin-left: 8px;
              padding: 2px 6px;
              background: #faad14;
              color: white;
              font-size: 10px;
              border-radius: 4px;
            }
          }

          .member-status {
            display: flex;
            gap: 12px;
            font-size: 12px;
            color: #8c8c8c;

            .status-text {
              &.online {
                color: #52c41a;
              }

              &.offline {
                color: #8c8c8c;
              }

              &.away {
                color: #faad14;
              }
            }
          }
        }

        .member-select {
          pointer-events: none;
        }
      }
    }

    .selected-members {
      margin-top: 12px;
      padding: 12px;
      background: #fafafa;
      border-radius: 6px;

      h5 {
        margin-bottom: 8px;
        color: #262626;
        font-size: 12px;
      }

      .selected-tags {
        display: flex;
        flex-wrap: wrap;
        gap: 4px;

        .member-tag {
          margin: 0;
        }
      }
    }

    .setting-item {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;

      .setting-desc {
        color: #8c8c8c;
        font-size: 12px;
      }
    }

    .audio-test {
      display: flex;
      gap: 12px;
      align-items: center;
      flex-wrap: wrap;

      .test-result {
        width: 100%;
        margin-top: 12px;
      }
    }

    .network-status {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px;
      background: #fafafa;
      border-radius: 6px;

      .network-info {
        .network-quality {
          margin-bottom: 4px;

          .quality-label {
            font-size: 12px;
            color: #8c8c8c;
          }
        }

        .network-details {
          display: flex;
          gap: 16px;
          font-size: 12px;
          color: #8c8c8c;
        }
      }
    }
  }
}

@media (max-width: 768px) {
  .initiate-call-modal {
    .form-section {
      .audio-test {
        flex-direction: column;
        align-items: stretch;
      }

      .network-status {
        flex-direction: column;
        gap: 12px;
        align-items: stretch;
      }
    }
  }
}
</style> 