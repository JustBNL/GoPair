import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import LoginView from '@/views/LoginView.vue'

/**
 * 路由元信息类型扩展
 */
declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    requiresGuest?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: {
        title: '登录 - GoPair',
        requiresGuest: true
      }
    },
    {
      path: '/rooms',
      name: 'rooms',
      component: () => import('@/views/RoomsView.vue'),
      meta: {
        title: '房间管理 - GoPair',
        requiresAuth: true
      }
    },
    {
      path: '/rooms/:roomId',
      name: 'roomDetail',
      component: () => import('@/views/RoomDetailView.vue'),
      meta: {
        title: '房间详情 - GoPair',
        requiresAuth: true
      }
    },
    {
      path: '/',
      name: 'home',
      redirect: (to) => {
        // 动态重定向逻辑：已登录用户进入房间管理页，未登录用户进入登录页
        const authStore = useAuthStore()
        const decision = authStore.isLoggedIn ? '/rooms' : '/login'
        
        console.group('🏠 ROOT REDIRECT DECISION')
        console.log('Auth State:', {
          user: authStore.user?.nickname || null,
          token: authStore.token ? '***' + authStore.token.slice(-6) : null,
          isLoggedIn: authStore.isLoggedIn
        })
        console.log('Decision:', decision)
        console.groupEnd()
        
        return decision
      }
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'notFound',
      redirect: '/'
    }
  ]
})

// 标准路由守卫 - Vue Router官方推荐模式
router.beforeEach((to, from, next) => {
  console.group(`🧭 NAVIGATION: ${from.path} → ${to.path}`)
  
  const authStore = useAuthStore()
  
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title as string
  }
  
  // 初始化认证状态（仅在首次加载时，不在 logout 后重复触发）
  if (!authStore.isInitialized) {
    console.log('🔄 Initializing auth state...')
    authStore.initAuth()
    console.log('✅ Auth state initialized')
  }
  
  // 检查登录状态
  const isLoggedIn = authStore.isLoggedIn
  
  console.log('📊 Auth Status Check:', {
    user: authStore.user?.nickname || null,
    token: authStore.token ? '***' + authStore.token.slice(-6) : null,
    isLoggedIn: isLoggedIn,
    requiresAuth: to.meta.requiresAuth,
    requiresGuest: to.meta.requiresGuest
  })
  
  // 已登录用户访问访客页面，重定向到房间管理页
  if (to.meta.requiresGuest && isLoggedIn) {
    console.log('🔀 Redirect: Logged user accessing guest page → /rooms')
    console.groupEnd()
    next('/rooms')
    return
  }
  
  // 未登录用户访问需要认证的页面，重定向到登录页
  if (to.meta.requiresAuth && !isLoggedIn) {
    console.log('🔀 Redirect: Unauthenticated user accessing protected page → /login')
    console.groupEnd()
    next('/login')
    return
  }
  
  // 正常继续导航
  console.log('✅ Navigation PASS')
  console.groupEnd()
  next()
})

export default router
