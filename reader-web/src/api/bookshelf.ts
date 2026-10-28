import { request } from './http'
import type {
  AddBookRequest,
  BookshelfItem,
  UpdateBookRequest,
} from '@/types/api'

export const bookshelfApi = {
  add(data: AddBookRequest) {
    return request<BookshelfItem>({ url: '/bookshelf', method: 'POST', data })
  },
  list(keyword?: string) {
    return request<BookshelfItem[]>({
      url: '/bookshelf',
      method: 'GET',
      params: keyword ? { keyword } : undefined,
    })
  },
  rename(itemId: number, data: UpdateBookRequest) {
    return request<BookshelfItem>({
      url: `/bookshelf/${itemId}`,
      method: 'PUT',
      data,
    })
  },
  remove(itemId: number) {
    return request<void>({ url: `/bookshelf/${itemId}`, method: 'DELETE' })
  },
}
