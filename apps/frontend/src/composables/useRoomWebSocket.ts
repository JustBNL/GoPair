import { ref, computed, readonly, watch, onBeforeUnmount, type Ref } from 'vue'
import { useWebSocket, buildSubscribeMessage, buildUnsubscribeMessage } from './useWebSocket'
import { WS_ENDPOINTS, WS_FEATURES } from '@/config/websocket'
import { useAuthStore } from '@/stores/auth'
import { useRoomMessageStore } from '@/stores/roomMessage'
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

/** 内存中保留的最大消息条数，超出后丢弃最早的（已迁移到 store，此常量仅作向后兼容） */
const MAX_MESSAGES = 200

/**
 * 纯JS DOM实现emoji弹幕：从屏幕右侧飘向左侧，动画结束后自动移除DOM
 * 不依赖Vue组件、CSS动画或任何前端框架，确保100%可靠
 */
function spawnEmojiDOM(emoji: string, senderNickname: string): void {
  const el = document.createElement('div')
  el.textContent = emoji
  el.style.cssText = [
    'position:fixed',
    'top:' + (Math.random() * 88 + 6) + 'vh',
    'left:100vw',
    'font-size:' + (Math.floor(Math.random() * 28) + 32) + 'px',
    'z-index:99999',
    'pointer-events:none',
    'user-select:none',
    'transform-origin:center center',
    'transition:transform 0.15s ease-out',
    'opacity:0',
  ].join(';')

  document.body.appendChild(el)

  // 触发入场缩放动画
  requestAnimationFrame(() => {
    el.style.transition = 'transform 0.15s ease-out, opacity 0.15s ease-out'
    el.style.transform = 'translateX(-5vw) scale(1.4)'
    el.style.opacity = '1'

    // 2.5秒横穿整个屏幕
    const duration = 2500 + Math.random() * 500
    let startTime: number | null = null

    function animate(ts: number) {
      if (!startTime) startTime = ts
      const elapsed = ts - startTime
      const progress = Math.min(elapsed / duration, 1)
      // ease-in-out 缓动
      const eased = progress < 0.5
        ? 2 * progress * progress
        : 1 - Math.pow(-2 * progress + 2, 2) / 2
      const scale = progress < 0.15
        ? 0.3 + (progress / 0.15) * 1.1
        : progress > 0.85
          ? 0.6 + ((1 - progress) / 0.15) * 0.4
          : 1.0
      const opacity = progress < 0.1
        ? progress / 0.1
        : progress > 0.85
          ? (1 - progress) / 0.15
          : 1
      el.style.left = (100 - progress * 115) + 'vw'
      el.style.transform = 'translateX(0) scale(' + scale + ')'
      el.style.opacity = String(opacity)
      if (progress < 1) {
        requestAnimationFrame(animate)
      } else {
        el.remove()
      }
    }

    requestAnimationFrame(animate)
  })
}

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
  onRoomClosed?: (data: { roomId: number; operatorId: number }) => void
  onRoomRenewed?: (data: { roomId: number; expireTime: string; status: number }) => void
  onRoomReopened?: (data: { roomId: number; expireTime: string; status: number }) => void
}

/**
 * 房间WebSocket Composable
 * 自动管理房间相关的WebSocket订阅和状态同步
 */
