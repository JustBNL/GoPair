import { defineStore } from 'pinia'
import { ref, computed, readonly } from 'vue'
import { useWebSocket, buildSubscribeMessage } from '@/composables/useWebSocket'
import { WS_ENDPOINTS } from '@/config/websocket'
import { ConnectionState } from '@/types/websocket'
import { useChatStore } from '@/stores/chat'

/**
 * WebSocket全局状态管理Store
 * 提供响应式的WebSocket连接状态和自动管理
 *
 * * [核心策略]
 * - 单例模式：全局 WebSocket 实例由 store 统一持有，connectGlobal / disconnectGlobal 保证不重复创建
 */
export const useWebSocketStore = defineStore('websocket', () => {
  // ==================== 状态定义 ====================

  // 全局连接状态
  const globalConnectionState = ref<ConnectionState>(ConnectionState.DISCONNECTED)
  const currentUserId = ref<number | null>(null)
  const lastConnectionError = ref<Error | null>(null)

  // 连接统计
  const connectionAttempts = ref(0)
  const lastConnectTime = ref<Date | null>(null)
  const totalReconnects = ref(0)

  // 活跃连接追踪
  const activeConnections = ref<Set<string>>(new Set())
  const roomConnections = ref<Map<number, boolean>>(new Map())
  const voiceConnections = ref<Map<number, boolean>>(new Map())

  // ==================== 单例 WebSocket 实例（模块级，同 store 生命周期） ====================
  // 避免在 store 方法内调用 useWebSocket()，否则每次 connectGlobal 都会新建实例
  // 而 useWebSocket 内部的 onBeforeUnmount 会在组件卸载时意外关闭连接
  let globalWs: ReturnType<typeof useWebSocket> | null = null

  // ==================== 计算属性 ====================
  
  // 是否有任何活跃连接
  const hasActiveConnections = computed(() => activeConnections.value.size > 0)
  
  // 全局连接状态文本
  const connectionStateText = computed(() => {
    const stateMap = {
      [ConnectionState.DISCONNECTED]: '已断开',
      [ConnectionState.CONNECTING]: '连接中',
      [ConnectionState.CONNECTED]: '已连接',
      [ConnectionState.RECONNECTING]: '重连中',
      [ConnectionState.ERROR]: '连接错误'
    }
    return stateMap[globalConnectionState.value] || '未知'
  })
  
  // 连接质量指标
  const connectionQuality = computed(() => {
    if (globalConnectionState.value === ConnectionState.CONNECTED) {
      return totalReconnects.value < 3 ? 'good' : 'poor'
    }
    return 'disconnected'
  })

  // ==================== 全局连接管理 ====================

  /**
   * 建立全局WebSocket连接（单例：多次调用复用同一实例）
   */
  async function connectGlobal(userId: number): Promise<void> {
    // 已存在且处于连接/连接中状态，直接返回
    if (globalWs && globalWs.isConnected.value) {
      return
    }

    connectionAttempts.value++
    currentUserId.value = userId
    lastConnectionError.value = null

    try {
      if (WS_FEATURES.debug) console.log(`🔗 建立全局WebSocket连接: userId=${userId}`)

      // 使用全局连接端点
      const globalUrl = WS_ENDPOINTS.connect()

      // 单例：仅在 globalWs 不存在时创建一次
      if (!globalWs) {
        globalWs = useWebSocket()
      }

      await globalWs.connect(globalUrl, {
        onConnected: () => {
          globalConnectionState.value = ConnectionState.CONNECTED
          lastConnectTime.value = new Date()
          activeConnections.value.add('global')
          // 订阅私聊消息频道
          const channel = `user:${currentUserId.value}`
          globalWs!.send(buildSubscribeMessage(channel, ['message_send', 'friend_request', 'friend_status']))
        },
        onDisconnected: (event) => {
          globalConnectionState.value = ConnectionState.DISCONNECTED
          activeConnections.value.delete('global')
        },
        onError: (error) => {
          globalConnectionState.value = ConnectionState.ERROR
          lastConnectionError.value = error
          console.error(`❌ 全局WebSocket连接失败:`, error)
        },
        onMessage: (msg: any) => {
          // 转发消息到 chatStore
          const chatStore = useChatStore()
          chatStore.handleWebSocketMessage(msg)
        }
      })

    } catch (error) {
      lastConnectionError.value = error as Error
      globalConnectionState.value = ConnectionState.ERROR
      throw error
    }
  }

  /**
   * 断开全局WebSocket连接
   */
  function disconnectGlobal(): void {
    try {
      // 通过单例实例断开，而非每次新建
      globalWs?.disconnect()

      // 清理所有连接状态
      globalConnectionState.value = ConnectionState.DISCONNECTED
      currentUserId.value = null
      activeConnections.value.clear()
      roomConnections.value.clear()
      voiceConnections.value.clear()

      // 重置统计
      connectionAttempts.value = 0
      totalReconnects.value = 0
      lastConnectTime.value = null
      lastConnectionError.value = null
    } catch (error) {
      console.error(`❌ 断开全局WebSocket失败:`, error)
    }
  }

  // ==================== 连接追踪管理 ====================

  /**
   * 注册房间连接
   */
  function registerRoomConnection(roomId: number): void {
    roomConnections.value.set(roomId, true)
    activeConnections.value.add(`room:${roomId}`)
  }

  /**
   * 注销房间连接  
   */
  function unregisterRoomConnection(roomId: number): void {
    roomConnections.value.delete(roomId)
    activeConnections.value.delete(`room:${roomId}`)
  }

  /**
   * 注册语音连接
   */
  function registerVoiceConnection(callId: number): void {
    voiceConnections.value.set(callId, true)
    activeConnections.value.add(`voice:${callId}`)
  }

  /**
   * 注销语音连接
   */
  function unregisterVoiceConnection(callId: number): void {
    voiceConnections.value.delete(callId)
    activeConnections.value.delete(`voice:${callId}`)
  }

  // ==================== 连接状态查询 ====================

  /**
   * 检查房间是否已连接
   */
  function isRoomConnected(roomId: number): boolean {
    return roomConnections.value.get(roomId) || false
  }

  /**
   * 检查语音是否已连接
   */
  function isVoiceConnected(callId: number): boolean {
    return voiceConnections.value.get(callId) || false
  }

  /**
   * 获取连接状态摘要
   */
  function getConnectionSummary() {
    return {
      globalState: globalConnectionState.value,
      userId: currentUserId.value,
      activeConnections: Array.from(activeConnections.value),
      roomCount: roomConnections.value.size,
      voiceCount: voiceConnections.value.size,
      quality: connectionQuality.value,
      lastError: lastConnectionError.value?.message,
      attempts: connectionAttempts.value,
      reconnects: totalReconnects.value
    }
  }

  // ==================== 初始化和清理 ====================

  /**
   * 初始化WebSocket Store
   */
  function initializeWebSocket(): void {
    // 重置所有状态
    globalConnectionState.value = ConnectionState.DISCONNECTED
    currentUserId.value = null
    activeConnections.value.clear()
    roomConnections.value.clear() 
    voiceConnections.value.clear()
    
    // 重置统计
    connectionAttempts.value = 0
    totalReconnects.value = 0
    lastConnectionError.value = null
    lastConnectTime.value = null
  }

  /**
   * 清理WebSocket Store
   */
  function cleanupWebSocket(): void {
    disconnectGlobal()
  }

  // ==================== 返回 ====================
  
  return {
    // 状态
    globalConnectionState: readonly(globalConnectionState),
    currentUserId: readonly(currentUserId),
    lastConnectionError: readonly(lastConnectionError),
    connectionAttempts: readonly(connectionAttempts),
    lastConnectTime: readonly(lastConnectTime),
    totalReconnects: readonly(totalReconnects),
    activeConnections: readonly(activeConnections),
    
    // 计算属性
    hasActiveConnections,
    connectionStateText,
    connectionQuality,
    
    // 全局连接方法
    connectGlobal,
    disconnectGlobal,
    wsSend: (msg: Parameters<ReturnType<typeof useWebSocket>['send']>[0]) => globalWs?.send(msg),
    
    // 连接追踪方法
    registerRoomConnection,
    unregisterRoomConnection,
    registerVoiceConnection,
    unregisterVoiceConnection,
    
    // 查询方法
    isRoomConnected,
    isVoiceConnected,
    getConnectionSummary,
    
    // 管理方法
    initializeWebSocket,
    cleanupWebSocket
  }
}) 