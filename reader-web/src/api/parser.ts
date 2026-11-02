import { request } from './http'
import type { Book, BookMeta, Chapter } from '@/types/api'

export const parserApi = {
  /**
   * 取书对象：只含元数据、目录和章节标题，不含正文。
   * 大书首次解析仍可能较慢，单独放宽到 60 秒。
   */
  parse(fileId: number) {
    return request<Book>({ url: `/parser/${fileId}`, method: 'GET', timeout: 60000 })
  },
  /** 按章节下标按需加载正文。 */
  chapter(fileId: number, index: number) {
    return request<Chapter>({
      url: `/parser/${fileId}/chapter/${index}`,
      method: 'GET',
      timeout: 30000,
    })
  },
  /** 仅抽取元数据。 */
  meta(fileId: number) {
    return request<BookMeta>({ url: `/parser/${fileId}/meta`, method: 'GET' })
  },
}
