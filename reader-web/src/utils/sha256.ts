/**
 * 计算文件的 SHA-256 指纹（十六进制小写，64 位），与后端 FileService.sha256 对齐。
 * 使用 Web Crypto API 的 SubtleCrypto.digest。
 */
export async function sha256Hex(blob: Blob | ArrayBuffer): Promise<string> {
  const buffer =
    blob instanceof ArrayBuffer ? blob : await blob.arrayBuffer()
  const digest = await crypto.subtle.digest('SHA-256', buffer)
  const bytes = new Uint8Array(digest)
  let hex = ''
  for (const b of bytes) {
    hex += b.toString(16).padStart(2, '0')
  }
  return hex
}
