import { defineStore } from 'pinia'
import { ref, computed, nextTick, readonly } from 'vue'
import { MessageAPI } from '@/api/message'
import type { MessageVO } from '@/types/api'
import { useAuthStore } from '@/stores/auth'

/** 活跃窗口最大条数（屏幕可见范围 + 缓冲） */
const MAX_WINDOW = 150
/** 历史段压缩阈值（触发合并时保留的条数） */
const COMPRESS_SIZE = 50
/** 最多保留的历史段数量 */
const MAX_SEGMENTS = 10
/** 默认每页加载条数 */
const DEFAULT_PAGE_SIZE = 50

export const useRoomMessageStore = defineStore('roomMessage', () => {
  // ==================== 状态 ====================
  const currentRoomId = ref<number | null>(null)
  /** 活跃消息窗口（正序：旧→新） */
  const messages = ref<MessageVO[]>([])
  /** 已压缩的历史分段数组（每段正序，段之间时间倒序，即 [最新段, ..., 最旧段]） */
  const historySegments = ref<MessageVO[][]>([])
  /** 是否有更多历史可加载 */
  const hasMoreHistory = ref(true)
  /** 加载中标记 */
  const loadingInitial = ref(false)
  const loadingHistory = ref(false)

  // ==================== 计算属性 ====================
  const currentUserId = computed(() => useAuthStore().user?.userId)

  // ==================== 私有方法 ====================

  /**
   * 注入 isOwn 字段到消息
   */
  function enrichWithOwnership(list: MessageVO[]): MessageVO[] {
    const uid = currentUserId.value
    return list.map(m => ({ ...m, isOwn: m.senderId === uid }))
  }

  /**
   * 压缩历史段（当历史段数量超过 MAX_SEGMENTS 时丢弃最旧段）
   */
  function compressOldestSegment() {
    if (historySegments.value.length > MAX_SEGMENTS) {
      historySegments.value = historySegments.value.slice(0, MAX_SEGMENTS)
    }
  }

  // ==================== 公开方法 ====================

  /**
   * 初始化房间消息：加载最新 N 条（作为初始活跃窗口）。
   * currentRoomId 在函数入口同步设置，确保 WebSocket 推送在异步加载期间也能正确追加消息。
   */
  async function fetchInitialMessages(roomId: number) {
    // 立即同步设置 currentRoomId，防止 WebSocket 推送在异步 API 调用期间被丢弃
    if (currentRoomId.value !== roomId) {
      currentRoomId.value = roomId
      messages.value = []
      historySegments.value = []
      hasMoreHistory.value = true
      loadingInitial.value = false
      loadingHistory.value = false
    }
    loadingInitial.value = true
    try {
      const res = await MessageAPI.getLatestMessages(roomId, DEFAULT_PAGE_SIZE)
      const records = enrichWithOwnership((res.data || []) as MessageVO[])
      messages.value = records
      hasMoreHistory.value = records.length >= DEFAULT_PAGE_SIZE
    } finally {
      loadingInitial.value = false
    }
  }

  /**
   * 懒加载历史消息：滚动到顶部时调用
   * @param scrollContainer 用于恢复滚动位置（传 DOM 元素）
   */
  async function fetchHistoryMessages(scrollContainer?: HTMLElement | null) {
    if (loadingHistory.value || !hasMoreHistory.value) return
    if (messages.value.length === 0) return

    loadingHistory.value = true

    // 保存滚动位置
    const scrollHeightBefore = scrollContainer?.scrollHeight ?? 0

    try {
      const roomId = currentRoomId.value
      if (!roomId) return

      const oldestMsg = messages.value[0]
      const beforeMessageId = oldestMsg.messageId

      const res = await MessageAPI.getHistoryMessages(roomId, beforeMessageId, DEFAULT_PAGE_SIZE)
      const records = enrichWithOwnership((res.data || []) as MessageVO[])

      if (records.length === 0) {
        hasMoreHistory.value = false
        return
      }

      // 将历史段压入 historySegments（放在最前面）
      historySegments.value.unshift(records)

      // 更新活跃窗口：历史段的最旧消息 + 当前窗口
      const lastRecord = records[records.length - 1]
      if (lastRecord && messages.value.length > 0) {
        // 如果历史段的最后一条和当前窗口第一条时间相邻，则合并
        const currentFirst = messages.value[0]
        if (lastRecord.messageId === currentFirst.messageId) {
          // 完全重复，跳过（已有 deduplication）
          messages.value = [...records, ...messages.value.slice(1)]
        } else {
          messages.value = [...records, ...messages.value]
        }
      } else {
        messages.value = [...records, ...messages.value]
      }

      // 压缩超出限制的历史段
      compressOldestSegment()

      // 判断是否还有更多
      hasMoreHistory.value = records.length >= DEFAULT_PAGE_SIZE

      // 恢复滚动位置
      await nextTick()
      if (scrollContainer) {
        const addedHeight = scrollContainer.scrollHeight - scrollHeightBefore
        scrollContainer.scrollTop = addedHeight
      }
    } finally {
      loadingHistory.value = false
    }
  }

  /**
   * 通过 WebSocket 推送追加新消息
   */
  function appendMessage(msg: MessageVO) {
    if (currentRoomId.value === null) return

    const enriched = enrichWithOwnership([msg])[0]

    // 去重
    if (messages.value.some(m => m.messageId === enriched.messageId)) return

    // 如果是历史段中有重复，也跳过（极端场景）
    for (const seg of historySegments.value) {
      if (seg.some(m => m.messageId === enriched.messageId)) return
    }

    messages.value = [...messages.value, enriched]

    // 活跃窗口超过上限时，将最早的 N 条压缩到 historySegments
    if (messages.value.length > MAX_WINDOW) {
      const overflow = messages.value.slice(0, messages.value.length - MAX_WINDOW)
      messages.value = messages.value.slice(messages.value.length - MAX_WINDOW)
      if (historySegments.value.length > 0) {
        // 合并到最新段
        historySegments.value[0] = [...overflow, ...historySegments.value[0]]
      } else {
        historySegments.value.unshift(overflow)
      }
      compressOldestSegment()
    }
  }

  /**
   * 删除消息（WebSocket 推送驱动）
   */
  function removeMessage(messageId: number) {
    messages.value = messages.value.filter(m => m.messageId !== messageId)
    for (let i = 0; i < historySegments.value.length; i++) {
      const before = historySegments.value[i].length
      historySegments.value[i] = historySegments.value[i].filter(m => m.messageId !== messageId)
      if (historySegments.value[i].length !== before) break
    }
  }

  /**
   * 撤回消息（WebSocket 推送驱动）。
   * 保留撤回者信息，便于在 UI 中显示"xxx 撤回了消息"。
   */
  function recallMessage(messageId: number, recalledAt?: string, recallerNickname?: string) {
    const update = (list: MessageVO[]) =>
      list.map(m => m.messageId === messageId
        ? { ...m, isRecalled: true, recalledAt: recalledAt || new Date().toISOString(), recallerNickname }
        : m)
    messages.value = update(messages.value)
    historySegments.value = historySegments.value.map(seg => update(seg))
  }

  /**
   * 清空房间消息状态。
   *
   * * [执行策略]
   * - 切换到新房间：重置所有状态，包含更新 currentRoomId。
   * - 重复进入同一房间（已有消息）：只清空消息列表，保留 currentRoomId。
   * - 无参数调用（退出房间）：重置所有状态，currentRoomId 置为 null。
   */
  function clearRoom(roomId?: number) {
    if (roomId !== undefined && roomId !== currentRoomId.value) {
      // 切换到不同房间：重置所有状态，currentRoomId 同步更新
      currentRoomId.value = roomId
      messages.value = []
      historySegments.value = []
      hasMoreHistory.value = true
      loadingInitial.value = false
      loadingHistory.value = false
      return
    }
    if (roomId !== undefined && roomId === currentRoomId.value && messages.value.length > 0) {
      // 同一房间重复进入（已有消息）：只清空消息列表，currentRoomId 保持不变
      messages.value = []
      historySegments.value = []
      hasMoreHistory.value = true
      loadingInitial.value = false
      loadingHistory.value = false
      return
    }
    // 无参数调用（退出房间）或首次进入：重置所有状态
    currentRoomId.value = roomId ?? null
    messages.value = []
    historySegments.value = []
    hasMoreHistory.value = true
    loadingInitial.value = false
    loadingHistory.value = false
  }

  /**
   * 获取最后一条消息的 ID（用于 catch-up 时持久化到 sessionStorage）
   */
  function getLastMessageId(): number | null {
    if (messages.value.length > 0) {
      return messages.value[messages.value.length - 1].messageId
    }
    if (historySegments.value.length > 0) {
      const lastSeg = historySegments.value[historySegments.value.length - 1]
      if (lastSeg.length > 0) {
        return lastSeg[lastSeg.length - 1].messageId
      }
    }
    return null
  }

  return {
    // state
    currentRoomId: readonly(currentRoomId),
    messages: readonly(messages),
    historySegments: readonly(historySegments),
    hasMoreHistory: readonly(hasMoreHistory),
    loadingInitial: readonly(loadingInitial),
    loadingHistory: readonly(loadingHistory),
    // actions
    fetchInitialMessages,
    fetchHistoryMessages,
    appendMessage,
    removeMessage,
    recallMessage,
    clearRoom,
    getLastMessageId
  }
})
