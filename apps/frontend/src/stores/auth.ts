import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import type { UserInfo, LoginRequest, RegisterRequest, LoginResponse, RegisterResponse, CurrentUser, SendCodeRequest, ForgotPasswordRequest } from '@/types/api'
import type { FormMode } from '@/types/auth'
import { AuthAPI } from '@/api/auth'
import { Storage } from '@/utils/storage'
import { useWebSocketStore } from './websocket'
import { WS_FEATURES } from '@/config/websocket'

/**
 * 认证状态管理Store
 */
export const useAuthStore = defineStore('auth', () => {
  // ==================== 状态定义 ====================
  
  // 用户信息
  const user = ref<CurrentUser | null>(null)
  const token = ref<string | null>(null)
  
  // 加载状态
  const loginLoading = ref(false)
  const registerLoading = ref(false)
  
  // 界面状态
  const currentMode = ref<FormMode>('login')
  
  // 用户偏好
  const rememberEmail = ref(false)
  const savedEmail = ref('')
  
  // 初始化状态锁
  const isInitialized = ref(false)

  // ==================== 计算属性 ====================
  
  // 是否已登录
  const isLoggedIn = computed(() => !!(token.value && user.value))
  
  // 当前用户昵称
  const currentNickname = computed(() => user.value?.nickname || '用户')

  // ==================== 操作方法 ====================
  
  /**
   * 用户登录
   */
  async function login(loginData: LoginRequest): Promise<void> {
    loginLoading.value = true
    
    try {
      const response = await AuthAPI.login(loginData)
      
      // 确保响应数据完整
      if (!response.data || !response.data.token) {
        throw new Error('登录响应数据不完整')
      }
      
      // 构建当前用户对象（登录响应已包含完整数据，无需额外请求）
      const currentUser: CurrentUser = {
        userId: response.data.userId,
        nickname: response.data.nickname,
        token: response.data.token,
        email: response.data.email || '',
        avatar: response.data.avatar || ''
      }

      // 先持久化存储，再更新内存状态
      Storage.setToken(response.data.token)
      Storage.setUser(currentUser)
      
      // 为WebSocket认证设置Cookie（网关需要从Cookie中读取JWT）
      Storage.setCookieToken(response.data.token)
      
      // 然后更新内存状态
      token.value = response.data.token
      user.value = currentUser
      
      // 处理记住邮箱
      if (rememberEmail.value) {
        Storage.setSavedEmail(loginData.email)
        Storage.setRememberEmail(true)
      } else {
        Storage.removeSavedEmail()
        Storage.setRememberEmail(false)
      }
      
      // 登录成功后是否建立全局WebSocket连接（由开关控制）
      if (WS_FEATURES.enableGlobal) {
        try {
          const wsStore = useWebSocketStore()
          await wsStore.connectGlobal(currentUser.userId)
          if (WS_FEATURES.debug) console.log('✅ WebSocket全局连接建立成功')
        } catch (error) {
          if (WS_FEATURES.debug) console.error('❌ WebSocket全局连接建立失败:', error)
          // WebSocket连接失败不影响登录成功，只记录错误
        }
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
   * 用户注册（注册成功后自动登录）
   */
  async function register(registerData: RegisterRequest): Promise<void> {
    registerLoading.value = true
    try {
      await AuthAPI.register(registerData)
      // 注册成功后直接用相同凭据自动登录
      await login({
        email: registerData.email,
        password: registerData.password
      })
    } catch (error) {
      console.error('注册失败:', error)
      throw error
    } finally {
      registerLoading.value = false
    }
  }

  /**
   * 发送邮箱验证码
   */
  async function sendVerificationCode(email: string, type: 'register' | 'resetPassword'): Promise<void> {
    await AuthAPI.sendVerificationCode({ email, type })
    message.success('验证码已发送，请查收邮件')
  }

  /**
   * 忘记密码（验证码重置）
   */
  async function forgotPassword(data: ForgotPasswordRequest): Promise<void> {
    await AuthAPI.forgotPassword(data)
    message.success('密码重置成功，请使用新密码登录')
  }

  /**
   * 用户退出登录
   */
  /**
   * 用户退出登录
   */
  async function logout(): Promise<void> {
    // 退出前尝试离开活跃通话（fire and forget，不阻塞退出流程）
    try {
      const { VoiceAPI } = await import('@/api/voice')
      const roomMatch = window.location.pathname.match(/\/rooms\/(\d+)/)
      if (roomMatch) {
        const res = await VoiceAPI.getActiveCall(Number(roomMatch[1]))
        if (res.data?.callId) {
          await VoiceAPI.leaveCall(res.data.callId)
        }
      }
    } catch {
      // ignore
    }

    // 断开WebSocket连接
    try {
      const wsStore = useWebSocketStore()
      wsStore.disconnectGlobal()
      if (WS_FEATURES.debug) console.log('🔌 WebSocket全局连接已断开')
    } catch (error) {
      if (WS_FEATURES.debug) console.error('❌ 断开WebSocket连接失败:', error)
    }

    user.value = null
    token.value = null
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
    // 防止重复初始化
    if (isInitialized.value) {
      if (WS_FEATURES.debug) console.log('🔒 Auth already initialized, skipping')
      return
    }
    
    if (WS_FEATURES.debug) console.log('🔄 Starting auth initialization...')
    
    // 从本地存储恢复状态
    const storedToken = Storage.getToken()
    const storedUser = Storage.getUser()
    const storedRemember = Storage.getRememberEmail()
    const storedEmail = Storage.getSavedEmail()
    
    if (WS_FEATURES.debug) console.log('📦 Storage data:', {
      hasToken: !!storedToken,
      hasUser: !!storedUser,
      remember: storedRemember,
      savedEmail: storedEmail
    })
    
    if (storedToken && storedUser) {
      token.value = storedToken
      user.value = storedUser
      
      // 恢复Cookie，确保WebSocket认证正常
      Storage.setCookieToken(storedToken)
      
      if (WS_FEATURES.debug) console.log('✅ Auth state restored from storage')
      
      // 根据开关决定是否建立全局WebSocket连接
      if (WS_FEATURES.enableGlobal) {
        setTimeout(async () => {
          try {
            const wsStore = useWebSocketStore()
            await wsStore.connectGlobal(storedUser.userId)
            if (WS_FEATURES.debug) console.log('✅ WebSocket全局连接建立成功（从存储恢复）')
          } catch (error) {
            if (WS_FEATURES.debug) console.error('❌ WebSocket全局连接建立失败（从存储恢复）:', error)
          }
        }, 100)
      }
    } else {
      if (WS_FEATURES.debug) console.log('ℹ️ No valid auth data in storage')
    }
    
    rememberEmail.value = storedRemember
    savedEmail.value = storedEmail
    
    // 标记为已初始化
    isInitialized.value = true
    if (WS_FEATURES.debug) console.log('🏁 Auth initialization complete')
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
   * 注销账号（将账号状态设为已注销并退出登录）
   */
  async function cancelAccount(): Promise<void> {
    if (!user.value?.userId) throw new Error('未登录')
    await AuthAPI.cancelAccount(user.value.userId)
    // 注销成功后执行退出登录流程，清理所有本地状态
    await logout()
    message.success('账号已注销')
  }

  /**
   * 更新用户资料（昵称、邮箱、密码、头像）
   */
  async function updateProfile(data: { nickname?: string; email?: string; password?: string; avatar?: string }): Promise<void> {
    if (!user.value?.userId) throw new Error('未登录')
    await AuthAPI.updateUser({ userId: user.value.userId, ...data })
    // 同步更新本地用户信息（nickname、email、avatar）
    if (user.value) {
      const updatedUser = { ...user.value }
      if (data.nickname) updatedUser.nickname = data.nickname
      if (data.email) updatedUser.email = data.email
      if (data.avatar !== undefined) updatedUser.avatar = data.avatar
      user.value = updatedUser
      Storage.setUser(updatedUser)
    }
    message.success('资料更新成功')
  }

  /**
   * 刷新用户信息
   */
  async function refreshUser(): Promise<void> {
    if (user.value?.userId) {
      try {
        const response = await AuthAPI.getCurrentUser(user.value.userId)
        // 将完整的UserInfo转换为CurrentUser，保留当前token
        const updatedUser: CurrentUser = {
          userId: response.data.userId,
          nickname: response.data.nickname,
          token: user.value.token,
          email: response.data.email,
          avatar: response.data.avatar
        }
        user.value = updatedUser
        Storage.setUser(updatedUser)
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
    isInitialized,
    
    // 计算属性
    isLoggedIn,
    currentNickname,
    
    // 方法
    login,
    register,
    sendVerificationCode,
    forgotPassword,
    logout,
    switchMode,
    initAuth,
    setRememberEmail,
    getSavedEmail,
    refreshUser,
    updateProfile,
    cancelAccount
  }
})