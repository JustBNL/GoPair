import request from '@/utils/request'
import type { LoginRequest, LoginResponse, ApiResponse } from '@/types'

export const authApi = {
  login(data: LoginRequest) {
    return request.post<ApiResponse<LoginResponse>>(
      '/auth/login',
      new URLSearchParams(data as Record<string, string>).toString(),
    )
  },
}
