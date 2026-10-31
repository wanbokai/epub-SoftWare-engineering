<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useReaderStore } from '@/stores/reader'
import { bookshelfApi } from '@/api/bookshelf'
import { parserApi } from '@/api/parser'
import { syncApi } from '@/api/sync'
import { buildCfi, parseCfi, intraFromPercent, isModernCfi, percentOf } from '@/utils/locator'
import {
  deletePendingProgress,
  enqueueProgress,
  getCachedBook,
  getPendingProgress,
  putCachedBook,
} from '@/utils/db'
import { THEME_OPTIONS, themeOf } from '@/utils/theme'
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
const saving = ref(false)

const conflict = ref<ConflictResponse | null>(null)
const resolving = ref(false)

// 分页模式下的页面状态（流式模式下 pageCount 恒为 1）。
const contentEl = ref<HTMLElement | null>(null)
const pageEl = ref<HTMLElement | null>(null)
const pageIndex = ref(0)
const pageCount = ref(1)
const pageWidth = ref(0)
const pageHeight = ref(0)
const PAGE_GAP = 48

// 流式模式下当前滚动位置占可滚动范围的比例（0~1）。
const scrollRatio = ref(0)
const positionRestored = ref(false)
// 上一章时希望落到最后一页（分页模式）。
let pendingLastPage = false

const margins = [
  { value: 1, label: '窄' },
  { value: 2, label: '中' },
  { value: 3, label: '宽' },
]

const modes = [
  { value: 1, label: '分页' },
  { value: 2, label: '流式' },
]

const totalChapters = computed(() => book.value?.chapters.length ?? 0)

const currentChapter = computed<Chapter | null>(() => {
  if (!book.value) return null
  return book.value.chapters[currentIndex.value] ?? null
})

const isPaged = computed(() => store.config.pageMode === 1)

// 章内位置：分页模式用页码换算，流式模式用滚动比例。
const intra = computed(() => {
  if (!book.value) return 0
  if (isPaged.value) {
    return pageCount.value > 1 ? pageIndex.value / (pageCount.value - 1) : 0
  }
  return scrollRatio.value
})

// 全书进度：章节下标 + 章内位置，不再是"第几章 / 共几章"的粗估值。
const progressPercent = computed(() =>
  percentOf(currentIndex.value, intra.value, totalChapters.value),
)

const cfi = computed(() => (book.value ? buildCfi(currentIndex.value) : ''))

// 排版样式绑定（颜色取自统一主题色板）。
const readerStyle = computed(() => {
  const c = store.config
  const palette = themeOf(c.theme)
  const marginMap: Record<number, string> = { 1: '8px', 2: '24px', 3: '48px' }
  return {
    background: palette.surface,
    color: palette.text,
    fontSize: `${c.fontSize}px`,
    lineHeight: String(c.lineHeight),
    padding: `24px ${marginMap[c.pageMargin] ?? '24px'}`,
  }
})

// 分页轨道的列宽、列间距与横向位移。
const pageTrackStyle = computed(() => {
  if (!isPaged.value) return { width: '100%' }
  return {
    width: `${pageWidth.value}px`,
    height: `${pageHeight.value}px`,
    columnWidth: `${pageWidth.value}px`,
    columnGap: `${PAGE_GAP}px`,
    transform: `translateX(${-pageIndex.value * (pageWidth.value + PAGE_GAP)}px)`,
  }
})

// 节流上报进度。
let pushTimer: ReturnType<typeof setTimeout> | null = null

/** 量取可视区域尺寸并重算页数。 */
function measurePages() {
  const el = contentEl.value
  if (!el) return
  const styles = getComputedStyle(el)
  const padX = parseFloat(styles.paddingLeft) + parseFloat(styles.paddingRight)
  const padY = parseFloat(styles.paddingTop) + parseFloat(styles.paddingBottom)
  // 正文最大阅读宽度 720px，超宽屏居中原样排版。
  pageWidth.value = Math.min(720, Math.max(120, el.clientWidth - padX))
  pageHeight.value = Math.max(120, el.clientHeight - padY)
  updatePageCount()
}

/** 根据列轨道总宽度计算页数，并处理"上一章落到末页"。 */
function updatePageCount() {
  const track = pageEl.value
  if (!isPaged.value || !track) {
    pageCount.value = 1
    if (!isPaged.value) pageIndex.value = 0
    return
  }
  const step = pageWidth.value + PAGE_GAP
  const count = Math.max(1, Math.round((track.scrollWidth + PAGE_GAP) / step))
  pageCount.value = Math.min(count, 5000)
  if (pendingLastPage) {
    pendingLastPage = false
    pageIndex.value = pageCount.value - 1
    return
  }
  pageIndex.value = Math.min(pageIndex.value, pageCount.value - 1)
}

