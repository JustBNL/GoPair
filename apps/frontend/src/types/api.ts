/**
 * API统一响应格式
 */
export interface ApiResponse<T = any> {
  code: number
  msg: string
  data: T
}

/**
 * 用户信息接口
 */
export interface UserInfo {
  userId: number
  nickname: string
  email: string
  avatar?: string
  status: string
  remark?: string
  createTime: string
  updateTime: string
  token: string
}

/**
 * 登录请求接口
 */
export interface LoginRequest {
  email: string
  password: string
}

/**
 * 注册请求接口
 */
export interface RegisterRequest {
  nickname: string
  email: string
  password: string
}

/**
 * 分页查询基础参数
 */
export interface BaseQuery {
  pageNum?: number
  pageSize?: number
}

/**
 * 分页响应结果
 */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

/**
 * 错误码映射
 */
export const ERROR_CODES = {
  // 成功
  SUCCESS: 200,
  
  // 用户错误 (1000-1099)
  USER_NOT_FOUND: 1000,
  USER_ALREADY_EXISTS: 1001,
  EMAIL_ALREADY_EXISTS: 1003,
  INVALID_CREDENTIALS: 1004,
  PASSWORD_ERROR: 1005,
  NICKNAME_ALREADY_EXISTS: 1006,
  
  // 系统错误 (700-799)
  PARAM_ERROR: 700,
  PARAM_MISSING: 701,
  PARAM_TYPE_ERROR: 702,
  PARAM_BIND_ERROR: 703,
  
  // 授权认证错误 (800-899)
  UNAUTHORIZED: 800,
  TOKEN_EXPIRED: 801,
  TOKEN_INVALID: 802,
  ACCESS_DENIED: 803
} as const

/**
 * 错误消息映射
 */
export const ERROR_MESSAGES: Record<number, string> = {
  [ERROR_CODES.USER_NOT_FOUND]: '用户不存在',
  [ERROR_CODES.EMAIL_ALREADY_EXISTS]: '邮箱已存在',
  [ERROR_CODES.NICKNAME_ALREADY_EXISTS]: '昵称已存在',
  [ERROR_CODES.PASSWORD_ERROR]: '密码错误',
  [ERROR_CODES.PARAM_MISSING]: '缺少必要参数',
  [ERROR_CODES.UNAUTHORIZED]: '未授权访问',
  [ERROR_CODES.TOKEN_EXPIRED]: '令牌已过期',
  [ERROR_CODES.TOKEN_INVALID]: '无效的令牌'
} 