import request from '@/utils/request'
import type { ApiResponse, PageInfo, Message, MessageQuery } from '@/types'

export const messageApi = {
  getPage(params: MessageQuery) {
    return request.get<ApiResponse<PageInfo>>('/messages/page', { params })
  },
  getByRoom(roomId: number, pageNum = 1, pageSize = 50) {
    return request.get<ApiResponse<PageInfo>>(`/messages/room/${roomId}`, {
      params: { pageNum, pageSize },
    })
  },
}