/** 把 0~1 的章内位置换算成页码或滚动距离。 */
async function applyIntra(target: number) {
  await nextTick()
  measurePages()
  const ratio = Math.min(1, Math.max(0, target))
  if (isPaged.value) {
    pageIndex.value = Math.min(
      pageCount.value - 1,
      Math.max(0, Math.round(ratio * Math.max(0, pageCount.value - 1))),
    )
  } else if (contentEl.value) {
    contentEl.value.scrollTop =
      ratio * Math.max(0, contentEl.value.scrollHeight - contentEl.value.clientHeight)
    scrollRatio.value = ratio
  }
}

/** 从书架条目恢复上次阅读位置（章节 + 章内位置）。 */
async function restorePosition() {
  if (!book.value || positionRestored.value) return
  positionRestored.value = true

  const parsed = parseCfi(item.value?.progressCfi)
  const maxIndex = Math.max(0, book.value.chapters.length - 1)
  currentIndex.value = Math.min(Math.max(parsed >= 0 ? parsed : 0, 0), maxIndex)

  // 旧数据只有粗粒度百分比，不能还原章内位置，直接停在章首。
  const target = isModernCfi(item.value?.progressCfi)
    ? intraFromPercent(item.value?.progressPercent, currentIndex.value, book.value.chapters.length)
    : 0
  await applyIntra(target)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const list = await bookshelfApi.list()
    item.value = list.find((b) => b.id === itemId) ?? null
    if (!item.value) throw new Error('书架中找不到这本书')

    // 先展示本地缓存，做到二次打开秒开、断网也能读已打开过的书。
    const cached = await getCachedBook(item.value.fileId)
    if (cached) {
      book.value = cached
      loading.value = false
      await restorePosition()
    }

    try {
      const parsed = await parserApi.parse(item.value.fileId)
      book.value = parsed
      void putCachedBook(item.value.fileId, parsed)
      if (!cached) {
        loading.value = false
        await restorePosition()
      }
    } catch (e) {
      // 有缓存时静默降级为离线阅读；无缓存才提示错误。
      if (!cached) throw e
    }
  } catch (e) {
    error.value = (e as Error).message || '加载书籍失败'
  } finally {
    loading.value = false
  }
}

function gotoChapter(i: number, lastPage = false) {
  if (i < 0 || i >= totalChapters.value) return
  pendingLastPage = lastPage
  currentIndex.value = i
  showToc.value = false
}

function prevPage() {
  if (!isPaged.value) {
    gotoChapter(currentIndex.value - 1)
    return
  }
  if (pageIndex.value > 0) {
    pageIndex.value--
    return
  }
  // 当前章第一页：翻到上一章最后一页
  if (currentIndex.value > 0) gotoChapter(currentIndex.value - 1, true)
}

function nextPage() {
  if (!isPaged.value) {
    gotoChapter(currentIndex.value + 1)
    return
  }
  if (pageIndex.value < pageCount.value - 1) {
    pageIndex.value++
    return
  }
  if (currentIndex.value < totalChapters.value - 1) gotoChapter(currentIndex.value + 1)
}

function canPrev() {
  if (currentIndex.value === 0) return isPaged.value ? pageIndex.value > 0 : false
  return true
}

function canNext() {
  if (currentIndex.value >= totalChapters.value - 1) {
    return isPaged.value ? pageIndex.value < pageCount.value - 1 : false
  }
  return true
}

// 分页模式：点击左右区域翻页；流式模式：点击正文收起抽屉。
function onContentClick(e: MouseEvent) {
  if (showToc.value || showSettings.value) {
    showToc.value = false
    showSettings.value = false
    return
  }
  if (!isPaged.value) return
  const target = e.target as HTMLElement | null
  if (target?.closest('a')) return
  const el = contentEl.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const ratio = (e.clientX - rect.left) / Math.max(1, rect.width)
  if (ratio < 0.3) prevPage()
  else if (ratio > 0.7) nextPage()
}

let scrollRaf = 0
function onScroll() {
  if (isPaged.value || scrollRaf) return
  scrollRaf = requestAnimationFrame(() => {
    scrollRaf = 0
    const el = contentEl.value
    if (!el) return
    const max = el.scrollHeight - el.clientHeight
    scrollRatio.value = max > 0 ? Math.min(1, Math.max(0, el.scrollTop / max)) : 0
    schedulePush()
  })
}

