/**
 * 私聊相关 TypeScript 类型定义
 */

/** 好友关系状态 */
export enum FriendStatus {
  NONE = 0,
  PENDING_SENT = 1,
  PENDING_RECEIVED = 2,
  FRIEND = 3,
  REJECTED = 4
}

/** 私聊消息类型 */
export enum PrivateMessageType {
  TEXT = 1,
  IMAGE = 2,
  FILE = 3
}

/** 好友VO */
export interface FriendVO {
  friendId: number
  nickname: string
  email?: string
  avatar?: string
  avatarOriginalUrl?: string
  remark?: string
  createdAt: string
  lastMessageTime?: string
  lastMessageContent?: string
}

/** 好友申请VO */
export interface FriendRequestVO {
  requestId: number
  fromUserId: number
  fromNickname: string
  fromAvatar?: string
  toUserId: number
  toNickname?: string
  message?: string
  status: 'pending' | 'accepted' | 'rejected'
  createdAt: string
}

/** 私聊消息VO */
export interface PrivateMessageVO {
  messageId: number
  conversationId: number
  senderId: number
  receiverId: number
  senderNickname: string
  senderAvatar?: string
  messageType: PrivateMessageType
  messageTypeDesc: string
  content?: string
  fileUrl?: string
  fileName?: string
  fileSize?: number
  isRecalled: boolean
  recalledAt?: string
  isOwn: boolean
  createTime: string
}

/** 私聊会话VO */
export interface ConversationVO {
  conversationId: number
  friendId: number
  friendNickname: string
  friendAvatar?: string
  lastMessageContent?: string
  lastMessageTime?: string
  lastMessageType: PrivateMessageType
  messageCount: number
}

/** 发送私聊消息DTO */
export interface SendPrivateMessageDto {
  receiverId: number
  messageType: PrivateMessageType
  content?: string
  fileUrl?: string
  fileName?: string
  fileSize?: number
}

/** 发送好友请求DTO */
export interface FriendRequestDto {
  toUserId: number
  message?: string
}

/** 好友关系状态检查VO */
export interface FriendStatusVO {
  isFriend: boolean
  isRequestSent: boolean
  isRequestReceived: boolean
  requestId?: number
}

/** 用户公开资料 */
export interface UserPublicProfile {
  userId: number
  nickname: string
  avatar?: string
  avatarOriginalUrl?: string
  email?: string
}

/** 用户搜索结果VO */
export interface UserSearchResultVO {
  userId: number
  nickname: string
  avatar?: string
  email?: string
  friendStatus: FriendStatusVO
}
