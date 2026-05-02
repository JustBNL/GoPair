import { http } from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types/api'
import type {
  FriendVO,
  FriendRequestVO,
  PrivateMessageVO,
  ConversationVO,
  SendPrivateMessageDto,
  FriendRequestDto,
  FriendStatusVO,
  UserPublicProfile
} from '@/types/chat'

/**
 * 聊天服务 API 封装（好友 + 私聊）
 */
export class ChatAPI {
  // ==================== 好友 ====================

  static async sendFriendRequest(dto: FriendRequestDto): Promise<ApiResponse<FriendRequestVO>> {
    return http.post<FriendRequestVO>('/chat/friend/request', dto)
  }

  static async acceptFriendRequest(requestId: number): Promise<ApiResponse<void>> {
    return http.post<void>(`/chat/friend/accept/${requestId}`)
  }

  static async rejectFriendRequest(requestId: number): Promise<ApiResponse<void>> {
    return http.post<void>(`/chat/friend/reject/${requestId}`)
  }

  static async deleteFriend(friendId: number): Promise<ApiResponse<void>> {
    return http.delete<void>(`/chat/friend/${friendId}`)
  }

  static async getFriends(): Promise<ApiResponse<FriendVO[]>> {
    return http.get<FriendVO[]>('/chat/friend')
  }

  static async getIncomingRequests(): Promise<ApiResponse<FriendRequestVO[]>> {
    return http.get<FriendRequestVO[]>('/chat/friend/request/incoming')
  }

  static async getOutgoingRequests(): Promise<ApiResponse<FriendRequestVO[]>> {
    return http.get<FriendRequestVO[]>('/chat/friend/request/outgoing')
  }

  static async checkFriendStatus(userId: number): Promise<ApiResponse<FriendStatusVO>> {
    return http.get<FriendStatusVO>(`/chat/friend/check/${userId}`)
  }

  static async getUserProfile(userId: number): Promise<ApiResponse<UserPublicProfile>> {
    return http.get<UserPublicProfile>(`/chat/friend/user/${userId}`)
  }

  // ==================== 私聊消息 ====================

  static async sendMessage(dto: SendPrivateMessageDto): Promise<ApiResponse<PrivateMessageVO>> {
    return http.post<PrivateMessageVO>('/chat/message/send', dto)
  }

  static async getConversations(): Promise<ApiResponse<ConversationVO[]>> {
    return http.get<ConversationVO[]>('/chat/conversation')
  }

  static async getMessages(
    conversationId: number,
    pageNum = 1,
    pageSize = 20
  ): Promise<ApiResponse<PageResult<PrivateMessageVO>>> {
    return http.get<PageResult<PrivateMessageVO>>(
      `/chat/conversation/${conversationId}/message?pageNum=${pageNum}&pageSize=${pageSize}`
    )
  }

  static async deleteMessage(messageId: number): Promise<ApiResponse<void>> {
    return http.delete<void>(`/chat/message/${messageId}`)
  }

  static async recallMessage(messageId: number): Promise<ApiResponse<void>> {
    return http.post<void>(`/chat/message/${messageId}/recall`)
  }
}

export const {
  sendFriendRequest,
  acceptFriendRequest,
  rejectFriendRequest,
  deleteFriend,
  getFriends,
  getIncomingRequests,
  getOutgoingRequests,
  checkFriendStatus,
  getUserProfile,
  sendMessage: sendPrivateMessage,
  getConversations,
  getMessages,
  deleteMessage: deletePrivateMessage,
  recallMessage: recallPrivateMessage
} = ChatAPI
