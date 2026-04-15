import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const LoginView         = () => import('@/views/LoginView.vue')
const DashboardView     = () => import('@/views/DashboardView.vue')
const UserManageView    = () => import('@/views/users/UserManageView.vue')
const RoomManageView    = () => import('@/views/rooms/RoomManageView.vue')
const MessageManageView  = () => import('@/views/messages/MessageManageView.vue')
const FileManageView    = () => import('@/views/files/FileManageView.vue')
const AuditLogView      = () => import('@/views/auditLogs/AuditLogView.vue')
const VoiceCallView     = () => import('@/views/voiceCalls/VoiceCallView.vue')

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { requiresAuth: false },
    },
    {
      path: '/',
      component: () => import('@/components/layout/AppLayout.vue'),
      redirect: '/dashboard',
      meta: { requiresAuth: true },
      children: [
        { path: 'dashboard',    name: 'dashboard',    component: DashboardView,    meta: { title: '仪表盘',  icon: 'dashboard' } },
        { path: 'users',        name: 'users',         component: UserManageView,   meta: { title: '用户管理', icon: 'user' } },
        { path: 'rooms',        name: 'rooms',         component: RoomManageView,   meta: { title: '房间管理', icon: 'team' } },
        { path: 'messages',     name: 'messages',       component: MessageManageView,meta: { title: '消息管理', icon: 'message' } },
        { path: 'files',        name: 'files',          component: FileManageView,   meta: { title: '文件管理', icon: 'file' } },
        { path: 'audit-logs',  name: 'audit-logs',    component: AuditLogView,    meta: { title: '审计日志', icon: 'audit' } },
        { path: 'voice-calls', name: 'voice-calls',   component: VoiceCallView,    meta: { title: '通话记录', icon: 'phone' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

/* 路由守卫 */
router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()
  const requiresAuth = to.matched.some(r => r.meta.requiresAuth !== false)
  if (requiresAuth && !auth.isLoggedIn) {
    next('/login')
  } else if (to.name === 'login' && auth.isLoggedIn) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
