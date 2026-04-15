import dayjs from 'dayjs'

/**
 * 共享格式化工具函数
 * 所有组件共享同一份实现，保证 DRY 并支持统一缓存策略
 */

/** 时间格式化缓存：避免高频消息列表中同一时间戳重复解析 */
const TIME_CACHE = new Map<string, { value: string; ts: number }>()
const CACHE_TTL = 30_000 // 30s 缓存过期

/**
 * 格式化时间戳为显示字符串（统一 MM-DD HH:mm）
 * 兼容多种输入：number / string 数字 / ISO 字符串
 * 缓存结果，30s 内重复调用直接返回
 */
export function formatTime(timeInput: unknown): string {
  if (!timeInput) return ''

  const raw = String(timeInput)
  const cached = TIME_CACHE.get(raw)
  if (cached && Date.now() - cached.ts < CACHE_TTL) {
    return cached.value
  }

  let d: ReturnType<typeof dayjs>
  if (typeof timeInput === 'number') {
    d = dayjs(timeInput)
  } else if (typeof timeInput === 'string' && /^\d+$/.test(timeInput)) {
    d = dayjs(Number(timeInput))
  } else {
    d = dayjs(timeInput as string)
  }

  if (!d.isValid()) return ''
  const value = d.format('MM-DD HH:mm')

  TIME_CACHE.set(raw, { value, ts: Date.now() })
  return value
}

/** 清除时间缓存（页面切换时调用，防止泄漏） */
export function clearTimeCache(): void {
  TIME_CACHE.clear()
}
