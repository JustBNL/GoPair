import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  FriendVO,
  FriendRequestVO,
  ConversationVO,
  FriendStatusVO,
  PrivateMessageVO,
  UserSearchResultVO
} from '@/types/chat'
import { ChatAPI } from '@/api/chat'

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
    const res = await ChatAPI.getOutgoingRequests()
    outgoingRequests.value = res.data || []
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

  async function fetchMessages(conversationId: number) {
    messagesLoading.value = true
    try {
      const res = await ChatAPI.getMessages(conversationId, 1, 50)
      currentMessages.value = res.data?.records || []
    } finally {
      messagesLoading.value = false
    }
  }

  // ==================== 好友操作 ====================

  async function sendRequest(toUserId: number, message?: string) {
    await ChatAPI.sendFriendRequest({ toUserId, message })
    await fetchOutgoingRequests()
  }

  async function acceptRequest(requestId: number) {
    await ChatAPI.acceptFriendRequest(requestId)
    await Promise.all([fetchIncomingRequests(), fetchFriends()])
  }

  async function rejectRequest(requestId: number) {
    await ChatAPI.rejectFriendRequest(requestId)
    await fetchIncomingRequests()
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
      currentConversationId.value = null
      currentMessages.value = []
    }
  }

  function closeCurrentChat() {
    currentFriendId.value = null
    currentConversationId.value = null
    currentMessages.value = []
  }

  async function sendMessage(dto: Parameters<typeof ChatAPI.sendMessage>[0]) {
    const res = await ChatAPI.sendMessage(dto)
    const msg = res.data
    if (msg) {
      currentMessages.value = [...currentMessages.value, msg]
    }
    return msg
  }

  /** 通过 WebSocket 推送追加消息（供 composable 调用） */
  function appendMessage(msg: PrivateMessageVO) {
    if (msg.conversationId === currentConversationId.value) {
      const exists = currentMessages.value.some(m => m.messageId === msg.messageId)
      if (!exists) {
        currentMessages.value = [...currentMessages.value, msg]
      }
    }
    // 刷新会话列表
    fetchConversations()
  }

  /** 收到好友状态变更通知时刷新列表 */
  function onFriendStatusChanged() {
    fetchFriends()
    fetchIncomingRequests()
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
    incomingRequests, incomingCount, outgoingRequests,
    searchResults, searchLoading, searchTotal, searchError,
    friendSearchResults, friendSearchLoading,
    conversations, conversationsLoading,
    currentFriendId, currentConversationId,
    currentMessages, messagesLoading,

    // Friend actions
    fetchFriends, fetchIncomingRequests, fetchOutgoingRequests,
    sendRequest, acceptRequest, rejectRequest, removeFriend, checkFriendStatus,
    fetchSearchResults, clearSearchResults,
    fetchFriendSearchResults, clearFriendSearchResults,

    // Chat actions
    fetchConversations, fetchMessages,
    openChat, closeCurrentChat, sendMessage,
    appendMessage, onFriendStatusChanged,

    // Init
    initChat
  }
})
