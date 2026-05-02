import type { UserInfo, CurrentUser } from '@/types/api'
import { TOKEN_KEY, USER_KEY } from '@/types/auth'

/**
 * Cookie选项接口
 */
interface CookieOptions {
  path?: string
  domain?: string
  maxAge?: number
  sameSite?: 'Strict' | 'Lax' | 'None'
  secure?: boolean
}

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
  static setUser(user: UserInfo | CurrentUser): void {
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  }

  /**
   * 获取用户信息
   */
  static getUser(): CurrentUser | null {
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
   * 获取环境感知的Cookie选项
   */
  static getOptimalCookieOptions(): string {
    const isDevelopment = import.meta.env.MODE === 'development'
    
    if (isDevelopment) {
      // 开发环境：使用Lax策略支持跨域WebSocket握手
      return 'path=/; SameSite=Lax'
    } else {
      // 生产环境：使用Strict策略提供最高安全性
      return 'path=/; SameSite=Strict; Secure'
    }
  }

  /**
   * 设置Cookie Token（环境感知）
   */
  static setCookieToken(token: string): void {
    try {
      const cookieOptions = Storage.getOptimalCookieOptions()
      document.cookie = `token=${token}; ${cookieOptions}`
    } catch (error) {
      console.error('❌ 设置Cookie Token失败:', error)
    }
  }

  /**
   * 从Cookie获取Token
   */
  static getCookieToken(): string | null {
    try {
      const cookies = document.cookie.split(';')
      for (const cookie of cookies) {
        const [name, value] = cookie.trim().split('=')
        if (name === 'token') {
          return value || null
        }
      }
      return null
    } catch (error) {
      console.error('❌ 获取Cookie Token失败:', error)
      return null
    }
  }

  /**
   * 移除Cookie Token
   */
  static removeCookieToken(): void {
    try {
      // 设置过期时间为过去的时间来删除Cookie
      document.cookie = 'token=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT'
    } catch (error) {
      console.error('❌ 移除Cookie Token失败:', error)
    }
  }

  /**
   * 验证Token一致性（localStorage和Cookie）
   */
  static validateTokenConsistency(): boolean {
    const localToken = Storage.getToken()
    const cookieToken = Storage.getCookieToken()
    
    const isConsistent = localToken === cookieToken
    
    if (!isConsistent) {
    }
    
    return isConsistent
  }

  /**
   * 同步Token到Cookie（用于修复不一致状态）
   */
  static syncTokenToCookie(): void {
    const localToken = Storage.getToken()
    if (localToken) {
      Storage.setCookieToken(localToken)
    }
  }

  /**
   * 清除所有认证相关数据
   */
  static clearAuth(): void {
    Storage.removeToken()
    Storage.removeUser()
    Storage.removeCookieToken()
  }

  /**
   * 清除所有存储数据
   */
  static clearAll(): void {
    Storage.clearAuth()
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
  clearAuth,
  clearAll,
  isLoggedIn,
  // Cookie管理方法
  setCookieToken,
  getCookieToken,
  removeCookieToken,
  validateTokenConsistency,
  syncTokenToCookie,
  getOptimalCookieOptions
} = Storage 