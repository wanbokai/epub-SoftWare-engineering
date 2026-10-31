/**
 * 阅读位置锚点（Locator）。
 *
 * 锚点格式：`epubcfi(/6/<spine 步长>!/4)`。
 * EPUB CFI 中 `/6` 指向 spine，spine 内的 itemref 步长从 2 开始、每次 +2，
 * 因此第 n 章（0 基）对应 `2 * (n + 1)`。
 *
 * 旧实现写入 `epubcfi(/6/${index + 1})`，恢复时却用 /\((\d+)\)/ 匹配，
 * 由于括号后紧跟 `/`，正则永远不命中，导致"恢复上次位置"从未生效。
 * 这里统一为可稳定解析的格式，并对历史数据做兼容（见 toLegacyIndex）。
 */

/** 由章节下标生成锚点。 */
export function buildCfi(chapterIndex: number): string {
  const safe = Math.max(0, Math.floor(chapterIndex))
  return `epubcfi(/6/${2 * (safe + 1)}!/4)`
}

/**
 * 解析锚点得到章节下标；无法识别时返回 -1。
 * 兼容旧数据：旧格式为 `/6/${index + 1}`（无 `!`），按 n - 1 换算。
 */
export function parseCfi(cfi: string | null | undefined): number {
  if (!cfi) return -1
  const matched = cfi.match(/^epubcfi\(\/6\/(\d+)(!)?/)
  if (!matched) return -1
  const step = Number(matched[1])
  if (!Number.isFinite(step) || step < 1) return -1
  if (matched[2]) {
    // 新格式：spine 步长为偶数
    if (step % 2 !== 0) return -1
    return step / 2 - 1
  }
  // 旧格式：/6/(index + 1)
  return step - 1
}

/**
 * 判断是否为当前版本写入的锚点（带 `!` 的 CFI 结构）。
 * 旧版本只写章节号、且进度百分比按整章估算，不能用来还原章内位置。
 */
export function isModernCfi(cfi: string | null | undefined): boolean {
  return !!cfi && /^epubcfi\(\/6\/\d+!/.test(cfi)
}

/**
 * 由全书百分比反推"章内位置"（0~1），用于恢复阅读位置。
 *
 * @param percent      全书进度百分比（0~100）
 * @param chapterIndex 当前章节下标
 * @param totalChapters 总章数
 */
export function intraFromPercent(
  percent: number | string | null | undefined,
  chapterIndex: number,
  totalChapters: number,
): number {
  const value = Number(percent)
  if (!Number.isFinite(value) || totalChapters <= 0) return 0
  const overall = Math.min(100, Math.max(0, value)) / 100
  const intra = overall * totalChapters - chapterIndex
  return Math.min(1, Math.max(0, intra))
}

/**
 * 由章节下标与章内位置计算全书百分比（0~100）。
 */
export function percentOf(chapterIndex: number, intra: number, totalChapters: number): number {
  if (totalChapters <= 0) return 0
  const ratio = (chapterIndex + Math.min(1, Math.max(0, intra))) / totalChapters
  return Math.min(100, Math.max(0, ratio * 100))
}
