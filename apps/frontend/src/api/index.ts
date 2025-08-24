/**
 * API基础配置
 */

// API基础URL
export const API_BASE_URL = ''

// API端点
export const API_ENDPOINTS = {
  // 用户认证
  LOGIN: '/user/login',
  REGISTER: '/user/register',
  
  // 用户管理
  GET_USER: (userId: number) => `/user/${userId}`,
  UPDATE_USER: '/user',
  DELETE_USER: (userId: number) => `/user/${userId}`,
  GET_USER_PAGE: '/user/page',
  
  // 房间管理
  ROOM_CREATE: '/room',
  ROOM_JOIN: '/room/join',
  ROOM_LIST: '/room/my',
  ROOM_BY_CODE: (code: string) => `/room/code/${code}`,
  ROOM_MEMBERS: (roomId: number) => `/room/${roomId}/members`,
  ROOM_LEAVE: (roomId: number) => `/room/${roomId}/leave`,
  ROOM_CLOSE: (roomId: number) => `/room/${roomId}/close`
} as const

/**
 * 请求配置常量
 */
export const REQUEST_CONFIG = {
  TIMEOUT: 10000,
  RETRY_TIMES: 3,
  RETRY_DELAY: 1000
} as const

/**
 * 响应状态码
 */
export const HTTP_STATUS = {
  OK: 200,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  INTERNAL_SERVER_ERROR: 500
} as const

/**
 * 内容类型
 */
export const CONTENT_TYPE = {
  JSON: 'application/json;charset=UTF-8',
  FORM: 'application/x-www-form-urlencoded',
  MULTIPART: 'multipart/form-data'
} as const 