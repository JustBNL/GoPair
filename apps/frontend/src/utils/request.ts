import axios, { type AxiosInstance, type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import { message } from 'ant-design-vue'
import type { ApiResponse } from '@/types/api'
import { ERROR_MESSAGES, ERROR_CODES } from '@/types/api'
import { TOKEN_KEY } from '@/types/auth'

/**
 * 创建axios实例
 */
const request: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
})

/**
 * 请求拦截器
 */
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 自动添加token
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    console.error('请求拦截器错误:', error)
    return Promise.reject(error)
  }
)

/**
 * 响应拦截器
 */
request.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const { data } = response
    
    // 检查业务状态码
    if (data.code === ERROR_CODES.SUCCESS) {
      return response
    }
    
    // 处理业务错误
    const errorMessage = ERROR_MESSAGES[data.code] || data.msg || '操作失败'
    
    // 特殊错误处理
    if (data.code === ERROR_CODES.TOKEN_EXPIRED || data.code === ERROR_CODES.TOKEN_INVALID) {
      // Token过期，清除本地存储并跳转到登录页
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('gopair_user')
      message.error('登录已过期，请重新登录')
      window.location.href = '/login'
      return Promise.reject(new Error(errorMessage))
    }
    
    // 显示错误消息
    message.error(errorMessage)
    return Promise.reject(new Error(errorMessage))
  },
  (error) => {
    console.error('响应拦截器错误:', error)
    
    // 网络错误处理
    if (!error.response) {
      message.error('网络连接失败，请检查网络设置')
      return Promise.reject(error)
    }
    
    const { status } = error.response
    
    switch (status) {
      case 401:
        localStorage.removeItem(TOKEN_KEY)
        localStorage.removeItem('gopair_user')
        message.error('未授权访问，请重新登录')
        window.location.href = '/login'
        break
      case 403:
        message.error('访问被拒绝')
        break
      case 404:
        message.error('请求的资源不存在')
        break
      case 500:
        message.error('服务器内部错误')
        break
      default:
        message.error('请求失败，请稍后重试')
    }
    
    return Promise.reject(error)
  }
)

/**
 * 通用请求方法
 */
export const http = {
  get<T = any>(url: string, config?: InternalAxiosRequestConfig): Promise<ApiResponse<T>> {
    return request.get(url, config).then(res => res.data)
  },
  
  post<T = any>(url: string, data?: any, config?: InternalAxiosRequestConfig): Promise<ApiResponse<T>> {
    return request.post(url, data, config).then(res => res.data)
  },
  
  put<T = any>(url: string, data?: any, config?: InternalAxiosRequestConfig): Promise<ApiResponse<T>> {
    return request.put(url, data, config).then(res => res.data)
  },
  
  delete<T = any>(url: string, config?: InternalAxiosRequestConfig): Promise<ApiResponse<T>> {
    return request.delete(url, config).then(res => res.data)
  }
}

export default request 