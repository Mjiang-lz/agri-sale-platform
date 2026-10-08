import request from './request'

/**
 * M6 数据看板接口（管理后台 · 仪表盘）。
 *
 * 一个接口一次返回全部指标，前端不再为每张图单独发请求：
 * 总量 9 项、待办 2 项、分布 3 组、近 7 日趋势 3 组。
 * 后端全部为实时 COUNT，无缓存，所以每次刷新都是最新数字。
 *
 * - 平台运行指标汇总：GET /api/admin/dashboard/statistics
 */
export function getDashboardStatistics() {
  return request.get('/admin/dashboard/statistics')
}
