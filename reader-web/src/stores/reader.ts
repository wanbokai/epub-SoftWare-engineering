import { defineStore } from 'pinia'
import { ref } from 'vue'
import { syncApi } from '@/api/sync'
import type { ReaderConfig } from '@/types/api'

/**
 * 阅读器排版配置 store。默认值：日间主题、16px、行高 1.6、中边距、分页模式。
 */
const DEFAULT_CONFIG: ReaderConfig = {
  id: 0,
  userId: 0,
  theme: 1,
  fontSize: 16,
  lineHeight: 1.6,
  pageMargin: 2,
  pageMode: 1,
  syncVersion: 0,
  updatedAt: '',
}

export const useReaderStore = defineStore('reader', () => {
  const config = ref<ReaderConfig>({ ...DEFAULT_CONFIG })

  async function pull() {
    const resp = await syncApi.pull()
    if (resp.config) {
      config.value = { ...DEFAULT_CONFIG, ...resp.config }
    }
    return resp
  }

  async function save() {
    const c = config.value
    const newVersion = await syncApi.pushConfig({
      theme: c.theme,
      fontSize: c.fontSize,
      lineHeight: c.lineHeight,
      pageMargin: c.pageMargin,
      pageMode: c.pageMode,
      syncVersion: c.syncVersion,
    })
    config.value.syncVersion = newVersion
  }

  return { config, pull, save }
})
