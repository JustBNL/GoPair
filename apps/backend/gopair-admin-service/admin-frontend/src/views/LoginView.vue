<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api/auth'

const router = useRouter()
const auth   = useAuthStore()

const loading = ref(false)
const form    = ref({ username: '', password: '' })

async function handleLogin() {
  if (!form.value.username.trim() || !form.value.password) {
    message.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const { data } = await authApi.login(form.value)
    auth.setAuth(data)
    message.success(`欢迎，${data.nickname}`)
    router.push('/dashboard')
  } catch (err: unknown) {
    const e = err as { message?: string }
    message.error(e?.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-page__visual">
      <div class="login-page__visual-inner">
        <svg class="login-page__logo" width="64" height="64" viewBox="0 0 64 64" fill="none">
          <circle cx="24" cy="32" r="10" fill="currentColor" opacity="0.85"/>
          <circle cx="40" cy="32" r="10" fill="currentColor" opacity="0.55"/>
          <circle cx="32" cy="22" r="8" fill="currentColor" opacity="0.70"/>
          <circle cx="32" cy="42" r="8" fill="currentColor" opacity="0.70"/>
        </svg>
        <h1 class="login-page__brand">GoPair</h1>
        <p class="login-page__tagline">管理员控制台</p>
        <div class="login-page__shapes">
          <div class="login-page__shape login-page__shape--1"></div>
          <div class="login-page__shape login-page__shape--2"></div>
          <div class="login-page__shape login-page__shape--3"></div>
        </div>
      </div>
    </div>

    <div class="login-page__form-area">
      <div class="login-page__form-card">
        <div class="login-page__form-header">
          <h2 class="login-page__form-title">登录</h2>
          <p class="login-page__form-subtitle">请输入管理员账号信息</p>
        </div>

        <a-form class="login-page__form" layout="vertical" @finish="handleLogin">
          <a-form-item label="用户名" name="username" :rules="[{ required: true, message: '请输入用户名' }]">
            <a-input v-model:value="form.username" placeholder="请输入用户名" size="large" autocomplete="username" />
          </a-form-item>

          <a-form-item label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }]">
            <a-input-password v-model:value="form.password" placeholder="请输入密码" size="large" autocomplete="current-password" />
          </a-form-item>

          <a-form-item>
            <a-button type="primary" html-type="submit" size="large" block :loading="loading">
              登录
            </a-button>
          </a-form-item>
        </a-form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
  background-color: var(--color-bg);
}

.login-page__visual {
  flex: 1;
  display: none;
  background-color: var(--color-sidebar-bg);
  position: relative;
  overflow: hidden;
}

@media (min-width: 900px) {
  .login-page__visual { display: flex; align-items: center; justify-content: center; }
}

.login-page__visual-inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-4);
  color: var(--color-sidebar-text);
}

.login-page__logo { color: var(--color-sidebar-text); opacity: 0.9; }

.login-page__brand {
  font-family: var(--font-display);
  font-size: 36px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--color-sidebar-text);
}

.login-page__tagline {
  font-size: 15px;
  color: var(--color-sidebar-text-secondary);
  letter-spacing: 0.05em;
}

.login-page__shapes { position: absolute; inset: 0; overflow: hidden; pointer-events: none; }

.login-page__shape {
  position: absolute;
  border-radius: 50%;
  background: linear-gradient(135deg, oklch(60% 0.12 195 / 0.3), transparent);
}

.login-page__shape--1 {
  width: 300px; height: 300px;
  top: -80px; left: -60px;
  background: radial-gradient(circle, oklch(60% 0.14 195 / 0.2), transparent 70%);
}
.login-page__shape--2 {
  width: 200px; height: 200px;
  bottom: 60px; right: -40px;
  background: radial-gradient(circle, oklch(60% 0.10 195 / 0.15), transparent 70%);
}
.login-page__shape--3 {
  width: 160px; height: 160px;
  bottom: 180px; left: 40px;
  background: radial-gradient(circle, oklch(55% 0.10 195 / 0.12), transparent 70%);
}

.login-page__form-area {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-6);
}

@media (min-width: 900px) {
  .login-page__form-area { max-width: 440px; }
}

.login-page__form-card { width: 100%; max-width: 380px; }

.login-page__form-header { margin-bottom: var(--space-6); }

.login-page__form-title {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text-primary);
  letter-spacing: -0.02em;
}

.login-page__form-subtitle {
  margin-top: var(--space-2);
  font-size: 14px;
  color: var(--color-text-muted);
}

.login-page__form :deep(.ant-input-large) { height: 44px; }
</style>
