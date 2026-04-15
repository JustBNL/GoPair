<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'

const route    = useRoute()
const router  = useRouter()
const auth    = useAuthStore()
const app     = useAppStore()

const menuItems = [
  { key: 'dashboard',    label: '仪表盘',  icon: 'dashboard' },
  { key: 'users',       label: '用户管理', icon: 'user' },
  { key: 'rooms',       label: '房间管理', icon: 'team' },
  { key: 'messages',    label: '消息管理', icon: 'message' },
  { key: 'files',       label: '文件管理', icon: 'file' },
  { key: 'audit-logs', label: '审计日志', icon: 'audit' },
  { key: 'voice-calls',label: '通话记录', icon: 'phone' },
]

const activeKey = computed(() => String(route.name))

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar__logo">
      <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
        <circle cx="10" cy="14" r="5" fill="currentColor" opacity="0.9"/>
        <circle cx="18" cy="14" r="5" fill="currentColor" opacity="0.6"/>
        <circle cx="14" cy="10" r="4" fill="currentColor" opacity="0.75"/>
        <circle cx="14" cy="18" r="4" fill="currentColor" opacity="0.75"/>
      </svg>
      <span class="sidebar__logo-text">GoPair</span>
    </div>

    <nav class="sidebar__nav">
      <RouterLink
        v-for="item in menuItems"
        :key="item.key"
        :to="{ name: item.key }"
        class="sidebar__nav-item"
        :class="{ 'sidebar__nav-item--active': activeKey === item.key }"
      >
        <span class="sidebar__nav-icon">
          <svg v-if="item.icon === 'dashboard'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg>
          <svg v-else-if="item.icon === 'user'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="7" r="4"/><path d="M5 21v-2a7 7 0 0 1 14 0v2"/></svg>
          <svg v-else-if="item.icon === 'team'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="9" cy="7" r="4"/><circle cx="17" cy="7" r="3"/><path d="M3 21v-2a5 5 0 0 1 8-3"/><path d="M13 21v-2a5 5 0 0 1 2-4"/></svg>
          <svg v-else-if="item.icon === 'message'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
          <svg v-else-if="item.icon === 'file'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
          <svg v-else-if="item.icon === 'audit'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
          <svg v-else-if="item.icon === 'phone'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.15 14a19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 3.06 3h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L7.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 21 16.92z"/></svg>
        </span>
        <span class="sidebar__nav-label">{{ item.label }}</span>
      </RouterLink>
    </nav>

    <div class="sidebar__footer">
      <button class="sidebar__theme-btn" @click="app.toggleTheme" :title="app.isDark ? '切换亮色' : '切换暗色'">
        <svg v-if="app.isDark" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>
        <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>
      </button>
      <div class="sidebar__user">
        <span class="sidebar__user-name">{{ auth.nickname || '管理员' }}</span>
        <button class="sidebar__logout" @click="handleLogout" title="退出登录">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
        </button>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  background-color: var(--color-sidebar-bg);
  color: var(--color-sidebar-text);
  padding: var(--space-4) 0;
  overflow: hidden;
}

.sidebar__logo {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-4);
  margin-bottom: var(--space-4);
  color: var(--color-sidebar-text);
}

.sidebar__logo-text {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.sidebar__nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: 0 var(--space-3);
  overflow-y: auto;
}

.sidebar__nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  color: oklch(70% 0.01 265);
  text-decoration: none;
  font-size: 14px;
  transition: background-color var(--transition-fast), color var(--transition-fast);
}

.sidebar__nav-item:hover {
  background-color: var(--color-sidebar-hover);
  color: var(--color-sidebar-text);
}

.sidebar__nav-item--active {
  background-color: var(--color-sidebar-active);
  color: var(--color-sidebar-text);
}

.sidebar__nav-icon {
  flex-shrink: 0;
  opacity: 0.7;
  display: flex;
  align-items: center;
}

.sidebar__nav-item--active .sidebar__nav-icon,
.sidebar__nav-item:hover .sidebar__nav-icon {
  opacity: 1;
}

.sidebar__footer {
  padding: var(--space-3);
  margin-top: auto;
  border-top: 1px solid oklch(40% 0.015 265 / 0.5);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.sidebar__theme-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: oklch(70% 0.01 265);
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: background-color var(--transition-fast), color var(--transition-fast);
  margin-left: auto;
}

.sidebar__theme-btn:hover {
  background-color: var(--color-sidebar-hover);
  color: var(--color-sidebar-text);
}

.sidebar__user {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-2) var(--space-3);
}

.sidebar__user-name {
  font-size: 13px;
  color: oklch(70% 0.01 265);
}

.sidebar__logout {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: oklch(70% 0.01 265);
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: background-color var(--transition-fast), color var(--transition-fast);
}

.sidebar__logout:hover {
  background-color: var(--color-sidebar-hover);
  color: var(--color-danger);
}
</style>
