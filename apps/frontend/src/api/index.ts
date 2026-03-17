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
  ROOM_JOIN_ASYNC: '/room/join/async',
  ROOM_JOIN_RESULT: '/room/join/result',
  ROOM_LIST: '/room/my',
  ROOM_BY_CODE: (code: string) => `/room/code/${code}`,
  ROOM_MEMBERS: (roomId: number) => `/room/${roomId}/members`,
  ROOM_LEAVE: (roomId: number) => `/room/${roomId}/leave`,
  ROOM_CLOSE: (roomId: number) => `/room/${roomId}/close`,
  
  // 消息管理
  MESSAGE_SEND: '/message/send',
  MESSAGE_ROOM_LIST: (roomId: number) => `/message/room/${roomId}`,
  MESSAGE_LATEST: (roomId: number) => `/message/room/${roomId}/latest`,
  MESSAGE_GET: (messageId: number) => `/message/${messageId}`,
  MESSAGE_DELETE: (messageId: number) => `/message/${messageId}`,
  MESSAGE_COUNT: (roomId: number) => `/message/room/${roomId}/count`,
  
  // 文件管理
  FILE_UPLOAD: '/file/upload',
  FILE_ROOM_LIST: (roomId: number) => `/file/room/${roomId}`,
  FILE_INFO: (fileId: number) => `/file/${fileId}`,
  FILE_DOWNLOAD: (fileId: number) => `/file/${fileId}/download`,
  FILE_PREVIEW: (fileId: number) => `/file/${fileId}/preview`,
  FILE_DELETE: (fileId: number) => `/file/${fileId}`,
  FILE_STATS: (roomId: number) => `/file/room/${roomId}/stats`,
  FILE_CLEANUP: (roomId: number) => `/file/room/${roomId}/cleanup`,
  
  // 语音通话
  VOICE_INITIATE: '/voice/initiate',
  VOICE_JOIN: (callId: number) => `/voice/${callId}/join`,
  VOICE_LEAVE: (callId: number) => `/voice/${callId}/leave`,
  VOICE_END: (callId: number) => `/voice/${callId}/end`,
  VOICE_SIGNALING: '/voice/signaling',
  VOICE_GET: (callId: number) => `/voice/${callId}`,
  VOICE_ROOM_JOIN: (roomId: number) => `/voice/room/${roomId}/join`,
  VOICE_READY: (callId: number) => `/voice/${callId}/ready`,
  VOICE_ROOM_ACTIVE: (roomId: number) => `/voice/room/${roomId}/active`,
  VOICE_ROOM_HISTORY: (roomId: number) => `/voice/room/${roomId}/history`,
  VOICE_PARTICIPANTS: (callId: number) => `/voice/${callId}/participants`,
  VOICE_PARTICIPANT_STATUS: (callId: number, userId: number) => `/voice/${callId}/participants/${userId}/status`,
  VOICE_PERMISSION: (callId: number) => `/voice/permission/${callId}`,
  VOICE_CLEANUP: '/voice/cleanup'
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
