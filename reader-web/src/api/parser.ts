import { request } from './http'
import type { Book, BookMeta } from '@/types/api'

export const parserApi = {
  /** 解析完整书对象。 */
  parse(fileId: number) {
    return request<Book>({ url: `/parser/${fileId}`, method: 'GET' })
  },
  /** 仅抽取元数据。 */
  meta(fileId: number) {
    return request<BookMeta>({ url: `/parser/${fileId}/meta`, method: 'GET' })
  },
}
