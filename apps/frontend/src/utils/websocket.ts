import { WS_ENDPOINTS } from '@/api'
import type { WebSocketMessage } from '@/types/api'

/**
 * WebSocket连接状态
 */
export enum WebSocketState {
  CONNECTING = 0,
  OPEN = 1,
  CLOSING = 2,
  CLOSED = 3
}

/**
 * WebSocket事件回调类型
 */
export interface WebSocketCallbacks {
  onOpen?: (event: Event) => void
  onMessage?: (data: any) => void
  onError?: (event: Event) => void
  onClose?: (event: CloseEvent) => void
}

/**
 * WebSocket配置选项
 */
export interface WebSocketOptions {
  autoReconnect?: boolean
  reconnectInterval?: number
  maxReconnectAttempts?: number
  heartbeatInterval?: number
  protocols?: string[]
}

/**
 * WebSocket客户端基类
 */
export abstract class BaseWebSocketClient {
  protected ws: WebSocket | null = null
  protected url: string
  protected options: Required<WebSocketOptions>
  protected callbacks: WebSocketCallbacks = {}
  protected reconnectAttempts = 0
  protected heartbeatTimer: number | null = null
  protected isManualClose = false

  constructor(endpoint: string, options: WebSocketOptions = {}) {
    this.url = this.buildWebSocketUrl(endpoint)
    this.options = {
      autoReconnect: true,
      reconnectInterval: 3000,
      maxReconnectAttempts: 5,
      heartbeatInterval: 30000,
      protocols: [],
      ...options
    }
  }

  /**
   * 构建WebSocket URL
   */
  protected buildWebSocketUrl(endpoint: string): string {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    return `${protocol}//${host}${endpoint}`
  }

  /**
   * 连接WebSocket
   */
  connect(callbacks: WebSocketCallbacks = {}): Promise<void> {
    this.callbacks = { ...this.callbacks, ...callbacks }
    this.isManualClose = false

    return new Promise((resolve, reject) => {
      try {
        // 如果已经连接或正在连接，先关闭
        if (this.ws && this.ws.readyState !== WebSocketState.CLOSED) {
          this.ws.close()
        }

        // 创建新连接
        this.ws = new WebSocket(this.url, this.options.protocols)

        this.ws.onopen = (event) => {
          console.log(`WebSocket连接已建立: ${this.url}`)
          this.reconnectAttempts = 0
          this.startHeartbeat()
          this.callbacks.onOpen?.(event)
          resolve()
        }

        this.ws.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data)
            this.handleMessage(data)
            this.callbacks.onMessage?.(data)
          } catch (error) {
            console.error('WebSocket消息解析失败:', error)
          }
        }

        this.ws.onerror = (event) => {
          console.error(`WebSocket错误: ${this.url}`, event)
          this.callbacks.onError?.(event)
          reject(new Error('WebSocket连接失败'))
        }

        this.ws.onclose = (event) => {
          console.log(`WebSocket连接已关闭: ${this.url}`, event)
          this.stopHeartbeat()
          this.callbacks.onClose?.(event)

          // 自动重连
          if (!this.isManualClose && this.options.autoReconnect && 
              this.reconnectAttempts < this.options.maxReconnectAttempts) {
            this.scheduleReconnect()
          }
        }

      } catch (error) {
        console.error('WebSocket连接创建失败:', error)
        reject(error)
      }
    })
  }

  /**
   * 发送消息
   */
  send(data: any): boolean {
    if (this.ws && this.ws.readyState === WebSocketState.OPEN) {
      try {
        const message = typeof data === 'string' ? data : JSON.stringify(data)
        this.ws.send(message)
        return true
      } catch (error) {
        console.error('WebSocket发送消息失败:', error)
        return false
      }
    }
    console.warn('WebSocket未连接，无法发送消息')
    return false
  }

  /**
   * 关闭连接
   */
  close(code?: number, reason?: string): void {
    this.isManualClose = true
    this.stopHeartbeat()
    
    if (this.ws) {
      this.ws.close(code, reason)
      this.ws = null
    }
  }

  /**
   * 获取连接状态
   */
  getState(): WebSocketState {
    return this.ws ? this.ws.readyState : WebSocketState.CLOSED
  }

  /**
   * 是否已连接
   */
  isConnected(): boolean {
    return this.ws ? this.ws.readyState === WebSocketState.OPEN : false
  }

  /**
   * 安排重连
   */
  private scheduleReconnect(): void {
    this.reconnectAttempts++
    console.log(`准备第${this.reconnectAttempts}次重连...`)
    
    setTimeout(() => {
      this.connect(this.callbacks)
    }, this.options.reconnectInterval)
  }

  /**
   * 开始心跳
   */
  private startHeartbeat(): void {
    if (this.options.heartbeatInterval > 0) {
      this.heartbeatTimer = window.setInterval(() => {
        this.sendHeartbeat()
      }, this.options.heartbeatInterval)
    }
  }

  /**
   * 停止心跳
   */
  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  /**
   * 发送心跳（子类实现）
   */
  protected abstract sendHeartbeat(): void

  /**
   * 处理接收到的消息（子类实现）
   */
  protected abstract handleMessage(data: any): void

  /**
   * 添加事件监听器
   */
  on(event: keyof WebSocketCallbacks, callback: Function): void {
    this.callbacks[event] = callback as any
  }

  /**
   * 移除事件监听器
   */
  off(event: keyof WebSocketCallbacks): void {
    delete this.callbacks[event]
  }
}

