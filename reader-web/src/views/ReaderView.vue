<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useReaderStore } from '@/stores/reader'
import { bookshelfApi } from '@/api/bookshelf'
import { parserApi } from '@/api/parser'
import { syncApi } from '@/api/sync'
import { getAccessToken } from '@/api/http'
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
const chapterLoading = ref(false)
const chapterError = ref('')

const currentIndex = ref(0)
const showToc = ref(false)
const showSettings = ref(false)
const tocKeyword = ref('')
const tocBodyEl = ref<HTMLElement | null>(null)
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
// 宽屏时一屏显示两页（像翻书），窄屏仍是单页
const twoPage = ref(false)
const PAGE_GAP = 48

// 流式模式下当前滚动位置占可滚动范围的比例（0~1）。
const scrollRatio = ref(0)
const positionRestored = ref(false)

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

// 目录带原始下标，过滤后仍能正确定位章节。
const tocEntries = computed(() => (book.value?.toc ?? []).map((item, index) => ({ item, index })))
const filteredToc = computed(() => {
  const keyword = tocKeyword.value.trim().toLowerCase()
  if (!keyword) return tocEntries.value
  return tocEntries.value.filter(({ item }) => (item.title ?? '').toLowerCase().includes(keyword))
})

/** 目录里当前章的下标（TOC 可能没有 chapterIndex）。 */
function tocChapterIndex(entry: { item: { chapterIndex?: number | null }; index: number }): number {
  return entry.item.chapterIndex ?? entry.index
}

const currentChapter = computed<Chapter | null>(() => {
  if (!book.value) return null
  return book.value.chapters[currentIndex.value] ?? null
})

/**
 * EPUB 章节正文通常自带 <h1>第X章</h1>，若阅读器再渲染一次章节标题就会出现两个标题。
 * 这里检测正文开头的 h1–h3：只要它看起来就是本章标题，就不再显示阅读器的标题；
 * TXT 的标题行已在解析时剥离，仍由阅读器渲染。
 */
const showChapterTitle = computed(() => {
  const chapter = currentChapter.value
  if (!chapter) return false
  if (item.value?.fileType !== 1) return true

  // 去掉 <head>（内联样式可能很长），再看正文开头是否自带标题
  const html = chapter.content.replace(/<head[\s\S]*?<\/head>/i, '')
  const head = html.slice(0, 2000)
  const matched = head.match(/<h[1-3][^>]*>([\s\S]*?)<\/h[1-3]>/i)
  if (!matched) return true

  const heading = matched[1].replace(/<[^>]+>/g, '').replace(/\s+/g, '').trim()
  const title = chapter.title.replace(/\s+/g, '').trim()
  if (!heading || !title) return true

  return !(
    heading.includes(title) ||
    title.includes(heading) ||
    /第[0-9零一二三四五六七八九十百千两]+[章节回卷篇部]/.test(heading) ||
    /^(chapter|section)\s*\d+/i.test(heading)
  )
})

const isPaged = computed(() => store.config.pageMode === 1)
const pageStep = computed(() => (twoPage.value ? 2 : 1))

// 底栏显示的页码范围：单页为 "3/7 页"，两页为 "3-4/7 页"
const pageRange = computed(() => {
  if (!isPaged.value || pageCount.value <= 1) return ''
  const step = pageStep.value
  const start = Math.floor(pageIndex.value / step) * step + 1
  const end = Math.min(start + step - 1, pageCount.value)
  return start === end ? `${start}/${pageCount.value} 页` : `${start}-${end}/${pageCount.value} 页`
})

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
  // 流式模式的正文宽度按浏览器视口比例：窄 3/4、中 1/2、宽 1/3
  const flowWidthMap: Record<number, string> = { 1: '75vw', 2: '50vw', 3: '33.33vw' }
  // 分页模式仍用像素宽（由页宽计算控制）
  const maxWidthMap: Record<number, string> = { 1: '740px', 2: '700px', 3: '640px' }
  return {
    background: palette.surface,
    color: palette.text,
    fontSize: `${c.fontSize}px`,
    lineHeight: String(c.lineHeight),
    padding: `24px ${marginMap[c.pageMargin] ?? '24px'}`,
    '--reader-flow-width': flowWidthMap[c.pageMargin] ?? '50vw',
    '--reader-max-width': maxWidthMap[c.pageMargin] ?? '700px',
  }
})

