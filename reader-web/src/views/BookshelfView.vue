<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import TopBar from '@/components/TopBar.vue'
import { bookshelfApi } from '@/api/bookshelf'
import { fileApi } from '@/api/file'
import { sha256Hex } from '@/utils/sha256'
import type { BookshelfItem } from '@/types/api'

const router = useRouter()

const items = ref<BookshelfItem[]>([])
const keyword = ref('')
const loading = ref(false)
const uploading = ref(false)
const uploadMsg = ref('')
const fileInput = ref<HTMLInputElement>()

async function load(keywordStr?: string) {
  loading.value = true
  try {
    items.value = await bookshelfApi.list(keywordStr || undefined)
  } catch (e) {
    alert((e as Error).message || '加载书架失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  load(keyword.value.trim())
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

async function onRemove(item: BookshelfItem) {
  if (!confirm(`确定要从书架移除《${item.displayTitle}》吗？`)) return
  try {
    await bookshelfApi.remove(item.id)
    await load(keyword.value.trim())
  } catch (e) {
    alert((e as Error).message || '移除失败')
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
  return `${(Number(p)).toFixed(1)}%`
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

      <div v-else class="grid">
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
          </div>
          <div class="actions">
            <button class="btn" @click="onRename(item)">重命名</button>
            <button class="btn btn-danger" @click="onRemove(item)">移除</button>
          </div>
        </div>
      </div>
    </main>
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
}

.search {
  display: flex;
  gap: 8px;
  flex: 1;
}

.search .input {
  max-width: 360px;
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
</style>
