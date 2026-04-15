import request from '@/utils/request'
import type { ApiResponse, PageInfo, AuditLog, AuditQuery } from '@/types'

export const auditLogApi = {
  getPage(params: AuditQuery) {
    return request.get<ApiResponse<PageInfo>>('/audit-logs/page', { params })
  },
}
