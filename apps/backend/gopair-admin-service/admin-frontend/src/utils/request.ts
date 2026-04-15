import axios from 'axios'
import type { AxiosInstance, AxiosError, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

/* 基础 URL 指向后端 admin 服务 */
const BASE_URL = 'http://localhost:8088/admin'

const request: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
})

/* 请求拦截器：注入 JWT Token */
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const auth = useAuthStore()
    if (auth.token) {
      config.headers.set('Authorization', `Bearer ${auth.token}`)
    }
    return config
  },
  (error: AxiosError) => Promise.reject(error),
)

/* 响应拦截器：统一处理错误 */
request.interceptors.response.use(
  (response) => {
    const data = response.data as { code: number; msg: string }
    if (data.code === 401) {
      const auth = useAuthStore()
      auth.logout()
      router.push('/login')
      return Promise.reject(new Error(data.msg))
    }
    if (data.code !== 200) {
      return Promise.reject(new Error(data.msg))
    }
    return response
  },
  (error: AxiosError) => {
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        const auth = useAuthStore()
        auth.logout()
        router.push('/login')
      }
    }
    return Promise.reject(error)
  },
)

export default request
