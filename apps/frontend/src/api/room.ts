import { http } from '@/utils/request'
import type { ApiResponse, BaseQuery, PageResult } from '@/types/api'
import type { 
  RoomInfo, 
  CreateRoomRequest, 
  JoinRoomRequest, 
  RoomMember 
} from '@/types/room'
import { API_ENDPOINTS } from './index'

/**
 * 房间API服务
 */
export class RoomAPI {
  /**
   * 创建房间
   * @param roomData 房间数据
   * @returns 创建的房间信息
   */
  static async createRoom(roomData: CreateRoomRequest): Promise<ApiResponse<RoomInfo>> {
    return http.post<RoomInfo>(API_ENDPOINTS.ROOM_CREATE, roomData)
  }

  /**
   * 加入房间
   * @param joinData 加入房间数据
   * @returns 房间信息
   */
  static async joinRoom(joinData: JoinRoomRequest): Promise<ApiResponse<RoomInfo>> {
    return http.post<RoomInfo>(API_ENDPOINTS.ROOM_JOIN, joinData)
  }

  /**
   * 获取用户房间列表
   * @param query 分页查询参数
   * @returns 用户房间列表
   */
  static async getUserRooms(query: BaseQuery = {}): Promise<ApiResponse<PageResult<RoomInfo>>> {
    const url = `${API_ENDPOINTS.ROOM_LIST}?${new URLSearchParams(query as Record<string, string>).toString()}`
    return http.get<PageResult<RoomInfo>>(url)
  }

  /**
   * 根据房间码查询房间信息
   * @param roomCode 房间码
   * @returns 房间信息
   */
  static async getRoomByCode(roomCode: string): Promise<ApiResponse<RoomInfo>> {
    return http.get<RoomInfo>(API_ENDPOINTS.ROOM_BY_CODE(roomCode))
  }

  /**
   * 获取房间成员列表
   * @param roomId 房间ID
   * @returns 房间成员列表
   */
  static async getRoomMembers(roomId: number): Promise<ApiResponse<RoomMember[]>> {
    return http.get<RoomMember[]>(API_ENDPOINTS.ROOM_MEMBERS(roomId))
  }

  /**
   * 离开房间
   * @param roomId 房间ID
   * @returns 操作结果
   */
  static async leaveRoom(roomId: number): Promise<ApiResponse<boolean>> {
    return http.post<boolean>(API_ENDPOINTS.ROOM_LEAVE(roomId))
  }

  /**
   * 关闭房间（仅房主）
   * @param roomId 房间ID
   * @returns 操作结果
   */
  static async closeRoom(roomId: number): Promise<ApiResponse<boolean>> {
    return http.post<boolean>(API_ENDPOINTS.ROOM_CLOSE(roomId))
  }
}

/**
 * 导出便捷方法
 */
export const {
  createRoom,
  joinRoom,
  getUserRooms,
  getRoomByCode,
  getRoomMembers,
  leaveRoom,
  closeRoom
} = RoomAPI 