export function useRoomWebSocket(roomId: Ref<number>, handlers: RoomEventHandlers = {}) {
  const authStore = useAuthStore()
  const roomMessageStore = useRoomMessageStore()

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
        onMessage: handleRoomMessage,
        onReconnected: () => {
          if (WS_FEATURES.debug) console.log(`📡 房间WebSocket重连成功，发送catch-up: ${roomId.value}`)
          subscribed.value = false
          subscribeToRoomEvents()
          sendCatchUp()
        }
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
        WsEventType.SIGNALING,
        WsEventType.VOICE_ROSTER_UPDATE,
        WsEventType.ROOM_CLOSED,
        WsEventType.ROOM_RENEWED,
        WsEventType.ROOM_REOPENED
      ],
      currentUser.userId
    )

    const success = send(subscribeMessage)
    if (success) {
      subscribed.value = true
    } else {
    }
  }

  const unsubscribeFromRoom = (): void => {
    if (subscribed.value) {
      const currentUser = authStore.user
      const msg = buildUnsubscribeMessage(`room:${roomId.value}`, currentUser?.userId)
      send(msg)
      subscribed.value = false
    }
  }

  /** 从 sessionStorage 读取 lastMessageId 并发送 catch-up 消息 */
  const sendCatchUp = (): void => {
    const lastMsgId = roomMessageStore.getLastMessageId()
    if (lastMsgId == null) return
    const msg = {
      type: WsMessageType.CATCH_UP as any,
      eventType: 'catch_up' as any,
      data: {
        payload: {
          channel: `room:${roomId.value}`,
          lastMessageId: lastMsgId
        }
      }
    }
    if (WS_FEATURES.debug) console.log(`[房间WebSocket] 发送catch-up: roomId=${roomId.value}, lastMessageId=${lastMsgId}`)
    send(msg)
  }

  const handleRoomMessage = (message: any): void => {
    const { eventType } = message
    // Backend sends payload field; fall back to data for forward compatibility
    const data = message.payload ?? message.data
    console.log(`[useRoomWebSocket] 收到 WS 消息: eventType=${eventType}, messageId=${message.messageId}, hasData=${!!data}`)

    switch (eventType) {
      case WsEventType.MESSAGE_SEND: {
        if (WS_FEATURES.debug) console.log('✅ [房间WebSocket] 处理消息发送事件, channel={}, data=', message.channel, data)
        const enriched: any = { ...data }
        if (!enriched.createTime) {
          enriched.createTime = message.timestamp || new Date().toISOString()
        }
        const uid = authStore.user?.userId
        if (typeof enriched.isOwn === 'undefined') {
          enriched.isOwn = (uid != null) && (enriched.senderId === uid)
        }
        if (enriched.messageType === 5) {
          spawnEmojiDOM(enriched.content, enriched.senderNickname)
        }
        // 委托给 roomMessageStore 管理消息状态
        roomMessageStore.appendMessage(enriched)
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
        const recallerNickname = data?.recallerNickname
        if (recallMessageId) {
          roomMessageStore.recallMessage(recallMessageId, recalledAt, recallerNickname)
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
        console.log(`[useRoomWebSocket] 收到 CALL_START: callId=${callId}, initiatorId=${initiatorId}`)
        if (callId) {
          handlers.onCallStart?.(callId, initiatorId)
        }
        break
      }

      case WsEventType.CALL_END: {
        const callId = data?.callId
        console.log(`[useRoomWebSocket] 收到 CALL_END: callId=${callId}`)
        if (callId) {
          handlers.onCallEnd?.(callId)
        }
        break
      }

      case WsEventType.VOICE_ROSTER_UPDATE: {
        const callId = data?.callId
        console.log(`[useRoomWebSocket] 收到 VOICE_ROSTER_UPDATE: callId=${callId}`)
        if (callId) {
          handlers.onVoiceRosterUpdate?.(callId)
        }
        break
      }

      case WsEventType.SIGNALING: {
        console.log(`[useRoomWebSocket] 收到 SIGNALING: eventType=${eventType}, data.type=${data?.type}, data.fromUserId=${data?.fromUserId}, data.callId=${data?.callId}`)
        handlers.onSignaling?.(data)
        break
      }

      case WsEventType.ROOM_CLOSED: {
        handlers.onRoomClosed?.(data)
        break
      }

      case WsEventType.ROOM_RENEWED: {
        handlers.onRoomRenewed?.(data)
        break
      }

      case WsEventType.ROOM_REOPENED: {
        handlers.onRoomReopened?.(data)
        break
      }

      case 'catch_up_result': {
        if (WS_FEATURES.debug) console.log('[房间WebSocket] 收到catch_up_result: count=', data?.count, data?.messages?.length)
        const msgs: any[] = data?.messages || []
        for (const msg of msgs) {
          roomMessageStore.appendMessage(msg)
        }
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
