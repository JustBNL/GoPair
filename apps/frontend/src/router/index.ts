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
        title: '登录 - 聊天室',
        requiresGuest: true
      }
    },
    {
      path: '/rooms',
      name: 'rooms',
      component: () => import('@/views/RoomsView.vue'),
      meta: {
        title: '房间管理 - 聊天室',
        requiresAuth: true
      }
    },
    {
      path: '/rooms/:roomId',
      name: 'roomDetail',
      component: () => import('@/views/RoomDetailView.vue'),
      meta: {
        title: '房间详情 - 聊天室',
        requiresAuth: true
      }
    },
    {
      path: '/',
      name: 'home',
      redirect: (to) => {
        const authStore = useAuthStore()
        return authStore.isLoggedIn ? '/rooms' : '/login'
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
  const authStore = useAuthStore()
  
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title as string
  }
  
  // 初始化认证状态（仅在首次加载时，不在 logout 后重复触发）
  if (!authStore.isInitialized) {
    authStore.initAuth()
  }
  
  // 检查登录状态
  const isLoggedIn = authStore.isLoggedIn
  
  // 已登录用户访问访客页面，重定向到房间管理页
  if (to.meta.requiresGuest && isLoggedIn) {
    next('/rooms')
    return
  }
  
  // 未登录用户访问需要认证的页面，重定向到登录页
  if (to.meta.requiresAuth && !isLoggedIn) {
    next('/login')
    return
  }
  
  // 正常继续导航
  next()
})

export default router
