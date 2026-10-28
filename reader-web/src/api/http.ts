import axios, { type AxiosInstance, type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import type { ApiResponse, AuthResponse } from '@/types/api'

const TOKEN_KEY = 'reader_access_token'
const REFRESH_KEY = 'reader_refresh_token'

/**
 * 后端 API 基础实例。开发环境经 Vite 代理转发到 8080。
 */
const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// 请求拦截器：自动携带 access token。
http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：统一解包，401 时尝试用 refresh token 换新。
let refreshing: Promise<string | null> | null = null

async function tryRefresh(): Promise<string | null> {
  const refreshToken = localStorage.getItem(REFRESH_KEY)
  if (!refreshToken) return null
  try {
    const resp = await axios.post<ApiResponse<AuthResponse>>('/api/auth/refresh', {
      refreshToken,
    })
    if (resp.data.code === 0) {
      saveTokens(resp.data.data)
      return resp.data.data.accessToken
    }
    return null
  } catch {
    return null
  }
}

http.interceptors.response.use(
  (resp: AxiosResponse) => resp,
  async (error) => {
    const original = error.config as InternalAxiosRequestConfig & { _retried?: boolean }
    if (error.response?.status === 401 && !original._retried) {
      original._retried = true
      if (!refreshing) {
        refreshing = tryRefresh().finally(() => {
          refreshing = null
        })
      }
      const newToken = await refreshing
      if (newToken) {
        original.headers.Authorization = `Bearer ${newToken}`
        return http(original)
      }
      // 刷新失败，清空登录态。
      clearTokens()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export function saveTokens(auth: AuthResponse) {
  localStorage.setItem(TOKEN_KEY, auth.accessToken)
  localStorage.setItem(REFRESH_KEY, auth.refreshToken)
}

export function clearTokens() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
}

export function getAccessToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/**
 * 统一的请求封装，解包 ApiResponse 并做业务码校验。
 */
export async function request<T>(config: Parameters<AxiosInstance['request']>[0]): Promise<T> {
  const resp = await http.request<ApiResponse<T>>(config)
  const body = resp.data
  if (body.code !== 0) {
    const err = new Error(body.message || '请求失败') as Error & { code?: number }
    ;(err as { code?: number }).code = body.code
    throw err
  }
  return body.data
}

export default http
