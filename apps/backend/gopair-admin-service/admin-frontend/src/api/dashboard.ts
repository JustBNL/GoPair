import request from '@/utils/request'
import type { DashboardStats, ApiResponse } from '@/types'

export const dashboardApi = {
  getStats() {
    return request.get<ApiResponse<DashboardStats>>('/dashboard/stats')
  },
}
