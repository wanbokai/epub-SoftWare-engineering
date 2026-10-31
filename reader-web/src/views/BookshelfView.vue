<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/TopBar.vue'
import { bookshelfApi, type BookshelfSort } from '@/api/bookshelf'
import { fileApi } from '@/api/file'
import { sha256Hex } from '@/utils/sha256'
import type { BookshelfItem } from '@/types/api'

const router = useRouter()

const VIEW_KEY = 'reader_bookshelf_view'
const SORT_KEY = 'reader_bookshelf_sort'

const items = ref<BookshelfItem[]>([])
const keyword = ref('')
const loading = ref(false)
const uploading = ref(false)
const uploadMsg = ref('')
const fileInput = ref<HTMLInputElement>()

// 视图与排序偏好持久化，刷新后保持用户上次选择。
const viewMode = ref<'grid' | 'list'>(localStorage.getItem(VIEW_KEY) === 'list' ? 'list' : 'grid')
const sort = ref<BookshelfSort>(
  (['lastRead', 'created', 'title'] as const).includes(
    localStorage.getItem(SORT_KEY) as BookshelfSort,
  )
    ? (localStorage.getItem(SORT_KEY) as BookshelfSort)
    : 'lastRead',
)

// 移除方式二选一：仅移出书架 / 同时删除云端文件。
const removeTarget = ref<BookshelfItem | null>(null)
const removeCloudCopy = ref(false)
const removing = ref(false)

