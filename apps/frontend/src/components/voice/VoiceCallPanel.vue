<template>
  <div class="voice-call-panel">
    <div class="voice-panel-content">
      <!-- 状态显示 -->
      <div class="call-status-section">
        <div class="status-display">
          <div class="status-icon" :class="statusIconClass">
            <PhoneOutlined v-if="callState === 'idle'" />
            <AudioOutlined v-else />
          </div>
          <div class="status-text">
            <h3>语音通话</h3>
            <p>{{ callStateText }}</p>
          </div>
        </div>
      </div>

      <!-- 参与者列表 -->
      <div v-if="callState !== 'idle'" class="participants-section">
        <div class="participant-item" v-for="p in participants" :key="p.userId">
          <a-avatar :size="36">{{ p.nickname?.charAt(0) || 'U' }}</a-avatar>
          <span class="participant-name">{{ p.nickname }}</span>
          <span v-if="p.isSpeaking" class="speaking-dot">●</span>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="call-actions">
        <a-button
          v-if="callState === 'idle' && isOwner"
          type="primary"
          size="large"
          @click="initiateCall"
          :loading="loading"
        >
          <PhoneOutlined />
          发起通话
        </a-button>

        <a-button
          v-else-if="callState === 'calling' && !isOwner"
          type="primary"
          size="large"
          @click="joinCall"
          :loading="loading"
        >
          <PhoneOutlined />
          加入通话
        </a-button>

        <template v-if="callState === 'in-call'">
          <a-button
            :type="isMuted ? 'default' : 'primary'"
            size="large"
            @click="toggleMute"
          >
            <AudioMutedOutlined v-if="isMuted" />
            <AudioOutlined v-else />
            {{ isMuted ? '取消静音' : '静音' }}
          </a-button>
          <a-button danger size="large" @click="leaveCall" :loading="loading">
            <PhoneOutlined />
            离开通话
          </a-button>
        </template>

        <a-button
          v-if="callState !== 'idle' && isOwner"
          danger
          size="large"
          @click="endCall"
          :loading="loading"
          style="margin-left: 8px"
        >
          结束通话
        </a-button>
      </div>

      <!-- 空闲状态说明 -->
      <div v-if="callState === 'idle'" class="feature-description">
        <a-alert
          :message="isOwner ? '你是房主，可以发起语音通话' : '等待房主发起语音通话'"
          type="info"
          show-icon
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import { PhoneOutlined, AudioOutlined, AudioMutedOutlined } from '@ant-design/icons-vue'
import { VoiceAPI } from '@/api/voice'
import { WebRTCManager } from '@/utils/webrtc/WebRTCManager'
import type { CallParticipant } from '@/utils/webrtc/WebRTCManager'

interface Props {
  roomId: number
  currentUserId: number
  isOwner?: boolean
}

interface Emits {
  (e: 'call-state-changed', state: 'idle' | 'calling' | 'in-call'): void
}

const props = withDefaults(defineProps<Props>(), { isOwner: false })
const emit = defineEmits<Emits>()

// 状态
const callState = ref<'idle' | 'calling' | 'in-call'>('idle')
const loading = ref(false)
const isMuted = ref(false)
const currentCallId = ref<number | null>(null)
const participants = ref<CallParticipant[]>([])

// WebRTC 管理器
const rtcManager = new WebRTCManager()

// 设置信令发送函数（由父组件通过 sendSignaling 方法注入）
let signalingFn: ((data: any) => void) | null = null

rtcManager.setSignalingSender((msg) => {
  if (signalingFn) signalingFn(msg)
  else console.warn('[VoiceCallPanel] No signalingFn set, dropping:', msg)
})

rtcManager.setCurrentUserIdGetter(() => props.currentUserId)

rtcManager.callbacks = {
  ...rtcManager.callbacks,
  onParticipantJoined: (p) => {
    participants.value = Array.from(rtcManager.getCallState().participants.values())
  },
  onParticipantLeft: () => {
    participants.value = Array.from(rtcManager.getCallState().participants.values())
  },
  onParticipantSpeaking: (userId, isSpeaking) => {
    const p = participants.value.find(x => x.userId === userId)
    if (p) p.isSpeaking = isSpeaking
  }
}

// 计算属性
const callStateText = computed(() => {
  const map = { idle: '空闲', calling: '通话进行中（等待加入）', 'in-call': '通话中' }
  return map[callState.value]
})

const statusIconClass = computed(() => ({
  'status-idle': callState.value === 'idle',
  'status-active': callState.value === 'in-call',
  'status-calling': callState.value === 'calling'
}))