function onKeydown(e: KeyboardEvent) {
  const tag = (e.target as HTMLElement | null)?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return
  if (e.key === 'ArrowLeft' || e.key === 'PageUp') {
    e.preventDefault()
    prevPage()
  } else if (e.key === 'ArrowRight' || e.key === 'PageDown') {
    e.preventDefault()
    nextPage()
  }
}

let resizeTimer: ReturnType<typeof setTimeout> | null = null
function onResize() {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(async () => {
    const before = intra.value
    measurePages()
    if (isPaged.value) {
      pageIndex.value = Math.min(
        pageCount.value - 1,
        Math.max(0, Math.round(before * Math.max(0, pageCount.value - 1))),
      )
    }
  }, 150)
}

// 节流上报进度。
function schedulePush() {
  if (pushTimer) clearTimeout(pushTimer)
  pushTimer = setTimeout(() => pushProgress(), 800)
}

async function pushProgress() {
  if (!item.value) return
  const payload = {
    cfi: cfi.value,
    percent: Math.round(progressPercent.value * 100) / 100,
    syncVersion: item.value.syncVersion,
  }
  try {
    const newVersion = await syncApi.pushProgress(itemId, payload)
    item.value.syncVersion = newVersion
  } catch (e: unknown) {
    const err = e as { response?: { status?: number; data?: { data?: ConflictResponse } } }
    // 409 冲突：弹出消解面板。
    if (err.response?.status === 409 && err.response?.data?.data) {
      conflict.value = err.response.data.data
      return
    }
    // 无响应说明是网络问题：写入本地队列，联网后补传。
    if (!err.response) {
      await enqueueProgress({ itemId, ...payload })
    }
  }
}

