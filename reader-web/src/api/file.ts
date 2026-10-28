import { request } from './http'
import type { FileAsset, UploadCheckResponse } from '@/types/api'

export const fileApi = {
  /** 秒传预检：上传前先校验 SHA-256 是否已存在。 */
  check(sha256: string) {
    return request<UploadCheckResponse>({
      url: '/files/check',
      method: 'POST',
      params: { sha256 },
    })
  },
  /** 上传文件（multipart/form-data）。 */
  upload(sha256: string, fileType: number, file: File) {
    const form = new FormData()
    form.append('sha256', sha256)
    form.append('fileType', String(fileType))
    form.append('file', file)
    return request<FileAsset>({
      url: '/files/upload',
      method: 'POST',
      data: form,
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}
