/**
 * AI 聊天消息角色
 */
export type AiRole = 'user' | 'assistant' | 'system'

/**
 * 单条 AI 聊天消息
 */
export interface AiMessage {
  id: string
  role: AiRole
  content: string
  timestamp: number
  /** AI 正在流式生成中 */
  loading?: boolean
  /** 发生错误 */
  error?: boolean
}

/**
 * 发送给 GLM API 的消息格式
 */
export interface GlmChatMessage {
  role: AiRole
  content: string
}

/**
 * GLM API 请求体
 */
export interface GlmChatRequest {
  model: string
  messages: GlmChatMessage[]
  stream: boolean
  temperature?: number
  max_tokens?: number
}

/**
 * GLM API 流式响应的 delta
 */
export interface GlmStreamDelta {
  role?: AiRole
  content?: string
}

/**
 * GLM API 流式响应的 choice
 */
export interface GlmStreamChoice {
  index: number
  delta: GlmStreamDelta
  finish_reason: string | null
}

/**
 * GLM API 流式响应的单个 chunk
 */
export interface GlmStreamChunk {
  id: string
  object: string
  created: number
  model: string
  choices: GlmStreamChoice[]
}
