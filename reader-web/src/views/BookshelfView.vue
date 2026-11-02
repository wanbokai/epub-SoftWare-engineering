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
      <div class="page-head">
        <div>
          <div class="eyebrow">YOUR LIBRARY</div>
          <h1>我的书架</h1>
          <p class="text-sub">{{ items.length }} 本书 · 慢慢读，常常来</p>
        </div>
      </div>

      <div class="toolbar card">
        <div class="search">
          <span class="search-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none">
              <circle cx="11" cy="11" r="6.5" stroke="currentColor" stroke-width="1.8" />
              <path d="m16 16 4.2 4.2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </svg>
          </span>
          <input
            v-model="keyword"
            class="input search-input"
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

      <div v-else-if="items.length === 0" class="empty">
        <div class="empty-icon" aria-hidden="true">
          <svg viewBox="0 0 48 48" fill="none">
            <path d="M10 12.5h11.5c2 0 3.5 1.6 3.5 3.5v22c0-2-1.5-3.5-3.5-3.5H10z" stroke="currentColor" stroke-width="2" stroke-linejoin="round" />
            <path d="M38 12.5H26.5c-2 0-3.5 1.6-3.5 3.5v22c0-2 1.5-3.5 3.5-3.5H38z" stroke="currentColor" stroke-width="2" stroke-linejoin="round" />
          </svg>
        </div>
        <p class="empty-title">书架还空着</p>
        <p class="text-sub">导入一本 EPUB 或 TXT，开始你的第一段阅读</p>
      </div>

      <div v-else-if="viewMode === 'grid'" class="grid">
        <div v-for="item in items" :key="item.id" class="card book">
          <div class="cover" :class="item.fileType === 1 ? 'epub' : 'txt'">
            <span class="cover-mark">{{ item.displayTitle.slice(0, 1) }}</span>
            <span class="cover-type">{{ item.fileType === 1 ? 'EPUB' : 'TXT' }}</span>
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
            <span class="cover-type">{{ item.fileType === 1 ? 'EPUB' : 'TXT' }}</span>
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

/* ── 页庐 · 书架视觉 ───────────────────────────── */
.page {
  background: transparent;
}

.container {
  max-width: 1120px;
  padding: 36px 28px 64px;
}

.page-head {
  margin-bottom: 24px;
}

.page-head h1 {
  margin: 6px 0 6px;
  font-family: var(--serif);
  font-size: 30px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.page-head p {
  margin: 0;
  font-size: 13.5px;
}

.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px;
  margin-bottom: 22px;
  border-radius: 16px;
}

.search {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 240px;
}

.search-icon {
  position: absolute;
  left: 13px;
  top: 50%;
  width: 17px;
  height: 17px;
  transform: translateY(-50%);
  color: var(--text-sub);
  pointer-events: none;
}

.search-icon svg {
  width: 100%;
  height: 100%;
}

.search-input {
  height: 42px;
  padding-left: 38px;
}

.search .input {
  max-width: none;
}

.search .btn {
  flex: none;
  height: 42px;
  padding: 0 18px;
  white-space: nowrap;
}

.sort {
  width: auto;
  min-width: 124px;
  height: 42px;
  padding: 0 34px 0 12px;
}

.view-toggle {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface-2);
}

.view-btn {
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--text-sub);
  font-size: 15px;
  line-height: 1;
}

.view-btn:hover {
  color: var(--text);
}

.view-btn.active {
  background: var(--primary-soft);
  color: var(--primary-hover);
}

.toolbar .btn-primary {
  height: 42px;
  padding: 0 18px;
}

.upload-msg {
  margin: -8px 2px 16px;
  font-size: 13px;
  color: var(--primary);
}

.grid {
  gap: 18px;
}

.book {
  padding: 14px;
  border-radius: 18px;
  box-shadow: none;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.book:hover {
  transform: translateY(-3px);
  border-color: color-mix(in srgb, var(--primary) 28%, var(--border));
  box-shadow: var(--shadow-md);
}

.cover {
  position: relative;
  height: 150px;
  border-radius: 12px;
  overflow: hidden;
  justify-content: flex-start;
  align-items: flex-end;
}

.cover::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(120% 100% at 85% 0%, rgba(255, 255, 255, 0.22), transparent 55%),
    repeating-linear-gradient(115deg, rgba(255, 255, 255, 0.06) 0 2px, transparent 2px 14px);
  pointer-events: none;
}

