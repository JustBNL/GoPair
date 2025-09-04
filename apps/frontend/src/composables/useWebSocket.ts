import { ref, computed, readonly, onBeforeUnmount } from 'vue'
import { WS_CONFIG, buildWebSocketUrl } from '@/config/websocket'
import { Storage } from '@/utils/storage'
import { 
  ConnectionState, 
  WsMessageType
} from '@/types/websocket'
import type { 
  WsConnectionOptions, 
  WsEventCallbacks,
  WsMessage
} from '@/types/websocket'

/**
 * 基础WebSocket连接管理Composable
 * 提供简洁、响应式的WebSocket连接API
 */
export function useWebSocket(endpoint?: string, options: WsConnectionOptions = {}) {
  // 响应式状态
  const connectionState = ref<ConnectionState>(ConnectionState.DISCONNECTED)
  const lastError = ref<Error | null>(null)
  const reconnectAttempts = ref(0)

  // 私有状态
  let ws: WebSocket | null = null
  let reconnectTimer: number | null = null
  let heartbeatTimer: number | null = null
  let callbacks: WsEventCallbacks = {}

  // 合并配置
  const config = { ...WS_CONFIG.options, ...options }

  // 计算属性
  const isConnected = computed(() => connectionState.value === ConnectionState.CONNECTED)
  const isConnecting = computed(() => connectionState.value === ConnectionState.CONNECTING)
  const canReconnect = computed(() => 
    connectionState.value === ConnectionState.DISCONNECTED && 
    reconnectAttempts.value < config.maxReconnectAttempts
  )

  /**
   * 建立WebSocket连接
   */
  const connect = async (url?: string, wsCallbacks: WsEventCallbacks = {}): Promise<void> => {
    callbacks = { ...callbacks, ...wsCallbacks }
    
    if (ws?.readyState === WebSocket.OPEN) {
      console.log('WebSocket已连接，跳过重复连接')
      return
    }

    const wsUrl = url || (endpoint ? buildWebSocketUrl(endpoint) : '')
    if (!wsUrl) {
      throw new Error('WebSocket URL未指定')
    }

    // 连接前检查Token一致性并同步（确保WebSocket认证成功）
    try {
      if (!Storage.validateTokenConsistency()) {
        console.log('🔄 Token不一致，正在同步到Cookie...')
        Storage.syncTokenToCookie()
        
        // 验证同步是否成功
        if (!Storage.getCookieToken()) {
          throw new Error('Cookie Token同步失败，WebSocket认证可能失败')
        }
      }
      
      const cookieToken = Storage.getCookieToken()
      if (!cookieToken) {
        throw new Error('Cookie中未找到认证Token，请重新登录')
      }
      
      console.log('✅ Token一致性检查通过，准备建立WebSocket连接')
    } catch (error) {
      console.error('❌ WebSocket连接前置检查失败:', error)
      throw error
    }

    return new Promise((resolve, reject) => {
      try {
        connectionState.value = ConnectionState.CONNECTING
        lastError.value = null

        ws = new WebSocket(wsUrl)

        ws.onopen = (event) => {
          console.log(`✅ WebSocket连接成功: ${wsUrl}`)
          connectionState.value = ConnectionState.CONNECTED
          reconnectAttempts.value = 0
          startHeartbeat()
          callbacks.onConnected?.()
          resolve()
        }

        ws.onmessage = (event) => {
          try {
            const message: WsMessage = JSON.parse(event.data)
            handleMessage(message)
            callbacks.onMessage?.(message)
          } catch (error) {
            console.error('WebSocket消息解析失败:', error)
          }
        }

        ws.onerror = () => {
          const error = new Error(`WebSocket连接失败: ${wsUrl}`)
          lastError.value = error
          connectionState.value = ConnectionState.ERROR
          callbacks.onError?.(error)
          reject(error)
        }

        ws.onclose = (event) => {
          console.log(`🔌 WebSocket连接关闭: ${wsUrl}`, event.code)
          connectionState.value = ConnectionState.DISCONNECTED
          stopHeartbeat()
          callbacks.onDisconnected?.(event)

          // 自动重连
          if (config.autoReconnect && canReconnect.value && event.code !== 1000) {
            scheduleReconnect(wsUrl)
          }
        }

      } catch (error) {
        connectionState.value = ConnectionState.ERROR
        lastError.value = error as Error
        reject(error)
      }
    })
  }

  /**
   * 发送消息
   */
  const send = (message: Partial<WsMessage>): boolean => {
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      console.warn('WebSocket未连接，无法发送消息')
      return false
    }

    try {
      const wsMessage: WsMessage = {
        id: crypto.randomUUID(),
        type: message.type || WsMessageType.ROOM_MESSAGE,
        timestamp: Date.now(),
        ...message
      }
      
      ws.send(JSON.stringify(wsMessage))
      return true
    } catch (error) {
      console.error('WebSocket发送消息失败:', error)
      return false
    }
  }

  /**
   * 断开连接
   */
  const disconnect = (): void => {
    stopHeartbeat()
    stopReconnect()
    
    if (ws) {
      ws.close(1000, 'Manual disconnect')
      ws = null
    }
    
    connectionState.value = ConnectionState.DISCONNECTED
  }

  /**
   * 处理接收到的消息
   */
  const handleMessage = (message: WsMessage): void => {
    // 处理心跳响应
    if (message.type === WsMessageType.HEARTBEAT) {
      return
    }
    
    // 处理错误消息
    if (message.type === WsMessageType.ERROR) {
      const error = new Error(message.data?.message || 'WebSocket服务器错误')
      lastError.value = error
      callbacks.onError?.(error)
    }
  }

  /**
   * 开始心跳
   */
  const startHeartbeat = (): void => {
    if (config.heartbeatInterval > 0) {
      heartbeatTimer = window.setInterval(() => {
        send({ type: WsMessageType.HEARTBEAT })
      }, config.heartbeatInterval)
    }
  }

  /**
   * 停止心跳
   */
  const stopHeartbeat = (): void => {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  /**
   * 安排重连
   */
  const scheduleReconnect = (url: string): void => {
    if (reconnectTimer) return

    reconnectAttempts.value++
    connectionState.value = ConnectionState.RECONNECTING
    
    console.log(`🔄 安排第${reconnectAttempts.value}次重连...`)
    
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null
      connect(url, callbacks).catch((error) => {
        console.error('重连失败:', error)
      })
    }, config.reconnectInterval)
  }

  /**
   * 停止重连
   */
  const stopReconnect = (): void => {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  // 组件卸载时自动清理
  onBeforeUnmount(() => {
    disconnect()
  })

  return {
    // 响应式状态
    connectionState: readonly(connectionState),
    isConnected,
    isConnecting,
    canReconnect,
    lastError: readonly(lastError),
    reconnectAttempts: readonly(reconnectAttempts),
    
    // 方法
    connect,
    disconnect,
    send
  }
} 