// 分页轨道的列宽、列间距与横向位移。
const pageTrackStyle = computed(() => {
  if (!isPaged.value) return { width: '100%' }
  const step = pageStep.value
  // 两页模式下每次跨两页，位移也按两页计算
  const spreadStart = Math.floor(pageIndex.value / step) * step
  const width = twoPage.value ? pageWidth.value * 2 + PAGE_GAP : pageWidth.value
  return {
    width: `${width}px`,
    height: `${pageHeight.value}px`,
    columnWidth: `${pageWidth.value}px`,
    columnGap: `${PAGE_GAP}px`,
    transform: `translateX(${-spreadStart * (pageWidth.value + PAGE_GAP)}px)`,
  }
})

// 分页可视窗口：只露出一页或两页，避免宽屏时把后面的列也显示出来
const pageViewportStyle = computed(() => {
  if (!isPaged.value) return { width: '100%' }
  const width = twoPage.value ? pageWidth.value * 2 + PAGE_GAP : pageWidth.value
  return {
    width: `${width}px`,
    height: `${pageHeight.value}px`,
    margin: '0 auto',
    overflow: 'hidden',
  }
})

// 节流上报进度。
let pushTimer: ReturnType<typeof setTimeout> | null = null
// 正在后台补取的章节，避免重复请求。
const prefetching = new Set<number>()

/** 量取可视区域尺寸并重算页数。 */
function measurePages() {
  const el = contentEl.value
  if (!el) return
  const styles = getComputedStyle(el)
  const padX = parseFloat(styles.paddingLeft) + parseFloat(styles.paddingRight)
  const padY = parseFloat(styles.paddingTop) + parseFloat(styles.paddingBottom)
  const available = Math.max(120, el.clientWidth - padX)

  // 宽屏一屏两页；页边距同时影响每页最大宽度，保证“窄/中/宽”能看出区别
  twoPage.value = available >= 1000
  const cap = store.config.pageMargin === 1 ? 740 : store.config.pageMargin === 3 ? 640 : 700
  pageWidth.value = twoPage.value
    ? Math.min(cap, Math.max(120, Math.floor((available - PAGE_GAP) / 2)))
    : Math.min(cap, available)
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
  pageIndex.value = Math.min(pageIndex.value, pageCount.value - 1)
  // 两页模式对齐到左页，避免停在右半页
  pageIndex.value = Math.floor(pageIndex.value / pageStep.value) * pageStep.value
}

/** 把 0~1 的章内位置换算成页码或滚动距离。 */
async function applyIntra(target: number) {
  await nextTick()
  measurePages()
  const ratio = Math.min(1, Math.max(0, target))
  if (isPaged.value) {
    const step = pageStep.value
    let index = Math.round(ratio * Math.max(0, pageCount.value - 1))
    // 两页模式按“跨页起点”对齐，避免落在右半页
    index = Math.floor(index / step) * step
    pageIndex.value = Math.min(index, Math.max(0, Math.floor((pageCount.value - 1) / step) * step))
  } else if (contentEl.value) {
    contentEl.value.scrollTop =
      ratio * Math.max(0, contentEl.value.scrollHeight - contentEl.value.clientHeight)
    scrollRatio.value = ratio
  }
}

/** 按需加载某一章的正文；已加载过则直接返回。 */
async function loadChapter(index: number) {
  const chapter = book.value?.chapters[index]
  if (!chapter || !item.value) return
  if (chapter.content) {
    void prefetchChapter(index + 1)
    return
  }

  chapterLoading.value = true
  chapterError.value = ''
  try {
    const loaded = await parserApi.chapter(item.value.fileId, index)
    chapter.content = loaded.content ?? ''
  } catch (e) {
    chapterError.value = (e as Error).message || '本章加载失败，请稍后重试'
  } finally {
    chapterLoading.value = false
  }
  await nextTick()
  measurePages()
  // 静默预取下一章，翻页时直接命中内存，不再等接口。
  void prefetchChapter(index + 1)
}

