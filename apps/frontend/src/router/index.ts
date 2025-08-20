import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import LoginView from '@/views/LoginView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: {
        title: '登录 - GoPair',
        requiresGuest: true // 仅未登录用户可访问
      }
    },
    {
      path: '/',
      name: 'home',
      redirect: '/login' // 暂时重定向到登录页
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'notFound',
      redirect: '/login'
    }
  ]
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title as string
  }
  
  // 初始化认证状态
  authStore.initAuth()
  
  // 检查登录状态
  const isLoggedIn = authStore.isLoggedIn
  
  // 如果已登录但访问登录页，重定向到首页
  if (to.meta.requiresGuest && isLoggedIn) {
    next('/')
    return
  }
  
  // 如果未登录但访问需要认证的页面，重定向到登录页
  if (to.meta.requiresAuth && !isLoggedIn) {
    next('/login')
    return
  }
  
  next()
})

export default router
