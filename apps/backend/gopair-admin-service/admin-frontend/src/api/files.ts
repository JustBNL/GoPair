import request from '@/utils/request'
import type { ApiResponse, PageInfo, RoomFile, FileDetail, FileQuery } from '@/types'

export const fileApi = {
  getPage(params: FileQuery) {
    return request.get<ApiResponse<PageInfo>>('/files/page', { params })
  },
  getDetail(fileId: number) {
    return request.get<ApiResponse<FileDetail>>(`/files/${fileId}`)
  },
  deleteFile(fileId: number) {
    return request.post<ApiResponse<null>>(`/files/${fileId}/delete`)
  },
}
