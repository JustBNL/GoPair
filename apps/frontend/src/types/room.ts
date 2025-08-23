/**
 * 房间相关类型定义
 */

/**
 * 房间信息接口
 */
export interface RoomInfo {
  roomId: number
  roomCode: string
  roomName: string
  description?: string
  maxMembers: number
  currentMembers: number
  ownerId: number
  ownerNickname?: string
  status: number
  expireTime: string
  createTime: string
  updateTime: string
}

/**
 * 创建房间请求接口
 */
export interface CreateRoomRequest {
  roomName: string
  description?: string
  maxMembers?: number
  expireHours?: number
}

/**
 * 加入房间请求接口
 */
export interface JoinRoomRequest {
  roomCode: string
  displayName: string
}

/**
 * 房间成员信息接口
 */
export interface RoomMember {
  userId: number
  roomId: number
  displayName: string
  nickname: string
  joinTime: string
  isOwner: boolean
}

/**
 * 房间状态枚举
 */
export const ROOM_STATUS = {
  ACTIVE: 0,
  CLOSED: 1
} as const

export type RoomStatus = typeof ROOM_STATUS[keyof typeof ROOM_STATUS]

/**
 * 房间表单数据接口
 */
export interface CreateRoomFormData {
  roomName: string
  description: string
  maxMembers: number
  expireHours: number
}

export interface JoinRoomFormData {
  roomCode: string
  displayName: string
}

/**
 * 房间Store状态接口
 */
export interface RoomState {
  roomList: RoomInfo[]
  currentRoom: RoomInfo | null
  roomMembers: RoomMember[]
  loading: boolean
  createLoading: boolean
  joinLoading: boolean
} 