import request from '@/utils/request'
import { API_ENDPOINTS } from './index'
import type {
  ApiResponse,
  CallVO,
  CallInitiateDto,
  CallParticipantVO,
  CallType,
  ConnectionStatus
} from '@/types/api'

/**
 * 语音通话相关API
 */
export class VoiceAPI {

  /**
   * 发起通话
   */
  static async initiateCall(dto: CallInitiateDto): Promise<ApiResponse<CallVO>> {
    return request.post(API_ENDPOINTS.VOICE_INITIATE, dto)
  }

  /**
   * 加入通话
   */
  static async joinCall(callId: number): Promise<ApiResponse<boolean>> {
    return request.post(API_ENDPOINTS.VOICE_JOIN(callId))
  }

  /**
   * 离开通话
   */
  static async leaveCall(callId: number): Promise<ApiResponse<boolean>> {
    return request.post(API_ENDPOINTS.VOICE_LEAVE(callId))
  }

  /**
   * 结束通话
   */
  static async endCall(callId: number): Promise<ApiResponse<boolean>> {
    return request.post(API_ENDPOINTS.VOICE_END(callId))
  }

  /**
   * 获取通话信息
   */
  static async getCall(callId: number): Promise<ApiResponse<CallVO>> {
    return request.get(API_ENDPOINTS.VOICE_GET(callId))
  }

  /**
   * 获取房间当前活跃通话
   */
  static async getActiveCall(roomId: number): Promise<ApiResponse<CallVO>> {
    return request.get(API_ENDPOINTS.VOICE_ROOM_ACTIVE(roomId))
  }

  /**
   * 获取房间通话历史
   */
  static async getRoomCallHistory(
    roomId: number, 
    limit: number = 10
  ): Promise<ApiResponse<CallVO[]>> {
    return request.get(API_ENDPOINTS.VOICE_ROOM_HISTORY(roomId), {
      params: { limit }
    })
  }

  /**
   * 获取通话参与者列表
   */
  static async getCallParticipants(callId: number): Promise<ApiResponse<CallParticipantVO[]>> {
    return request.get(API_ENDPOINTS.VOICE_PARTICIPANTS(callId))
  }

  /**
   * 更新参与者连接状态
   */
  static async updateParticipantStatus(
    callId: number, 
    userId: number, 
    connectionStatus: ConnectionStatus
  ): Promise<ApiResponse<boolean>> {
    return request.post(API_ENDPOINTS.VOICE_PARTICIPANT_STATUS(callId, userId), null, {
      params: { connectionStatus }
    })
  }

  /**
   * 检查通话权限
   */
  static async checkCallPermission(callId: number): Promise<ApiResponse<boolean>> {
    return request.get(API_ENDPOINTS.VOICE_PERMISSION(callId))
  }

  /**
   * 清理无效通话
   */
  static async cleanupInactiveCalls(): Promise<ApiResponse<number>> {
    return request.post(API_ENDPOINTS.VOICE_CLEANUP)
  }

  /**
   * 静音/取消静音
   */
  static async toggleMute(callId: number, muted: boolean): Promise<ApiResponse<boolean>> {
    return request.post(`/voice/${callId}/mute`, { muted })
  }

  /**
   * 获取通话统计信息
   */
  static async getCallStats(callId: number): Promise<ApiResponse<{
    duration: number
    participantCount: number
    maxParticipants: number
    averageDuration: number
  }>> {
    return request.get(`/voice/${callId}/stats`)
  }

  /**
   * 获取房间通话统计
   */
  static async getRoomCallStats(roomId: number): Promise<ApiResponse<{
    totalCalls: number
    totalDuration: number
    averageCallDuration: number
    participantStats: Array<{
      userId: number
      nickname: string
      callCount: number
      totalDuration: number
    }>
  }>> {
    return request.get(`/voice/room/${roomId}/stats`)
  }

  /**
   * 邀请用户加入通话
   */
  static async inviteToCall(callId: number, userIds: number[]): Promise<ApiResponse<boolean>> {
    return request.post(`/voice/${callId}/invite`, { userIds })
  }

  /**
   * 踢出用户
   */
  static async kickUser(callId: number, userId: number): Promise<ApiResponse<boolean>> {
    return request.post(`/voice/${callId}/kick`, { userId })
  }

  /**
   * 设置通话质量
   */
  static async setCallQuality(callId: number, quality: 'low' | 'medium' | 'high'): Promise<ApiResponse<boolean>> {
    return request.post(`/voice/${callId}/quality`, { quality })
  }

  /**
   * 获取网络质量检测
   */
  static async getNetworkQuality(): Promise<ApiResponse<{
    latency: number
    bandwidth: number
    quality: 'poor' | 'fair' | 'good' | 'excellent'
    recommendations: string[]
  }>> {
    return request.get('/voice/network-quality')
  }

  /**
   * 测试音频设备
   */
  static async testAudioDevice(): Promise<ApiResponse<{
    microphone: boolean
    speaker: boolean
    recommendations: string[]
  }>> {
    return request.post('/voice/test-audio')
  }

  /**
   * 获取STUN/TURN服务器配置
   */
  static async getIceServers(): Promise<ApiResponse<{
    iceServers: Array<{
      urls: string[]
      username?: string
      credential?: string
    }>
  }>> {
    return request.get('/voice/ice-servers')
  }

  /**
   * 报告通话问题
   */
  static async reportCallIssue(callId: number, issue: {
    type: 'audio' | 'connection' | 'quality' | 'other'
    description: string
    severity: 'low' | 'medium' | 'high'
  }): Promise<ApiResponse<boolean>> {
    return request.post(`/voice/${callId}/report`, issue)
  }

  /**
   * 获取通话录音（如果启用）
   */
  static async getCallRecording(callId: number): Promise<ApiResponse<{
    recordingUrl: string
    duration: number
    size: number
    format: string
  }>> {
    return request.get(`/voice/${callId}/recording`)
  }

  /**
   * 开始录音
   */
  static async startRecording(callId: number): Promise<ApiResponse<boolean>> {
    return request.post(`/voice/${callId}/recording/start`)
  }

  /**
   * 停止录音
   */
  static async stopRecording(callId: number): Promise<ApiResponse<boolean>> {
    return request.post(`/voice/${callId}/recording/stop`)
  }
} 