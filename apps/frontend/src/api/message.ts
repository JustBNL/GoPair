import { http } from '@/utils/request'
import { API_ENDPOINTS } from './index'
import type {
  ApiResponse,
  PageResult,
  MessageVO,
  SendMessageDto,
  MessageQueryDto
} from '@/types/api'

/**
 * 消息相关API
 */
export class MessageAPI {

  /**
   * 发送消息
   */
  static async sendMessage(dto: SendMessageDto): Promise<ApiResponse<MessageVO>> {
    return http.post(API_ENDPOINTS.MESSAGE_SEND, dto)
  }

  /**
   * 获取房间消息列表（分页）
   */
  static async getRoomMessages(query: MessageQueryDto): Promise<ApiResponse<PageResult<MessageVO>>> {
    const { roomId, ...params } = query
    const url = `${API_ENDPOINTS.MESSAGE_ROOM_LIST(roomId)}?${new URLSearchParams(params as Record<string, string>).toString()}`
    return http.get(url)
  }

  /**
   * 获取房间最新消息
   */
  static async getLatestMessages(roomId: number, limit: number = 20): Promise<ApiResponse<MessageVO[]>> {
    const url = `${API_ENDPOINTS.MESSAGE_LATEST(roomId)}?limit=${limit}`
    return http.get(url)
  }

  /**
   * 根据ID获取消息详情
   */
  static async getMessageById(messageId: number): Promise<ApiResponse<MessageVO>> {
    return http.get(API_ENDPOINTS.MESSAGE_GET(messageId))
  }

  /**
   * 删除消息
   */
  static async deleteMessage(messageId: number): Promise<ApiResponse<boolean>> {
    return http.delete(API_ENDPOINTS.MESSAGE_DELETE(messageId))
  }

  /**
   * 统计房间消息数量
   */
  static async countRoomMessages(roomId: number): Promise<ApiResponse<number>> {
    return http.get(API_ENDPOINTS.MESSAGE_COUNT(roomId))
  }

  /**
   * 批量删除消息
   */
  static async batchDeleteMessages(messageIds: number[]): Promise<ApiResponse<boolean>> {
    return http.post('/message/batch-delete', { messageIds })
  }

  /**
   * 搜索消息
   */
  static async searchMessages(query: MessageQueryDto): Promise<ApiResponse<PageResult<MessageVO>>> {
    const { roomId, ...params } = query
    const url = `/message/room/${roomId}/search?${new URLSearchParams(params as Record<string, string>).toString()}`
    return http.get(url)
  }

  /**
   * 标记消息为已读
   */
  static async markAsRead(roomId: number, messageId?: number): Promise<ApiResponse<boolean>> {
    return http.post('/message/mark-read', {
      roomId,
      messageId
    })
  }

  /**
   * 获取未读消息数量
   */
  static async getUnreadCount(roomId: number): Promise<ApiResponse<number>> {
    return http.get(`/message/room/${roomId}/unread-count`)
  }

  /**
   * 撤回消息
   */
  static async recallMessage(messageId: number): Promise<ApiResponse<boolean>> {
    return http.post(`/message/${messageId}/recall`)
  }
} 