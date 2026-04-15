import request from '@/utils/request'
import type { ApiResponse, PageInfo, VoiceCall, VoiceCallDetail, VoiceCallParticipant, CallQuery } from '@/types'

export const voiceCallApi = {
  getPage(params: CallQuery) {
    return request.get<ApiResponse<PageInfo>>('/voice-calls/page', { params })
  },
  getDetail(callId: number) {
    return request.get<ApiResponse<VoiceCallDetail>>(`/voice-calls/${callId}`)
  },
  getParticipants(callId: number) {
    return request.get<ApiResponse<VoiceCallParticipant[]>>(`/voice-calls/${callId}/participants`)
  },
}