/**
 * 消息WebSocket客户端
 */
export class MessageWebSocketClient extends BaseWebSocketClient {
  private messageHandlers: Map<string, Function> = new Map()
  private roomId: number | null = null
  private userId: number | null = null

  constructor(options: WebSocketOptions = {}) {
    super(WS_ENDPOINTS.MESSAGE, options)
  }

  /**
   * 设置用户ID和房间ID（必须在连接前调用）
   */
  setUserAndRoom(userId: number, roomId: number): void {
    this.userId = userId
    this.roomId = roomId
    // 重新构建URL
    this.url = this.buildWebSocketUrl(`${WS_ENDPOINTS.MESSAGE}?userId=${userId}&roomId=${roomId}`)
  }

  /**
   * 加入房间
   */
  joinRoom(roomId: number): void {
    this.roomId = roomId
    this.send({
      type: 'join-room',
      roomId: roomId,
      timestamp: Date.now()
    })
  }

  /**
   * 离开房间
   */
  leaveRoom(): void {
    if (this.roomId) {
      this.send({
        type: 'leave-room',
        roomId: this.roomId,
        timestamp: Date.now()
      })
      this.roomId = null
    }
  }

  /**
   * 发送消息
   */
  sendMessage(messageData: any): void {
    this.send({
      type: 'message',
      data: messageData,
      timestamp: Date.now()
    })
  }

  /**
   * 添加消息处理器
   */
  addMessageHandler(type: string, handler: Function): void {
    this.messageHandlers.set(type, handler)
  }

  /**
   * 移除消息处理器
   */
  removeMessageHandler(type: string): void {
    this.messageHandlers.delete(type)
  }

  protected sendHeartbeat(): void {
    this.send({
      type: 'heartbeat',
      timestamp: Date.now()
    })
  }

  protected handleMessage(data: any): void {
    const { type } = data
    const handler = this.messageHandlers.get(type)
    
    if (handler) {
      handler(data)
    } else {
      console.log('未处理的消息类型:', type, data)
    }
  }
}

/**
 * 语音信令WebSocket客户端
 */
export class VoiceSignalingClient extends BaseWebSocketClient {
  private signalingHandlers: Map<string, Function> = new Map()
  private userId: number | null = null

  constructor(options: WebSocketOptions = {}) {
    super(WS_ENDPOINTS.SIGNALING, options)
  }

  /**
   * 设置用户ID
   */
  setUserId(userId: number): void {
    this.userId = userId
  }

  /**
   * 构建WebSocket URL（包含用户ID）
   */
  protected buildWebSocketUrl(endpoint: string): string {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    const userParam = this.userId ? `?userId=${this.userId}` : ''
    return `${protocol}//${host}${endpoint}${userParam}`
  }

  /**
   * 加入通话
   */
  joinCall(callId: number): void {
    this.send({
      type: 'join-call',
      callId: callId,
      timestamp: Date.now()
    })
  }

  /**
   * 离开通话
   */
  leaveCall(callId: number): void {
    this.send({
      type: 'leave-call',
      callId: callId,
      timestamp: Date.now()
    })
  }

  /**
   * 发送WebRTC信令
   */
  sendSignaling(data: {
    callId: number
    type: string
    targetUserId?: number
    data?: any
    extra?: any
  }): void {
    this.send(data)
  }

  /**
   * 添加信令处理器
   */
  addSignalingHandler(type: string, handler: Function): void {
    this.signalingHandlers.set(type, handler)
  }

  /**
   * 移除信令处理器
   */
  removeSignalingHandler(type: string): void {
    this.signalingHandlers.delete(type)
  }

  protected sendHeartbeat(): void {
    this.send({
      type: 'heartbeat',
      timestamp: Date.now()
    })
  }

  protected handleMessage(data: any): void {
    const { type } = data
    const handler = this.signalingHandlers.get(type)
    
    if (handler) {
      handler(data)
    } else {
      console.log('未处理的信令类型:', type, data)
    }
  }
}

/**
 * WebSocket管理器
 */
export class WebSocketManager {
  private messageClient: MessageWebSocketClient | null = null
  private voiceClient: VoiceSignalingClient | null = null

  /**
   * 获取消息WebSocket客户端
   */
  getMessageClient(): MessageWebSocketClient {
    if (!this.messageClient) {
      this.messageClient = new MessageWebSocketClient()
    }
    return this.messageClient
  }

  /**
   * 获取语音信令WebSocket客户端
   */
  getVoiceClient(): VoiceSignalingClient {
    if (!this.voiceClient) {
      this.voiceClient = new VoiceSignalingClient()
    }
    return this.voiceClient
  }

  /**
   * 关闭所有连接
   */
  closeAll(): void {
    this.messageClient?.close()
    this.voiceClient?.close()
    this.messageClient = null
    this.voiceClient = null
  }
}

// 全局WebSocket管理器实例
export const wsManager = new WebSocketManager() 