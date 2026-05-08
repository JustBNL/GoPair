import { ref, computed, readonly, watch, onBeforeUnmount, type Ref } from 'vue'
import { useWebSocket, buildSubscribeMessage, buildUnsubscribeMessage } from './useWebSocket'
import { WS_ENDPOINTS, WS_FEATURES } from '@/config/websocket'
import { useAuthStore } from '@/stores/auth'
import {
  WsMessageType,
  WsEventType,
  ConnectionState
} from '@/types/websocket'
import type {
  RoomMessage,
  RoomWsState,
  SubscriptionConfig
} from '@/types/websocket'
import type { MessageVO } from '@/types/api'

/**
 * 房间WebSocket事件处理器接口
 */
interface RoomEventHandlers {
  onMessage?: (message: MessageVO) => void
  onMessageDelete?: (messageId: number) => void
  onFileUpload?: (file: any) => void
  onFileDelete?: (fileId: number) => void
  onMemberJoin?: (member: any) => void
  onMemberLeave?: (userId: number) => void
  onMemberKick?: (targetUserId: number) => void
  onTyping?: (userId: number, isTyping: boolean) => void
  onCallStart?: (callId: number, initiatorId: number) => void
  onCallEnd?: (callId: number) => void
  onSignaling?: (data: any) => void
  onVoiceRosterUpdate?: (callId: number) => void
  onEmojiReceived?: (emoji: string, senderNickname: string) => void
  onRoomRenewed?: (data: { roomId: number; expireTime: string; status: number }) => void
}

/**
 * 房间WebSocket Composable
 * 自动管理房间相关的WebSocket订阅和状态同步
 */
