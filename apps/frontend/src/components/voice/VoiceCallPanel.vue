<template>
  <div class="voice-call-panel">
    <!-- 加载中 -->
    <div v-if="loading" class="state-center">
      <a-spin size="large" />
      <p>加载中...</p>
    </div>

    <!-- 无活跃通话 / 有活跃通话但未加入（统一显示加入按钮） -->
    <div v-else-if="callState === 'idle' || callState === 'active'" class="state-center">
      <div :class="['voice-icon', callState]"><PhoneOutlined /></div>
      <h3>语音频道</h3>
      <p v-if="callState === 'idle'" class="desc">语音频道已准备就绪</p>
      <p v-else class="desc">{{ currentCall?.participantCount ?? 0 }} 人正在通话中</p>
      <div v-if="callState === 'active'" class="participant-avatars">
        <a-avatar
          v-for="p in (currentCall?.participants ?? []).slice(0, 5)"
          :key="p.userId"
          :size="36"
          class="participant-avatar"
        >{{ p.userId }}</a-avatar>
        <span v-if="(currentCall?.participantCount ?? 0) > 5" class="more-count">
          +{{ (currentCall?.participantCount ?? 0) - 5 }}
        </span>
      </div>
      <a-button type="primary" size="large" :loading="actionLoading" @click="handleJoin">
        <PhoneOutlined /> 加入通话
      </a-button>
    </div>

    <!-- 已加入通话 -->
    <div v-else-if="callState === 'in-call'" class="state-in-call">
      <div class="call-header">
        <div class="voice-icon in-call"><PhoneOutlined /></div>
        <div>
          <h3>通话中</h3>
          <p class="desc">{{ currentCall?.participantCount ?? 1 }} 人参与</p>
        </div>
      </div>
      <div class="participants-list">
        <div v-for="p in (currentCall?.participants ?? [])" :key="p.userId" class="participant-row">
          <a-avatar :size="32">{{ p.userId }}</a-avatar>
          <span class="participant-id">用户 {{ p.userId }}</span>
          <a-tag v-if="p.initiator" color="blue" size="small">发起人</a-tag>
        </div>
      </div>
      <div class="call-controls">
        <a-button shape="circle" size="large" @click="handleToggleMute" :class="{'ctrl-btn--off': isMuted}">
          <AudioMutedOutlined v-if="isMuted" />
          <AudioOutlined v-else />
        </a-button>
        <a-button shape="circle" size="large" @click="handleToggleSpeaker" :class="{'ctrl-btn--off': isSpeakerOff}">
          <SoundOutlined v-if="!isSpeakerOff" />
          <AudioMutedOutlined v-else />
        </a-button>
      </div>
      <div class="call-actions">
        <a-button danger size="large" :loading="actionLoading" @click="handleLeave">离开通话</a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import {
  PhoneOutlined,
  AudioOutlined,
  AudioMutedOutlined,
  SoundOutlined
} from '@ant-design/icons-vue'
import { VoiceAPI } from '@/api/voice'
import { WebRTCManager } from '@/utils/webrtc/WebRTCManager'
import type { CallVO } from '@/types/api'

interface Props {
  roomId: number
  currentUserId: number
}

