import { request } from './http'
import type {
  AuthResponse,
  LoginRequest,
  RefreshRequest,
  RegisterRequest,
} from '@/types/api'

export const authApi = {
  register(data: RegisterRequest) {
    return request<AuthResponse>({ url: '/auth/register', method: 'POST', data })
  },
  login(data: LoginRequest) {
    return request<AuthResponse>({ url: '/auth/login', method: 'POST', data })
  },
  refresh(data: RefreshRequest) {
    return request<AuthResponse>({ url: '/auth/refresh', method: 'POST', data })
  },
  logout() {
    return request<void>({ url: '/auth/logout', method: 'POST' })
  },
}
