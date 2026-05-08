import { http } from '@/utils/request'
import type { ApiResponse, BaseQuery, PageResult } from '@/types/api'
import type {
  RoomInfo,
  CreateRoomRequest,
  JoinRoomRequest,
  RoomMember,
  UpdateRoomPasswordRequest
} from '@/types/room'
import { API_ENDPOINTS } from './index'

/**
 * 房间API服务
 */
export class RoomAPI {
  static async createRoom(roomData: CreateRoomRequest): Promise<ApiResponse<RoomInfo>> {
    return http.post<RoomInfo>(API_ENDPOINTS.ROOM_CREATE, roomData)
  }

  static async joinRoomAsync(joinData: JoinRoomRequest): Promise<ApiResponse<{ joinToken: string; message: string }>> {
    return http.post<{ joinToken: string; message: string }>(API_ENDPOINTS.ROOM_JOIN_ASYNC, joinData)
  }

  static async getJoinResult(token: string): Promise<ApiResponse<{ status: 'JOINED' | 'PROCESSING' | 'FAILED'; roomId?: number; userId?: number; message?: string }>> {
    const url = `${API_ENDPOINTS.ROOM_JOIN_RESULT}?token=${encodeURIComponent(token)}`
    return http.get<{ status: 'JOINED' | 'PROCESSING' | 'FAILED'; roomId?: number; userId?: number; message?: string }>(url)
  }

  static async getUserRooms(query: BaseQuery = {}): Promise<ApiResponse<PageResult<RoomInfo>>> {
    const url = `${API_ENDPOINTS.ROOM_LIST}?${new URLSearchParams(query as Record<string, string>).toString()}`
    return http.get<PageResult<RoomInfo>>(url)
  }

  static async getRoomByCode(roomCode: string): Promise<ApiResponse<RoomInfo>> {
    return http.get<RoomInfo>(API_ENDPOINTS.ROOM_BY_CODE(roomCode))
  }

  static async getRoomMembers(roomId: number): Promise<ApiResponse<RoomMember[]>> {
    return http.get<RoomMember[]>(API_ENDPOINTS.ROOM_MEMBERS(roomId))
  }

  static async leaveRoom(roomId: number): Promise<ApiResponse<boolean>> {
    return http.post<boolean>(API_ENDPOINTS.ROOM_LEAVE(roomId))
  }

  static async closeRoom(roomId: number): Promise<ApiResponse<boolean>> {
    return http.post<boolean>(API_ENDPOINTS.ROOM_CLOSE(roomId))
  }

  static async updateRoomPassword(roomId: number, data: UpdateRoomPasswordRequest): Promise<ApiResponse<void>> {
    return http.patch<void>(API_ENDPOINTS.ROOM_UPDATE_PASSWORD(roomId), data)
  }

  static async updatePasswordVisibility(roomId: number, visible: number): Promise<ApiResponse<void>> {
    return http.patch<void>(API_ENDPOINTS.ROOM_UPDATE_PASSWORD_VISIBILITY(roomId), { visible })
  }

  static async getRoomCurrentPassword(roomId: number): Promise<ApiResponse<RoomInfo>> {
    return http.get<RoomInfo>(API_ENDPOINTS.ROOM_CURRENT_PASSWORD(roomId))
  }

  static async kickMember(roomId: number, userId: number): Promise<ApiResponse<null>> {
    return http.delete<null>(API_ENDPOINTS.ROOM_KICK_MEMBER(roomId, userId))
  }

  static async renewRoom(roomId: number, extendHours: number): Promise<ApiResponse<RoomInfo>> {
    return http.post<RoomInfo>(API_ENDPOINTS.ROOM_RENEW(roomId), { extendHours })
  }

  static async reopenRoom(roomId: number, expireHours: number): Promise<ApiResponse<RoomInfo>> {
    return http.post<RoomInfo>(API_ENDPOINTS.ROOM_REOPEN(roomId), { expireHours })
  }
}

export const {
  createRoom,
  joinRoomAsync,
  getJoinResult,
  getUserRooms,
  getRoomByCode,
  getRoomMembers,
  leaveRoom,
  closeRoom,
  updateRoomPassword,
  updatePasswordVisibility,
  getRoomCurrentPassword,
  kickMember,
  renewRoom,
  reopenRoom
} = RoomAPI