async function load(keywordStr?: string, sortVal?: BookshelfSort) {
  loading.value = true
  try {
    items.value = await bookshelfApi.list(keywordStr || undefined, sortVal ?? sort.value)
  } catch (e) {
    alert((e as Error).message || '加载书架失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  load(keyword.value.trim())
}

function setSort(value: BookshelfSort) {
  sort.value = value
  localStorage.setItem(SORT_KEY, value)
  load(keyword.value.trim(), value)
}

function setView(mode: 'grid' | 'list') {
  viewMode.value = mode
  localStorage.setItem(VIEW_KEY, mode)
}

async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  const ext = file.name.split('.').pop()?.toLowerCase()
  const fileType = ext === 'epub' ? 1 : ext === 'txt' ? 2 : 0
  if (fileType === 0) {
    alert('仅支持 .epub 或 .txt 文件')
    input.value = ''
    return
  }

  uploading.value = true
  uploadMsg.value = '正在计算文件指纹…'
  try {
    const sha256 = await sha256Hex(file)
    // 秒传预检。
    const check = await fileApi.check(sha256)
    let fileId = check.fileId
    if (check.exists && fileId) {
      uploadMsg.value = '文件已存在，秒传成功'
    } else {
      uploadMsg.value = '正在上传…'
      const asset = await fileApi.upload(sha256, fileType, file)
      fileId = asset.id
      uploadMsg.value = '上传完成'
    }
    // 入架。
    const displayTitle = file.name.replace(/\.(epub|txt)$/i, '')
    await bookshelfApi.add({ fileId, displayTitle })
    uploadMsg.value = ''
    await load()
  } catch (err) {
    alert((err as Error).message || '上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function onRename(item: BookshelfItem) {
  const name = prompt('重命名书籍', item.displayTitle)
  if (name === null || name.trim() === '') return
  try {
    await bookshelfApi.rename(item.id, { displayTitle: name.trim() })
    await load(keyword.value.trim())
  } catch (e) {
    alert((e as Error).message || '重命名失败')
  }
}

function onRemove(item: BookshelfItem) {
  removeTarget.value = item
  removeCloudCopy.value = false
}

async function confirmRemove() {
  const target = removeTarget.value
  if (!target) return
  removing.value = true
  try {
    await bookshelfApi.remove(target.id, removeCloudCopy.value)
    removeTarget.value = null
    await load(keyword.value.trim())
  } catch (e) {
    alert((e as Error).message || '移除失败')
  } finally {
    removing.value = false
  }
}

function openReader(item: BookshelfItem) {
  router.push({ name: 'reader', params: { itemId: item.id } })
}

function formatSize(size: number) {
  if (!size) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function formatPercent(p?: number) {
  if (p == null) return ''
  return `${Number(p).toFixed(1)}%`
}

function formatLastRead(value?: string) {
  if (!value) return '从未阅读'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '从未阅读'
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${month}月${day}日 ${hh}:${mm}`
}

onMounted(() => load())
</script>

<template>
  <div class="page">
    <TopBar />

    <main class="container">
      <div class="toolbar">
        <div class="search">
          <input
            v-model="keyword"
            class="input"
            type="text"
            placeholder="搜索书名或作者…"
            @keyup.enter="onSearch"
          />
          <button class="btn" @click="onSearch">搜索</button>
        </div>

        <select
          class="input sort"
          :value="sort"
          title="排序方式"
          @change="setSort(($event.target as HTMLSelectElement).value as BookshelfSort)"
        >
          <option value="lastRead">最近阅读</option>
          <option value="created">导入时间</option>
          <option value="title">书名</option>
        </select>

        <div class="view-toggle">
          <button
            class="view-btn"
            :class="{ active: viewMode === 'grid' }"
            title="网格视图"
            @click="setView('grid')"
          >
            ▦
          </button>
          <button
            class="view-btn"
            :class="{ active: viewMode === 'list' }"
            title="列表视图"
            @click="setView('list')"
          >
            ☰
          </button>
        </div>

        <button class="btn btn-primary" :disabled="uploading" @click="fileInput?.click()">
          {{ uploading ? uploadMsg || '上传中…' : '＋ 导入图书' }}
        </button>
        <input
          ref="fileInput"
          type="file"
          accept=".epub,.txt"
          style="display: none"
          @change="onFileChange"
        />
      </div>

      <p v-if="uploadMsg" class="upload-msg">{{ uploadMsg }}</p>

      <div v-if="loading" class="empty text-sub">加载中…</div>

      <div v-else-if="items.length === 0" class="empty text-sub">
        书架空空如也，点击右上角「导入图书」开始阅读
      </div>

      <div v-else-if="viewMode === 'grid'" class="grid">
        <div v-for="item in items" :key="item.id" class="card book">
          <div class="cover" :class="item.fileType === 1 ? 'epub' : 'txt'">
            <span>{{ item.fileType === 1 ? 'EPUB' : 'TXT' }}</span>
          </div>
          <div class="book-body" @click="openReader(item)">
            <div class="title">{{ item.displayTitle }}</div>
            <div class="meta text-sub">
              {{ item.metaAuthor || '未知作者' }} · {{ formatSize(item.fileSize) }}
            </div>
            <div class="progress">
              <div class="bar">
                <div class="bar-fill" :style="{ width: formatPercent(item.progressPercent) || '0%' }" />
              </div>
              <span class="text-sub">{{ formatPercent(item.progressPercent) || '未读' }}</span>
            </div>
            <div class="last-read text-sub">{{ formatLastRead(item.lastReadAt) }}</div>
          </div>
          <div class="actions">
            <button class="btn" @click="onRename(item)">重命名</button>
            <button class="btn btn-danger" @click="onRemove(item)">移除</button>
          </div>
        </div>
      </div>

      <div v-else class="list">
        <div v-for="item in items" :key="item.id" class="card row">
          <div class="cover small" :class="item.fileType === 1 ? 'epub' : 'txt'">
            <span>{{ item.fileType === 1 ? 'EPUB' : 'TXT' }}</span>
          </div>
          <div class="row-main" @click="openReader(item)">
            <div class="title">{{ item.displayTitle }}</div>
            <div class="meta text-sub">
              {{ item.metaAuthor || '未知作者' }} · {{ formatSize(item.fileSize) }} ·
              {{ formatLastRead(item.lastReadAt) }}
            </div>
          </div>
          <div class="row-progress">
            <div class="bar">
              <div class="bar-fill" :style="{ width: formatPercent(item.progressPercent) || '0%' }" />
            </div>
            <span class="text-sub">{{ formatPercent(item.progressPercent) || '未读' }}</span>
          </div>
          <div class="actions">
            <button class="btn" @click="onRename(item)">重命名</button>
            <button class="btn btn-danger" @click="onRemove(item)">移除</button>
          </div>
        </div>
      </div>
    </main>

    <!-- 移除方式选择 -->
    <div v-if="removeTarget" class="modal">
      <div class="card modal-card">
        <h3>移除《{{ removeTarget.displayTitle }}》</h3>
        <p class="text-sub">请选择移除方式：</p>
        <label class="radio">
          <input v-model="removeCloudCopy" type="radio" :value="false" />
          仅移出我的书架（保留云端文件）
        </label>
        <label class="radio">
          <input v-model="removeCloudCopy" type="radio" :value="true" />
          同时删除云端文件副本（无其他引用时回收）
        </label>
        <div class="modal-actions">
          <button class="btn" :disabled="removing" @click="removeTarget = null">取消</button>
          <button class="btn btn-danger" :disabled="removing" @click="confirmRemove">
            {{ removing ? '移除中…' : '确认移除' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
}

.container {
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.search {
  display: flex;
  gap: 8px;
  flex: 1;
  min-width: 220px;
}

.search .input {
  max-width: 360px;
}

.sort {
  width: auto;
  padding: 8px 10px;
}

.view-toggle {
  display: inline-flex;
  border: 1px solid var(--border);
  border-radius: 8px;
  overflow: hidden;
  background: var(--surface);
}

.view-btn {
  border: none;
  background: transparent;
  color: var(--text-sub);
  padding: 6px 12px;
  font-size: 15px;
}

.view-btn.active {
  background: var(--primary);
  color: #fff;
}

.upload-msg {
  color: var(--primary);
  font-size: 14px;
  margin: 0 0 16px;
}

.empty {
  text-align: center;
  padding: 80px 0;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.book {
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--surface);
}

.cover {
  height: 120px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  letter-spacing: 2px;
}

.cover.small {
  width: 64px;
  height: 48px;
  font-size: 11px;
  letter-spacing: 1px;
  flex-shrink: 0;
}

.cover.epub {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
}

.cover.txt {
  background: linear-gradient(135deg, #10b981, #059669);
}

.book-body {
  cursor: pointer;
  flex: 1;
}

.title {
  font-weight: 600;
  font-size: 16px;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  font-size: 13px;
  margin-bottom: 8px;
}

.progress {
  display: flex;
  align-items: center;
  gap: 8px;
}

.last-read {
  font-size: 12px;
  margin-top: 6px;
}

.bar {
  flex: 1;
  height: 6px;
  background: var(--border);
  border-radius: 3px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: var(--primary);
  border-radius: 3px;
  transition: width 0.3s;
}

.progress span {
  font-size: 12px;
  white-space: nowrap;
}

.actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  background: var(--surface);
}

.row-main {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.row-main .title {
  margin-bottom: 2px;
}

.row-main .meta {
  margin-bottom: 0;
}

.row-progress {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 220px;
  flex-shrink: 0;
}

.row-progress span {
  font-size: 12px;
  white-space: nowrap;
}

.modal {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 30;
}

.modal-card {
  width: 420px;
  max-width: 92%;
  background: var(--surface);
  color: var(--text);
}

.modal-card h3 {
  margin: 0 0 8px;
  font-size: 18px;
}

.radio {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
  font-size: 14px;
  cursor: pointer;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}

@media (max-width: 720px) {
  .row {
    flex-wrap: wrap;
  }

  .row-progress {
    width: 100%;
    order: 3;
  }
}
</style>
