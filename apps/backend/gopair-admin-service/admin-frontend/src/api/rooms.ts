import request from '@/utils/request'
import type { ApiResponse, PageInfo, Room, RoomDetail, RoomQuery } from '@/types'

export const roomApi = {
  getPage(params: RoomQuery) {
    return request.get<ApiResponse<PageInfo>>('/rooms/page', { params })
  },
  getDetail(roomId: number) {
    return request.get<ApiResponse<RoomDetail>>(`/rooms/${roomId}`)
  },
  close(roomId: number) {
    return request.post<ApiResponse<null>>(`/rooms/${roomId}/close`)
  },
}
