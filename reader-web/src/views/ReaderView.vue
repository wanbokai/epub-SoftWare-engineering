<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useReaderStore } from '@/stores/reader'
import { bookshelfApi } from '@/api/bookshelf'
import { parserApi } from '@/api/parser'
import { syncApi } from '@/api/sync'
import type { Book, BookshelfItem, Chapter, ConflictResponse } from '@/types/api'

const route = useRoute()
const router = useRouter()
const store = useReaderStore()

const itemId = Number(route.params.itemId)

const book = ref<Book | null>(null)
const item = ref<BookshelfItem | null>(null)
const loading = ref(true)
const error = ref('')

const currentIndex = ref(0)
const showToc = ref(false)
const showSettings = ref(false)

const conflict = ref<ConflictResponse | null>(null)
const resolving = ref(false)

// 节流上报进度。
let pushTimer: ReturnType<typeof setTimeout> | null = null

const currentChapter = computed<Chapter | null>(() => {
  if (!book.value) return null
  return book.value.chapters[currentIndex.value] ?? null
})

// 阅读进度百分比：按章节位置估算。
const progressPercent = computed(() => {
  if (!book.value || book.value.chapters.length === 0) return 0
  return ((currentIndex.value + 1) / book.value.chapters.length) * 100
})

const cfi = computed(() => {
  const ch = currentChapter.value
  return ch ? `epubcfi(/6/${currentIndex.value + 1})` : ''
})

// 排版样式绑定。
const readerStyle = computed(() => {
  const c = store.config
  const marginMap: Record<number, string> = { 1: '8px', 2: '24px', 3: '48px' }
  const themeBg: Record<number, string> = {
    1: '#ffffff',
    2: '#1f2937',
    3: '#f5e9d3',
    4: '#cce8cf',
  }
  const themeText: Record<number, string> = {
    1: '#1f2937',
    2: '#e5e7eb',
    3: '#3f3a2e',
    4: '#1f2937',
  }
  return {
    background: themeBg[c.theme] ?? '#ffffff',
    color: themeText[c.theme] ?? '#1f2937',
    fontSize: `${c.fontSize}px`,
    lineHeight: c.lineHeight,
    padding: `24px ${marginMap[c.pageMargin] ?? '24px'}`,
  }
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const list = await bookshelfApi.list()
    item.value = list.find((b) => b.id === itemId) ?? null

    const parsed = await parserApi.parse(item.value!.fileId)
    book.value = parsed

    // 恢复阅读进度。
    if (item.value?.progressCfi) {
      const m = item.value.progressCfi.match(/\((\d+)\)/)
      if (m) {
        currentIndex.value = Math.min(Math.max(Number(m[1]) - 1, 0), parsed.chapters.length - 1)
      }
    }
  } catch (e) {
    error.value = (e as Error).message || '加载书籍失败'
  } finally {
    loading.value = false
  }
}

function gotoChapter(i: number) {
  if (i < 0 || i >= (book.value?.chapters.length ?? 0)) return
  currentIndex.value = i
  showToc.value = false
  schedulePush()
}

function prev() {
  gotoChapter(currentIndex.value - 1)
}

function next() {
  gotoChapter(currentIndex.value + 1)
}

// 节流上报进度。
function schedulePush() {
  if (pushTimer) clearTimeout(pushTimer)
  pushTimer = setTimeout(() => pushProgress(), 800)
}

async function pushProgress() {
  if (!item.value) return
  try {
    const newVersion = await syncApi.pushProgress(itemId, {
      cfi: cfi.value,
      percent: Math.round(progressPercent.value * 100) / 100,
      syncVersion: item.value.syncVersion,
    })
    item.value.syncVersion = newVersion
  } catch (e: unknown) {
    const err = e as { response?: { status?: number; data?: { data?: ConflictResponse } } }
    // 409 冲突：弹出消解面板。
    if (err.response?.status === 409 && err.response?.data?.data) {
      conflict.value = err.response.data.data
    }
  }
}

async function resolve(choice: string) {
  if (!conflict.value) return
  resolving.value = true
  try {
    const newVersion = await syncApi.resolve(itemId, {
      choice,
      cfi: choice === 'local' ? cfi.value : undefined,
      percent: choice === 'local' ? Math.round(progressPercent.value * 100) / 100 : undefined,
    })
    if (item.value) item.value.syncVersion = newVersion
    conflict.value = null
  } catch (e) {
    alert((e as Error).message || '消解失败')
  } finally {
    resolving.value = false
  }
}

function closeReader() {
  router.push('/')
}

watch(currentIndex, () => schedulePush())

onMounted(async () => {
  try {
    await store.pull()
  } catch {
    // 配置拉取失败不阻塞阅读。
  }
  await load()
})
</script>

