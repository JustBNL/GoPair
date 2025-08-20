import type { UserInfo } from '@/types/api'
import { TOKEN_KEY, USER_KEY, EMAIL_KEY, REMEMBER_KEY } from '@/types/auth'

/**
 * 本地存储工具类
 */
export class Storage {
  /**
   * 设置Token
   */
  static setToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token)
  }

  /**
   * 获取Token
   */
  static getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY)
  }

  /**
   * 移除Token
   */
  static removeToken(): void {
    localStorage.removeItem(TOKEN_KEY)
  }

  /**
   * 设置用户信息
   */
  static setUser(user: UserInfo): void {
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  }

  /**
   * 获取用户信息
   */
  static getUser(): UserInfo | null {
    const userStr = localStorage.getItem(USER_KEY)
    if (userStr) {
      try {
        return JSON.parse(userStr)
      } catch (error) {
        console.error('解析用户信息失败:', error)
        Storage.removeUser()
        return null
      }
    }
    return null
  }

  /**
   * 移除用户信息
   */
  static removeUser(): void {
    localStorage.removeItem(USER_KEY)
  }

  /**
   * 设置记住的邮箱
   */
  static setSavedEmail(email: string): void {
    localStorage.setItem(EMAIL_KEY, email)
  }

  /**
   * 获取记住的邮箱
   */
  static getSavedEmail(): string {
    return localStorage.getItem(EMAIL_KEY) || ''
  }

  /**
   * 移除记住的邮箱
   */
  static removeSavedEmail(): void {
    localStorage.removeItem(EMAIL_KEY)
  }

  /**
   * 设置是否记住邮箱
   */
  static setRememberEmail(remember: boolean): void {
    localStorage.setItem(REMEMBER_KEY, remember.toString())
  }

  /**
   * 获取是否记住邮箱
   */
  static getRememberEmail(): boolean {
    return localStorage.getItem(REMEMBER_KEY) === 'true'
  }

  /**
   * 清除所有认证相关数据
   */
  static clearAuth(): void {
    Storage.removeToken()
    Storage.removeUser()
  }

  /**
   * 清除所有存储数据
   */
  static clearAll(): void {
    Storage.clearAuth()
    Storage.removeSavedEmail()
    localStorage.removeItem(REMEMBER_KEY)
  }

  /**
   * 检查是否已登录
   */
  static isLoggedIn(): boolean {
    const token = Storage.getToken()
    const user = Storage.getUser()
    return !!(token && user)
  }
}

/**
 * 导出便捷方法
 */
export const {
  setToken,
  getToken,
  removeToken,
  setUser,
  getUser,
  removeUser,
  setSavedEmail,
  getSavedEmail,
  removeSavedEmail,
  setRememberEmail,
  getRememberEmail,
  clearAuth,
  clearAll,
  isLoggedIn
} = Storage 