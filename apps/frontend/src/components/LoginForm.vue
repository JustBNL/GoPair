<template>
  <div class="login-form-container">
    <div class="form-header">
      <h2 class="form-title">欢迎来到 GoPair</h2>
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
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import { 
  UserOutlined, 
  LockOutlined, 
  MailOutlined 
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'
import type { LoginFormData, RegisterFormData } from '@/types/auth'

// ==================== 组件状态 ====================

const authStore = useAuthStore()

// 表单引用
const loginFormRef = ref<FormInstance>()
const registerFormRef = ref<FormInstance>()

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
  password: ''
})

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
  password: [
    { required: true, message: '请输入密码' },
    { min: 6, max: 50, message: '密码长度必须在6-50个字符之间' }
  ]
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
    
    // 处理记住邮箱
    authStore.setRememberEmail(values.remember || false, values.email)
    
    // 登录成功后的跳转逻辑可以在路由守卫中处理
  } catch (error) {
    console.error('登录失败:', error)
  }
}

/**
 * 登录失败处理
 */
function handleLoginFailed(errorInfo: any) {
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
      password: values.password
    })
    
    // 注册成功后自动切换到登录页面，并填充邮箱
    activeTab.value = 'login'
    loginForm.email = values.email
    loginForm.password = ''
    
    // 清空注册表单
    registerFormRef.value?.resetFields()
  } catch (error) {
    console.error('注册失败:', error)
  }
}

/**
 * 注册失败处理
 */
function handleRegisterFailed(errorInfo: any) {
  console.error('注册表单验证失败:', errorInfo)
  message.error('请检查输入信息')
}

// ==================== 生命周期 ====================

onMounted(() => {
  // 初始化认证状态
  authStore.initAuth()
  
  // 如果有保存的邮箱，自动填充
  const savedEmail = authStore.getSavedEmail()
  if (savedEmail) {
    loginForm.email = savedEmail
    loginForm.remember = authStore.rememberEmail
  }
})
</script>

<style scoped>
/* ==================== 表单容器布局 ==================== */

/* 登录表单主容器：居中布局，自适应宽度 */
.login-form-container {
  width: 100%;
  max-width: 100%;
  margin: 0 auto;
}

/* ==================== 表单头部样式 ==================== */

/* 表单头部：标题和副标题区域，居中对齐 */
.form-header {
  text-align: center;
  margin-bottom: 32px;
}

/* 表单主标题：大字体，深色，强调品牌 */
.form-title {
  font-size: 28px;
  font-weight: 600;
  color: #1a202c;
  margin-bottom: 8px;
}

/* 表单副标题：中等字体，灰色，说明功能 */
.form-subtitle {
  font-size: 16px;
  color: #718096;
  margin: 0;
}

/* ==================== 标签页样式 ==================== */

/* 登录标签页容器：登录/注册切换 */
.login-tabs {
  margin-bottom: 24px;
}

/* 标签页导航区域：增加底部间距 */
.login-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 32px;
}

/* 单个标签页：字体大小，字重，内边距 */
.login-tabs :deep(.ant-tabs-tab) {
  font-size: 16px;
  font-weight: 500;
  padding: 12px 24px;
}

/* ==================== 表单选项区域 ==================== */

/* 表单选项容器：记住密码等功能的布局 */
.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* ==================== 过渡动画效果 ==================== */

/* 滑动下拉动画：注册时昵称字段的显示/隐藏效果 */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.3s ease;
}

/* 滑动下拉进入状态：从上方20px位置淡入 */
.slide-down-enter-from {
  opacity: 0;
  transform: translateY(-20px);
}

/* 滑动下拉离开状态：向上方20px位置淡出 */
.slide-down-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

/* ==================== 两段式响应式布局 ==================== */

/* 精简竖屏模式：当不满足宽屏条件时 */
@media not ((min-aspect-ratio: 6/5) and (min-width: 1024px)) {
  /* 表单容器：紧凑布局，适配小屏 */
  .login-form-container {
    max-width: 100%;
    padding: 0;
  }

  /* 精简模式表头：减小边距 */
  .form-header {
    margin-bottom: 0px;
    margin-top: 8px;
  }
  
  /* 精简模式标题：减小字体，突出简洁性 */
  .form-title {
    font-size: 22px;
    margin-bottom: 0px;
  }
  
  /* 精简模式副标题：保持可读性的小字体 */
  .form-subtitle {
    font-size: 14px;
  }
  
  /* 精简模式标签页导航：紧凑间距 */
  .login-tabs :deep(.ant-tabs-nav) {
    margin-bottom: 24px;
  }
  
  /* 精简模式标签页：小字体，紧凑内边距，适合触摸 */
  .login-tabs :deep(.ant-tabs-tab) {
    font-size: 14px;
    padding: 10px 20px;
  }
  
  /* 精简模式表单项：减少底部间距，紧凑布局 */
  .login-tabs :deep(.ant-form-item) {
    margin-bottom: 12px;
 }
}
</style> 