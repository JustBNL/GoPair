/**
 * WebSocket配置中心
 * 统一管理所有WebSocket相关配置，支持环境感知和动态端点构建
 */

/**
 * WebSocket配置接口
 */
export interface WebSocketConfig {
  base: string
  endpoints: {
    connect: string
    room: string
    voice: string
  }
  options: {
    autoReconnect: boolean
    reconnectInterval: number
    maxReconnectAttempts: number
    heartbeatInterval: number
  }
}

/**
 * 环境配置
 */
const ENV_CONFIG = {
  development: {
    base: '/ws',  // 开发环境使用代理路径（已通过/ws代理到/api/ws）
    gateway: window.location.host  // 使用代理统一的域名
  },
  production: {
    base: '/api/ws',
    gateway: window.location.host
  }
} as const

/**
 * 获取当前环境
 */
const getCurrentEnv = (): 'development' | 'production' => {
  return import.meta.env.MODE === 'development' ? 'development' : 'production'
}

/**
 * WebSocket配置
 */
export const WS_CONFIG: WebSocketConfig = {
  base: ENV_CONFIG[getCurrentEnv()].base,
  endpoints: {
    connect: '/connect',
    room: '/room',
    voice: '/voice'
  },
  options: {
    autoReconnect: true,
    reconnectInterval: 3000,
    maxReconnectAttempts: 5,
    heartbeatInterval: 30000
  }
}

// 特性开关（可根据环境变量覆盖）
export const WS_FEATURES = {
  // 调试日志开关
  debug: import.meta.env.VITE_WS_DEBUG === 'true' || getCurrentEnv() === 'development',
  // 是否在登录/恢复时建立全局WS连接
  enableGlobal: import.meta.env.VITE_WS_ENABLE_GLOBAL === 'true' || true
} as const

export const isDebug = (): boolean => WS_FEATURES.debug

/**
 * 构建WebSocket URL
 */
export const buildWebSocketUrl = (endpoint: string, params?: Record<string, string | number>): string => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const env = getCurrentEnv()
  const host = ENV_CONFIG[env].gateway
  
  let url = `${protocol}//${host}${WS_CONFIG.base}${endpoint}`
  
  // 添加路径参数
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      url = url.replace(`{${key}}`, String(value))
    }
  }
  
  return url
}

/**
 * WebSocket端点助手
 */
export const WS_ENDPOINTS = {
  /**
   * 全局连接端点
   */
  connect: () => buildWebSocketUrl(WS_CONFIG.endpoints.connect),
  
  /**
   * 房间端点
   */
  room: (roomId: number) => buildWebSocketUrl(WS_CONFIG.endpoints.room + `/${roomId}`),
  
  /**
   * 语音端点
   */
  voice: (callId: number) => buildWebSocketUrl(WS_CONFIG.endpoints.voice + `/${callId}`)
} as const 