/**
 * 通用表单校验工具函数
 *
 * 前后端共用同一套正则常量，前端负责客户端预检，后端负责最终兜底。
 */

/**
 * RFC 5322 简化版邮箱正则，与后端 RegexConstants.EMAIL_PATTERN 保持一致。
 * 允许字母、数字、. _ % + - 作为用户名，字母数字 . - 作为域名，TLD 至少 2 个字母。
 */
const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/

/**
 * 校验邮箱格式
 * @param email 待校验的邮箱字符串
 * @returns true 表示格式合法
 */
export function validateEmail(email: string): boolean {
  if (!email) return false
  return EMAIL_REGEX.test(email.trim())
}
