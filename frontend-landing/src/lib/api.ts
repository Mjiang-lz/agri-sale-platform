/**
 * 平台接口客户端。
 *
 * 对应 agri-sale-platform 后端（Spring Boot :8080，context-path /api）。
 * 只调用 PublicEndpoints 白名单里的**公开只读**接口，不需要登录：
 *   GET /api/home/products     首页推荐商品
 *   GET /api/home/knowledge    首页最新农业知识
 *   GET /api/home/stats        首页平台运行数据（在售货源 / 有效求购 / 知识条数）
 *   GET /api/trade/products    商品货源列表（支持关键词/品类/价格区间/产地）
 *   GET /api/demand/list       求购需求列表
 *
 * 注意：GET /api/trade/products/{id}（详情）**不在白名单**，游客打不开，
 * 所以卡片不要深链到详情页，跳公开列表页即可。
 *
 * 本文件只负责取平台真实数据，**不提供任何演示/兜底数据** ——
 * 页面上出现的每个商品、每个数字都必须能在库里查到，理由见 useRemote.ts。
 */

export const API_BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '/api'

/** 平台业务界面（Vue 应用）地址，用于「进入平台 / 登录 / 免费入驻」等跳转 */
export const PLATFORM_BASE =
  (import.meta.env.VITE_PLATFORM_BASE_URL as string | undefined) ?? 'http://localhost:5173'

/** 上传产物的挂载前缀；OBS 模式下后端直接给完整 URL，这里只兜本地 /files/** */
const FILE_BASE = (import.meta.env.VITE_FILE_BASE_URL as string | undefined) ?? ''

export type ApiProduct = {
  productId: number
  productName: string
  category?: string
  price?: number | string
  stock?: number
  unit?: string
  imageUrl?: string
  origin?: string
  sellerId?: number
  auditStatus?: string
  status?: number
}

export type ApiKnowledge = {
  knowledgeId: number
  title: string
  category?: string
  content?: string
  imageUrl?: string
  viewCount?: number
  createTime?: string
}

/** 首页平台运行数据（GET /home/stats） */
export type HomeStats = {
  /** 在售货源数：审核通过且已上架 */
  productCount?: number
  /** 有效求购数：状态有效且未过有效期 */
  demandCount?: number
  /** 农业知识数：审核通过 */
  knowledgeCount?: number
  /** 知识分类分布，后端已按条数倒序 */
  knowledgeByCategory?: Record<string, number>
}

/** 后端统一返回体：{ code, message, data } */
type Result<T> = { code: number; message?: string; data: T }

/** MyBatis-Plus 分页结果 */
export type ApiPage<T> = { records: T[]; total: number; size?: number; current?: number }

const DEFAULT_TIMEOUT = 4000

async function fetchApi<T>(path: string, timeout = DEFAULT_TIMEOUT): Promise<T> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeout)
  try {
    const res = await fetch(`${API_BASE}${path}`, {
      signal: controller.signal,
      headers: { Accept: 'application/json' },
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const body = (await res.json()) as Result<T>
    if (body?.code !== 200) throw new Error(body?.message ?? `接口返回 code=${body?.code}`)
    return body.data
  } finally {
    clearTimeout(timer)
  }
}

/** 首页推荐商品（公开） */
export const fetchHotProducts = (limit = 6) =>
  fetchApi<ApiProduct[]>(`/home/products?limit=${limit}`)

/** 首页最新农业知识（公开） */
export const fetchHomeNews = (limit = 6) =>
  fetchApi<ApiKnowledge[]>(`/home/knowledge?limit=${limit}`)

/**
 * 首页平台运行数据（公开）。
 *
 * 用于落地页展示真实平台规模。**不要把数字写死在前端** ——
 * 「在售货源 / 有效求购 / 知识条数」三个口径都由后端统一定义
 * （见后端 HomeStatisticsVO 的说明），前端只负责显示。
 */
export const fetchHomeStats = () => fetchApi<HomeStats>('/home/stats')

/** 商品货源检索（公开，游客可访问） */
export function fetchProductPage(
  opts: { pageNum?: number; pageSize?: number; category?: string; keyword?: string } = {},
) {
  const query = new URLSearchParams()
  query.set('pageNum', String(opts.pageNum ?? 1))
  query.set('pageSize', String(opts.pageSize ?? 50))
  if (opts.category) query.set('category', opts.category)
  if (opts.keyword) query.set('keyword', opts.keyword)
  return fetchApi<ApiPage<ApiProduct>>(`/trade/products?${query.toString()}`)
}

/** 求购需求列表（公开） */
export const fetchDemandList = (pageSize = 6) =>
  fetchApi<ApiPage<unknown>>(`/demand/list?pageNum=1&pageSize=${pageSize}`)

/** 组装图片地址：完整 URL 原样用，相对路径补 /files 前缀 */
export function resolveImage(url?: string): string | null {
  if (!url) return null
  const trimmed = url.trim()
  if (!trimmed) return null
  if (/^https?:\/\//i.test(trimmed) || trimmed.startsWith('data:')) return trimmed
  return `${FILE_BASE}${trimmed.startsWith('/') ? '' : '/'}${trimmed}`
}

/** 拼平台页面地址 */
export const platformUrl = (path: string) => `${PLATFORM_BASE}${path.startsWith('/') ? path : `/${path}`}`

/** 后端 LocalDateTime 字符串 → 中文日期 */
export function formatCnDate(value?: string): string {
  if (!value) return ''
  const normalized = value.replace(' ', 'T')
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) return value.slice(0, 10)
  return `${date.getFullYear()} 年 ${date.getMonth() + 1} 月 ${date.getDate()} 日`
}

/** 价格展示：后端 BigDecimal 可能是字符串 */
export function formatPrice(value?: number | string): string {
  if (value === undefined || value === null || value === '') return '面议'
  const num = typeof value === 'number' ? value : Number(value)
  if (Number.isNaN(num)) return String(value)
  return num >= 100 ? num.toFixed(0) : num.toFixed(2).replace(/\.?0+$/, '')
}
