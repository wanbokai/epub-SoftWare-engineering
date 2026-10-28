import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import { saveTokens, clearTokens, getAccessToken } from '@/api/http'
import type { LoginRequest, RegisterRequest } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const userId = ref<number | null>(null)
  const username = ref<string>('')

  const isLoggedIn = computed(() => !!getAccessToken())

  function setSession(id: number, name: string) {
    userId.value = id
    username.value = name
  }

  async function login(data: LoginRequest) {
    const auth = await authApi.login(data)
    saveTokens(auth)
    setSession(auth.userId, auth.username)
    return auth
  }

  async function register(data: RegisterRequest) {
    const auth = await authApi.register(data)
    saveTokens(auth)
    setSession(auth.userId, auth.username)
    return auth
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      // 忽略登出接口错误，本地态仍需清理。
    }
    clearTokens()
    userId.value = null
    username.value = ''
  }

  return { userId, username, isLoggedIn, login, register, logout, setSession }
})