interface Emits {
  (e: 'call-state-changed', state: 'idle' | 'calling' | 'in-call'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

type CallState = 'idle' | 'active' | 'in-call'

const callState = ref<CallState>('idle')
const currentCall = ref<CallVO | null>(null)
const loading = ref(false)
const actionLoading = ref(false)
const isMuted = ref(false)
const isSpeakerOff = ref(false)

let webrtcManager: WebRTCManager | null = null
const pendingSignals: any[] = []

async function drainPendingSignals(): Promise<void> {
  if (pendingSignals.length === 0) return
  console.log('[WebRTC] Flushing buffered signals:', pendingSignals.length)
  const signals = pendingSignals.splice(0, pendingSignals.length)
  for (const sig of signals) {
    await handleSignalingInternal(sig)
  }
}

function cleanupWebRTC(): void {
  if (webrtcManager) {
    webrtcManager.leaveCall()
    webrtcManager = null
  }
  pendingSignals.length = 0
  isMuted.value = false
  isSpeakerOff.value = false
}

/**
 * 离开通话并清理本地资源（同时通知后端）
 * 用于：onBeforeUnmount、beforeunload、退出登录、返回大厅
 */
async function leaveAndCleanup(): Promise<void> {
  if (callState.value === 'in-call' && currentCall.value?.callId) {
    const callId = currentCall.value.callId
    cleanupWebRTC()
    currentCall.value = null
    callState.value = 'idle'
    emit('call-state-changed', 'idle')
    try {
      await VoiceAPI.leaveCall(callId)
    } catch (e) {
      console.warn('[WebRTC] leaveCall API failed (may be ok on unload):', e)
    }
  } else {
    cleanupWebRTC()
  }
}

/**
 * 供父组件调用：在导航离开前主动退出通话
 */
async function handleLeaveBeforeUnmount(): Promise<void> {
  await leaveAndCleanup()
}

onMounted(async () => {
  loading.value = true
  try {
    const res = await VoiceAPI.getActiveCall(props.roomId)
    if (res.data) {
      currentCall.value = res.data
      callState.value = 'active'
    }
  } catch {
    // 无活跃通话，保持 idle
  } finally {
    loading.value = false
  }

  // 页面刷新/关闭时用 sendBeacon 通知后端离开通话
  window.addEventListener('beforeunload', handleBeforeUnload)
})

function handleBeforeUnload(): void {
  if (callState.value === 'in-call' && currentCall.value?.callId) {
    const callId = currentCall.value.callId
    cleanupWebRTC()
    // sendBeacon 是 beforeunload 里唯一可靠的异步请求方式
    const token = document.cookie.match(/token=([^;]+)/)?.[1] ||
      localStorage.getItem('token') || sessionStorage.getItem('token') || ''
    navigator.sendBeacon(
      `/voice/${callId}/leave`,
      new Blob([JSON.stringify({})], { type: 'application/json' })
    )
  }
}

onBeforeUnmount(async () => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  await leaveAndCleanup()
})

/**
 * 加入通话（两阶段）：
 * 阶段一：joinOrCreateCall — 后端记录参与者，但不广播 participant-join
 * 阶段二：WebRTC 本地就绪（麦克风 + callState = in-call）
 * 阶段三：notifyReady — 后端广播 participant-join，其他人此时才发 offer
 */
async function handleJoin() {
  actionLoading.value = true
  try {
    // 阶段一：通知后端加入（不触发 participant-join）
    const res = await VoiceAPI.joinOrCreateCall(props.roomId)
    currentCall.value = res.data
    const resolvedCallId = res.data.callId

    // 阶段二-A：建立 WebRTC Manager 并绑定信令发送器
    webrtcManager = new WebRTCManager({}, {
      onError: (err) => {
        console.error('[WebRTC] Error:', err)
        antMessage.error('音频连接失败: ' + err.message)
      }
    })
    webrtcManager.setCurrentUserIdGetter(() => props.currentUserId)
    webrtcManager.setSignalingSender((msg: any) => {
      VoiceAPI.forwardSignaling({
        callId: resolvedCallId,
        type: msg.type,
        targetUserId: msg.targetUserId,
        data: msg.sdp ?? msg.candidate ?? msg
      }).catch((e) => console.error('[WebRTC] Signaling send failed:', e))
    })

    // 阶段二-B：获取麦克风
    if (!window.isSecureContext) {
      antMessage.error('当前页面不是安全上下文（需要 HTTPS），无法获取麦克风。请使用 https:// 地址访问。', 6)
      webrtcManager = null
    } else if (!navigator.mediaDevices?.getUserMedia) {
      antMessage.error('当前浏览器不支持麦克风访问，请使用 Chrome / Safari 最新版。', 6)
      webrtcManager = null
    } else {
      try {
        await webrtcManager.initializeLocalStream()
        console.log('[WebRTC] Local audio stream ready, callId:', resolvedCallId)
      } catch (err: any) {
        const msg = err?.message ?? String(err)
        if (msg.includes('Permission') || msg.includes('NotAllowed') || msg.includes('权限')) {
          antMessage.warning('请在浏览器中允许麦克风权限后重试。', 5)
        } else {
          antMessage.warning('麦克风获取失败，通话已加入但无音频: ' + msg)
        }
        webrtcManager = null
      }
    }

    // 阶段二-C：设置 WebRTC 内部通话状态
    if (webrtcManager) {
      await webrtcManager.joinCall(resolvedCallId)
    }

    // 阶段二-D：标记 callState = in-call，信令不再被 buffer
    callState.value = 'in-call'
    isMuted.value = false
    isSpeakerOff.value = false
    emit('call-state-changed', 'in-call')

    // 阶段二-E：冲刷在等待期间缓冲的信令
    await drainPendingSignals()

    // 阶段三：本端完全就绪，通知后端广播 participant-join
    await VoiceAPI.notifyReady(resolvedCallId)
    console.log('[WebRTC] notifyReady sent, callId:', resolvedCallId)

    antMessage.success('已加入通话')
  } catch (error: any) {
    cleanupWebRTC()
    callState.value = currentCall.value ? 'active' : 'idle'
    antMessage.error(error.response?.data?.msg || '加入通话失败')
  } finally {
    actionLoading.value = false
  }
}

/**
 * 离开通话：清理本地资源，直接回到 idle。
 * 若是最后一人，后端广播 call_end，所有客户端经 notifyCallEnd 回到 idle。
 */
async function handleLeave() {
  if (!currentCall.value) return
  actionLoading.value = true
  try {
    const callId = currentCall.value.callId
    cleanupWebRTC()
    await VoiceAPI.leaveCall(callId)
    currentCall.value = null
    callState.value = 'idle'
    emit('call-state-changed', 'idle')
    antMessage.success('已离开通话')
  } catch (error: any) {
    antMessage.error(error.response?.data?.msg || '操作失败')
  } finally {
    actionLoading.value = false
  }
}

async function notifyCallStart(callId: number) {
  if (callState.value === 'idle') {
    try {
      const res = await VoiceAPI.getCall(callId)
      currentCall.value = res.data
      callState.value = 'active'
    } catch { /* ignore */ }
  }
}

function notifyCallEnd(_callId: number) {
  if (callState.value !== 'idle') {
    cleanupWebRTC()
    currentCall.value = null
    callState.value = 'idle'
    emit('call-state-changed', 'idle')
  }
}

async function handleSignalingInternal(data: any): Promise<void> {
  if (!webrtcManager) {
    console.warn('[WebRTC] Manager not ready, signal lost:', data?.type)
    return
  }

  const sigType = data?.type
  console.log('[WebRTC] Processing signal:', sigType, 'from:', data?.fromUserId)

  try {
    if (sigType === 'participant-join') {
      const newUserId = data?.userId
      if (newUserId && newUserId !== props.currentUserId) {
        console.log('[WebRTC] New participant, sending offer to:', newUserId)
        await webrtcManager.addParticipant(newUserId)
      }
    } else if (sigType === 'offer' || sigType === 'answer') {
      const sdpPayload = data?.data
      console.log('[WebRTC] SDP payload:', sigType, 'fromUserId:', data?.fromUserId, 'payload type:', typeof sdpPayload, sdpPayload ? JSON.stringify(sdpPayload).substring(0, 80) : 'NULL')
      if (!sdpPayload) {
        console.error('[WebRTC] Missing SDP payload for', sigType, 'full data:', JSON.stringify(data))
        return
      }
      try {
        const sdpObj = sdpPayload instanceof RTCSessionDescription ? sdpPayload : new RTCSessionDescription(sdpPayload)
        console.log('[WebRTC] RTCSessionDescription created, type:', sdpObj.type)
        await webrtcManager.handleSignalingMessage({
          type: sigType,
          fromUserId: data?.fromUserId,
          targetUserId: data?.targetUserId ?? props.currentUserId,
          sdp: sdpObj
        })
        console.log('[WebRTC] handleSignalingMessage done for:', sigType)
      } catch (sdpErr) {
        console.error('[WebRTC] SDP processing error for', sigType, ':', sdpErr, 'payload was:', sdpPayload)
      }
    } else if (sigType === 'ice-candidate') {
      const candidatePayload = data?.data
      if (!candidatePayload) {
        console.error('[WebRTC] Missing ICE candidate payload')
        return
      }
      await webrtcManager.handleSignalingMessage({
        type: sigType,
        fromUserId: data?.fromUserId,
        targetUserId: data?.targetUserId ?? props.currentUserId,
        candidate: candidatePayload instanceof RTCIceCandidate ? candidatePayload : new RTCIceCandidate(candidatePayload)
      })
    } else if (sigType === 'participant-leave') {
      const leftUserId = data?.userId
      if (leftUserId) {
        webrtcManager.removeParticipant(leftUserId)
      }
    } else {
      console.warn('[WebRTC] Unknown signal type:', sigType)
    }
  } catch (error) {
    console.error('[WebRTC] Error processing signal:', sigType, error)
  }
}

async function handleSignaling(data: any): Promise<void> {
  if (!webrtcManager) {
    console.log('[WebRTC] Manager not ready, buffering signal:', data?.type)
    pendingSignals.push(data)
    return
  }
  if (callState.value !== 'in-call') {
    console.log('[WebRTC] Not in-call yet, buffering signal:', data?.type)
    pendingSignals.push(data)
    return
  }
  await handleSignalingInternal(data)
}

function handleToggleMute() {
  if (!webrtcManager) return
  isMuted.value = webrtcManager.toggleMute()
}

function handleToggleSpeaker() {
  isSpeakerOff.value = !isSpeakerOff.value
  document.querySelectorAll('audio[data-voice-user-id]').forEach((el) => {
    ;(el as HTMLAudioElement).muted = isSpeakerOff.value
  })
}

defineExpose({ notifyCallStart, notifyCallEnd, handleSignaling, handleLeaveBeforeUnmount })
</script>

<style scoped lang="scss">
.voice-call-panel {
  display: flex;
  flex-direction: column;
  min-height: 320px;
  padding: 24px;

  .state-center {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    flex: 1;
    gap: 12px;
    text-align: center;

    h3 { margin: 0; font-size: 20px; color: #262626; }
    .desc { margin: 0; color: #8c8c8c; font-size: 14px; }
  }

  .voice-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 72px;
    height: 72px;
    border-radius: 50%;
    font-size: 32px;

    &.idle    { background: #f0f5ff; color: #1890ff; }
    &.active  { background: #fff7e6; color: #fa8c16; animation: pulse 1.5s ease-in-out infinite; }
    &.in-call { background: #f6ffed; color: #52c41a; }
  }

  .participant-avatars {
    display: flex;
    align-items: center;
    gap: 4px;
    margin: 8px 0;

    .participant-avatar { border: 2px solid #fff; }
    .more-count { font-size: 12px; color: #8c8c8c; margin-left: 4px; }
  }

  .state-in-call {
    display: flex;
    flex-direction: column;
    gap: 20px;

    .call-header {
      display: flex;
      align-items: center;
      gap: 16px;

      h3 { margin: 0; font-size: 18px; color: #262626; }
      .desc { margin: 0; color: #8c8c8c; font-size: 13px; }
    }

    .participants-list {
      border: 1px solid #f0f0f0;
      border-radius: 8px;
      overflow: hidden;

      .participant-row {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 10px 14px;
        border-bottom: 1px solid #f0f0f0;

        &:last-child { border-bottom: none; }
        .participant-id { flex: 1; font-size: 14px; color: #262626; }
      }
    }

    .call-controls {
      display: flex;
      justify-content: center;
      gap: 16px;

      .ctrl-btn--off {
        background: #ff4d4f;
        border-color: #ff4d4f;
        color: #fff;

        &:hover {
          background: #ff7875;
          border-color: #ff7875;
        }
      }
    }

    .call-actions {
      display: flex;
      gap: 12px;
    }
  }
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50%       { transform: scale(1.08); }
}
</style>
