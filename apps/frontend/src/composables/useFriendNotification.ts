import { message as antMessage } from 'ant-design-vue'
import { useChatStore } from '@/stores/chat'

export interface FriendStatusPayload {
  action: 'accepted' | 'rejected' | 'deleted'
  friendId: number
  message: string
}

export function useFriendNotification() {
  const chatStore = useChatStore()

  function resolveNickname(friendId: number): string {
    const friend = chatStore.friends.find(f => f.friendId === friendId)
    return friend?.nickname || friend?.friendNickname || '该用户'
  }

  function handleFriendStatusNotification(payload: FriendStatusPayload) {
    const { action, friendId } = payload
    const nickname = resolveNickname(friendId)

    switch (action) {
      case 'deleted':
        antMessage.warning(`${nickname} 删除了您的好友`, 4)
        if (chatStore.currentFriendId === friendId) {
          chatStore.closeCurrentChat()
        }
        break
      case 'accepted':
        antMessage.success(`${nickname} 同意了您的好友请求`)
        break
      case 'rejected':
        antMessage.info(`${nickname} 拒绝了您的好友请求`)
        break
      default:
        break
    }
  }

  return { handleFriendStatusNotification }
}
