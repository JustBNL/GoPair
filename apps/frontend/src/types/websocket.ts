/**
 * WebSocket类型定义
 * 为新的WebSocket架构提供完整的类型安全保障
 */

/**
 * WebSocket连接状态
 */
export enum ConnectionState {
  DISCONNECTED = 'disconnected',
  CONNECTING = 'connecting', 
  CONNECTED = 'connected',
  RECONNECTING = 'reconnecting',
  ERROR = 'error'
}

/**
 * WebSocket消息类型
 */
export enum WsMessageType {
  // 系统消息
  HEARTBEAT = 'heartbeat',
  SUBSCRIBE = 'subscribe',
  UNSUBSCRIBE = 'unsubscribe', 
  ERROR = 'error',
  
  // 业务消息
  ROOM_MESSAGE = 'room_message',
  ROOM_FILE = 'room_file',
  ROOM_MEMBER = 'room_member',
  ROOM_SYSTEM = 'room_system'
}

/**
 * WebSocket事件类型
 */
export enum WsEventType {
  // 聊天事件
  MESSAGE_SEND = 'message_send',
  MESSAGE_DELETE = 'message_delete',
  MESSAGE_EDIT = 'message_edit',
  MESSAGE_RECALL = 'message_recall',
  
  // 文件事件
  FILE_UPLOAD = 'file_upload',
  FILE_DELETE = 'file_delete',
  FILE_DOWNLOAD = 'file_download',
  
  // 成员事件
  MEMBER_JOIN = 'member_join',
  MEMBER_LEAVE = 'member_leave',
  MEMBER_KICK = 'member_kick',
  MEMBER_TYPING = 'member_typing',
  
  // 语音事件
  CALL_START = 'call_start',
  CALL_END = 'call_end',
  SIGNALING = 'signaling',
  VOICE_ROSTER_UPDATE = 'voice_roster_update',

  // 房间状态事件
  ROOM_CLOSED = 'room_closed',
  ROOM_RENEWED = 'room_renewed',
  ROOM_REOPENED = 'room_reopened'
}

/**
 * WebSocket消息基础接口
 */
export interface WsMessage {
  id: string
  type: WsMessageType
  eventType?: WsEventType
  timestamp: number
  data?: any
}

/**
 * 房间消息
 */
export interface RoomMessage extends WsMessage {
  roomId: number
  senderId: number
  senderNickname: string
}

/**
 * WebSocket连接选项
 */
export interface WsConnectionOptions {
  autoReconnect?: boolean
  reconnectInterval?: number
  maxReconnectAttempts?: number
  heartbeatInterval?: number
  timeout?: number
}

/**
 * WebSocket事件回调
 */
export interface WsEventCallbacks {
  onConnected?: () => void
  onDisconnected?: (event: CloseEvent) => void
  onError?: (error: Error) => void
  onMessage?: (message: WsMessage) => void
}

/**
 * 订阅配置
 */
export interface SubscriptionConfig {
  roomId?: number
  events: WsEventType[]
  autoCleanup?: boolean
}

/**
 * 房间WebSocket状态
 */
export interface RoomWsState {
  messages: any[]
  files: any[]
  members: any[]
  isTyping: Record<number, boolean>
  onlineCount: number
} 