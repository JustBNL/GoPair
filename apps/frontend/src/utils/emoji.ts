/**
 * Emoji 渲染工具
 *
 * 使用 Twemoji CDN 将 Unicode emoji 转换为彩色图片，
 * 解决不同平台/浏览器 emoji 显示不一致的问题。
 *
 * 通过 CDN 引入而非 npm 包：避免 npm 安装问题，同时减少 bundle 体积。
 */

const TWEMOJI_BASE = 'https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/'
const TWEMOJI_CDN = 'https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/2/twemoji.min.js'

/**
 * HTML 转义：防止 v-html XSS 攻击。
 * 在 Twemoji 替换之前执行，确保普通文本中的 < > & 等字符被安全转义。
 */
export function escapeHtml(text: string): string {
  const map: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;'
  }
  return text.replace(/[&<>"']/g, (m) => map[m])
}

/**
 * 加载 Twemoji 脚本（懒加载，仅首次使用时加载，已加载则复用）。
 */
function loadTwemoji(): Promise<void> {
  return new Promise((resolve) => {
    if ((window as unknown as Record<string, unknown>).twemoji) {
      resolve()
      return
    }
    const script = document.createElement('script')
    script.src = TWEMOJI_CDN
    script.onload = () => resolve()
    script.onerror = () => resolve() // 加载失败时静默降级
    document.head.appendChild(script)
  })
}

// 预加载 Promise，页面首次使用时触发 CDN 加载
let twemojiReady: Promise<void> | null = null

function ensureTwemoji(): Promise<void> {
  if (!twemojiReady) {
    twemojiReady = loadTwemoji()
  }
  return twemojiReady
}

/**
 * 渲染文本中的 emoji。
 *
 * 策略：优先尝试使用已加载的 Twemoji；若未加载，先返回已转义的纯文本，
 * 同时异步触发 Twemoji 加载，下次渲染时自动升级为彩色图片。
 * 这保证了首次渲染无阻塞、后续渲染更美观。
 *
 * @param text - 原始消息文本
 * @returns 已转义并替换 emoji 的 HTML 字符串，供 v-html 使用
 */
export function renderEmojiContent(text: string): string {
  if (!text) return ''

  const escaped = escapeHtml(text)
  const twemoji = (window as unknown as Record<string, unknown>).twemoji as
    | { parse: (t: string, o: Record<string, unknown>) => string }
    | undefined

  if (twemoji) {
    return twemoji.parse(escaped, {
      base: TWEMOJI_BASE,
      ext: '.png'
    })
  }

  // Twemoji 未加载：触发懒加载，下次渲染自动升级
  ensureTwemoji()
  return escaped
}

/**
 * 预加载 Twemoji（可选择在页面空闲时调用，加速首次 emoji 渲染）。
 */
export function preloadTwemoji(): void {
  ensureTwemoji()
}

/**
 * Emoji 分类数据，供 EmojiPicker / EmojiBar 组件使用。
 */
export const EMOJI_CATEGORIES = [
  {
    key: '常用',
    emojis: ['😄', '🎉', '👍', '❤️', '🔥', '😂', '🙏', '💡', '👏', '🚀', '✨', '😎']
  },
  {
    key: '表情',
    emojis: ['😊', '😅', '🤔', '😢', '😮', '🥰', '🤩', '😇', '🤗', '🫡', '😌', '🙃']
  },
  {
    key: '手势',
    emojis: ['👋', '🤝', '👏', '🙌', '✌️', '🤞', '👌', '🤟', '🤙', '💪', '🖐️', '✋']
  },
  {
    key: '物品',
    emojis: ['💡', '📱', '💻', '🎮', '🎁', '🏆', '📷', '🔑', '💎', '🎵', '☕', '🍕']
  },
  {
    key: '符号',
    emojis: ['❤️', '⭐', '💯', '✅', '❌', '🔥', '💫', '🌟', '✨', '🎯', '💬', '🗨️']
  }
]

// 全局 Twemoji 类型声明
declare global {
  interface Window {
    twemoji: {
      parse: (text: string, options?: Record<string, unknown>) => string
    }
  }
}
