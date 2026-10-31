/**
 * 本地 IndexedDB 存储。
 *
 * - `books`：按 fileId 缓存已解析的 Book，二次打开秒开，并支持离线阅读已打开过的书；
 * - `pending`：离线/网络失败时暂存阅读进度，恢复网络后自动补传（同一本书只保留最新一条）。
 *
 * 浏览器禁用 IndexedDB（如隐私模式）时所有方法静默降级，不影响在线阅读。
 */
import type { Book } from '@/types/api'

interface CachedBook {
  fileId: number
  book: Book
  cachedAt: number
}

export interface PendingProgress {
  id?: number
  itemId: number
  cfi: string
  percent: number
  syncVersion: number
  queuedAt: number
}

const DB_NAME = 'reader-local'
const DB_VERSION = 1
const BOOK_STORE = 'books'
const PENDING_STORE = 'pending'

let dbPromise: Promise<IDBDatabase> | null = null

function openDb(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise
  const created = new Promise<IDBDatabase>((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('当前浏览器不支持 IndexedDB'))
      return
    }
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(BOOK_STORE)) {
        db.createObjectStore(BOOK_STORE, { keyPath: 'fileId' })
      }
      if (!db.objectStoreNames.contains(PENDING_STORE)) {
        db.createObjectStore(PENDING_STORE, { keyPath: 'id', autoIncrement: true })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error ?? new Error('IndexedDB 打开失败'))
  })
  dbPromise = created
  created.catch(() => {
    dbPromise = null
  })
  return created
}

/**
 * 在一个事务内执行请求，事务提交后才 resolve，避免"写入未落盘就继续"。
 */
async function withStore<T>(
  storeName: string,
  mode: IDBTransactionMode,
  run: (store: IDBObjectStore) => IDBRequest<T>,
): Promise<T> {
  const db = await openDb()
  return new Promise<T>((resolve, reject) => {
    const tx = db.transaction(storeName, mode)
    const request = run(tx.objectStore(storeName))
    tx.oncomplete = () => resolve(request.result)
    tx.onerror = () => reject(tx.error ?? new Error('IndexedDB 事务失败'))
    tx.onabort = () => reject(tx.error ?? new Error('IndexedDB 事务中止'))
  })
}

/** 读取已缓存的书籍；未命中或不可用时返回 null。 */
export async function getCachedBook(fileId: number): Promise<Book | null> {
  try {
    const record = await withStore<CachedBook | undefined>(
      BOOK_STORE,
      'readonly',
      (store) => store.get(fileId),
    )
    return record?.book ?? null
  } catch {
    return null
  }
}

/** 缓存解析结果。 */
export async function putCachedBook(fileId: number, book: Book): Promise<void> {
  try {
    await withStore<IDBValidKey>(BOOK_STORE, 'readwrite', (store) =>
      store.put({ fileId, book, cachedAt: Date.now() } as CachedBook),
    )
  } catch {
    // 缓存失败不影响阅读
  }
}

/** 读取待补传的进度（按入队顺序）。 */
export async function getPendingProgress(): Promise<PendingProgress[]> {
  try {
    return await withStore<PendingProgress[]>(PENDING_STORE, 'readonly', (store) => store.getAll())
  } catch {
    return []
  }
}

/** 入队一条进度；同一本书的旧记录会被替换，避免离线期间堆积。 */
export async function enqueueProgress(entry: Omit<PendingProgress, 'id' | 'queuedAt'>): Promise<void> {
  try {
    const pending = await getPendingProgress()
    for (const old of pending) {
      if (old.itemId === entry.itemId && old.id != null) {
        await withStore<undefined>(PENDING_STORE, 'readwrite', (store) => store.delete(old.id!))
      }
    }
    await withStore<IDBValidKey>(PENDING_STORE, 'readwrite', (store) =>
      store.add({ ...entry, queuedAt: Date.now() }),
    )
  } catch {
    // 队列不可用时放弃补传，不影响当前阅读
  }
}

/** 删除已成功补传的记录。 */
export async function deletePendingProgress(id: number): Promise<void> {
  try {
    await withStore<undefined>(PENDING_STORE, 'readwrite', (store) => store.delete(id))
  } catch {
    // 忽略
  }
}