// 发起通话（房主）
const initiateCall = async () => {
  try {
    loading.value = true
    const res = await VoiceAPI.initiateCall({ roomId: props.roomId, callType: 'VOICE' as any })
    currentCallId.value = res.data.callId
    await rtcManager.initiateCall(res.data.callId, [])
    callState.value = 'in-call'
    emit('call-state-changed', 'in-call')
    antMessage.success('通话已发起')
  } catch (e: any) {
    antMessage.error(e?.response?.data?.msg || '发起通话失败')
  } finally {
    loading.value = false
  }
}

// 加入通话（非房主，收到 call_start 后可调用）
const joinCall = async () => {
  if (!currentCallId.value) return
  try {
    loading.value = true
    await VoiceAPI.joinCall(currentCallId.value)
    await rtcManager.joinCall(currentCallId.value)
    callState.value = 'in-call'
    emit('call-state-changed', 'in-call')
    antMessage.success('已加入通话')
  } catch (e: any) {
    antMessage.error(e?.response?.data?.msg || '加入通话失败')
  } finally {
    loading.value = false
  }
}

// 静音切换
const toggleMute = () => {
  isMuted.value = !rtcManager.toggleMute()
}

// 离开通话
const leaveCall = async () => {
  if (!currentCallId.value) return
  try {
    loading.value = true
    await VoiceAPI.leaveCall(currentCallId.value)
    rtcManager.leaveCall()
    participants.value = []
    callState.value = 'idle'
    currentCallId.value = null
    emit('call-state-changed', 'idle')
  } catch (e: any) {
    antMessage.error(e?.response?.data?.msg || '离开通话失败')
  } finally {
    loading.value = false
  }
}

// 结束通话（房主）
const endCall = async () => {
  if (!currentCallId.value) return
  try {
    loading.value = true
    await VoiceAPI.endCall(currentCallId.value)
    rtcManager.leaveCall()
    participants.value = []
    callState.value = 'idle'
    currentCallId.value = null
    emit('call-state-changed', 'idle')
  } catch (e: any) {
    antMessage.error(e?.response?.data?.msg || '结束通话失败')
  } finally {
    loading.value = false
  }
}

// ---- 供父组件调用的方法（通过 defineExpose）----

/**
 * 注入信令发送函数（由 RoomDetailView 传入 WebSocket send 方法）
 */
const setSignalingSender = (fn: (data: any) => void) => {
  signalingFn = fn
}

/**
 * 收到 call_start 事件（后端推送）
 */
const onCallStart = (data: any) => {
  currentCallId.value = data.callId
  if (!props.isOwner) {
    callState.value = 'calling'
    emit('call-state-changed', 'calling')
  }
}

/**
 * 收到 voice_roster_update 事件
 */
const onRosterUpdate = async (data: any) => {
  if (!currentCallId.value) return
  const roster: number[] = data.participants || []
  for (const userId of roster) {
    if (userId !== props.currentUserId) {
      await rtcManager.addParticipant(userId)
    }
  }
  participants.value = Array.from(rtcManager.getCallState().participants.values())
}

/**
 * 收到 signaling 事件（WebRTC 信令）
 */
const onSignaling = async (message: any) => {
  const sigData = message.data || message
  await rtcManager.handleSignalingMessage(sigData)
}

/**
 * 收到 call_end 事件
 */
const onCallEnd = () => {
  rtcManager.leaveCall()
  participants.value = []
  callState.value = 'idle'
  currentCallId.value = null
  emit('call-state-changed', 'idle')
}

// 组件销毁时清理
onBeforeUnmount(() => {
  rtcManager.destroy()
})

defineExpose({
  setSignalingSender,
  onCallStart,
  onRosterUpdate,
  onSignaling,
  onCallEnd
})
</script>

<style scoped lang="scss">
.voice-call-panel {
  padding: 24px;
  background: white;
  border-radius: 8px;

  .voice-panel-content {
    text-align: center;

    .call-status-section {
      margin-bottom: 24px;

      .status-display {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 12px;

        .status-icon {
          font-size: 48px;
          transition: color 0.3s;
          &.status-idle { color: #8c8c8c; }
          &.status-calling { color: #faad14; }
          &.status-active { color: #52c41a; }
        }

        .status-text {
          h3 { margin: 0; font-size: 20px; color: #262626; }
          p { margin: 4px 0 0; color: #8c8c8c; font-size: 14px; }
        }
      }
    }

    .participants-section {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: 16px;
      margin-bottom: 24px;

      .participant-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 4px;
        .participant-name { font-size: 12px; color: #595959; }
        .speaking-dot { color: #52c41a; font-size: 10px; }
      }
    }

    .call-actions {
      display: flex;
      justify-content: center;
      gap: 12px;
      margin-bottom: 24px;
      flex-wrap: wrap;
    }

    .feature-description {
      max-width: 400px;
      margin: 0 auto;
    }
  }
}
</style>
