import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'

dayjs.locale('zh-cn')

/**
 * 格式化时间
 * @param dateTime ISO 字符串
 * @param format 输出格式，默认 "YYYY-MM-DD HH:mm"
 */
export function formatTime(dateTime: string | null | undefined, format = 'YYYY-MM-DD HH:mm'): string {
  if (!dateTime) return '—'
  return dayjs(dateTime).format(format)
}

/**
 * 通话时长（秒）转 "X小时X分钟X秒" 或 "X分X秒"
 */
export function formatDuration(seconds: number | null | undefined): string {
  if (!seconds && seconds !== 0) return '—'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  if (h > 0) return `${h}小时${m}分钟${s}秒`
  if (m > 0) return `${m}分钟${s}秒`
  return `${s}秒`
}

/**
 * 文件大小（字节）转人类可读字符串
 */
export function formatFileSize(bytes: number | null | undefined): string {
  if (!bytes) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`
}
