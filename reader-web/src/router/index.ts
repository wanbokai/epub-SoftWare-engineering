import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getAccessToken } from '@/api/http'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    name: 'bookshelf',
    component: () => import('@/views/BookshelfView.vue'),
  },
  {
    path: '/reader/:itemId',
    name: 'reader',
    component: () => import('@/views/ReaderView.vue'),
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('@/views/SettingsView.vue'),
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 全局路由守卫：未登录访问受保护页时跳转登录。
router.beforeEach((to) => {
  const authed = !!getAccessToken()
  if (!to.meta.public && !authed) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.public && authed && (to.name === 'login' || to.name === 'register')) {
    return { name: 'bookshelf' }
  }
  return true
})

export default router
