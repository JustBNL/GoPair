import request from '@/utils/request'
import type { ApiResponse, PageInfo, User, UserDetail, UserQuery } from '@/types'

export const userApi = {
  getPage(params: UserQuery) {
    return request.get<ApiResponse<PageInfo>>('/users/page', { params })
  },
  getDetail(userId: number) {
    return request.get<ApiResponse<UserDetail>>(`/users/${userId}`)
  },
  disable(userId: number) {
    return request.post<ApiResponse<null>>(`/users/${userId}/disable`)
  },
  enable(userId: number) {
    return request.post<ApiResponse<null>>(`/users/${userId}/enable`)
  },
  migrateEmail(userId: number, newEmail: string) {
    return request.post<ApiResponse<null>>(`/users/${userId}/migrate-email`, null, {
      params: { newEmail },
    })
  },
}
