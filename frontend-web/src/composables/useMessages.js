import { computed, ref } from 'vue'
import request from '@/api/request'
import { useUserStore } from '@/store/user'
import { isPendingApply } from '@/constants/finance'

/**
 * 顶栏「消息提醒」铃铛的数据源。
 *
 * ★ 设计前提：**不新建通知表、不造假消息**。这里每一条都是从后端已有业务数据
 *   实时派生出来的「待办」与「已发生的结果」，用户点进去能看到对应的真实记录。
 *   这样做的好处是不会出现「红点 3 条、点开什么都没有」这种最伤信任的情况。
 *
 * 派生口径按角色分流（与 MainLayout 的菜单分流同一套判据 —— 看**登录账号的真实角色**，
 * 不用 activeRole，否则会出现「菜单没有但接口能调」的错位）：
 *
 *   农户 / 采购方 / 管理员（有 trade 权限）
 *     · 商品待平台审核（auditStatus === '待审核'）   → /trade/my-products
 *     · 订单待发货（我是卖家）                        → /trade/orders
 *     · 订单待付款 / 待收货（我是买家）                → /trade/orders
 *     · 专家回复了你的提问（status === '已回复'）       → /expert/qa      ← 用户点名要的
 *     · 预约有了新进展（已接受/已完成/已拒绝）          → /expert/appointment
 *   专家
 *     · 农户提问等你回复                              → /expert/qa
 *     · 预约待你处理                                  → /expert/appointment
 *   银行用户
 *     · 融资申请待审批（applyStatus === '已推送银行'）  → /finance/bank-review
 *
 * 失败策略：每一条独立 try/catch，某一条挂掉不影响其它条目；整体异常时**保留上一次
 * 的结果**，不清空 —— 否则网络抖一下铃铛就从 3 跳成 0，比不显示更糟。
 */

const items = ref([])
const loading = ref(false)
const loaded = ref(false)
let lastLoadAt = 0

/** 30 秒内的重复请求直接复用缓存，避免每次点铃铛都打六七个接口 */
const FRESH_MS = 30_000

const listOf = (d) => (Array.isArray(d) ? d : Array.isArray(d?.records) ? d.records : [])

/** 订单状态字段在两个接口里叫法不同，统一读一次 */
const orderStatusOf = (o) => o?.orderStatus || o?.status || ''

const countBy = (rows, want) => rows.filter((r) => want.includes(orderStatusOf(r))).length

