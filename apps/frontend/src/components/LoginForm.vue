<template>
  <div class="login-form-container">
    <div class="form-header">
      <h2 class="form-title">欢迎</h2>
    </div>

    <a-tabs
      v-model:activeKey="activeTab"
      centered
      class="login-tabs"
      @change="handleTabChange"
    >
      <!-- 登录标签页 -->
      <a-tab-pane key="login" tab="登录">
        <a-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          layout="vertical"
          @finish="handleLogin"
          @finishFailed="handleLoginFailed"
        >
          <a-form-item name="email" label="邮箱地址">
            <a-input
              v-model:value="loginForm.email"
              size="large"
              placeholder="请输入邮箱地址"
              :prefix="h(UserOutlined)"
              autocomplete="email"
            />
          </a-form-item>

          <a-form-item name="password" label="密码">
            <a-input-password
              v-model:value="loginForm.password"
              size="large"
              placeholder="请输入密码"
              :prefix="h(LockOutlined)"
              autocomplete="current-password"
            />
          </a-form-item>

          <a-form-item>
            <div class="form-options">
              <a-checkbox v-model:checked="loginForm.remember">
                记住邮箱
              </a-checkbox>
            </div>
          </a-form-item>

          <a-form-item>
            <a-button
              type="primary"
              html-type="submit"
              size="large"
              block
              :loading="authStore.loginLoading"
            >
              登录
            </a-button>
          </a-form-item>
        </a-form>
      </a-tab-pane>

      <!-- 注册标签页 -->
      <a-tab-pane key="register" tab="注册">
        <a-form
          ref="registerFormRef"
          :model="registerForm"
          :rules="registerRules"
          layout="vertical"
          @finish="handleRegister"
          @finishFailed="handleRegisterFailed"
        >
          <Transition name="slide-down" appear>
            <a-form-item name="nickname" label="昵称">
              <a-input
                v-model:value="registerForm.nickname"
                size="large"
                placeholder="请输入昵称"
                :prefix="h(UserOutlined)"
                autocomplete="nickname"
              />
            </a-form-item>
          </Transition>

          <a-form-item name="email" label="邮箱地址">
            <a-input
              v-model:value="registerForm.email"
              size="large"
              placeholder="请输入邮箱地址"
              :prefix="h(MailOutlined)"
              autocomplete="email"
            />
          </a-form-item>

          <a-form-item name="code" label="邮箱验证码">
            <div class="code-input-row">
              <a-input
                v-model:value="registerForm.code"
                size="large"
                placeholder="请输入6位验证码"
                :prefix="h(SafetyOutlined)"
                maxlength="6"
                class="code-input"
              />
              <a-button
                size="large"
                :disabled="registerCodeCooldown > 0 || !registerForm.email"
                :loading="sendingRegisterCode"
                class="send-code-btn"
                @click="sendRegisterCode"
              >
                {{ registerCodeCooldown > 0 ? `${registerCodeCooldown}s后重发` : '获取验证码' }}
              </a-button>
            </div>
          </a-form-item>

          <a-form-item name="password" label="密码">
            <a-input-password
              v-model:value="registerForm.password"
              size="large"
              placeholder="请输入密码 (6-50个字符)"
              :prefix="h(LockOutlined)"
              autocomplete="new-password"
            />
          </a-form-item>

          <a-form-item>
            <a-button
              type="primary"
              html-type="submit"
              size="large"
              block
              :loading="authStore.registerLoading"
            >
              立即注册
            </a-button>
          </a-form-item>
        </a-form>
      </a-tab-pane>

      <!-- 忘记密码标签页 -->
      <a-tab-pane key="forgot" tab="忘记密码">
        <a-form
          ref="forgotFormRef"
          :model="forgotForm"
          :rules="forgotRules"
          layout="vertical"
          @finish="handleForgotPassword"
          @finishFailed="handleForgotFailed"
        >
          <a-form-item name="email" label="注册邮箱">
            <a-input
              v-model:value="forgotForm.email"
              size="large"
              placeholder="请输入注册时使用的邮箱"
              :prefix="h(MailOutlined)"
              autocomplete="email"
            />
          </a-form-item>

          <a-form-item name="code" label="邮箱验证码">
            <div class="code-input-row">
              <a-input
                v-model:value="forgotForm.code"
                size="large"
                placeholder="请输入6位验证码"
                :prefix="h(SafetyOutlined)"
                maxlength="6"
                :disabled="forgotCodeCooldown > 0 || !forgotForm.email"
                class="code-input"
              />
              <a-button
                size="large"
                :disabled="forgotCodeCooldown > 0 || !forgotForm.email"
                :loading="sendingForgotCode"
                class="send-code-btn"
                @click="sendForgotCode"
              >
                {{ forgotCodeCooldown > 0 ? `${forgotCodeCooldown}s后重发` : '获取验证码' }}
              </a-button>
            </div>
          </a-form-item>

          <a-form-item name="newPassword" label="新密码">
            <a-input-password
              v-model:value="forgotForm.newPassword"
              size="large"
              placeholder="请输入新密码 (6-50个字符)"
              :prefix="h(LockOutlined)"
              autocomplete="new-password"
            />
          </a-form-item>

          <a-form-item>
            <a-button
              type="primary"
              html-type="submit"
              size="large"
              block
            >
              重置密码
            </a-button>
          </a-form-item>
        </a-form>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import {
  UserOutlined,
  LockOutlined,
  MailOutlined,
  SafetyOutlined
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'
import type { LoginFormData, RegisterFormData, ForgotPasswordFormData } from '@/types/auth'

// ==================== 组件状态 ====================

const router = useRouter()
const authStore = useAuthStore()

// 表单引用
const loginFormRef = ref<FormInstance>()
const forgotFormRef = ref<FormInstance>()

// 当前活跃标签
const activeTab = ref('login')

// 登录表单数据
const loginForm = reactive<LoginFormData>({
  email: '',
  password: '',
  remember: false
})

// 注册表单数据
const registerForm = reactive<RegisterFormData>({
  nickname: '',
  email: '',
  password: '',
  code: ''
})

// 忘记密码表单数据
const forgotForm = reactive<ForgotPasswordFormData>({
  email: '',
  code: '',
  newPassword: ''
})

// 验证码发送状态
const sendingRegisterCode = ref(false)
const sendingForgotCode = ref(false)
const registerCodeCooldown = ref(0)
const forgotCodeCooldown = ref(0)

let registerTimer: ReturnType<typeof setInterval> | null = null
let forgotTimer: ReturnType<typeof setInterval> | null = null

// ==================== 表单验证规则 ====================

const loginRules = {
  email: [
    { required: true, message: '请输入邮箱地址' },
    { type: 'email', message: '请输入有效的邮箱地址' }
  ],
  password: [
    { required: true, message: '请输入密码' }
  ]
}

const registerRules = {
  nickname: [
    { required: true, message: '请输入昵称' },
    { min: 1, max: 20, message: '昵称长度必须在1-20个字符之间' }
  ],
  email: [
    { required: true, message: '请输入邮箱地址' },
    { type: 'email', message: '请输入有效的邮箱地址' }
  ],
  code: [
    { required: true, message: '请输入邮箱验证码' },
    { len: 6, message: '验证码为6位数字' }
  ],
  password: [
    { required: true, message: '请输入密码' },
    { min: 6, max: 50, message: '密码长度必须在6-50个字符之间' }
  ]
}

const forgotRules = {
  email: [
    { required: true, message: '请输入注册邮箱' },
    { type: 'email', message: '请输入有效的邮箱地址' }
  ],
  code: [
    { required: true, message: '请输入邮箱验证码' },
    { len: 6, message: '验证码为6位数字' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码' },
    { min: 6, max: 50, message: '密码长度必须在6-50个字符之间' }
  ]
}

// ==================== 验证码倒计时 ====================

function startCooldown(type: 'register' | 'forgot') {
  const cooldown = type === 'register' ? registerCodeCooldown : forgotCodeCooldown
  const timer = type === 'register' ? registerTimer : forgotTimer
  if (timer) clearInterval(timer)
  cooldown.value = 60
  const interval = setInterval(() => {
    cooldown.value--
    if (cooldown.value <= 0) clearInterval(interval)
  }, 1000)
  if (type === 'register') registerTimer = interval
  else forgotTimer = interval
}

async function sendRegisterCode() {
  if (!registerForm.email) {
    message.warning('请先输入邮箱地址')
    return
  }
  sendingRegisterCode.value = true
  try {
    await authStore.sendVerificationCode(registerForm.email, 'register')
    startCooldown('register')
  } catch {
    // 错误已在 store/request 层处理
  } finally {
    sendingRegisterCode.value = false
  }
}

async function sendForgotCode() {
  if (!forgotForm.email) {
    message.warning('请先输入邮箱地址')
    return
  }
  sendingForgotCode.value = true
  try {
    await authStore.sendVerificationCode(forgotForm.email, 'resetPassword')
    startCooldown('forgot')
  } catch {
    // 错误已在 store/request 层处理
  } finally {
    sendingForgotCode.value = false
  }
}

// ==================== 事件处理 ====================

/**
 * Tab切换处理
 */
function handleTabChange(key: string) {
  authStore.switchMode(key as 'login' | 'register')

  // 切换时保留邮箱信息
  if (key === 'register' && loginForm.email) {
    registerForm.email = loginForm.email
  } else if (key === 'login' && registerForm.email) {
    loginForm.email = registerForm.email
  } else if (key === 'forgot' && loginForm.email) {
    forgotForm.email = loginForm.email
  }
}

/**
 * 登录提交处理
 */
async function handleLogin(values: LoginFormData) {
  try {
    await authStore.login({
      email: values.email,
      password: values.password
    })
    authStore.setRememberEmail(values.remember || false, values.email)
    router.push('/rooms')
  } catch (error) {
    console.error('登录失败:', error)
  }
}

function handleLoginFailed(errorInfo: unknown) {
  console.error('登录表单验证失败:', errorInfo)
  message.error('请检查输入信息')
}

/**
 * 注册提交处理
 */
async function handleRegister(values: RegisterFormData) {
  try {
    await authStore.register({
      nickname: values.nickname,
      email: values.email,
      password: values.password,
      code: values.code
    })
    activeTab.value = 'login'
    loginForm.email = values.email
    loginForm.password = ''
    registerFormRef.value?.resetFields()
  } catch (error) {
    console.error('注册失败:', error)
  }
}

function handleRegisterFailed(errorInfo: unknown) {
  console.error('注册表单验证失败:', errorInfo)
  message.error('请检查输入信息')
}

/**
 * 忘记密码提交处理
 */
async function handleForgotPassword(values: ForgotPasswordFormData) {
  try {
    await authStore.forgotPassword({
      email: values.email,
      code: values.code,
      newPassword: values.newPassword
    })
    activeTab.value = 'login'
    loginForm.email = values.email
    loginForm.password = ''
    forgotFormRef.value?.resetFields()
  } catch (error) {
    console.error('重置密码失败:', error)
  }
}

function handleForgotFailed(errorInfo: unknown) {
  console.error('忘记密码表单验证失败:', errorInfo)
  message.error('请检查输入信息')
}

// ==================== 生命周期 ====================

onMounted(() => {
  authStore.initAuth()
  const savedEmail = authStore.getSavedEmail()
  if (savedEmail) {
    loginForm.email = savedEmail
    loginForm.remember = authStore.rememberEmail
  }
})
</script>

<style scoped>
.login-form-container {
  width: 100%;
  max-width: 100%;
  margin: 0 auto;
}

.form-header {
  text-align: center;
  margin-bottom: 32px;
}

.form-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.login-tabs {
  margin-bottom: 24px;
}

.login-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 32px;
}

.login-tabs :deep(.ant-tabs-tab) {
  font-size: 16px;
  font-weight: 500;
  padding: 12px 24px;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.code-input-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.code-input {
  flex: 1;
}

.send-code-btn {
  flex-shrink: 0;
  min-width: 110px;
}

.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.3s ease;
}

.slide-down-enter-from {
  opacity: 0;
  transform: translateY(-20px);
}

.slide-down-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

@media not ((min-aspect-ratio: 6/5) and (min-width: 1024px)) {
  .login-form-container {
    max-width: 100%;
    padding: 0;
  }

  .form-header {
    margin-bottom: 0px;
    margin-top: 8px;
  }

  .form-title {
    font-size: 22px;
    margin-bottom: 0px;
  }

  .login-tabs :deep(.ant-tabs-nav) {
    margin-bottom: 24px;
  }

  .login-tabs :deep(.ant-tabs-tab) {
    font-size: 14px;
    padding: 10px 16px;
  }

  .login-tabs :deep(.ant-form-item) {
    margin-bottom: 12px;
  }

  .send-code-btn {
    min-width: 90px;
    font-size: 13px;
  }
}
</style>
