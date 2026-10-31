import { request } from './http'
import type {
  AddBookRequest,
  BookshelfItem,
  UpdateBookRequest,
} from '@/types/api'

/** 书架排序：最近阅读 / 导入时间 / 书名。 */
export type BookshelfSort = 'lastRead' | 'created' | 'title'

export const bookshelfApi = {
  add(data: AddBookRequest) {
    return request<BookshelfItem>({ url: '/bookshelf', method: 'POST', data })
  },
  list(keyword?: string, sort?: BookshelfSort) {
    const params: Record<string, string> = {}
    if (keyword) params.keyword = keyword
    if (sort) params.sort = sort
    return request<BookshelfItem[]>({
      url: '/bookshelf',
      method: 'GET',
      params: Object.keys(params).length > 0 ? params : undefined,
    })
  },
  rename(itemId: number, data: UpdateBookRequest) {
    return request<BookshelfItem>({
      url: `/bookshelf/${itemId}`,
      method: 'PUT',
      data,
    })
  },
  /**
   * 移除书架条目。
   *
   * @param removeCloudCopy true=同时删除云端文件副本（引用计数归零时回收）；false=仅移出个人书架
   */
  remove(itemId: number, removeCloudCopy = false) {
    return request<void>({
      url: `/bookshelf/${itemId}`,
      method: 'DELETE',
      params: { removeCloudCopy },
    })
  },
}