export function useRoomWebSocket(roomId: Ref<number>, handlers: RoomEventHandlers = {}) {
  const authStore = useAuthStore()

  const roomState = ref<RoomWsState>({
    messages: [],
    files: [],
    members: [],
    isTyping: {},
    onlineCount: 0
  })

  const subscribed = ref(false)
  const subscriptionError = ref<Error | null>(null)

  const {
    connectionState,
    isConnected,
    connect,
    disconnect,
    send
  } = useWebSocket()

  const isRoomConnected = computed(() =>
    isConnected.value && subscribed.value
  )

  const typingUsers = computed(() =>
    Object.entries(roomState.value.isTyping)
      .filter(([_, isTyping]) => isTyping)
      .map(([userId, _]) => parseInt(userId))
  )

  const connectToRoom = async (): Promise<void> => {
    if (!roomId.value) {
      throw new Error('房间ID不能为空')
    }
    try {
      subscriptionError.value = null
      const roomUrl = WS_ENDPOINTS.room(roomId.value)
      await connect(roomUrl, {
        onConnected: () => {
          if (WS_FEATURES.debug) console.log(`📡 房间WebSocket连接成功: ${roomId.value}`)
          subscribeToRoomEvents()
        },
        onDisconnected: () => {
          subscribed.value = false
          if (WS_FEATURES.debug) console.log(`📡 房间WebSocket连接断开: ${roomId.value}`)
        },
        onError: (error) => {
          subscriptionError.value = error
          if (WS_FEATURES.debug) console.error(`❌ 房间WebSocket连接失败: ${roomId.value}`, error)
        },
        onMessage: handleRoomMessage
      })
    } catch (error) {
      subscriptionError.value = error as Error
      throw error
    }
  }

  const subscribeToRoomEvents = (): void => {
    const currentUser = authStore.user
    if (!currentUser) {
      return
    }

    const subscribeMessage = buildSubscribeMessage(
      `room:${roomId.value}`,
      [
        WsEventType.MESSAGE_SEND,
        WsEventType.MESSAGE_DELETE,
        WsEventType.MESSAGE_RECALL,
        WsEventType.FILE_UPLOAD,
        WsEventType.FILE_DELETE,
        WsEventType.MEMBER_JOIN,
        WsEventType.MEMBER_LEAVE,
        WsEventType.MEMBER_KICK,
        WsEventType.MEMBER_TYPING,
        WsEventType.CALL_START,
        WsEventType.CALL_END,
        WsEventType.VOICE_ROSTER_UPDATE,
        WsEventType.ROOM_RENEWED
      ],
      currentUser.userId
    )

    const success = send(subscribeMessage)
    if (success) {
      subscribed.value = true
    } else {
    }

    // 额外订阅 user:{userId} 频道的信令消息
    const signalingUserId = currentUser.userId
    const signalingSubscribeMsg = buildSubscribeMessage(
      `user:${signalingUserId}`,
      [WsEventType.SIGNALING],
      signalingUserId
    )
    send(signalingSubscribeMsg)
    if (WS_FEATURES.debug) console.log(`✅ 信令频道订阅: user:${signalingUserId}`)
  }

  const unsubscribeFromRoom = (): void => {
    if (subscribed.value) {
      const currentUser = authStore.user
      const msg = buildUnsubscribeMessage(`room:${roomId.value}`, currentUser?.userId)
      send(msg)
      subscribed.value = false
    }
  }

  const handleRoomMessage = (message: any): void => {
    const { eventType } = message
    // Backend sends payload field; fall back to data for forward compatibility
    const data = message.payload ?? message.data
    if (WS_FEATURES.debug) console.log('🎯 [房间WebSocket] 收到消息:', { eventType, messageId: message.messageId, data })

    switch (eventType) {
      case WsEventType.MESSAGE_SEND: {
        if (WS_FEATURES.debug) console.log('✅ [房间WebSocket] 处理消息发送事件', data)
        const enriched: any = { ...data }
        if (!enriched.createTime) {
          enriched.createTime = message.timestamp || new Date().toISOString()
        }
        const uid = authStore.user?.userId
        if (typeof enriched.isOwn === 'undefined') {
          enriched.isOwn = (uid != null) && (enriched.senderId === uid)
        }
        // EMOJI 消息：触发动画回调，同时加入聊天消息列表
        if (enriched.messageType === 5) {
          handlers.onEmojiReceived?.(enriched.content, enriched.senderNickname)
        }
        roomState.value.messages = [...roomState.value.messages, enriched]
        handlers.onMessage?.(enriched)
        break
      }

      case WsEventType.MESSAGE_DELETE: {
        const messageId = data?.messageId
        if (messageId) {
          roomState.value.messages = roomState.value.messages.filter(m => m.messageId !== messageId)
          handlers.onMessageDelete?.(messageId)
        }
        break
      }

      case WsEventType.MESSAGE_RECALL: {
        const recallMessageId = data?.messageId
        const recalledAt = data?.recalledAt
        if (recallMessageId) {
          roomState.value.messages = roomState.value.messages.map(m => {
            if (m.messageId === recallMessageId) {
              return { ...m, isRecalled: true, recalledAt: recalledAt || new Date().toISOString() }
            }
            return m
          })
        }
        break
      }

      case WsEventType.FILE_UPLOAD: {
        roomState.value.files = [...roomState.value.files, data]
        handlers.onFileUpload?.(data)
        break
      }

      case WsEventType.FILE_DELETE: {
        const fileId = data?.fileId
        if (fileId) {
          roomState.value.files = roomState.value.files.filter(f => f.fileId !== fileId)
          handlers.onFileDelete?.(fileId)
        }
        break
      }

      case WsEventType.MEMBER_JOIN: {
        roomState.value.members = [...roomState.value.members, data]
        roomState.value.onlineCount++
        handlers.onMemberJoin?.(data)
        break
      }

      case WsEventType.MEMBER_LEAVE: {
        const userId = data?.userId
        if (userId) {
          roomState.value.members = roomState.value.members.filter(m => m.userId !== userId)
          roomState.value.onlineCount = Math.max(0, roomState.value.onlineCount - 1)
          handlers.onMemberLeave?.(userId)
        }
        break
      }

      case WsEventType.MEMBER_KICK: {
        const targetUserId = data?.targetUserId
        if (targetUserId) {
          handlers.onMemberKick?.(targetUserId)
        }
        break
      }

      case WsEventType.MEMBER_TYPING: {
        const { userId: typingUserId, isTyping } = data || {}
        if (typingUserId) {
          roomState.value.isTyping = { ...roomState.value.isTyping, [typingUserId]: isTyping }
          handlers.onTyping?.(typingUserId, isTyping)
        }
        break
      }

      case WsEventType.CALL_START: {
        const callId = data?.callId
        const initiatorId = data?.initiatorId
        if (callId) {
          handlers.onCallStart?.(callId, initiatorId)
        }
        break
      }

      case WsEventType.CALL_END: {
        const callId = data?.callId
        if (callId) {
          handlers.onCallEnd?.(callId)
        }
        break
      }

      case WsEventType.VOICE_ROSTER_UPDATE: {
        const callId = data?.callId
        if (callId) {
          handlers.onVoiceRosterUpdate?.(callId)
        }
        break
      }

      case WsEventType.SIGNALING: {
        handlers.onSignaling?.(data)
        break
      }

      case WsEventType.ROOM_RENEWED: {
        handlers.onRoomRenewed?.(data)
        break
      }

      default:
    }
  }

  const sendRoomMessage = (messageData: any): boolean => {
    return send({
      type: WsMessageType.ROOM_MESSAGE,
      eventType: WsEventType.MESSAGE_SEND,
      data: {
        roomId: roomId.value,
        ...messageData
      }
    })
  }

  const sendTypingStatus = (isTyping: boolean): void => {
    send({
      type: WsMessageType.ROOM_MEMBER,
      eventType: WsEventType.MEMBER_TYPING,
      data: {
        roomId: roomId.value,
        isTyping
      }
    })
  }

  const disconnectFromRoom = (): void => {
    unsubscribeFromRoom()
    disconnect()
    roomState.value = {
      messages: [],
      files: [],
      members: [],
      isTyping: {},
      onlineCount: 0
    }
  }

  const replaceMessages = (list: any[]): void => {
    roomState.value.messages = Array.isArray(list) ? list : []
  }

  watch(roomId, (newRoomId, oldRoomId) => {
    if (oldRoomId && newRoomId !== oldRoomId) {
      disconnectFromRoom()
    }
    if (newRoomId && newRoomId > 0) {
      connectToRoom().catch(error => {
        console.error('房间WebSocket连接失败:', error)
      })
    } else if (newRoomId === 0 || !newRoomId) {
    }
  }, { immediate: true })

  onBeforeUnmount(() => {
    disconnectFromRoom()
  })

  return {
    roomState: readonly(roomState),
    connectionState: readonly(connectionState),
    subscribed: readonly(subscribed),
    subscriptionError: readonly(subscriptionError),
    isConnected,
    isRoomConnected,
    typingUsers,
    connectToRoom,
    disconnectFromRoom,
    sendRoomMessage,
    sendTypingStatus,
    replaceMessages
  }
}
