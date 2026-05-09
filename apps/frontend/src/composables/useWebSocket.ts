import { ref, computed, readonly, onBeforeUnmount } from 'vue'
import { WS_CONFIG, buildWebSocketUrl, WS_FEATURES } from '@/config/websocket'
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
 * 生成兼容的UUID
 * 兼容不支持crypto.randomUUID()的浏览器环境
 */
function generateUUID(): string {
  // 优先使用原生API
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  
  // 兼容方案：使用Math.random生成UUID v4
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    const r = Math.random() * 16 | 0
    const v = c === 'x' ? r : (r & 0x3 | 0x8)
    return v.toString(16)
  })
}

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
      return
    }

    const wsUrl = url || (endpoint ? buildWebSocketUrl(endpoint) : '')
    if (!wsUrl) {
      throw new Error('WebSocket URL未指定')
    }

    // 连接前检查Token一致性并同步（确保WebSocket认证成功）
    try {
      if (!Storage.validateTokenConsistency()) {
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
    } catch (error) {
      throw error
    }

    return new Promise((resolve, reject) => {
      try {
        connectionState.value = ConnectionState.CONNECTING
        lastError.value = null

        ws = new WebSocket(wsUrl)

        ws.onopen = (event) => {
          if (WS_FEATURES.debug) console.log(`✅ WebSocket连接成功: ${wsUrl}`)
          connectionState.value = ConnectionState.CONNECTED
          reconnectAttempts.value = 0
          startHeartbeat()
          console.log(`[WS] onopen触发，wsUrl=${wsUrl}, 调用onConnected回调前`)
          callbacks.onConnected?.()
          console.log(`[WS] onConnected回调执行完成`)
          resolve()
        }

        ws.onmessage = (event) => {
          try {
            const message: WsMessage = JSON.parse(event.data)
            handleMessage(message)
            if (message.type === WsMessageType.HEARTBEAT) {
              return
            }
            if (WS_FEATURES.debug) console.log('[WS] message', message)
            console.log('[WS] ⬇️ 收到消息, endpoint={}, type={}, channel={}, eventType={}, payloadKeys={}', endpoint || 'unknown', message.type, message.channel, message.eventType, message.payload ? Object.keys(message.payload) : 'none')
            callbacks.onMessage?.(message)
          } catch (error) {
            if (WS_FEATURES.debug) console.error('WebSocket消息解析失败:', error)
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
          if (WS_FEATURES.debug) console.log(`🔌 WebSocket连接关闭: ${wsUrl}`, event.code)
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
      console.log(`[WS] ⬆️ 发送失败，连接未就绪: wsUrl=${wsUrl}, readyState=${ws?.readyState}`)
      return false
    }

    try {
      // 通用包头 + 透传调用方字段
      const wsMessage = {
        messageId: generateUUID(),
        timestamp: new Date().toISOString(),
        ...message
      }
      ws.send(JSON.stringify(wsMessage))
      console.log(`[WS] ⬆️ 发送消息: endpoint=${endpoint || 'unknown'}, type=${message.type}, channel=${message.channel}, eventType=${message.eventType}`)
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
      const error = new Error(message.data?.errorMessage || message.data?.message || 'WebSocket服务器错误')
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
        send({ 
          type: WsMessageType.HEARTBEAT,
          eventType: "heartbeat" as any
        })
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
    
    if (WS_FEATURES.debug) console.log(`🔄 安排第${reconnectAttempts.value}次重连...`)
    
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null
      connect(url, callbacks).catch((error) => {
        if (WS_FEATURES.debug) console.error('重连失败:', error)
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

/**
 * 订阅/退订消息构造器
 */
export function buildSubscribeMessage(channel: string, eventTypes: string[], userId?: number) {
  return {
    type: WsMessageType.SUBSCRIBE,
    eventType: 'subscribe' as any,
    data: {
      payload: {
        channel,
        userId,
        eventTypes
      }
    }
  }
}

export function buildUnsubscribeMessage(channel: string, userId?: number) {
  return {
    type: WsMessageType.UNSUBSCRIBE,
    eventType: 'unsubscribe' as any,
    data: {
      payload: {
        channel,
        userId
      }
    }
  }
} 