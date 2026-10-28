import { request } from './http'
import type {
  ConflictResponse,
  PushConfigRequest,
  PushProgressRequest,
  ResolveRequest,
  SyncPullResponse,
} from '@/types/api'

export const syncApi = {
  pushProgress(itemId: number, data: PushProgressRequest) {
    return request<number>({
      url: `/v1/sync/progress/${itemId}`,
      method: 'PUT',
      data,
    })
  },
  pushConfig(data: PushConfigRequest) {
    return request<number>({ url: '/v1/sync/config', method: 'PUT', data })
  },
  pull() {
    return request<SyncPullResponse>({ url: '/v1/sync/pull', method: 'GET' })
  },
  resolve(itemId: number, data: ResolveRequest) {
    return request<number>({
      url: `/v1/sync/resolve/${itemId}`,
      method: 'POST',
      data,
    })
  },
}

export type { ConflictResponse }
