/**
 * 阅读主题色板。
 *
 * 主题切换不仅作用于阅读页正文（ReaderView 内联样式），
 * 也通过 CSS 变量作用于整站外壳与设置页，做到“点击即预览”。
 * 默认“日间”是浅绿色系，与「页庐」的品牌色一致。
 */

export interface ThemePalette {
  bg: string
  bgGlow1: string
  bgGlow2: string
  surface: string
  surfaceSoft: string
  text: string
  textSub: string
  border: string
  primary: string
  primaryHover: string
  primarySoft: string
}

export const THEMES: Record<number, ThemePalette> = {
  // 日间 · 青禾
  1: {
    bg: '#f4f8f2',
    bgGlow1: '#e3f0de',
    bgGlow2: '#edf7e8',
    surface: '#ffffff',
    surfaceSoft: '#fafcf8',
    text: '#24312a',
    textSub: '#6e7f72',
    border: '#dce8d9',
    primary: '#4f7a52',
    primaryHover: '#3e6442',
    primarySoft: '#e6f1e3',
  },
  // 夜间
  2: {
    bg: '#111a14',
    bgGlow1: '#17241a',
    bgGlow2: '#0e1711',
    surface: '#1a271e',
    surfaceSoft: '#1e2c22',
    text: '#e4ede6',
    textSub: '#9caf9f',
    border: '#2d3e31',
    primary: '#8fc08d',
    primaryHover: '#a5d0a2',
    primarySoft: '#243629',
  },
  // 羊皮纸
  3: {
    bg: '#f6edda',
    bgGlow1: '#f1e3c8',
    bgGlow2: '#f9f1e1',
    surface: '#fbf4e6',
    surfaceSoft: '#fdf8ee',
    text: '#3f3a2e',
    textSub: '#7c6f57',
    border: '#e5d6b8',
    primary: '#8a6a3b',
    primaryHover: '#6e5330',
    primarySoft: '#f0e4cc',
  },
  // 护眼
  4: {
    bg: '#e9f3e6',
    bgGlow1: '#ddedd8',
    bgGlow2: '#f1f8ee',
    surface: '#f8fcf6',
    surfaceSoft: '#fbfdf9',
    text: '#1f3527',
    textSub: '#5b7563',
    border: '#cfe3d2',
    primary: '#3f6f45',
    primaryHover: '#315737',
    primarySoft: '#ddeedc',
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
  root.style.setProperty('--bg-glow-1', palette.bgGlow1)
  root.style.setProperty('--bg-glow-2', palette.bgGlow2)
  root.style.setProperty('--surface', palette.surface)
  root.style.setProperty('--surface-2', palette.surfaceSoft)
  root.style.setProperty('--text', palette.text)
  root.style.setProperty('--text-sub', palette.textSub)
  root.style.setProperty('--border', palette.border)
  root.style.setProperty('--primary', palette.primary)
  root.style.setProperty('--primary-hover', palette.primaryHover)
  root.style.setProperty('--primary-soft', palette.primarySoft)
  root.dataset.theme = String(Number(theme) || 1)
}
