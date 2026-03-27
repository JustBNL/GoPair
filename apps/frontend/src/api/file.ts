 import request, { http } from '@/utils/request'
import { API_ENDPOINTS } from './index'
import type {
  ApiResponse,
  PageResult,
  FileVO,
  FileUploadDto
} from '@/types/api'

/**
 * 文件上传进度回调类型
 */
export interface UploadProgressCallback {
  (progressEvent: { loaded: number; total?: number; progress?: number }): void
}

/**
 * 房间文件统计信息
 */
export interface RoomFileStats {
  fileCount: number
  totalSize: number
  totalSizeFormatted: string
}

/**
 * 文件相关API
 */
export class FileAPI {

  /**
   * 上传用户头像
   */
  static async uploadAvatar(file: File): Promise<ApiResponse<string>> {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<string>(API_ENDPOINTS.FILE_AVATAR, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    } as any)
  }

  /**
   * 上传文件
   */
  static async uploadFile(
    dto: FileUploadDto,
    onProgress?: UploadProgressCallback
  ): Promise<ApiResponse<FileVO>> {
    const formData = new FormData()
    formData.append('file', dto.file)
    formData.append('roomId', dto.roomId.toString())
    
    if (dto.description) {
      formData.append('description', dto.description)
    }
    
    if (dto.overwrite !== undefined) {
      formData.append('overwrite', dto.overwrite.toString())
    }

    return http.post(API_ENDPOINTS.FILE_UPLOAD, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      },
      onUploadProgress: onProgress
    } as any)
  }

  /**
   * 获取房间文件列表
   */
  static async getRoomFiles(
    roomId: number,
    pageNum: number = 1,
    pageSize: number = 20
  ): Promise<ApiResponse<PageResult<FileVO>>> {
    return http.get(API_ENDPOINTS.FILE_ROOM_LIST(roomId), {
      params: { pageNum, pageSize }
    } as any)
  }

  /**
   * 根据ID获取文件信息
   */
  static async getFileInfo(fileId: number): Promise<ApiResponse<FileVO>> {
    return request.get(API_ENDPOINTS.FILE_INFO(fileId))
  }

  /**
   * 下载文件
   */
  static async downloadFile(fileId: number): Promise<ApiResponse<string>> {
    return http.get(API_ENDPOINTS.FILE_DOWNLOAD(fileId))
  }

  /**
   * 获取文件下载URL
   */
  static getDownloadUrl(fileId: number): string {
    return `${API_ENDPOINTS.FILE_DOWNLOAD(fileId)}`
  }

  /**
   * 预览文件
   */
  static async previewFile(fileId: number): Promise<Blob> {
    const response = await request.get(API_ENDPOINTS.FILE_PREVIEW(fileId), {
      responseType: 'blob'
    })
    return response.data
  }

  /**
   * 获取文件预览URL
   */
  static getPreviewUrl(fileId: number): string {
    return `${API_ENDPOINTS.FILE_PREVIEW(fileId)}`
  }

  /**
   * 删除文件
   */
  static async deleteFile(fileId: number): Promise<ApiResponse<boolean>> {
    return request.delete(API_ENDPOINTS.FILE_DELETE(fileId))
  }

  /**
   * 获取房间文件统计信息
   */
  static async getRoomFileStats(roomId: number): Promise<ApiResponse<RoomFileStats>> {
    return http.get<RoomFileStats>(API_ENDPOINTS.FILE_STATS(roomId))
  }

  /**
   * 清理房间文件
   */
  static async cleanupRoomFiles(roomId: number): Promise<ApiResponse<number>> {
    return request.post(API_ENDPOINTS.FILE_CLEANUP(roomId))
  }

  /**
   * 批量删除文件
   */
  static async batchDeleteFiles(fileIds: number[]): Promise<ApiResponse<boolean>> {
    return request.post('/file/batch-delete', { fileIds })
  }

  /**
   * 批量下载文件
   */
  static async batchDownloadFiles(fileIds: number[]): Promise<Blob> {
    const response = await request.post('/file/batch-download',
      { fileIds },
      { responseType: 'blob' }
    )
    return response.data
  }

  /**
   * 检查文件是否存在
   */
  static async checkFileExists(fileName: string, roomId: number): Promise<ApiResponse<boolean>> {
    return request.get('/file/exists', {
      params: { fileName, roomId }
    })
  }

  /**
   * 获取支持的文件类型
   */
  static async getSupportedFileTypes(): Promise<ApiResponse<string[]>> {
    return request.get('/file/supported-types')
  }

  /**
   * 获取文件上传限制
   */
  static async getUploadLimits(): Promise<ApiResponse<{
    maxFileSize: number
    maxRoomSize: number
    allowedTypes: string[]
  }>> {
    return request.get('/file/upload-limits')
  }

  /**
   * 创建文件分享链接
   */
  static async createShareLink(fileId: number, expireHours?: number): Promise<ApiResponse<{
    shareUrl: string
    expireTime: string
  }>> {
    return request.post(`/file/${fileId}/share`, {
      expireHours: expireHours || 24
    })
  }

  /**
   * 通过分享链接访问文件
   */
  static async accessSharedFile(shareToken: string): Promise<ApiResponse<FileVO>> {
    return request.get(`/file/share/${shareToken}`)
  }

  /**
   * 文件搜索
   */
  static async searchFiles(
    roomId: number,
    keyword: string,
    fileType?: string,
    pageNum: number = 1,
    pageSize: number = 20
  ): Promise<ApiResponse<PageResult<FileVO>>> {
    return request.get(`/file/room/${roomId}/search`, {
      params: { keyword, fileType, pageNum, pageSize }
    })
  }
}
