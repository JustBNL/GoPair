import { ref, computed, readonly, watch, onBeforeUnmount, type Ref } from 'vue'
import { useWebSocket, buildSubscribeMessage, buildUnsubscribeMessage } from './useWebSocket'
import { WS_ENDPOINTS } from '@/config/websocket'
import { 
  WsMessageType, 
  WsEventType,
  ConnectionState 
} from '@/types/websocket'
import type { 
  VoiceMessage, 
  VoiceWsState
} from '@/types/websocket'

/**
 * 语音WebSocket事件处理器接口
 */
interface VoiceEventHandlers {
  onCallInvite?: (callData: any) => void
  onCallAccept?: (callData: any) => void
  onCallReject?: (callData: any) => void
  onCallEnd?: (callData: any) => void
  onParticipantJoin?: (participant: any) => void
  onParticipantLeave?: (userId: number) => void
  onSignaling?: (signaling: any) => void
}

/**
 * 语音WebSocket Composable
 * 专门处理语音通话的WebSocket连接和信令
 */
export function useVoiceWebSocket(callId: Ref<number | null>, handlers: VoiceEventHandlers = {}) {
  // 语音状态
  const voiceState = ref<VoiceWsState>({
    callState: 'idle',
    participants: [],
    isInitiator: false,
    isMuted: false
  })

  const subscribed = ref(false)
  const subscriptionError = ref<Error | null>(null)

  // 基础WebSocket连接
  const { 
    connectionState, 
    isConnected, 
    connect, 
    disconnect, 
    send 
  } = useWebSocket()

  // 计算属性
  const isVoiceConnected = computed(() => 
    isConnected.value && subscribed.value && callId.value !== null
  )

  const canJoinCall = computed(() => 
    voiceState.value.callState === 'ringing' && !voiceState.value.isInitiator
  )

  const isInCall = computed(() => 
    voiceState.value.callState === 'connected'
  )

  /**
   * 连接到语音WebSocket
   */
  const connectToVoice = async (targetCallId: number): Promise<void> => {
    try {
      subscriptionError.value = null
      
      // 建立到语音服务的WebSocket连接
      const voiceUrl = WS_ENDPOINTS.voice(targetCallId)
      await connect(voiceUrl, {
        onConnected: () => {
          subscribeToVoiceEvents(targetCallId)
        },
        onDisconnected: () => {
          subscribed.value = false
          voiceState.value.callState = 'idle'
        },
        onError: (error) => {
          subscriptionError.value = error
          console.error(`❌ 语音WebSocket连接失败: ${targetCallId}`, error)
        },
        onMessage: handleVoiceMessage
      })

    } catch (error) {
      subscriptionError.value = error as Error
      throw error
    }
  }

  /**
   * 订阅语音事件
   */
  const subscribeToVoiceEvents = (targetCallId: number): void => {
    const events = [
      WsEventType.CALL_START,
      WsEventType.CALL_END,
      WsEventType.PARTICIPANT_JOIN,
      WsEventType.PARTICIPANT_LEAVE
    ]

    const subscribeMessage = buildSubscribeMessage(`voice:${targetCallId}`, events)

    const success = send(subscribeMessage)
    if (success) {
      subscribed.value = true
    }
  }

  /**
   * 取消语音事件订阅
   */
  const unsubscribeFromVoice = (): void => {
    if (subscribed.value && callId.value) {
      const msg = buildUnsubscribeMessage(`voice:${callId.value}`)
      send(msg)
      subscribed.value = false
    }
  }

  /**
   * 处理语音消息
   */
  const handleVoiceMessage = (message: any): void => {
    const { eventType, data } = message

    switch (eventType) {
      case WsEventType.CALL_START:
        voiceState.value.callState = 'ringing'
        handlers.onCallInvite?.(data)
        break

      case WsEventType.CALL_END:
        voiceState.value.callState = 'ended'
        voiceState.value.participants = []
        handlers.onCallEnd?.(data)
        break

      case WsEventType.PARTICIPANT_JOIN:
        voiceState.value.participants.push(data)
        if (voiceState.value.callState === 'ringing') {
          voiceState.value.callState = 'connected'
        }
        handlers.onParticipantJoin?.(data)
        break

      case WsEventType.PARTICIPANT_LEAVE:
        const userId = data?.userId
        if (userId) {
          const index = voiceState.value.participants.findIndex(p => p.userId === userId)
          if (index > -1) {
            voiceState.value.participants.splice(index, 1)
          }
          handlers.onParticipantLeave?.(userId)
        }
        break

      default:
        // 处理WebRTC信令消息
        if (message.type === WsMessageType.VOICE_SIGNALING) {
          handlers.onSignaling?.(data)
        } else {
        }
    }
  }

  /**
   * 发起语音通话邀请
   */
  const inviteToCall = (roomId: number, targetUserIds: number[]): boolean => {
    voiceState.value.isInitiator = true
    return send({
      type: WsMessageType.VOICE_INVITE,
      data: {
        callId: callId.value,
        roomId,
        targetUserIds
      }
    })
  }

  /**
   * 接受语音通话
   */
  const acceptCall = (): boolean => {
    return send({
      type: WsMessageType.VOICE_ACCEPT,
      data: {
        callId: callId.value
      }
    })
  }

  /**
   * 拒绝语音通话
   */
  const rejectCall = (): boolean => {
    return send({
      type: WsMessageType.VOICE_REJECT,
      data: {
        callId: callId.value
      }
    })
  }

  /**
   * 结束语音通话
   */
  const endCall = (): boolean => {
    const result = send({
      type: WsMessageType.VOICE_END,
      data: {
        callId: callId.value
      }
    })
    
    if (result) {
      voiceState.value.callState = 'ended'
      voiceState.value.participants = []
    }
    
    return result
  }

  /**
   * 发送WebRTC信令
   */
  const sendSignaling = (signalingData: any): boolean => {
    return send({
      type: WsMessageType.VOICE_SIGNALING,
      data: {
        callId: callId.value,
        ...signalingData
      }
    })
  }

  /**
   * 切换静音状态
   */
  const toggleMute = (): void => {
    voiceState.value.isMuted = !voiceState.value.isMuted
    // 通知其他参与者静音状态变化
    send({
      type: WsMessageType.VOICE_SIGNALING,
      data: {
        callId: callId.value,
        type: 'mute-status',
        muted: voiceState.value.isMuted
      }
    })
  }

  /**
   * 断开语音连接
   */
  const disconnectFromVoice = (): void => {
    unsubscribeFromVoice()
    disconnect()
    
    // 重置状态
    voiceState.value = {
      callState: 'idle',
      participants: [],
      isInitiator: false,
      isMuted: false
    }
  }

  // 监听callId变化，自动管理连接
  watch(callId, (newCallId, oldCallId) => {
    if (oldCallId && newCallId !== oldCallId) {
      disconnectFromVoice()
    }
    
    if (newCallId) {
      connectToVoice(newCallId).catch(error => {
      })
    }
  }, { immediate: true })

  // 组件卸载时自动清理
  onBeforeUnmount(() => {
    disconnectFromVoice()
  })

  return {
    // 响应式状态
    voiceState: readonly(voiceState),
    connectionState: readonly(connectionState),
    subscribed: readonly(subscribed),
    subscriptionError: readonly(subscriptionError),
    
    // 计算属性
    isConnected,
    isVoiceConnected,
    canJoinCall,
    isInCall,
    
    // 方法
    connectToVoice,
    disconnectFromVoice,
    inviteToCall,
    acceptCall,
    rejectCall,
    endCall,
    sendSignaling,
    toggleMute
  }
} 