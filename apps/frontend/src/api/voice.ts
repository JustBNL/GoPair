import { http } from '@/utils/request'
import { API_ENDPOINTS } from './index'
import type {
  ApiResponse,
  CallVO,
  CallParticipantVO,
  ConnectionStatus
} from '@/types/api'

/**
 * 语音通话相关API
 */
export class VoiceAPI {

  /**
   * 加入或创建通话（按需创建模式）
   * 若房间无活跃通话则自动创建，否则直接加入。
   * 注意：此调用不触发 participant-join，需 WebRTC 就绪后调用 notifyReady。
   */
  static async joinOrCreateCall(roomId: number): Promise<ApiResponse<CallVO>> {
    return http.post(API_ENDPOINTS.VOICE_ROOM_JOIN(roomId))
  }

  /**
   * 通知 WebRTC 就绪，触发后端向其他参与者广播 participant-join。
   * 应在本地音频流和 callState = in-call 之后调用。
   */
  static async notifyReady(callId: number): Promise<ApiResponse<boolean>> {
    return http.post(API_ENDPOINTS.VOICE_READY(callId))
  }

  /**
   * 加入通话（通过 callId）
   */
  static async joinCall(callId: number): Promise<ApiResponse<CallVO>> {
    return http.post(API_ENDPOINTS.VOICE_JOIN(callId))
  }

  /**
   * 离开通话
   */
  static async leaveCall(callId: number): Promise<ApiResponse<boolean>> {
    return http.post(API_ENDPOINTS.VOICE_LEAVE(callId))
  }

  /**
   * 结束通话
   */
  static async endCall(callId: number): Promise<ApiResponse<boolean>> {
    return http.post(API_ENDPOINTS.VOICE_END(callId))
  }

  /**
   * 转发 WebRTC 信令
   */
  static async forwardSignaling(dto: {
    callId: number
    type: string
    targetUserId?: number
    data?: any
  }): Promise<ApiResponse<boolean>> {
    return http.post(API_ENDPOINTS.VOICE_SIGNALING, dto)
  }

  /**
   * 获取通话信息
   */
  static async getCall(callId: number): Promise<ApiResponse<CallVO>> {
    return http.get(API_ENDPOINTS.VOICE_GET(callId))
  }

  /**
   * 获取房间当前活跃通话（仅查询，不自动创建）
   */
  static async getActiveCall(roomId: number): Promise<ApiResponse<CallVO>> {
    return http.get(API_ENDPOINTS.VOICE_ROOM_ACTIVE(roomId))
  }

  /**
   * 获取房间通话历史
   */
  static async getRoomCallHistory(
    roomId: number,
    limit: number = 10
  ): Promise<ApiResponse<CallVO[]>> {
    return http.get(API_ENDPOINTS.VOICE_ROOM_HISTORY(roomId), {
      params: { limit }
    })
  }

  /**
   * 获取通话参与者列表
   */
  static async getCallParticipants(callId: number): Promise<ApiResponse<CallParticipantVO[]>> {
    return http.get(API_ENDPOINTS.VOICE_PARTICIPANTS(callId))
  }

  /**
   * 更新参与者连接状态
   */
  static async updateParticipantStatus(
    callId: number,
    userId: number,
    connectionStatus: ConnectionStatus
  ): Promise<ApiResponse<boolean>> {
    return http.post(API_ENDPOINTS.VOICE_PARTICIPANT_STATUS(callId, userId), null, {
      params: { connectionStatus }
    })
  }

  /**
   * 检查通话权限
   */
  static async checkCallPermission(callId: number): Promise<ApiResponse<boolean>> {
    return http.get(API_ENDPOINTS.VOICE_PERMISSION(callId))
  }

  /**
   * 清理无效通话
   */
  static async cleanupInactiveCalls(): Promise<ApiResponse<number>> {
    return http.post(API_ENDPOINTS.VOICE_CLEANUP)
  }
}
