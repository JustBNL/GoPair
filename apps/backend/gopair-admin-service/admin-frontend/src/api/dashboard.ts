import request from '@/utils/request'
import type { DashboardStats, DashboardTrends, RecentRoom, ApiResponse } from '@/types'

export const dashboardApi = {
  getStats() {
    return request.get<ApiResponse<DashboardStats>>('/dashboard/stats')
  },
  getTrends() {
    return request.get<ApiResponse<DashboardTrends>>('/dashboard/trends')
  },
  getRecentRooms(limit: number = 5) {
    return request.get<ApiResponse<RecentRoom[]>>('/dashboard/recent-rooms', { params: { limit } })
  },
}