export function useMessages() {
  const userStore = useUserStore()

  const role = computed(() => userStore.profile?.roleName || '')
  const perms = computed(() => String(userStore.menuPerms || '').split(',').filter(Boolean))

  /** 铃铛角标：待办条数之和（比「有几条分组」更能说明有多少事要做） */
  const total = computed(() => items.value.reduce((n, i) => n + (i.count || 0), 0))

  const canTrade = computed(() => perms.value.includes('trade') || role.value === '管理员')
  const isExpert = computed(() => role.value === '专家')
  const isBank = computed(() => role.value === '银行用户')

  async function load(force = false) {
    if (loading.value) return
    const now = Date.now()
    if (!force && loaded.value && now - lastLoadAt < FRESH_MS) return

    loading.value = true
    const next = []
    try {
      /* ---------- 供给侧：我发布的货与卖出的单 ---------- */
      if (canTrade.value) {
        const [mine, sold] = await Promise.all([
          request.get('/trade/my-products', { params: { pageNum: 1, pageSize: 100 }, silent: true }).catch(() => []),
          request.get('/trade/order/sold', { params: { pageNum: 1, pageSize: 100 }, silent: true }).catch(() => [])
        ])
        const mineRows = listOf(mine)
        const pendAudit = mineRows.filter((p) => p.auditStatus === '待审核').length
        if (pendAudit) {
          next.push({
            key: 'product-audit',
            count: pendAudit,
            tone: 'warning',
            title: '商品待平台审核',
            desc: '通过后自动上架，期间买家看不到',
            path: '/trade/my-products'
          })
        }
        const pendShip = countBy(listOf(sold), ['待发货'])
        if (pendShip) {
          next.push({
            key: 'order-ship',
            count: pendShip,
            tone: 'primary',
            title: '订单待发货',
            desc: '买家已付款，请尽快安排发货',
            path: '/trade/orders'
          })
        }
      }

      /* ---------- 需求侧：我买的单 ---------- */
      if (!isExpert.value && !isBank.value) {
        const buy = await request
          .get('/trade/order/list', { params: { pageNum: 1, pageSize: 100 }, silent: true })
          .catch(() => [])
        const buyRows = listOf(buy)
        const pendPay = countBy(buyRows, ['待付款'])
        if (pendPay) {
          next.push({
            key: 'order-pay',
            count: pendPay,
            tone: 'danger',
            title: '订单待付款',
            desc: '超时未付会自动取消，重新下单要重拍',
            path: '/trade/orders'
          })
        }
        const pendRecv = countBy(buyRows, ['待收货'])
        if (pendRecv) {
          next.push({
            key: 'order-recv',
            count: pendRecv,
            tone: 'primary',
            title: '订单待收货',
            desc: '确认收货后这笔交易才算完成',
            path: '/trade/orders'
          })
        }
      }

      /* ---------- 专家侧互动：提问被回复、预约有结论 ---------- */
      if (perms.value.includes('expert') && !isExpert.value) {
        const qs = await request.get('/expert/qa/my', { silent: true }).catch(() => [])
        const answered = listOf(qs).filter((q) => q.status === '已回复')
        if (answered.length) {
          const latest = answered[0]
          next.push({
            key: 'qa-answered',
            count: answered.length,
            tone: 'success',
            title: '专家回复了你的提问',
            desc: latest?.title || '点此查看全部回复',
            path: '/expert/qa'
          })
        }

        const appts = await request.get('/expert/appointment/my', { silent: true }).catch(() => [])
        const moved = listOf(appts).filter((a) => a.apptStatus && a.apptStatus !== '待处理')
        if (moved.length) {
          next.push({
            key: 'appt-moved',
            count: moved.length,
            tone: 'success',
            title: '预约有了新进展',
            desc: `${moved[0].expertName || '专家'}：${moved[0].apptStatus}`,
            path: '/expert/appointment'
          })
        }
      }

      /* ---------- 专家工作台：待回复的提问与预约 ---------- */
      if (isExpert.value) {
        const [pendQ, appts] = await Promise.all([
          request.get('/expert/qa/pending', { silent: true }).catch(() => []),
          request.get('/expert/appointment/expert', { params: { status: '待处理' }, silent: true }).catch(() => [])
        ])
        const pq = listOf(pendQ).length
        if (pq) {
          next.push({
            key: 'expert-qa',
            count: pq,
            tone: 'warning',
            title: '农户提问等你回复',
            desc: '回复越及时，农户越可能转成线下预约',
            path: '/expert/qa'
          })
        }
        const pa = listOf(appts).length
        if (pa) {
          next.push({
            key: 'expert-appt',
            count: pa,
            tone: 'primary',
            title: '预约待你处理',
            desc: '接受后农户会按预约时间联系你',
            path: '/expert/appointment'
          })
        }
      }

      /* ---------- 银行侧：待审批的融资申请 ---------- */
      if (isBank.value) {
        const applies = await request.get('/loan-admin/applies', { silent: true }).catch(() => [])
        const n = listOf(applies).filter((a) => isPendingApply(a.applyStatus)).length
        if (n) {
          next.push({
            key: 'bank-approve',
            count: n,
            tone: 'warning',
            title: '融资申请待审批',
            desc: '通过时需填写放款金额',
            path: '/finance/bank-review'
          })
        }
      }

      items.value = next
      loaded.value = true
      lastLoadAt = Date.now()
    } catch (e) {
      // 整体失败时保留上一次的结果，不清空
    } finally {
      loading.value = false
    }
  }

  return { items, total, loading, load }
}
