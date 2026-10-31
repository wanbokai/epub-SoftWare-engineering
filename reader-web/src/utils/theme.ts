/**
 * 阅读主题色板。
 *
 * 主题切换不仅作用于阅读页正文（ReaderView 内联样式），
 * 也通过 CSS 变量作用于整站外壳与设置页，做到"点击即预览"。
 */

export interface ThemePalette {
  bg: string
  surface: string
  text: string
  textSub: string
  border: string
  primary: string
}

export const THEMES: Record<number, ThemePalette> = {
  // 日间
  1: {
    bg: '#f7f8fa',
    surface: '#ffffff',
    text: '#1f2937',
    textSub: '#6b7280',
    border: '#e5e7eb',
    primary: '#3b82f6',
  },
  // 夜间
  2: {
    bg: '#111827',
    surface: '#1f2937',
    text: '#e5e7eb',
    textSub: '#9ca3af',
    border: '#374151',
    primary: '#60a5fa',
  },
  // 羊皮纸
  3: {
    bg: '#f5e9d3',
    surface: '#fbf3e2',
    text: '#3f3a2e',
    textSub: '#7c6f57',
    border: '#e2d3b3',
    primary: '#b45309',
  },
  // 护眼
  4: {
    bg: '#e8f3e9',
    surface: '#f5fbf5',
    text: '#1f3527',
    textSub: '#5b7563',
    border: '#cfe3d2',
    primary: '#15803d',
  },
}

export const THEME_OPTIONS = [
  { value: 1, label: '日间' },
  { value: 2, label: '夜间' },
  { value: 3, label: '羊皮纸' },
  { value: 4, label: '护眼' },
]

export function themeOf(theme: number | null | undefined): ThemePalette {
  return THEMES[Number(theme)] ?? THEMES[1]
}

/** 把主题色板写入根元素 CSS 变量，全站即时生效。 */
export function applyTheme(theme: number | null | undefined): void {
  if (typeof document === 'undefined') return
  const palette = themeOf(theme)
  const root = document.documentElement
  root.style.setProperty('--bg', palette.bg)
  root.style.setProperty('--surface', palette.surface)
  root.style.setProperty('--text', palette.text)
  root.style.setProperty('--text-sub', palette.textSub)
  root.style.setProperty('--border', palette.border)
  root.style.setProperty('--primary', palette.primary)
  root.dataset.theme = String(Number(theme) || 1)
}
