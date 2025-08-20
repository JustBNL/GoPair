import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import type { UserInfo, LoginRequest, RegisterRequest } from '@/types/api'
import type { FormMode } from '@/types/auth'
import { AuthAPI } from '@/api/auth'
import { Storage } from '@/utils/storage'

/**
 * 认证状态管理Store
 */
export const useAuthStore = defineStore('auth', () => {
  // ==================== 状态定义 ====================
  
  // 用户信息
  const user = ref<UserInfo | null>(null)
  const token = ref<string | null>(null)
  
  // 加载状态
  const loginLoading = ref(false)
  const registerLoading = ref(false)
  
  // 界面状态
  const currentMode = ref<FormMode>('login')
  
  // 用户偏好
  const rememberEmail = ref(false)
  const savedEmail = ref('')

  // ==================== 计算属性 ====================
  
  // 是否已登录
  const isLoggedIn = computed(() => !!(token.value && user.value))
  
  // 当前用户昵称
  const currentUserName = computed(() => user.value?.nickname || '用户')

  // ==================== 操作方法 ====================
  
  /**
   * 用户登录
   */
  async function login(loginData: LoginRequest): Promise<void> {
    loginLoading.value = true
    try {
      const response = await AuthAPI.login(loginData)
      
      // 保存用户信息和token
      user.value = response.data
      token.value = response.data.token
      
      // 持久化存储
      Storage.setUser(response.data)
      Storage.setToken(response.data.token)
      
      // 处理记住邮箱
      if (rememberEmail.value) {
        Storage.setSavedEmail(loginData.email)
        Storage.setRememberEmail(true)
      } else {
        Storage.removeSavedEmail()
        Storage.setRememberEmail(false)
      }
      
      message.success('登录成功')
    } catch (error) {
      console.error('登录失败:', error)
      throw error
    } finally {
      loginLoading.value = false
    }
  }

  /**
   * 用户注册
   */
  async function register(registerData: RegisterRequest): Promise<void> {
    registerLoading.value = true
    try {
      await AuthAPI.register(registerData)
      
      message.success('注册成功，请使用新账号登录')
      
      // 注册成功后切换到登录模式，并填充邮箱
      currentMode.value = 'login'
      savedEmail.value = registerData.email
    } catch (error) {
      console.error('注册失败:', error)
      throw error
    } finally {
      registerLoading.value = false
    }
  }

  /**
   * 用户退出登录
   */
  function logout(): void {
    // 清除状态
    user.value = null
    token.value = null
    
    // 清除存储
    Storage.clearAuth()
    
    message.success('退出登录成功')
  }

  /**
   * 切换登录/注册模式
   */
  function switchMode(mode: FormMode): void {
    currentMode.value = mode
  }

  /**
   * 初始化认证状态
   */
  function initAuth(): void {
    // 从本地存储恢复状态
    const storedToken = Storage.getToken()
    const storedUser = Storage.getUser()
    const storedRemember = Storage.getRememberEmail()
    const storedEmail = Storage.getSavedEmail()
    
    if (storedToken && storedUser) {
      token.value = storedToken
      user.value = storedUser
    }
    
    rememberEmail.value = storedRemember
    savedEmail.value = storedEmail
  }

  /**
   * 设置记住邮箱
   */
  function setRememberEmail(remember: boolean, email?: string): void {
    rememberEmail.value = remember
    if (remember && email) {
      savedEmail.value = email
      Storage.setSavedEmail(email)
    } else if (!remember) {
      savedEmail.value = ''
      Storage.removeSavedEmail()
    }
    Storage.setRememberEmail(remember)
  }

  /**
   * 获取保存的邮箱
   */
  function getSavedEmail(): string {
    return savedEmail.value
  }

  /**
   * 刷新用户信息
   */
  async function refreshUser(): Promise<void> {
    if (user.value?.userId) {
      try {
        const response = await AuthAPI.getCurrentUser(user.value.userId)
        user.value = response.data
        Storage.setUser(response.data)
      } catch (error) {
        console.error('刷新用户信息失败:', error)
        // 如果刷新失败，可能是token过期，执行登出
        logout()
      }
    }
  }

  // ==================== 返回 ====================
  
  return {
    // 状态
    user,
    token,
    loginLoading,
    registerLoading,
    currentMode,
    rememberEmail,
    savedEmail,
    
    // 计算属性
    isLoggedIn,
    currentUserName,
    
    // 方法
    login,
    register,
    logout,
    switchMode,
    initAuth,
    setRememberEmail,
    getSavedEmail,
    refreshUser
  }
}) 