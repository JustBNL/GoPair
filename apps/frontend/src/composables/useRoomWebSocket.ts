import { ref, computed, readonly, watch, onBeforeUnmount, type Ref } from 'vue'
import { useWebSocket } from './useWebSocket'
import { WS_ENDPOINTS } from '@/config/websocket'
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
  onTyping?: (userId: number, isTyping: boolean) => void
}

/**
 * 房间WebSocket Composable
 * 自动管理房间相关的WebSocket订阅和状态同步
 */
export function useRoomWebSocket(roomId: Ref<number>, handlers: RoomEventHandlers = {}) {
  // 房间状态
  const roomState = ref<RoomWsState>({
    messages: [],
    files: [],
    members: [],
    isTyping: {},
    onlineCount: 0
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
  const isRoomConnected = computed(() => 
    isConnected.value && subscribed.value
  )

  const typingUsers = computed(() => 
    Object.entries(roomState.value.isTyping)
      .filter(([_, isTyping]) => isTyping)
      .map(([userId, _]) => parseInt(userId))
  )

  /**
   * 连接到房间WebSocket
   */
  const connectToRoom = async (): Promise<void> => {
    if (!roomId.value) {
      throw new Error('房间ID不能为空')
    }

    try {
      subscriptionError.value = null
      
      // 建立到房间的WebSocket连接
      const roomUrl = WS_ENDPOINTS.room(roomId.value)
      await connect(roomUrl, {
        onConnected: () => {
          console.log(`📡 房间WebSocket连接成功: ${roomId.value}`)
          subscribeToRoomEvents()
        },
        onDisconnected: () => {
          subscribed.value = false
          console.log(`📡 房间WebSocket连接断开: ${roomId.value}`)
        },
        onError: (error) => {
          subscriptionError.value = error
          console.error(`❌ 房间WebSocket连接失败: ${roomId.value}`, error)
        },
        onMessage: handleRoomMessage
      })

    } catch (error) {
      subscriptionError.value = error as Error
      throw error
    }
  }

  /**
   * 订阅房间事件
   */
  const subscribeToRoomEvents = (): void => {
    const events = [
      WsEventType.MESSAGE_SEND,
      WsEventType.MESSAGE_DELETE,
      WsEventType.FILE_UPLOAD,
      WsEventType.FILE_DELETE,
      WsEventType.MEMBER_JOIN,
      WsEventType.MEMBER_LEAVE,
      WsEventType.MEMBER_TYPING
    ]

    const subscribeMessage = {
      type: WsMessageType.SUBSCRIBE,
      data: {
        roomId: roomId.value,
        events: events
      }
    }

    const success = send(subscribeMessage)
    if (success) {
      subscribed.value = true
      console.log(`✅ 房间事件订阅成功: ${roomId.value}`)
    }
  }

  /**
   * 取消房间事件订阅
   */
  const unsubscribeFromRoom = (): void => {
    if (subscribed.value) {
      send({
        type: WsMessageType.UNSUBSCRIBE,
        data: { roomId: roomId.value }
      })
      subscribed.value = false
      console.log(`📤 取消房间订阅: ${roomId.value}`)
    }
  }

  /**
   * 处理房间消息
   */
  const handleRoomMessage = (message: any): void => {
    const { eventType, data } = message

    switch (eventType) {
      case WsEventType.MESSAGE_SEND:
        roomState.value.messages.push(data)
        handlers.onMessage?.(data)
        break

      case WsEventType.MESSAGE_DELETE:
        const messageId = data?.messageId
        if (messageId) {
          const index = roomState.value.messages.findIndex(m => m.messageId === messageId)
          if (index > -1) {
            roomState.value.messages.splice(index, 1)
          }
          handlers.onMessageDelete?.(messageId)
        }
        break

      case WsEventType.FILE_UPLOAD:
        roomState.value.files.push(data)
        handlers.onFileUpload?.(data)
        break

      case WsEventType.FILE_DELETE:
        const fileId = data?.fileId
        if (fileId) {
          const index = roomState.value.files.findIndex(f => f.fileId === fileId)
          if (index > -1) {
            roomState.value.files.splice(index, 1)
          }
          handlers.onFileDelete?.(fileId)
        }
        break

      case WsEventType.MEMBER_JOIN:
        roomState.value.members.push(data)
        roomState.value.onlineCount++
        handlers.onMemberJoin?.(data)
        break

      case WsEventType.MEMBER_LEAVE:
        const userId = data?.userId
        if (userId) {
          const index = roomState.value.members.findIndex(m => m.userId === userId)
          if (index > -1) {
            roomState.value.members.splice(index, 1)
          }
          roomState.value.onlineCount = Math.max(0, roomState.value.onlineCount - 1)
          handlers.onMemberLeave?.(userId)
        }
        break

      case WsEventType.MEMBER_TYPING:
        const { userId: typingUserId, isTyping } = data || {}
        if (typingUserId) {
          roomState.value.isTyping[typingUserId] = isTyping
          handlers.onTyping?.(typingUserId, isTyping)
        }
        break

      default:
        console.log('未处理的房间事件:', eventType, data)
    }
  }

  /**
   * 发送房间消息
   */
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

  /**
   * 发送输入状态
   */
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

  /**
   * 断开房间连接
   */
  const disconnectFromRoom = (): void => {
    unsubscribeFromRoom()
    disconnect()
    
    // 重置状态
    roomState.value = {
      messages: [],
      files: [],
      members: [],
      isTyping: {},
      onlineCount: 0
    }
  }

  // 监听房间ID变化，自动重新连接
  watch(roomId, (newRoomId, oldRoomId) => {
    if (oldRoomId && newRoomId !== oldRoomId) {
      disconnectFromRoom()
    }
    
    if (newRoomId) {
      connectToRoom().catch(error => {
        console.error('房间WebSocket重连失败:', error)
      })
    }
  }, { immediate: true })

  // 组件卸载时自动清理
  onBeforeUnmount(() => {
    disconnectFromRoom()
  })

  return {
    // 响应式状态
    roomState: readonly(roomState),
    connectionState: readonly(connectionState),
    subscribed: readonly(subscribed),
    subscriptionError: readonly(subscriptionError),
    
    // 计算属性
    isConnected,
    isRoomConnected,
    typingUsers,
    
    // 方法
    connectToRoom,
    disconnectFromRoom,
    sendRoomMessage,
    sendTypingStatus
  }
} 