/** 后台补取章节：不触发“正在加载本章”，也不重新分页。 */
async function prefetchChapter(index: number) {
  const chapter = book.value?.chapters[index]
  if (!chapter || chapter.content || !item.value || prefetching.has(index)) return
  prefetching.add(index)
  try {
    const loaded = await parserApi.chapter(item.value.fileId, index)
    chapter.content = loaded.content ?? ''
  } catch {
    // 预取失败不打扰用户，真正翻到时会再请求一次。
  } finally {
    prefetching.delete(index)
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
  await loadChapter(currentIndex.value)
  await applyIntra(target)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const list = await bookshelfApi.list()
    item.value = list.find((b) => b.id === itemId) ?? null
    if (!item.value) throw new Error('书架中找不到这本书')

    // 文件内容按 SHA 寻址，解析结果不会变化：命中缓存直接使用，
    // 不再每次打开都重新下载整本书，避免大书重复占用带宽。
    const cached = await getCachedBook(item.value.fileId)
    if (cached) {
      book.value = cached
      loading.value = false
      await restorePosition()
      return
    }

    const parsed = await parserApi.parse(item.value.fileId)
    book.value = parsed
    void putCachedBook(item.value.fileId, parsed)
    loading.value = false
    await restorePosition()
  } catch (e) {
    error.value = (e as Error).message || '加载书籍失败'
  } finally {
    loading.value = false
  }
}

async function gotoChapter(i: number, lastPage = false) {
  if (i < 0 || i >= totalChapters.value) return
  currentIndex.value = i
  showToc.value = false

  await loadChapter(i)
  await nextTick()
  measurePages()
  if (!isPaged.value && contentEl.value) {
    // 流式模式切章后回到章首，避免沿用上一章的滚动位置。
    contentEl.value.scrollTop = 0
    scrollRatio.value = 0
  }
  if (lastPage) {
    const step = pageStep.value
    pageIndex.value = Math.max(0, Math.floor((pageCount.value - 1) / step) * step)
  }
  schedulePush()
}

function prevPage() {
  if (!isPaged.value) {
    gotoChapter(currentIndex.value - 1)
    return
  }
  const step = pageStep.value
  if (pageIndex.value >= step) {
    pageIndex.value -= step
    return
  }
  // 当前章第一屏：翻到上一章最后一屏
  if (currentIndex.value > 0) gotoChapter(currentIndex.value - 1, true)
}

