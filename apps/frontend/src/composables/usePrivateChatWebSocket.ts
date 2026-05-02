import { ref, onBeforeUnmount } from 'vue'
import { useWebSocket } from './useWebSocket'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { buildSubscribeMessage, buildUnsubscribeMessage } from './useWebSocket'
import type { PrivateMessageVO } from '@/types/chat'

/**
 * 私聊 WebSocket Composable。
 *
 * <p>创建独立的 WebSocket 连接，订阅 user:{userId} 频道，
 * 处理私聊消息推送和好友状态通知。
 * 复用现有的全局连接逻辑。
 */
export function usePrivateChatWebSocket() {
  const authStore = useAuthStore()
  const chatStore = useChatStore()

  const subscribed = ref(false)
  const channel = ref('')

  const { connect, disconnect, send, isConnected } = useWebSocket()

  async function connectAndSubscribe() {
    const user = authStore.user
    if (!user) return

    const userId = user.userId
    channel.value = `user:${userId}`

    await connect(`/connect`, {
      onConnected: () => {
        if (isConnected.value) {
          const msg = buildSubscribeMessage(channel.value, ['message_send', 'friend_request', 'friend_status'])
          send(msg)
          subscribed.value = true
        }
      },
      onMessage: (msg: any) => {
        handleMessage(msg)
      }
    })
  }

  function handleMessage(message: any) {
    const payload = message.payload || message.data
    if (!payload) return

    // 私聊消息
    if (message.eventType === 'message_send' && payload.chatType === 'private') {
      const msg: PrivateMessageVO = {
        messageId: payload.messageId,
        conversationId: payload.conversationId,
        senderId: payload.senderId,
        receiverId: payload.receiverId,
        senderNickname: payload.senderNickname || '未知用户',
        senderAvatar: payload.senderAvatar,
        messageType: payload.messageType,
        messageTypeDesc: payload.messageTypeDesc || '',
        content: payload.content,
        fileUrl: payload.fileUrl,
        fileName: payload.fileName,
        fileSize: payload.fileSize,
        isRecalled: false,
        isOwn: payload.senderId === authStore.user?.userId,
        createTime: payload.createTime || new Date().toISOString()
      }
      chatStore.appendMessage(msg)
    }

    // 好友请求通知
    if (message.eventType === 'friend_request') {
      chatStore.fetchIncomingRequests()
    }

    // 好友状态变更（被删除/被同意/被拒绝）
    if (message.eventType === 'friend_status') {
      chatStore.onFriendStatusChanged()
    }
  }

  function unsubscribeFromChannel() {
    if (subscribed.value && channel.value) {
      send(buildUnsubscribeMessage(channel.value))
      subscribed.value = false
    }
  }

  onBeforeUnmount(() => {
    unsubscribeFromChannel()
    disconnect()
  })

  return {
    subscribed,
    connect: connectAndSubscribe,
    disconnect,
    unsubscribeFromChannel
  }
}