.cover.epub {
  background: linear-gradient(140deg, #6f9a6d 0%, #4c7852 55%, #355c3c 100%);
}

.cover.txt {
  background: linear-gradient(140deg, #9dbd94 0%, #6e936d 55%, #4b7051 100%);
}

.cover-mark {
  position: relative;
  z-index: 1;
  margin: 0 0 4px 16px;
  font-family: var(--serif);
  font-size: 52px;
  line-height: 1;
  color: rgba(255, 255, 255, 0.9);
  text-shadow: 0 2px 12px rgba(24, 45, 28, 0.25);
}

.cover-type {
  position: absolute;
  top: 11px;
  right: 11px;
  z-index: 1;
  padding: 3px 8px;
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  font-size: 9.5px;
  letter-spacing: 0.12em;
  color: rgba(255, 255, 255, 0.92);
}

.cover.small {
  width: 54px;
  height: 54px;
  border-radius: 10px;
}

.cover.small .cover-type {
  position: static;
  border: none;
  background: none;
  padding: 0;
  font-size: 9px;
  color: rgba(255, 255, 255, 0.95);
}

.book-body {
  padding: 2px 2px 0;
}

.title {
  margin: 2px 0 4px;
  font-size: 15.5px;
}

.meta {
  margin-bottom: 10px;
  font-size: 12.5px;
}

.progress .bar {
  height: 5px;
  background: var(--primary-soft);
}

.bar-fill {
  background: linear-gradient(90deg, var(--primary), var(--primary-hover));
}

.progress span {
  font-size: 11.5px;
}

.last-read {
  margin-top: 7px;
  font-size: 11.5px;
}

.actions {
  gap: 6px;
}

.actions .btn {
  padding: 5px 10px;
  border-color: transparent;
  background: var(--surface-2);
  font-size: 12.5px;
}

.actions .btn:hover {
  border-color: var(--border);
  background: var(--primary-soft);
}

.actions .btn-danger {
  background: transparent;
}

.list {
  gap: 10px;
}

.row {
  padding: 12px 16px;
  border-radius: 14px;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.row:hover {
  border-color: color-mix(in srgb, var(--primary) 26%, var(--border));
  box-shadow: var(--shadow-sm);
}

.row .title {
  margin: 0 0 3px;
  font-size: 15px;
}

.row .meta {
  margin-bottom: 0;
  font-size: 12px;
}

.row-progress {
  width: 220px;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 96px 0;
  text-align: center;
}

.empty-icon {
  width: 52px;
  height: 52px;
  margin-bottom: 6px;
  color: var(--primary);
  opacity: 0.55;
}

.empty-icon svg {
  width: 100%;
  height: 100%;
}

.empty-title {
  margin: 0;
  font-family: var(--serif);
  font-size: 22px;
  color: var(--text);
}

.empty p {
  margin: 0;
  font-size: 13.5px;
}

.modal {
  background: color-mix(in srgb, #1b2a1f 34%, transparent);
  backdrop-filter: blur(3px);
}

.modal-card {
  width: 440px;
  padding: 26px;
  border-radius: 18px;
  box-shadow: var(--shadow-md);
}

.modal-card h3 {
  margin: 0 0 6px;
  font-family: var(--serif);
  font-size: 20px;
  font-weight: 600;
}

.radio {
  margin: 8px 0;
  padding: 11px 12px;
  border: 1px solid var(--border);
  border-radius: 10px;
  font-size: 13.5px;
  transition: border-color 0.18s ease, background 0.18s ease;
}

.radio:hover {
  background: var(--surface-2);
}

.radio:has(input:checked) {
  border-color: color-mix(in srgb, var(--primary) 45%, var(--border));
  background: var(--primary-soft);
}

.radio input {
  accent-color: var(--primary);
}

@media (max-width: 720px) {
  .container {
    padding: 24px 16px 48px;
  }

  .page-head h1 {
    font-size: 25px;
  }

  .toolbar {
    padding: 10px;
  }

  .sort {
    flex: 1;
  }

  .cover {
    height: 132px;
  }
}
</style>
