import { defineStore } from 'pinia'
import { ref, computed, nextTick } from 'vue'
import type {
  FriendVO,
  FriendRequestVO,
  ConversationVO,
  FriendStatusVO,
  PrivateMessageVO,
  UserSearchResultVO
} from '@/types/chat'
import { ChatAPI } from '@/api/chat'
import { useAuthStore } from '@/stores/auth'
import { useFriendNotification } from '@/composables/useFriendNotification'

/** 与后端 computeConversationId 保持一致：minId * 1_000_000_0000 + maxId */
const CONVERSATION_ID_MULTIPLIER = 1_000_000_0000
/** 活跃消息窗口最大条数 */
const MAX_WINDOW = 200
/** 历史段最多保留数量 */
const MAX_SEGMENTS = 10

function computeConversationId(userIdA: number, userIdB: number): number {
  const min = Math.min(userIdA, userIdB)
  const max = Math.max(userIdA, userIdB)
  return min * CONVERSATION_ID_MULTIPLIER + max
}

/**
 * 聊天状态管理（好友 + 私聊）
 */
export const useChatStore = defineStore('chat', () => {
  // ==================== 好友状态 ====================
  const friends = ref<FriendVO[]>([])
  const friendsLoading = ref(false)

  // 收到的待处理申请
  const incomingRequests = ref<FriendRequestVO[]>([])
  const incomingCount = computed(
    () => incomingRequests.value.filter(r => r.status === 'pending').length
  )

  // 发出的申请
  const outgoingRequests = ref<FriendRequestVO[]>([])
  const requestsLoading = ref(false)

  // ==================== 用户搜索状态 ====================
  const searchResults = ref<UserSearchResultVO[]>([])
  const searchLoading = ref(false)
  const searchTotal = ref(0)
  const searchError = ref<string | null>(null)

  // ==================== 好友搜索状态 ====================
  const friendSearchResults = ref<FriendVO[]>([])
  const friendSearchLoading = ref(false)

  // ==================== 私聊会话状态 ====================
  const conversations = ref<ConversationVO[]>([])
  const conversationsLoading = ref(false)

  // 当前打开的私聊
  const currentFriendId = ref<number | null>(null)
  const currentConversationId = ref<number | null>(null)

  // 当前私聊的消息列表
  const currentMessages = ref<PrivateMessageVO[]>([])
  const messagesLoading = ref(false)

  // 是否有更多历史消息可加载（cursor 分页）
  const hasMoreHistory = ref(true)

  // 私聊历史分段缓存：数组顺序 [最新段, ..., 最旧段]
  const privateHistorySegments = ref<PrivateMessageVO[][]>([])

  // 未读消息计数
  const unreadMessageCount = ref(0)
  const hasUnread = computed(() => unreadMessageCount.value > 0)

  // ==================== 加载操作 ====================

  async function fetchFriends() {
    friendsLoading.value = true
    try {
      const res = await ChatAPI.getFriends()
      friends.value = res.data || []
    } finally {
      friendsLoading.value = false
    }
  }

  async function fetchIncomingRequests() {
    const res = await ChatAPI.getIncomingRequests()
    incomingRequests.value = res.data || []
  }

  async function fetchOutgoingRequests() {
    requestsLoading.value = true
    try {
      const res = await ChatAPI.getOutgoingRequests()
      outgoingRequests.value = res.data || []
    } finally {
      requestsLoading.value = false
    }
  }

  async function fetchConversations() {
    conversationsLoading.value = true
    try {
      const res = await ChatAPI.getConversations()
      conversations.value = res.data || []
    } finally {
      conversationsLoading.value = false
    }
  }

  async function fetchMessages(conversationId: number, beforeMessageId?: number, scrollContainer?: HTMLElement | null) {
    if (!hasMoreHistory.value && beforeMessageId != null) return
    messagesLoading.value = true

    // 保存滚动位置
    const scrollHeightBefore = scrollContainer?.scrollHeight ?? 0

    try {
      const res = await ChatAPI.getMessages(conversationId, beforeMessageId, 50)
      const records = (res.data?.records || []) as PrivateMessageVO[]

      if (beforeMessageId == null) {
        // 首次加载：清空后直接赋值
        currentMessages.value = records
        privateHistorySegments.value = []
      } else {
        // 上拉加载历史：将更早的消息头插到数组前面
        currentMessages.value = [...records, ...currentMessages.value]

        // 将历史段压入缓存
        if (records.length > 0) {
          privateHistorySegments.value.unshift(records)
          // 压缩超出限制的旧段
          if (privateHistorySegments.value.length > MAX_SEGMENTS) {
            privateHistorySegments.value = privateHistorySegments.value.slice(0, MAX_SEGMENTS)
          }
        }
      }

      // 判断是否还有更多
      hasMoreHistory.value = records.length >= 50

      // 恢复滚动位置
      await nextTick()
      if (scrollContainer) {
        const addedHeight = scrollContainer.scrollHeight - scrollHeightBefore
        scrollContainer.scrollTop = addedHeight
      }
    } finally {
      messagesLoading.value = false
    }
  }

  // ==================== 好友操作 ====================

  async function sendRequest(toUserId: number, message?: string) {
    await ChatAPI.sendFriendRequest({ toUserId, message })
  }

  async function acceptRequest(requestId: number) {
    await ChatAPI.acceptFriendRequest(requestId)
    await Promise.all([fetchIncomingRequests(), fetchOutgoingRequests(), fetchFriends()])
  }

  async function rejectRequest(requestId: number) {
    await ChatAPI.rejectFriendRequest(requestId)
    await Promise.all([fetchIncomingRequests(), fetchOutgoingRequests()])
  }

  async function removeFriend(friendId: number) {
    await ChatAPI.deleteFriend(friendId)
    friends.value = friends.value.filter(f => f.friendId !== friendId)
    if (currentFriendId.value === friendId) {
      closeCurrentChat()
    }
  }

  async function checkFriendStatus(userId: number): Promise<FriendStatusVO> {
    const res = await ChatAPI.checkFriendStatus(userId)
    return res.data
  }

  async function fetchSearchResults(keyword: string, pageNum = 1, pageSize = 20) {
    if (!keyword.trim()) {
      searchResults.value = []
      searchTotal.value = 0
      searchError.value = null
      return
    }
    searchLoading.value = true
    searchError.value = null
    try {
      const res = await ChatAPI.searchUsers(keyword, pageNum, pageSize)
      searchResults.value = res.data?.records || []
      searchTotal.value = res.data?.total || 0
    } catch {
      searchError.value = '搜索服务暂时不可用，请稍后重试'
    } finally {
      searchLoading.value = false
    }
  }

  /** 清空搜索结果 */
  function clearSearchResults() {
    searchResults.value = []
    searchTotal.value = 0
    searchError.value = null
  }

  /** 搜索好友列表（通过后端过滤） */
  async function fetchFriendSearchResults(keyword: string) {
    friendSearchLoading.value = true
    try {
      const res = await ChatAPI.getFriends(keyword)
      friendSearchResults.value = res.data || []
    } finally {
      friendSearchLoading.value = false
    }
  }

  /** 清空好友搜索结果 */
  function clearFriendSearchResults() {
    friendSearchResults.value = []
  }

  // ==================== 私聊操作 ====================

  async function openChat(friendId: number) {
    currentFriendId.value = friendId
    const conv = conversations.value.find(c => c.friendId === friendId)
    if (conv) {
      currentConversationId.value = conv.conversationId
      await fetchMessages(conv.conversationId)
    } else {
      const authStore = useAuthStore()
      const cid = computeConversationId(authStore.user!.userId, friendId)
      currentConversationId.value = cid
      await fetchMessages(cid)
    }
  }

  function closeCurrentChat() {
    currentFriendId.value = null
    currentConversationId.value = null
    currentMessages.value = []
    hasMoreHistory.value = true
    privateHistorySegments.value = []
  }

  /**
   * 发送私聊消息。
   *
   * * [执行策略]
   * - HTTP 请求仅负责将消息写入数据库，不在 UI 中追加消息。
   * - 消息的 UI 展示统一由 WebSocket 推送（appendMessage）驱动，保证幂等。
   * - 这样可以避免 HTTP 响应和 WebSocket 推送竞争追加顺序导致的重复气泡问题。
   */
  async function sendMessage(dto: Parameters<typeof ChatAPI.sendMessage>[0]) {
    const res = await ChatAPI.sendMessage(dto)
    // 不在这里追加消息，WebSocket 推送会统一处理
    return res.data
  }

  /** 通过 WebSocket 推送追加消息（供 composable 调用） */
  function appendMessage(msg: PrivateMessageVO) {
    if (msg.conversationId === currentConversationId.value) {
      const exists = currentMessages.value.some(m => m.messageId === msg.messageId)
      if (!exists) {
        currentMessages.value = [...currentMessages.value, msg]
        // 活跃窗口超过上限时，将最早的 N 条压缩到 historySegments
        if (currentMessages.value.length > MAX_WINDOW) {
          const overflow = currentMessages.value.slice(0, currentMessages.value.length - MAX_WINDOW)
          currentMessages.value = currentMessages.value.slice(currentMessages.value.length - MAX_WINDOW)
          if (privateHistorySegments.value.length > 0) {
            privateHistorySegments.value[0] = [...overflow, ...privateHistorySegments.value[0]]
          } else {
            privateHistorySegments.value.unshift(overflow)
          }
        }
      }
    } else {
      unreadMessageCount.value++
    }
    // 刷新会话列表
    fetchConversations()
  }

  /** 清空未读消息计数（下拉打开时调用） */
  function clearUnread() {
    unreadMessageCount.value = 0
  }

  /** 处理 WebSocket 推送消息（统一入口） */
  function handleWebSocketMessage(message: any) {
    try {
      const payload = message.payload || message.data
      if (!payload) return

      if (message.eventType === 'message_send' && payload.chatType === 'private') {
        const authStore = useAuthStore()
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
        appendMessage(msg)
      }

      if (message.eventType === 'friend_request') {
        fetchIncomingRequests()
      }

      if (message.eventType === 'friend_status') {
        useFriendNotification().handleFriendStatusNotification(payload)
        onFriendStatusChanged()
      }
    } catch (error) {
      console.error('[ChatStore] handleWebSocketMessage 处理消息异常:', error, 'message=', message)
    }
  }

  function onFriendStatusChanged() {
    fetchFriends()
    fetchIncomingRequests()
    fetchOutgoingRequests()
  }

  // ==================== 初始化 ====================

  async function initChat() {
    await Promise.all([
      fetchFriends(),
      fetchIncomingRequests(),
      fetchOutgoingRequests(),
      fetchConversations()
    ])
  }

  return {
    // State
    friends, friendsLoading,
    incomingRequests, incomingCount,
    outgoingRequests, requestsLoading,
    searchResults, searchLoading, searchTotal, searchError,
    friendSearchResults, friendSearchLoading,
    conversations, conversationsLoading,
    currentFriendId, currentConversationId,
    currentMessages, messagesLoading, hasMoreHistory,
    privateHistorySegments,
    unreadMessageCount, hasUnread,

    // Friend actions
    fetchFriends, fetchIncomingRequests, fetchOutgoingRequests,
    sendRequest, acceptRequest, rejectRequest, removeFriend, checkFriendStatus,
    fetchSearchResults, clearSearchResults,
    fetchFriendSearchResults, clearFriendSearchResults,

    // Chat actions
    fetchConversations, fetchMessages,
    openChat, closeCurrentChat, sendMessage,
    appendMessage, clearUnread, handleWebSocketMessage, onFriendStatusChanged,

    // Init
    initChat
  }
})
