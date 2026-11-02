<script setup lang="ts">
import { ref, onMounted } from 'vue'
import TopBar from '@/components/TopBar.vue'
import { useReaderStore } from '@/stores/reader'

const store = useReaderStore()
const saving = ref(false)
const saved = ref(false)

const themes = [
  { value: 1, label: '日间' },
  { value: 2, label: '夜间' },
  { value: 3, label: '羊皮纸' },
  { value: 4, label: '护眼' },
]

const margins = [
  { value: 1, label: '窄' },
  { value: 2, label: '中' },
  { value: 3, label: '宽' },
]

const modes = [
  { value: 1, label: '分页' },
  { value: 2, label: '流式' },
]

async function save() {
  saving.value = true
  saved.value = false
  try {
    await store.save()
    saved.value = true
  } catch (e) {
    alert((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  store.pull().catch(() => {})
})
</script>

<template>
  <div class="page">
    <TopBar />

    <main class="container">
      <div class="page-head">
        <div class="eyebrow">READING</div>
        <h1>阅读设置</h1>
        <p class="text-sub">这些设置会通过云同步保存，并在多设备间保持一致。</p>
      </div>

      <div class="card settings-card">
        <div class="field">
          <label>主题</label>
          <div class="seg">
            <button
              v-for="t in themes"
              :key="t.value"
              class="seg-item"
              :class="{ active: store.config.theme === t.value }"
              @click="store.config.theme = t.value"
            >
              <span class="dot" :class="`theme-${t.value}`"></span>
              {{ t.label }}
            </button>
          </div>
        </div>

        <div class="field">
          <label>字号：{{ store.config.fontSize }}px</label>
          <input
            v-model.number="store.config.fontSize"
            class="range"
            type="range"
            min="12"
            max="28"
            step="1"
          />
        </div>

        <div class="field">
          <label>行高：{{ store.config.lineHeight.toFixed(1) }} 倍</label>
          <input
            v-model.number="store.config.lineHeight"
            class="range"
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

        <div class="actions">
          <button class="btn btn-primary" :disabled="saving" @click="save">
            {{ saving ? '保存中…' : '保存设置' }}
          </button>
          <span v-if="saved" class="saved">已保存 ✓</span>
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
  max-width: 640px;
  margin: 0 auto;
  padding: 24px;
}

h1 {
  margin: 0 0 4px;
  font-size: 22px;
}

.field {
  margin-top: 24px;
}

.field label {
  display: block;
  font-size: 14px;
  color: var(--text-sub);
  margin-bottom: 10px;
}

.seg {
  display: inline-flex;
  gap: 8px;
}

.seg-item {
  border: 1px solid var(--border);
  background: var(--surface);
  border-radius: 8px;
  padding: 8px 18px;
  font-size: 14px;
  color: var(--text);
}

.seg-item.active {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
}

.range {
  width: 100%;
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 32px;
}

.saved {
  color: #10b981;
  font-size: 14px;
}

/* ── 页庐 · 设置页视觉 ─────────────────────────── */
.page {
  background: transparent;
}

.container {
  max-width: 760px;
  padding: 36px 28px 64px;
}

.page-head {
  margin-bottom: 22px;
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

.settings-card {
  padding: 6px 26px 24px;
  border-radius: 18px;
}

.field {
  margin-top: 0;
  padding: 20px 2px;
  border-bottom: 1px solid var(--border);
}

.field:last-of-type {
  border-bottom: none;
}

.field label {
  margin-bottom: 12px;
  font-size: 13.5px;
  letter-spacing: 0.01em;
}

.seg {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.seg-item {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 16px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--surface-2);
  font-size: 13.5px;
  color: var(--text-sub);
  transition: background 0.18s ease, border-color 0.18s ease, color 0.18s ease,
    transform 0.18s ease;
}

.seg-item:hover {
  border-color: color-mix(in srgb, var(--primary) 38%, var(--border));
  color: var(--text);
}

.seg-item.active {
  background: var(--primary-soft);
  border-color: color-mix(in srgb, var(--primary) 45%, var(--border));
  color: var(--primary-hover);
  font-weight: 600;
}

.dot {
  width: 11px;
  height: 11px;
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

.range {
  width: 100%;
  height: 6px;
  accent-color: var(--primary);
  cursor: pointer;
}

.actions {
  gap: 14px;
  margin-top: 26px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}

.actions .btn-primary {
  min-width: 160px;
  height: 44px;
  font-size: 14.5px;
}

.saved {
  color: var(--primary);
  font-size: 13px;
}

@media (max-width: 720px) {
  .container {
    padding: 24px 16px 48px;
  }

  .page-head h1 {
    font-size: 25px;
  }

  .settings-card {
    padding: 4px 18px 20px;
  }
}
</style>
