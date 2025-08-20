import { http } from '@/utils/request'
import type { ApiResponse, UserInfo, LoginRequest, RegisterRequest } from '@/types/api'
import { API_ENDPOINTS } from './index'

/**
 * 认证API服务
 */
export class AuthAPI {
  /**
   * 用户登录
   * @param loginData 登录数据
   * @returns 用户信息和token
   */
  static async login(loginData: LoginRequest): Promise<ApiResponse<UserInfo>> {
    return http.post<UserInfo>(API_ENDPOINTS.LOGIN, loginData)
  }

  /**
   * 用户注册
   * @param registerData 注册数据
   * @returns 注册结果
   */
  static async register(registerData: RegisterRequest): Promise<ApiResponse<boolean>> {
    return http.post<boolean>(API_ENDPOINTS.REGISTER, registerData)
  }

  /**
   * 获取当前用户信息
   * @param userId 用户ID
   * @returns 用户信息
   */
  static async getCurrentUser(userId: number): Promise<ApiResponse<UserInfo>> {
    return http.get<UserInfo>(API_ENDPOINTS.GET_USER(userId))
  }

  /**
   * 更新用户信息
   * @param userData 用户数据
   * @returns 更新结果
   */
  static async updateUser(userData: Partial<UserInfo>): Promise<ApiResponse<boolean>> {
    return http.put<boolean>(API_ENDPOINTS.UPDATE_USER, userData)
  }

  /**
   * 删除用户
   * @param userId 用户ID
   * @returns 删除结果
   */
  static async deleteUser(userId: number): Promise<ApiResponse<boolean>> {
    return http.delete<boolean>(API_ENDPOINTS.DELETE_USER(userId))
  }
}

/**
 * 导出便捷方法
 */
export const {
  login,
  register,
  getCurrentUser,
  updateUser,
  deleteUser
} = AuthAPI 