function nextPage() {
  if (!isPaged.value) {
    gotoChapter(currentIndex.value + 1)
    return
  }
  const step = pageStep.value
  if (pageIndex.value + step < pageCount.value) {
    pageIndex.value += step
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
    return isPaged.value ? pageIndex.value + pageStep.value < pageCount.value : false
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

/** 页面隐藏/关闭时用 keepalive 直接发一次，避免卸载时请求被浏览器取消。 */
function pushProgressKeepalive() {
  if (!item.value) return
  const token = getAccessToken()
  if (!token) return
  const payload = {
    cfi: cfi.value,
    percent: Math.round(progressPercent.value * 100) / 100,
    syncVersion: item.value.syncVersion,
  }
  try {
    void fetch(`/api/v1/sync/progress/${itemId}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(payload),
      keepalive: true,
    }).catch(() => {
      // 关闭页面时失败不提示，下次打开会以服务端版本为准。
    })
  } catch {
    // fetch 本身同步抛错（极少数环境）时忽略。
  }
}

function onVisibilityChange() {
  if (document.visibilityState === 'hidden') {
    void pushProgress()
  }
}

function onPageHide() {
  pushProgressKeepalive()
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

// 打开目录时清空搜索，并把当前章滚到可视区域中间。
watch(showToc, async (open) => {
  if (!open) return
  tocKeyword.value = ''
  await nextTick()
  requestAnimationFrame(() => {
    tocBodyEl.value
      ?.querySelector<HTMLElement>('.toc-item.active')
      ?.scrollIntoView({ block: 'center' })
  })
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
  window.addEventListener('pagehide', onPageHide)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  if (pushTimer) clearTimeout(pushTimer)
  if (resizeTimer) clearTimeout(resizeTimer)
  if (scrollRaf) cancelAnimationFrame(scrollRaf)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', onResize)
  window.removeEventListener('online', flushPending)
  window.removeEventListener('pagehide', onPageHide)
  document.removeEventListener('visibilitychange', onVisibilityChange)
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
    <div v-if="loading" class="center">
      <p>加载中…</p>
      <p class="text-sub" style="font-size: 13px">
        首次打开较大的书需要解析，可能需要几十秒，请稍候
      </p>
    </div>
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
      <div class="page-viewport" :style="pageViewportStyle">
        <div ref="pageEl" class="page-track" :style="pageTrackStyle">
          <article v-if="currentChapter">
            <h2 v-if="showChapterTitle" class="ch-title">{{ currentChapter.title }}</h2>
            <div v-if="chapterLoading" class="chapter-tip">正在加载本章…</div>
            <div v-else-if="chapterError" class="chapter-tip">{{ chapterError }}</div>
            <!-- EPUB 章节为 XHTML 片段，用 v-html 渲染；TXT 为纯文本用白空间保留。 -->
            <div
              v-else-if="book && item && item.fileType === 1"
              class="ch-body"
              v-html="currentChapter.content"
            ></div>
            <pre v-else class="ch-body txt">{{ currentChapter.content }}</pre>
          </article>
        </div>
      </div>
    </main>

    <!-- 底栏 -->
    <footer class="bar bottom">
      <button class="icon-btn" :disabled="!canPrev()" @click="prevPage">
        ‹ {{ isPaged ? '上一页' : '上一章' }}
      </button>
      <span class="pos">
        {{ currentIndex + 1 }} / {{ totalChapters }}
        <template v-if="isPaged && pageCount > 1"> · {{ pageRange }}</template>
      </span>
      <span class="pct">{{ progressPercent.toFixed(1) }}%</span>
      <button class="icon-btn" :disabled="!canNext()" @click="nextPage">
        {{ isPaged ? '下一页' : '下一章' }} ›
      </button>
    </footer>

    <!-- 目录抽屉 -->
    <div v-if="showToc" class="drawer">
      <div class="drawer-head">
        <span>目录</span>
        <button class="drawer-close" aria-label="关闭目录" @click="showToc = false">×</button>
      </div>
      <div ref="tocBodyEl" class="drawer-body">
        <div class="toc-search">
          <input v-model="tocKeyword" class="input" type="text" placeholder="搜索章节…" />
        </div>
        <div v-if="!tocEntries.length" class="toc-empty">无目录</div>
        <div v-else-if="!filteredToc.length" class="toc-empty">没有匹配的章节</div>
        <template v-else>
          <div
            v-for="entry in filteredToc"
            :key="entry.index"
            class="toc-item"
            :class="{ active: tocChapterIndex(entry) === currentIndex }"
            @click="gotoChapter(tocChapterIndex(entry))"
          >
            {{ entry.item.title }}
          </div>
        </template>
      </div>
    </div>

    <!-- 设置抽屉 -->
    <div v-if="showSettings" class="drawer">
      <div class="drawer-head">
        <span>阅读设置</span>
        <button class="drawer-close" aria-label="关闭设置" @click="showSettings = false">×</button>
      </div>
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
              <span class="theme-dot" :class="`theme-${t.value}`"></span>
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
  height: 100vh;
  min-height: 100vh;
  overflow: hidden;
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
  /* 必须限制在视口高度内：否则内容会把 flex 项撑高，分页时量到整章高度，页数永远为 1 */
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  transition: background 0.2s, color 0.2s;
}

/* 分页可视窗口：只显示当前一页/两页，由 JS 设置宽度和高度 */
.page-viewport {
  width: 100%;
}

/* 分页模式：隐藏纵向滚动，按列横向翻页。 */
.content.paged {
  overflow: hidden;
}

.content.paged .page-track {
  margin: 0;
  column-fill: auto;
  will-change: transform;
  transition: transform 0.18s ease;
}

.ch-title {
  font-size: 1.6em;
  margin: 0 0 20px;
  text-align: center;
}

.chapter-tip {
  text-align: center;
  color: var(--text-sub);
  font-size: 14px;
  padding: 40px 0;
}

.ch-body {
  max-width: min(var(--reader-max-width, 720px), 100%);
  margin: 0 auto;
  overflow-wrap: break-word;
}

/* 流式模式：正文宽度按浏览器界面比例铺开（3/4、1/2、1/3），并保留最小可读宽度 */
.content:not(.paged) .ch-body {
  max-width: min(100%, max(320px, var(--reader-flow-width, 50vw)));
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

/* ── 页庐 · 阅读器视觉 ─────────────────────────── */
.bar {
  gap: 10px;
  padding: 9px 18px;
  background: color-mix(in srgb, var(--surface) 90%, transparent);
  backdrop-filter: blur(12px);
}

.bar.top {
  height: 60px;
  box-shadow: 0 1px 0 rgba(36, 49, 42, 0.04);
}

.bar.bottom {
  height: 56px;
  padding: 8px 18px;
}

.bar-top .bar-title,
.bar-title {
  font-family: var(--serif);
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.bar.top > .icon-btn:first-child {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border-radius: 50%;
  background: var(--surface-2);
  color: var(--text-sub);
  font-size: 16px;
}

.bar.top > .icon-btn:first-child:hover {
  background: var(--primary-soft);
  color: var(--primary-hover);
}

.bar-actions .icon-btn {
  padding: 7px 13px;
  border-radius: 999px;
  font-size: 13px;
  color: var(--text-sub);
}

.bar-actions .icon-btn:hover {
  background: var(--primary-soft);
  color: var(--primary-hover);
}

.ch-title {
  margin-bottom: 26px;
  font-family: var(--serif);
  font-size: 1.55em;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.ch-body {
  text-align: justify;
  letter-spacing: 0.01em;
}

.chapter-tip {
  color: var(--text-sub);
}

.bar.bottom .icon-btn {
  padding: 7px 14px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--surface);
  font-size: 12.5px;
  color: var(--text-sub);
}

.bar.bottom .icon-btn:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--primary) 38%, var(--border));
  background: var(--primary-soft);
  color: var(--primary-hover);
}

.pos {
  font-size: 12.5px;
}

.pct {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--primary);
}

.drawer {
  width: 340px;
  border-right: 1px solid var(--border);
  box-shadow: 18px 0 48px rgba(24, 37, 27, 0.12);
}

.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  font-family: var(--serif);
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.03em;
}

.drawer-close {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: var(--surface-2);
  color: var(--text-sub);
  font-size: 18px;
  line-height: 1;
}

.drawer-close:hover {
  background: var(--primary-soft);
  color: var(--primary-hover);
}

.drawer-body {
  padding: 10px 12px 16px;
}

.toc-search {
  position: sticky;
  top: 0;
  z-index: 1;
  padding: 2px 0 10px;
  background: var(--surface);
}

.toc-search .input {
  height: 38px;
  padding: 8px 12px;
  font-size: 13px;
}

.toc-empty {
  padding: 18px 12px;
  text-align: center;
  font-size: 13px;
  color: var(--text-sub);
}

.toc-item {
  padding: 11px 14px;
  border-radius: 10px;
  font-size: 13.5px;
  color: var(--text-sub);
  transition: background 0.16s ease, color 0.16s ease;
}

.toc-item:hover {
  background: var(--surface-2);
  color: var(--text);
}

.toc-item.active {
  background: var(--primary-soft);
  color: var(--primary-hover);
  font-weight: 600;
}

.field {
  margin: 0 4px 16px;
}

.field label {
  margin-bottom: 8px;
  font-size: 12.5px;
}

.seg {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.seg-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--surface-2);
  font-size: 12.5px;
  color: var(--text-sub);
}

.theme-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 1px solid rgba(0, 0, 0, 0.08);
  flex: none;
}

.theme-1 {
  background: #6f9a6d;
}

.theme-2 {
  background: #2a3a2e;
}

.theme-3 {
  background: #d8c29a;
}

.theme-4 {
  background: #7fae7d;
}

.seg-item:hover {
  border-color: color-mix(in srgb, var(--primary) 35%, var(--border));
  color: var(--text);
}

.seg-item.active {
  background: var(--primary-soft);
  border-color: color-mix(in srgb, var(--primary) 45%, var(--border));
  color: var(--primary-hover);
  font-weight: 600;
}

.field input[type='range'] {
  accent-color: var(--primary);
  height: 4px;
  cursor: pointer;
}

.drawer-body .btn {
  width: calc(100% - 8px);
  height: 40px;
  margin: 4px 4px 10px;
  font-size: 13.5px;
}

.modal {
  background: color-mix(in srgb, #1b2a1f 38%, transparent);
  backdrop-filter: blur(3px);
}

.modal-card {
  padding: 24px;
  border-radius: 18px;
  box-shadow: var(--shadow-md);
}

.modal-card h3 {
  margin: 0 0 6px;
  font-family: var(--serif);
  font-size: 20px;
  font-weight: 600;
}

.tag {
  border-radius: 999px;
  padding: 3px 9px;
  background: var(--primary-soft);
  color: var(--primary-hover);
}

@media (max-width: 640px) {
  .drawer {
    width: 86vw;
    max-width: 340px;
  }

  .bar-actions .icon-btn {
    padding: 7px 9px;
  }

  .bar.bottom {
    padding: 8px 10px;
  }

  .bar.bottom .icon-btn {
    padding: 7px 9px;
    font-size: 12px;
  }

  .pos {
    font-size: 11.5px;
  }
}
</style>
