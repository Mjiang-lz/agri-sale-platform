/**
 * 文本展示兜底
 * ------------------------------------------------------------------
 * 历史测试数据里存在以错误编码写入的记录（标题/分类会渲染成 "?" 之类），
 * 列表展示前用这里的方法过滤掉，避免污染首页与知识列表。
 * 只做前端展示过滤，不改动数据库，也不影响接口返回。
 */

/** 文本是否可正常展示（非空且不是乱码） */
export function isReadable(text) {
  if (text === null || text === undefined) return false
  const s = String(text).trim()
  if (!s) return false
  // 包含 Unicode 替换字符说明解码失败
  if (s.includes('\uFFFD')) return false
  /*
   * 至少要有一个「真字」：汉字、字母或数字。
   * 早期版本只判断「整串是否全为问号/菱形」，像 "????:??????" 这种夹着分隔符的
   * 乱码会漏网，仍然会以 "????:??????" 的样子出现在列表里。
   */
  return /[\u4e00-\u9fa5A-Za-z0-9]/.test(s)
}

/** 截断长文本，用于列表摘要 */
export function summary(text, max = 88) {
  if (!isReadable(text)) return ''
  const s = String(text).replace(/\s+/g, ' ').trim()
  return s.length > max ? `${s.slice(0, max)}…` : s
}

/**
 * 时间展示
 * ------------------------------------------------------------------
 * 后端的 LocalDateTime 会被序列化成 ISO 形式（2026-07-17T13:30:00），
 * jackson.date-format 只作用于 java.util.Date，管不到它，
 * 因此列表里会出现一个突兀的「T」。这里在展示层统一成「YYYY-MM-DD HH:mm」。
 */
export function formatDateTime(value) {
  if (value === null || value === undefined || value === '') return ''
  const s = String(value)
  const m = s.match(/^(\d{4})-(\d{2})-(\d{2})[T\s](\d{2}):(\d{2})/)
  if (m) return `${m[1]}-${m[2]}-${m[3]} ${m[4]}:${m[5]}`
  // 只有日期部分（如 value-format 回填）时原样返回
  return s
}
