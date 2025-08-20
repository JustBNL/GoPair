import type { UserInfo, LoginRequest, RegisterRequest } from './api'

/**
 * 认证状态接口
 */
export interface AuthState {
  // 用户信息
  user: UserInfo | null
  token: string | null
  
  // 登录状态
  isLoggedIn: boolean
  loginLoading: boolean
  registerLoading: boolean
  
  // 界面状态
  currentMode: 'login' | 'register'
  
  // 用户偏好
  rememberEmail: boolean
  savedEmail: string
}

/**
 * 登录表单数据
 */
export interface LoginFormData {
  email: string
  password: string
  remember?: boolean
}

/**
 * 注册表单数据
 */
export interface RegisterFormData {
  nickname: string
  email: string
  password: string
}

/**
 * 表单验证规则
 */
export interface FormRules {
  [key: string]: Array<{
    required?: boolean
    message?: string
    type?: string
    min?: number
    max?: number
    pattern?: RegExp
    validator?: (rule: any, value: any) => Promise<void>
  }>
}

/**
 * 认证Store操作接口
 */
export interface AuthActions {
  // 登录相关
  login: (loginData: LoginRequest) => Promise<void>
  register: (registerData: RegisterRequest) => Promise<void>
  logout: () => void
  
  // 状态管理
  switchMode: (mode: 'login' | 'register') => void
  initAuth: () => void
  
  // 用户偏好
  setRememberEmail: (remember: boolean, email?: string) => void
  getSavedEmail: () => string
}

/**
 * Token存储键名
 */
export const TOKEN_KEY = 'gopair_token'
export const USER_KEY = 'gopair_user'
export const EMAIL_KEY = 'gopair_saved_email'
export const REMEMBER_KEY = 'gopair_remember_email'

/**
 * 表单模式
 */
export type FormMode = 'login' | 'register'

/**
 * 认证错误类型
 */
export interface AuthError {
  code: number
  message: string
  field?: string
} 