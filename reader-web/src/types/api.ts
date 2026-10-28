/**
 * 统一响应结构（与后端 com.reader.common.ApiResponse 对齐）。
 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/**
 * 认证响应（注册/登录/刷新统一返回）。
 */
export interface AuthResponse {
  accessToken: string
  refreshToken: string
  userId: number
  username: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RefreshRequest {
  refreshToken: string
}

/**
 * 物理文件资源实体（file_asset 表）。
 */
export interface FileAsset {
  id: number
  sha256: string
  storageKey: string
  fileType: number // 1=EPUB, 2=TXT
  fileSize: number
  metaTitle: string
  metaAuthor: string
  refCount: number
  createdAt: string
}

/**
 * 秒传预检响应。
 */
export interface UploadCheckResponse {
  exists: boolean
  sha256: string
  fileId: number
  metaTitle: string
  metaAuthor: string
}

/**
 * 用户书架条目（bookshelf_item 表 + 关联文件元信息）。
 */
export interface BookshelfItem {
  id: number
  userId: number
  fileId: number
  displayTitle: string
  progressCfi: string
  progressPercent: number
  lastReadAt: string
  syncVersion: number
  createdAt: string
  fileType: number
  fileSize: number
  metaTitle: string
  metaAuthor: string
}

export interface AddBookRequest {
  fileId: number
  displayTitle: string
}

export interface UpdateBookRequest {
  displayTitle: string
}

/**
 * 排版配置（reader_config 表）。
 */
export interface ReaderConfig {
  id: number
  userId: number
  theme: number // 1日间/2夜间/3羊皮纸/4护眼
  fontSize: number
  lineHeight: number
  pageMargin: number // 1窄/2中/3宽
  pageMode: number // 1分页/2流式
  syncVersion: number
  updatedAt: string
}

export interface PushProgressRequest {
  cfi: string
  percent: number
  syncVersion: number
}

export interface PushConfigRequest {
  theme: number
  fontSize: number
  lineHeight: number
  pageMargin: number
  pageMode: number
  syncVersion: number
}

export interface ResolveRequest {
  choice: string // cloud / local / lww
  cfi?: string
  percent?: number
}

export interface ProgressSummary {
  cfi: string
  percent: number
}

export interface ConflictResponse {
  cloud: ProgressSummary
  local: ProgressSummary
  cloudVersion: number
  localVersion: number
}

export interface SyncPullResponse {
  progressList: BookshelfItem[]
  config: ReaderConfig
}

/**
 * 统一书对象（与后端 parser.Book 对齐）。
 */
export interface BookMeta {
  title: string
  author: string
  cover: string
}

export interface Chapter {
  id: string
  title: string
  content: string
}

export interface TocItem {
  title: string
  href: string
  level: number
  children: TocItem[]
}

export interface Book {
  meta: BookMeta
  spine: string[]
  chapters: Chapter[]
  toc: TocItem[]
}