<template>
  <div class="reader">
    <!-- 顶栏 -->
    <header class="bar top">
      <button class="icon-btn" @click="closeReader">←</button>
      <div class="bar-title">{{ book?.meta.title || item?.displayTitle || '阅读' }}</div>
      <div class="bar-actions">
        <button class="icon-btn" @click="showToc = !showToc">目录</button>
        <button class="icon-btn" @click="showSettings = !showSettings">设置</button>
      </div>
    </header>

    <!-- 正文 -->
    <div v-if="loading" class="center">加载中…</div>
    <div v-else-if="error" class="center">
      <p>{{ error }}</p>
      <button class="btn" @click="closeReader">返回书架</button>
    </div>
    <main v-else class="content" :style="readerStyle" @click="showToc = false">
      <article v-if="currentChapter">
        <h2 class="ch-title">{{ currentChapter.title }}</h2>
        <!-- EPUB 章节为 XHTML 片段，用 v-html 渲染；TXT 为纯文本用白空间保留。 -->
        <div v-if="book && item && item.fileType === 1" class="ch-body" v-html="currentChapter.content"></div>
        <pre v-else class="ch-body txt">{{ currentChapter.content }}</pre>
      </article>
    </main>

    <!-- 底栏 -->
    <footer class="bar bottom">
      <button class="icon-btn" :disabled="currentIndex === 0" @click="prev">‹ 上一章</button>
      <span class="pos">{{ currentIndex + 1 }} / {{ book?.chapters.length }}</span>
      <span class="pct">{{ progressPercent.toFixed(1) }}%</span>
      <button class="icon-btn" :disabled="currentIndex >= (book?.chapters.length ?? 0) - 1" @click="next">下一章 ›</button>
    </footer>

    <!-- 目录抽屉 -->
    <div v-if="showToc" class="drawer">
      <div class="drawer-head">目录</div>
      <div class="drawer-body">
        <div
          v-for="(t, i) in book?.toc ?? []"
          :key="i"
          class="toc-item"
          :class="{ active: i === currentIndex }"
          @click="gotoChapter(i)"
        >
          {{ t.title }}
        </div>
        <div v-if="!book?.toc?.length" class="text-sub" style="padding: 16px">无目录</div>
      </div>
    </div>

    <!-- 设置抽屉 -->
    <div v-if="showSettings" class="drawer">
      <div class="drawer-head">阅读设置</div>
      <div class="drawer-body">
        <div class="field">
          <label>字号 {{ store.config.fontSize }}px</label>
          <input v-model.number="store.config.fontSize" type="range" min="12" max="28" />
        </div>
        <div class="field">
          <label>行高 {{ store.config.lineHeight.toFixed(1) }}</label>
          <input v-model.number="store.config.lineHeight" type="range" min="1.2" max="2.4" step="0.1" />
        </div>
        <button class="btn btn-primary" @click="store.save()">保存设置</button>
      </div>
    </div>

    <!-- 冲突消解面板 -->
    <div v-if="conflict" class="modal">
      <div class="card modal-card">
        <h3>同步冲突</h3>
        <p class="text-sub">检测到云端与本机阅读进度不一致，请选择保留哪一方：</p>
        <div class="conflict-row">
          <span class="tag">云端</span>
          <span>{{ conflict.cloud.percent?.toFixed?.(1) ?? conflict.cloud.percent }}%</span>
        </div>
        <div class="conflict-row">
          <span class="tag">本机</span>
          <span>{{ conflict.local.percent }}%</span>
        </div>
        <div class="modal-actions">
          <button class="btn" :disabled="resolving" @click="resolve('cloud')">保留云端</button>
          <button class="btn" :disabled="resolving" @click="resolve('local')">采用本机</button>
          <button class="btn" :disabled="resolving" @click="resolve('lww')">最后写入胜</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.reader {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}

.bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
}

.bar.top {
  position: sticky;
  top: 0;
  z-index: 10;
}

.bar.bottom {
  border-bottom: none;
  border-top: 1px solid var(--border);
  justify-content: space-between;
}

.bar-title {
  flex: 1;
  font-weight: 600;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bar-actions {
  display: flex;
  gap: 4px;
}

.icon-btn {
  border: none;
  background: none;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 14px;
  color: var(--text);
}

.icon-btn:hover {
  background: var(--bg);
}

.center {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.content {
  flex: 1;
  overflow-y: auto;
  transition: background 0.2s, color 0.2s;
}

.ch-title {
  font-size: 1.6em;
  margin: 0 0 20px;
  text-align: center;
}

.ch-body {
  max-width: 720px;
  margin: 0 auto;
}

.ch-body.txt {
  font-family: inherit;
  white-space: pre-wrap;
  word-break: break-word;
}

.pos,
.pct {
  font-size: 13px;
  color: var(--text-sub);
}

.drawer {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: 280px;
  background: var(--surface);
  border-right: 1px solid var(--border);
  z-index: 20;
  display: flex;
  flex-direction: column;
}

.drawer-head {
  padding: 16px;
  font-weight: 600;
  border-bottom: 1px solid var(--border);
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.toc-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}

.toc-item:hover {
  background: var(--bg);
}

.toc-item.active {
  background: rgba(59, 130, 246, 0.1);
  color: var(--primary);
}

.field {
  margin-bottom: 16px;
}

.field label {
  display: block;
  font-size: 13px;
  color: var(--text-sub);
  margin-bottom: 6px;
}

.field input[type='range'] {
  width: 100%;
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
  width: 360px;
  max-width: 90%;
}

.modal-card h3 {
  margin: 0 0 8px;
}

.conflict-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border);
}

.tag {
  background: var(--bg);
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 12px;
  color: var(--text-sub);
}

.modal-actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
  flex-wrap: wrap;
}
</style>
