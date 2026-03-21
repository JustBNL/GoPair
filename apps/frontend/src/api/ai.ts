import type { GlmChatMessage, GlmStreamChunk } from '@/types/ai'

const GLM_API_URL = 'https://open.bigmodel.cn/api/paas/v4/chat/completions'
const GLM_MODEL = 'glm-4-flash'

/**
 * 系统提示词
 */
const SYSTEM_PROMPT = `你是 GoPair 的智能 AI 助手。GoPair 是一个实时协作平台，支持创建房间、多人聊天、文件共享和语音通话。
请用简洁、友好的中文回答用户的问题，帮助用户更好地使用 GoPair。`

/**
 * 携带上下文的最大历史消息条数（避免 token 超限）
 */
const MAX_HISTORY = 10

/**
 * 发起流式聊天请求，通过回调逐字返回内容
 * @param messages   用户历史消息（不含 system）
 * @param onChunk    每收到一个字符片段时的回调
 * @param onDone     流结束时的回调
 * @param onError    发生错误时的回调
 * @returns          用于中断请求的 AbortController
 */
export function streamChat(
  messages: GlmChatMessage[],
  onChunk: (text: string) => void,
  onDone: () => void,
  onError: (err: Error) => void
): AbortController {
  const apiKey = import.meta.env.VITE_ZHIPU_API_KEY as string

  const controller = new AbortController()

  // 截取最近 N 条历史，避免 token 超限
  const trimmedHistory = messages.slice(-MAX_HISTORY)

  const body: string = JSON.stringify({
    model: GLM_MODEL,
    stream: true,
    temperature: 0.7,
    max_tokens: 1024,
    messages: [
      { role: 'system', content: SYSTEM_PROMPT },
      ...trimmedHistory
    ]
  })

  ;(async () => {
    try {
      const response = await fetch(GLM_API_URL, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${apiKey}`
        },
        body,
        signal: controller.signal
      })

      if (!response.ok) {
        const errText = await response.text()
        throw new Error(`API 请求失败 (${response.status}): ${errText}`)
      }

      const reader = response.body?.getReader()
      if (!reader) throw new Error('无法读取响应流')

      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })

        // SSE 每行以 "data: " 开头，以 "\n\n" 分隔
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''

        for (const line of lines) {
          const trimmed = line.trim()
          if (!trimmed || !trimmed.startsWith('data: ')) continue
          const jsonStr = trimmed.slice(6)
          if (jsonStr === '[DONE]') {
            onDone()
            return
          }
          try {
            const chunk = JSON.parse(jsonStr) as GlmStreamChunk
            const delta = chunk.choices?.[0]?.delta?.content
            if (delta) onChunk(delta)
          } catch {
            // 忽略解析失败的行
          }
        }
      }

      onDone()
    } catch (err: any) {
      if (err?.name === 'AbortError') return
      onError(err instanceof Error ? err : new Error(String(err)))
    }
  })()

  return controller
}