/** 补传离线期间积压的进度。 */
async function flushPending() {
  if (!item.value) return
  const pending = (await getPendingProgress()).filter((p) => p.itemId === itemId)
  for (const entry of pending) {
    try {
      const version = await syncApi.pushProgress(itemId, {
        cfi: entry.cfi,
        percent: entry.percent,
        syncVersion: entry.syncVersion,
      })
      if (item.value) item.value.syncVersion = Math.max(item.value.syncVersion, version)
      if (entry.id != null) await deletePendingProgress(entry.id)
    } catch (e) {
      const err = e as { response?: { status?: number; data?: { data?: ConflictResponse } } }
      if (err.response?.status === 409) {
        // 版本已落后，丢弃这条旧进度，交给冲突面板处理。
        if (entry.id != null) await deletePendingProgress(entry.id)
        if (err.response.data?.data) conflict.value = err.response.data.data
      }
      break
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

    // 选择"保留云端"时，把本机阅读位置同步到云端位置，避免界面与数据不一致。
    if (choice === 'cloud') {
      const fresh = (await bookshelfApi.list()).find((b) => b.id === itemId)
      if (fresh && book.value) {
        item.value = fresh
        const idx = parseCfi(fresh.progressCfi)
        const maxIndex = Math.max(0, book.value.chapters.length - 1)
        currentIndex.value = Math.min(Math.max(idx >= 0 ? idx : 0, 0), maxIndex)
        const target = isModernCfi(fresh.progressCfi)
          ? intraFromPercent(fresh.progressPercent, currentIndex.value, book.value.chapters.length)
          : 0
        await applyIntra(target)
      }
    }
  } catch (e) {
    alert((e as Error).message || '消解失败')
  } finally {
    resolving.value = false
  }
}

async function saveSettings() {
  saving.value = true
  try {
    await store.save()
  } catch (e) {
    alert((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

function closeReader() {
  router.push('/')
}

watch(currentIndex, async () => {
  pageIndex.value = 0
  scrollRatio.value = 0
  await nextTick()
  measurePages()
  schedulePush()
})

// 书籍内容替换（缓存 → 服务端解析结果）后重新量取页面。
watch(book, async () => {
  await nextTick()
  measurePages()
})

watch(pageIndex, () => schedulePush())

watch(
  () => [store.config.fontSize, store.config.lineHeight, store.config.pageMargin, store.config.pageMode],
  async () => {
    const before = intra.value
    await nextTick()
    measurePages()
    if (isPaged.value) {
      pageIndex.value = Math.min(
        pageCount.value - 1,
        Math.max(0, Math.round(before * Math.max(0, pageCount.value - 1))),
      )
    }
    schedulePush()
  },
)

onMounted(async () => {
  try {
    await store.pull()
  } catch {
    // 配置拉取失败不阻塞阅读。
  }
  await load()
  void flushPending()
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', onResize)
  window.addEventListener('online', flushPending)
})

onUnmounted(() => {
  if (pushTimer) clearTimeout(pushTimer)
  if (resizeTimer) clearTimeout(resizeTimer)
  if (scrollRaf) cancelAnimationFrame(scrollRaf)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', onResize)
  window.removeEventListener('online', flushPending)
  // 离开前补一次上报，避免 800ms 防抖窗口内退出导致进度丢失。
  void pushProgress()
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
    <main
      v-else
      ref="contentEl"
      class="content"
      :class="{ paged: isPaged }"
      :style="readerStyle"
      @click="onContentClick"
      @scroll="onScroll"
    >
      <div ref="pageEl" class="page-track" :style="pageTrackStyle">
        <article v-if="currentChapter">
          <h2 class="ch-title">{{ currentChapter.title }}</h2>
          <!-- EPUB 章节为 XHTML 片段，用 v-html 渲染；TXT 为纯文本用白空间保留。 -->
          <div
            v-if="book && item && item.fileType === 1"
            class="ch-body"
            v-html="currentChapter.content"
          ></div>
          <pre v-else class="ch-body txt">{{ currentChapter.content }}</pre>
        </article>
      </div>
    </main>

    <!-- 底栏 -->
    <footer class="bar bottom">
      <button class="icon-btn" :disabled="!canPrev()" @click="prevPage">
        ‹ {{ isPaged ? '上一页' : '上一章' }}
      </button>
      <span class="pos">
        {{ currentIndex + 1 }} / {{ totalChapters }}
        <template v-if="isPaged && pageCount > 1"> · {{ pageIndex + 1 }}/{{ pageCount }} 页</template>
      </span>
      <span class="pct">{{ progressPercent.toFixed(1) }}%</span>
      <button class="icon-btn" :disabled="!canNext()" @click="nextPage">
        {{ isPaged ? '下一页' : '下一章' }} ›
      </button>
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
          <label>主题</label>
          <div class="seg">
            <button
              v-for="t in THEME_OPTIONS"
              :key="t.value"
              class="seg-item"
              :class="{ active: store.config.theme === t.value }"
              @click="store.config.theme = t.value"
            >
              {{ t.label }}
            </button>
          </div>
        </div>

        <div class="field">
          <label>字号 {{ store.config.fontSize }}px</label>
          <input v-model.number="store.config.fontSize" type="range" min="12" max="28" />
        </div>

        <div class="field">
          <label>行高 {{ store.config.lineHeight.toFixed(1) }}</label>
          <input
            v-model.number="store.config.lineHeight"
            type="range"
            min="1.2"
            max="2.4"
            step="0.1"
          />
        </div>

        <div class="field">
          <label>页边距</label>
          <div class="seg">
            <button
              v-for="m in margins"
              :key="m.value"
              class="seg-item"
              :class="{ active: store.config.pageMargin === m.value }"
              @click="store.config.pageMargin = m.value"
            >
              {{ m.label }}
            </button>
          </div>
        </div>

        <div class="field">
          <label>翻页模式</label>
          <div class="seg">
            <button
              v-for="p in modes"
              :key="p.value"
              class="seg-item"
              :class="{ active: store.config.pageMode === p.value }"
              @click="store.config.pageMode = p.value"
            >
              {{ p.label }}
            </button>
          </div>
        </div>

        <button class="btn btn-primary" :disabled="saving" @click="saveSettings">
          {{ saving ? '保存中…' : '保存设置' }}
        </button>
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

.icon-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
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

/* 分页模式：隐藏纵向滚动，按列横向翻页。 */
.content.paged {
  overflow: hidden;
}

.content.paged .page-track {
  margin: 0 auto;
  column-fill: auto;
  will-change: transform;
  transition: transform 0.18s ease;
}

.ch-title {
  font-size: 1.6em;
  margin: 0 0 20px;
  text-align: center;
}

.ch-body {
  max-width: 720px;
  margin: 0 auto;
  overflow-wrap: break-word;
}

.ch-body.txt {
  font-family: inherit;
  white-space: pre-wrap;
  word-break: break-word;
}

.ch-body :deep(img) {
  max-width: 100%;
  height: auto;
}

.pos,
.pct {
  font-size: 13px;
  color: var(--text-sub);
}

.pos {
  white-space: nowrap;
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
  margin: 0 8px 16px;
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

.seg {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.seg-item {
  border: 1px solid var(--border);
  background: var(--surface);
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 13px;
  color: var(--text);
}

.seg-item.active {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
}

.drawer-body .btn {
  margin: 8px;
  width: calc(100% - 16px);
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
  background: var(--surface);
  color: var(--text);
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
