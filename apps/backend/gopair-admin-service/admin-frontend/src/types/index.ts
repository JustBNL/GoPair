/* ================================
   GoPair Admin — 全局类型定义
   ================================ */

/* ---------- 通用响应 ---------- */
export interface ApiResponse<T = unknown> {
  code: number
  msg: string
  data: T
}

export interface PageInfo {
  records: unknown[]
  total: number
  size: number
  current: number
  pages: number
}

/* ---------- 认证 ---------- */
export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  adminId: number
  username: string
  nickname: string
}

/* ---------- 仪表盘 ---------- */
export interface DashboardStats {
  totalUsers: number
  todayNewUsers: number
  activeRooms: number
  todayNewRooms: number
  todayMessages: number
  todayVoiceCallDuration: number
}

/* ---------- 用户 ---------- */
export interface User {
  userId: number
  nickname: string
  email: string
  avatar: string
  status: string
  remark: string
  createTime: string
  updateTime: string
}

export interface UserDetail {
  user: User
  roomCount: number
  ownedRoomCount: number
}

export interface UserQuery {
  pageNum: number
  pageSize: number
  keyword?: string
}

/* ---------- 房间 ---------- */
export interface Room {
  roomId: number
  roomCode: string
  roomName: string
  description: string
  maxMembers: number
  currentMembers: number
  ownerId: number
  status: number
  expireTime: string
  version: number
  passwordMode: number
  createTime: string
  updateTime: string
}

export interface RoomMember {
  id: number
  roomId: number
  userId: number
  role: number
  status: number
  joinTime: string
  lastActiveTime: string
  createTime: string
  updateTime: string
}

export interface RoomDetail {
  room: Room
  members: RoomMember[]
  userMap: Record<string, User>
}

export interface RoomQuery {
  pageNum: number
  pageSize: number
  status?: number
  keyword?: string
}

/* ---------- 消息 ---------- */
export interface Message {
  messageId: number
  roomId: number
  senderId: number
  messageType: number
  content: string
  fileUrl: string | null
  fileName: string | null
  fileSize: number | null
  replyToId: number | null
  createTime: string
  updateTime: string
}

export interface MessageQuery {
  pageNum: number
  pageSize: number
  roomId?: number
  keyword?: string
}

/* ---------- 文件 ---------- */
export interface RoomFile {
  fileId: number
  roomId: number
  uploaderId: number
  uploaderNickname: string
  fileName: string
  filePath: string
  fileSize: number
  thumbnailSize: number
  fileType: string
  contentType: string
  downloadCount: number
  uploadTime: string
  createTime: string
  updateTime: string
}

export interface FileDetail extends RoomFile {}

export interface FileQuery {
  pageNum: number
  pageSize: number
  roomId?: number
  keyword?: string
}

/* ---------- 审计日志 ---------- */
export interface AuditLog {
  id: number
  adminId: number
  adminUsername: string
  operation: string
  targetType: string
  targetId: string
  detail: string
  ipAddress: string
  userAgent: string
  createTime: string
}

export interface AuditQuery {
  pageNum: number
  pageSize: number
  adminId?: number
  operation?: string
  targetType?: string
}

/* ---------- 通话 ---------- */
export interface VoiceCall {
  callId: number
  roomId: number
  initiatorId: number
  callType: number
  status: number
  startTime: string
  endTime: string | null
  duration: number
  isAutoCreated: boolean
}

export interface VoiceCallDetail extends VoiceCall {}

export interface VoiceCallParticipant {
  id: number
  callId: number
  userId: number
  joinTime: string
  leaveTime: string | null
  connectionStatus: number
}

export interface CallQuery {
  pageNum: number
  pageSize: number
  roomId?: number
  status?: number
}
