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
  userRole?: number // 用户角色 0-成员 1-管理员 2-房主
  relationshipType?: 'created' | 'joined' // 关系类型：创建的房间或加入的房间
  joinTime?: string // 用户加入房间的时间
  passwordMode?: number // 密码模式 0-关闭 1-固定密码 2-动态令牌
  passwordVisible?: number // 密码是否可见 0-隐藏 1-显示
  currentPassword?: string // 当前密码/令牌（仅房主可见）
  remainingSeconds?: number // 动态令牌剩余秒数
  closedTime?: string // 关闭时间，null表示手动关闭（可重新开启），非null表示系统关闭（不可重新开启）
}

/**
 * 密码模式枚举
 */
export const PASSWORD_MODE = {
  OFF: 0,
  FIXED: 1,
  TOTP: 2
} as const

export type PasswordMode = typeof PASSWORD_MODE[keyof typeof PASSWORD_MODE]

/**
 * 创建房间请求接口
 */
export interface CreateRoomRequest {
  roomName: string
  description?: string
  maxMembers?: number
  expireMinutes?: number
  passwordMode?: number
  rawPassword?: string
  passwordVisible?: number
}

/**
 * 加入房间请求接口
 */
export interface JoinRoomRequest {
  roomCode: string
  password?: string
}

/**
 * 更新房间密码请求接口
 */
export interface UpdateRoomPasswordRequest {
  mode: number
  rawPassword?: string
  visible?: number
}

/**
 * 房间当前密码响应
 */
export interface RoomPasswordInfo {
  passwordMode: number
  passwordVisible: number
  currentPassword?: string
  remainingSeconds?: number
}

/**
 * 房间成员信息接口
 */
export interface RoomMember {
  userId: number
  roomId: number
  nickname: string
  joinTime: string
  isOwner: boolean
  status?: 'online' | 'offline' | 'away'
  role?: 'owner' | 'member'
  lastActiveTime?: string
  avatar?: string
}

/**
 * 房间状态枚举
 */
export const ROOM_STATUS = {
  ACTIVE: 0,
  CLOSED: 1,
  EXPIRED: 2,
  ARCHIVED: 3,
  DISABLED: 4
} as const

export type RoomStatus = typeof ROOM_STATUS[keyof typeof ROOM_STATUS]

/**
 * 房间表单数据接口
 */
export interface CreateRoomFormData {
  roomName: string
  description: string
  maxMembers: number
  expireMinutes: number
  expirePreset: number       // 预设档位（-1 表示自定义）
  customDurationValue?: number  // 自定义数值
  customDurationUnit?: TimeUnit // 自定义单位
  passwordMode: number
  rawPassword: string
  passwordVisible: number
}

export interface JoinRoomFormData {
  roomCode: string
  password: string
}

/**
 * 续期时长档位（分钟）
 */
export const RENEW_MINUTES_OPTIONS = [
  { value: 60, label: '1小时' },
  { value: 1440, label: '1天' },
  { value: 4320, label: '3天' },
  { value: 10080, label: '7天' }
] as const

export type RenewMinutesOption = typeof RENEW_MINUTES_OPTIONS[number]['value']

/**
 * 时间单位枚举
 */
export enum TimeUnit {
  MINUTES = 'minutes',
  HOURS = 'hours',
  DAYS = 'days'
}

/**
 * 时间单位选项（用于下拉选择器）
 */
export const TIME_UNIT_OPTIONS = [
  { value: TimeUnit.MINUTES, label: '分钟' },
  { value: TimeUnit.HOURS, label: '小时' },
  { value: TimeUnit.DAYS, label: '天' }
] as const

/**
 * 自定义时长输入数据接口
 */
export interface CustomDurationInput {
  value: number
  unit: TimeUnit
}

/**
 * 将自定义时长输入转换为分钟数
 * @param input 自定义时长输入
 * @returns 分钟数
 */
export function convertToMinutes(input: CustomDurationInput): number {
  switch (input.unit) {
    case TimeUnit.MINUTES:
      return input.value
    case TimeUnit.HOURS:
      return input.value * 60
    case TimeUnit.DAYS:
      return input.value * 24 * 60
  }
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

/**
 * 重新开启房间请求
 */
export interface ReopenRoomRequest {
  expireMinutes: number
}
