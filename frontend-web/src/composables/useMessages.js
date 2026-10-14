import { computed, ref, watch } from 'vue'
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
 *
 * ★ 已读红点：`total` 是**未读**条数之和，不是待办总数。点「全部已读」或点开某条
 *   即把当前条数写成基线（见下方 READ_PREFIX 的说明）。★ 面板里**始终列出全部待办**，
 *   已读的只是不再计入角标 —— 「红点消失」绝不能变成「待办消失」，那才是真的漏事。
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

/**
 * 「已读」基线：key = `rxt_msg_read_<userId>`，值 = `{ [待办 key]: 我确认过的条数 }`。
 *
 * ★ 为什么是 localStorage 而不是加一张通知表：这里的「消息」并不是真的消息，
 *   而是从后端业务数据**实时派生**出来的待办（见文件开头说明）。服务端没有
 *   可挂「已读」字段的实体；而「这批待办我看过了」本质上是**这台浏览器上
 *   这个人的视图状态**，放客户端既够用，又不必为它加表加接口。
 *
 * ★ 为什么记「条数」而不是一个布尔：布尔区分不了「我看过了」和「之后又多了几条」。
 *   记条数才做得到「已读 → 处理掉 1 条 → 又来 1 条」时重新亮红点。
 *
 * ★ 按 userId 分开存：同一台浏览器上换个账号登录，不应继承上一个人的已读状态。
 */
const READ_PREFIX = 'rxt_msg_read_'

function loadReadMap(userId) {
  if (!userId) return {}
  try {
    const raw = localStorage.getItem(READ_PREFIX + userId)
    const obj = raw ? JSON.parse(raw) : null
    return obj && typeof obj === 'object' ? obj : {}
  } catch {
    // 存坏了就当没读过，最多多亮一次红点，不能让整个消息面板跟着挂掉
    return {}
  }
}

function saveReadMap(userId, map) {
  if (!userId) return
  try {
    localStorage.setItem(READ_PREFIX + userId, JSON.stringify({ ...map }))
  } catch {
    // 隐私模式 / 配额满：红点消退不了，但功能本身不该报错
  }
}

export function useMessages() {
  const userStore = useUserStore()

  const role = computed(() => userStore.profile?.roleName || '')
  const perms = computed(() => String(userStore.menuPerms || '').split(',').filter(Boolean))

  /* ---------------- 「已读」基线 ---------------- */
  const userId = computed(() => userStore.profile?.userId || '')
  const readMap = ref({})
  // 换账号要换一份基线。immediate 保证首次进入（profile 可能还没到位）也能读到
  watch(userId, (id) => { readMap.value = loadReadMap(id) }, { immediate: true })

  /** 某条待办还剩几条「没看过」。条数回落到基线及以下 → 视为已读 */
  function unreadOf(item) {
    return Math.max(0, (item?.count || 0) - (readMap.value[item?.key] || 0))
  }

  /** 铃铛角标：**未读**待办条数之和（比「有几条分组」更能说明还有多少事要看） */
  const total = computed(() => items.value.reduce((n, i) => n + unreadOf(i), 0))

  /**
   * 待办总条数（不分读没读）。面板标题用它。
   * ★ 与 total 分开是刻意的：红点可以被「全部已读」清掉，但待办本身不会因此消失 ——
   *   标题必须继续如实报出「还有几件事要做」，否则清红点就等于把待办藏起来。
   */
  const pendingTotal = computed(() => items.value.reduce((n, i) => n + (i.count || 0), 0))

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

      // ★ 基线只跟降、不跟升：条数比上次确认的少了，说明用户确实处理掉了一部分，
      //   把基线跟着降下来。否则「3 条已读 → 处理掉 1 条 → 又来 1 条」会回到 3，
      //   与基线相等，新增的那条被静默吞掉、永远不提醒。
      let baselineChanged = false
      next.forEach((it) => {
        const ack = readMap.value[it.key] || 0
        if ((it.count || 0) < ack) {
          readMap.value[it.key] = it.count || 0
          baselineChanged = true
        }
      })
      if (baselineChanged) saveReadMap(userId.value, readMap.value)

      items.value = next
      loaded.value = true
      lastLoadAt = Date.now()
    } catch (e) {
      // 整体失败时保留上一次的结果，不清空
    } finally {
      loading.value = false
    }
  }

  /** 全部标记为已读：把每条当前的条数写成基线，红点随即归零 */
  function markAllRead() {
    items.value.forEach((it) => { readMap.value[it.key] = it.count || 0 })
    saveReadMap(userId.value, readMap.value)
  }

  /** 标记单条已读。点进某条待办时调用 —— 用户已经看到它了 */
  function markRead(key) {
    const it = items.value.find((i) => i.key === key)
    if (!it) return
    readMap.value[key] = it.count || 0
    saveReadMap(userId.value, readMap.value)
  }

  return { items, total, pendingTotal, loading, load, unreadOf, markAllRead, markRead }
}
