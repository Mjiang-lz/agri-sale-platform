<template>
  <div class="dashboard-page" v-loading="loading">
    <!--
      页面用途：平台管理员的运营总览。
      数据全部来自 GET /api/admin/dashboard/statistics，一个接口取回
      总量 / 待办 / 分布 / 近 7 日趋势四类指标，刷新即重新统计。
    -->
    <section class="dash-hero">
      <div>
        <span class="eyebrow">M6 · 平台治理</span>
        <h2>运营数据看板</h2>
        <p>用户、商品、订单与融资的全平台汇总。所有数字由后端实时统计得出，管理员在其他页面新增数据后刷新本页即可看到变化。</p>
      </div>
      <div class="hero-side">
        <div class="hero-time">
          <span>统计生成时间</span>
          <strong>{{ formatDateTime(statistics.generatedAt) }}</strong>
        </div>
        <el-button class="hero-refresh" :loading="loading" @click="loadStatistics">
          {{ loading ? '统计中' : '刷新数据' }}
        </el-button>
      </div>
    </section>

    <el-alert
      v-if="errorMessage"
      class="dash-alert"
      type="error"
      show-icon
      :closable="false"
      :title="errorMessage"
      description="请确认后端服务已启动，且当前登录账号具备管理员权限。"
    />

    <template v-else>
      <!-- 待办：需要管理员立刻处理的量，单独放最上面 -->
      <section class="todo-grid">
        <article class="todo-card audit" @click="$router.push('/audit/product')">
          <div class="todo-icon">审</div>
          <div class="todo-body">
            <span>待审核内容</span>
            <strong>{{ formatNumber(statistics.pendingAuditTotal) }}</strong>
            <small>商品 + 知识 + 评论合计</small>
          </div>
          <el-icon class="todo-arrow"><ArrowRight /></el-icon>
        </article>
        <article class="todo-card finance" @click="$router.push('/finance/manage')">
          <div class="todo-icon">贷</div>
          <div class="todo-body">
            <span>待审批融资</span>
            <strong>{{ formatNumber(statistics.pendingFinanceTotal) }}</strong>
            <small>状态为「已推送银行」</small>
          </div>
          <el-icon class="todo-arrow"><ArrowRight /></el-icon>
        </article>
      </section>

      <!-- 总量概览 -->
      <section class="stat-grid">
        <article v-for="item in overviewCards" :key="item.label" class="stat-card" :class="item.theme">
          <span class="stat-label">{{ item.label }}</span>
          <strong class="stat-value">{{ formatNumber(item.value) }}</strong>
          <small class="stat-extra">{{ item.extra }}</small>
        </article>
      </section>

      <!-- 近 7 日趋势：三张折线图，共用一套坐标轴刻度逻辑 -->
      <section class="chart-grid">
        <el-card
          v-for="chart in trendCharts"
          :key="chart.key"
          shadow="never"
          class="chart-card"
        >
          <template #header>
            <div class="card-head">
              <div>
                <strong>{{ chart.title }}</strong>
                <span>近 7 日（含今天）</span>
              </div>
              <span class="card-total">
                合计 <b>{{ formatNumber(chart.total) }}</b>
                <template v-if="chart.hasData"> · 峰值 {{ formatNumber(chart.peak) }}</template>
              </span>
            </div>
          </template>
          <div v-if="chart.hasData" class="line-chart">
            <div class="chart-axis">
              <!-- 绝对定位对齐 gridline：flex space-between 只是近似，刻度多了或少了就会错位 -->
              <span
                v-for="tick in axisTicks(chart.peak)"
                :key="tick"
                :style="{ top: `${(scaleY(tick, chart) / CHART_HEIGHT) * 100}%` }"
              >{{ tick }}</span>
            </div>
            <div class="chart-body">
              <svg
                class="chart-svg"
                :viewBox="`0 0 ${chart.width} ${chart.height}`"
                preserveAspectRatio="none"
                @mousemove="onHover(chart, $event)"
                @mouseleave="hoverPoint = null"
              >
                <!-- 横向参考线 -->
                <line
                  v-for="tick in axisTicks(chart.peak)"
                  :key="`grid-${tick}`"
                  x1="0"
                  :x2="chart.width"
                  :y1="scaleY(tick, chart)"
                  :y2="scaleY(tick, chart)"
                  stroke="#eef2ef"
                  stroke-width="1"
                />
                <!-- 面积 -->
                <polygon :points="areaPoints(chart)" :fill="chart.color" opacity="0.12" />
                <!-- 折线 -->
                <polyline
                  :points="linePoints(chart)"
                  fill="none"
                  :stroke="chart.color"
                  stroke-width="2"
                  stroke-linejoin="round"
                  stroke-linecap="round"
                />
                <!-- 数据点 -->
                <circle
                  v-for="(point, index) in chart.points"
                  :key="index"
                  :cx="point.x"
                  :cy="point.y"
                  r="3"
                  fill="#fff"
                  :stroke="chart.color"
                  stroke-width="2"
                />
              </svg>
              <div class="chart-hover" :style="hoverStyle(chart)">
                <template v-if="hoverPoint">
                  <b>{{ hoverPoint.date.slice(5) }}</b>
                  <span>新增 {{ hoverPoint.count }}</span>
                </template>
              </div>
            </div>
            <div class="chart-labels">
              <span
                v-for="point in chart.points"
                :key="point.date"
                :style="{ left: `${(point.x / chart.width) * 100}%` }"
              >{{ point.date.slice(5) }}</span>
            </div>
          </div>
          <el-empty v-else description="近 7 日无新增数据" :image-size="70" />
        </el-card>
      </section>

      <!-- 分布：角色占比、审核状态环图、融资漏斗 -->
      <section class="dist-grid">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-head">
              <div><strong>用户角色分布</strong><span>按角色统计用户数</span></div>
              <span class="card-total">共 <b>{{ formatNumber(statistics.userTotal) }}</b> 人</span>
            </div>
          </template>
          <div v-if="roleDistribution.length" class="bar-list">
            <div v-for="item in roleDistribution" :key="item.name" class="bar-row">
              <div class="bar-meta">
                <span>{{ item.name }}</span>
                <b>{{ formatNumber(item.count) }} 人 · {{ percent(item.count, statistics.userTotal) }}%</b>
              </div>
              <div class="bar-track">
                <span class="bar-fill" :style="{ width: `${percent(item.count, roleMax)}%`, background: roleColor(item.name) }" />
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无角色数据" :image-size="70" />
        </el-card>

        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-head">
              <div><strong>内容审核状态</strong><span>商品 / 知识 / 评论合计</span></div>
            </div>
          </template>
          <div v-if="auditTotal > 0" class="donut-wrap">
            <div class="donut" :style="donutStyle">
              <div class="donut-hole">
                <strong>{{ formatNumber(auditTotal) }}</strong>
                <span>内容总数</span>
              </div>
            </div>
            <ul class="legend">
              <li v-for="item in auditDistribution" :key="item.name">
                <i :style="{ background: auditColor(item.name) }" />
                <span class="legend-name">{{ item.name }}</span>
                <b>{{ formatNumber(item.count) }}</b>
                <small>{{ percent(item.count, auditTotal) }}%</small>
              </li>
            </ul>
          </div>
          <el-empty v-else description="暂无审核数据" :image-size="70" />
        </el-card>

        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-head">
              <div><strong>融资申请状态分布</strong><span>按申请状态词表统计</span></div>
              <span class="card-total">共 <b>{{ formatNumber(statistics.financeApplyTotal) }}</b> 笔</span>
            </div>
          </template>
          <div v-if="financeDistribution.length" class="bar-list">
            <div v-for="item in financeDistribution" :key="item.name" class="bar-row">
              <div class="bar-meta">
                <span>{{ item.name }}</span>
                <b>{{ formatNumber(item.count) }} 笔 · {{ percent(item.count, statistics.financeApplyTotal) }}%</b>
              </div>
              <div class="bar-track">
                <span
                  class="bar-fill"
                  :style="{ width: `${percent(item.count, financeMax)}%`, background: financeColor(item.name) }"
                />
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无融资申请" :image-size="70" />
        </el-card>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import { getDashboardStatistics } from '@/api/dashboard'

/** 图表画布逻辑尺寸（viewBox 坐标，与 CSS 显示尺寸解耦）。 */
const CHART_WIDTH = 560
const CHART_HEIGHT = 150

/** 接口失败时不阻断页面，只提示并保留骨架。 */
const loading = ref(false)
const errorMessage = ref('')
const hoverPoint = ref(null)

/** 接口原始返回；所有展示数字都从这里派生，不另存一份。 */
const statistics = ref({})

/** 角色配色：同一角色在任何图表里都是同一个颜色。 */
const ROLE_COLORS = ['#2f8a5b', '#4a9fd8', '#df8a32', '#8a6fd0', '#c9564e', '#5b8a72']
const AUDIT_COLORS = { 待审核: '#df8a32', 已通过: '#2f8a5b', 未通过: '#c9564e' }
/** 融资状态按流转顺序由浅到深，一眼能看出卡在哪一段。 */
const FINANCE_COLORS = ['#b8ccc2', '#8fb8a4', '#5b9c7c', '#df8a32', '#c9564e', '#1d5637']

const overviewCards = computed(() => [
  { label: '用户总数', value: statistics.value.userTotal, extra: '平台注册账号', theme: 'green' },
  { label: '商品总数', value: statistics.value.productTotal, extra: `在架 ${formatNumber(statistics.value.productOnSale)} 件`, theme: 'green' },
  { label: '求购需求', value: statistics.value.demandTotal, extra: '累计发布需求', theme: 'blue' },
  { label: '订单总数', value: statistics.value.orderTotal, extra: '累计成交订单', theme: 'blue' },
  { label: '融资申请', value: statistics.value.financeApplyTotal, extra: '累计提交申请', theme: 'orange' },
  { label: '联合贷款小组', value: statistics.value.loanGroupTotal, extra: '已组建联保小组', theme: 'orange' },
  { label: '农业知识', value: statistics.value.knowledgeTotal, extra: '知识库条目', theme: 'purple' },
  { label: '商品评论', value: statistics.value.commentTotal, extra: '累计评论条数', theme: 'purple' }
])

const roleDistribution = computed(() => statistics.value.roleUserDistribution || [])
const financeDistribution = computed(() => statistics.value.financeStatusDistribution || [])
const auditDistribution = computed(() => statistics.value.auditStatusDistribution || [])

const roleMax = computed(() => Math.max(1, ...roleDistribution.value.map((item) => Number(item.count || 0))))
const financeMax = computed(() => Math.max(1, ...financeDistribution.value.map((item) => Number(item.count || 0))))
const auditTotal = computed(() => auditDistribution.value.reduce((sum, item) => sum + Number(item.count || 0), 0))

/** 环图用 conic-gradient 画：按各状态占比切角度，0 值不占角度。 */
const donutStyle = computed(() => {
  const total = auditTotal.value
  if (!total) return {}
  let cursor = 0
  const stops = auditDistribution.value
    .filter((item) => Number(item.count) > 0)
    .map((item) => {
      const start = cursor
      cursor += (Number(item.count) / total) * 100
      return `${auditColor(item.name)} ${start.toFixed(2)}% ${cursor.toFixed(2)}%`
    })
  return { background: `conic-gradient(${stops.join(', ')})` }
})

/** 三张趋势图共用一个工厂，配置不同即可复用同一条折线渲染逻辑。 */
const trendCharts = computed(() => [
  { key: 'newUserTrend', title: '新增用户趋势', color: '#2f8a5b', data: statistics.value.newUserTrend || [] },
  { key: 'newOrderTrend', title: '新增订单趋势', color: '#4a9fd8', data: statistics.value.newOrderTrend || [] },
  { key: 'newFinanceTrend', title: '新增融资申请趋势', color: '#df8a32', data: statistics.value.newFinanceTrend || [] }
].map((chart) => {
  const data = chart.data.map((item) => ({ date: item.date, count: Number(item.count || 0) }))
  const peak = Math.max(1, ...data.map((item) => item.count))
  const step = data.length > 1 ? CHART_WIDTH / (data.length - 1) : CHART_WIDTH
  const points = data.map((item, index) => ({
    date: item.date,
    count: item.count,
    x: data.length > 1 ? index * step : CHART_WIDTH / 2,
    y: CHART_HEIGHT - (item.count / peak) * (CHART_HEIGHT - 16)
  }))
  return {
    ...chart,
    width: CHART_WIDTH,
    height: CHART_HEIGHT,
    peak,
    // 演示库的数据日期可能远早于今天，7 天全 0 时画折线只会得到一条贴底直线，不如直接说「无数据」。
    hasData: data.some((item) => item.count > 0),
    total: data.reduce((sum, item) => sum + item.count, 0),
    points
  }
}))

/** Y 轴刻度取整到易读的档位，避免出现 3.7 这种刻度。 */
function axisTicks(peak) {
  const step = niceStep(peak)
  const ticks = []
  for (let value = 0; value <= peak + step / 2; value += step) ticks.push(value)
  return ticks
}

function niceStep(peak) {
  if (peak <= 4) return 1
  if (peak <= 10) return 2
  if (peak <= 50) return 10
  if (peak <= 200) return 50
  return 100
}

function scaleY(value, chart) {
  return CHART_HEIGHT - (value / chart.peak) * (CHART_HEIGHT - 16)
}

function linePoints(chart) {
  return chart.points.map((point) => `${point.x},${point.y}`).join(' ')
}

function areaPoints(chart) {
  if (!chart.points.length) return ''
  return `0,${CHART_HEIGHT} ${linePoints(chart)} ${CHART_WIDTH},${CHART_HEIGHT}`
}

/** 鼠标横向位置 → 最近的数据点，用来做 tooltip，避免为每个点挂监听。 */
function onHover(chart, event) {
  if (!chart.points.length) return
  const rect = event.currentTarget.getBoundingClientRect()
  const ratio = (event.clientX - rect.left) / rect.width
  const index = Math.min(chart.points.length - 1, Math.max(0, Math.round(ratio * (chart.points.length - 1))))
  hoverPoint.value = chart.points[index]
}

function hoverStyle(chart) {
  if (!hoverPoint.value || !chart.points.length) return { opacity: 0 }
  const index = chart.points.findIndex((point) => point.date === hoverPoint.value.date)
  if (index < 0) return { opacity: 0 }
  return { opacity: 1, left: `${(chart.points[index].x / CHART_WIDTH) * 100}%` }
}

function formatNumber(value) {
  const number = Number(value || 0)
  return number.toLocaleString('zh-CN')
}

function percent(value, total) {
  const base = Number(total || 0)
  if (!base) return 0
  return Math.round((Number(value || 0) / base) * 100)
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '--'
}

function roleColor(name) {
  const index = roleDistribution.value.findIndex((item) => item.name === name)
  return ROLE_COLORS[(index < 0 ? 0 : index) % ROLE_COLORS.length]
}

function auditColor(name) {
  return AUDIT_COLORS[name] || '#b8ccc2'
}

function financeColor(name) {
  const index = financeDistribution.value.findIndex((item) => item.name === name)
  return FINANCE_COLORS[(index < 0 ? 0 : index) % FINANCE_COLORS.length]
}

async function loadStatistics() {
  loading.value = true
  errorMessage.value = ''
  hoverPoint.value = null
  try {
    const data = await getDashboardStatistics()
    statistics.value = data || {}
  } catch (error) {
    errorMessage.value = error?.message || '看板数据加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadStatistics)
</script>

<style scoped>
.dashboard-page { color: #24332b; }
.dash-hero { display: flex; justify-content: space-between; align-items: center; gap: 24px; padding: 28px; border-radius: 18px; color: #fff; background: linear-gradient(125deg, #173f2a, #2b8154); box-shadow: 0 16px 34px rgba(23, 63, 42, .16); }
.eyebrow { color: #ccebd5; font-size: 13px; }

.dash-hero h2 { margin: 9px 0 7px; font-size: 26px; white-space: nowrap; }
.dash-hero p { margin: 0; max-width: 760px; color: rgba(255,255,255,.72); line-height: 1.7; }
/* 左侧文案区允许收缩，否则在中等宽度下会把标题挤成竖排 */
.dash-hero > div:first-child { flex: 1; min-width: 0; }

.hero-side { display: flex; align-items: center; gap: 14px; flex-shrink: 0; }
.hero-time { padding: 12px 18px; text-align: right; border: 1px solid rgba(255,255,255,.18); border-radius: 14px; background: rgba(255,255,255,.1); }
.hero-time span { display: block; color: rgba(255,255,255,.66); font-size: 12px; }
.hero-time strong { display: block; margin-top: 5px; font-size: 15px; letter-spacing: .5px; }
.hero-refresh { color: #173f2a; font-weight: 600; background: #ccebd5; border-color: transparent; }
.hero-refresh:hover { color: #173f2a; background: #e3f5ea; border-color: transparent; }
.dash-alert { margin-top: 18px; }
.todo-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin: 18px 0; }
.todo-card { display: flex; align-items: center; gap: 16px; padding: 20px 22px; cursor: pointer; border: 1px solid #e1eae4; border-left: 4px solid #df8a32; border-radius: 14px; background: #fff; transition: transform .18s ease, box-shadow .18s ease; }
.todo-card:hover { transform: translateY(-2px); box-shadow: 0 12px 24px rgba(23, 63, 42, .1); }
.todo-card.finance { border-left-color: #4a9fd8; }
.todo-icon { display: grid; place-items: center; width: 46px; height: 46px; color: #fff; font-size: 19px; border-radius: 12px; background: linear-gradient(140deg, #e6a259, #df8a32); }
.todo-card.finance .todo-icon { background: linear-gradient(140deg, #6bb2e0, #4a9fd8); }
.todo-body { flex: 1; }
.todo-body span { display: block; color: #75827a; font-size: 13px; }
.todo-body strong { display: block; margin: 4px 0 3px; font-size: 27px; line-height: 1.1; color: #1d5637; }
.todo-card.finance .todo-body strong { color: #22658f; }
.todo-body small { color: #8b978f; font-size: 11px; }
.todo-arrow { color: #c2cec6; font-size: 20px; }
.stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 18px; }
.stat-card { position: relative; padding: 18px 18px 18px 22px; border: 1px solid #e1eae4; border-radius: 14px; background: #fff; overflow: hidden; }
.stat-card::before { content: ''; position: absolute; inset: 0 auto 0 0; width: 3px; background: #2f8a5b; }
.stat-card.blue::before { background: #4a9fd8; }
.stat-card.orange::before { background: #df8a32; }
.stat-card.purple::before { background: #8a6fd0; }
.stat-label { display: block; color: #75827a; font-size: 13px; }
.stat-value { display: block; margin: 7px 0 4px; font-size: 26px; color: #1d5637; letter-spacing: .5px; }
.stat-extra { color: #8b978f; font-size: 11px; }
.chart-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; margin-bottom: 18px; }
.dist-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.chart-card { border-radius: 15px; }
.card-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.card-head strong { display: block; font-size: 15px; }
.card-head span { margin-top: 4px; color: #75827a; font-size: 12px; }
.card-total b { color: #1d5637; }
/* 两列两行网格：左列放刻度，右列上下分别是画布和日期标签。
   用 flex 会把日期当成画布的第三个兄弟节点挤到右侧。 */
.line-chart { display: grid; grid-template-columns: 26px minmax(0, 1fr); grid-template-rows: 150px auto; column-gap: 8px; }

.chart-axis { grid-area: 1 / 1; position: relative; width: 26px; height: 150px; color: #9aa69e; font-size: 10px; }
.chart-axis span { position: absolute; right: 0; line-height: 1; transform: translateY(-50%); }
.chart-body { grid-area: 1 / 2; position: relative; height: 150px; min-width: 0; }
.chart-svg { width: 100%; height: 150px; cursor: crosshair; }
.chart-hover { position: absolute; top: 4px; z-index: 2; padding: 5px 9px; pointer-events: none; white-space: nowrap; border-radius: 8px; color: #fff; font-size: 11px; background: rgba(29, 86, 55, .9); transform: translateX(-50%); transition: left .12s ease, opacity .12s ease; }
.chart-hover b { margin-right: 6px; }
.chart-labels { grid-area: 2 / 2; position: relative; height: 14px; margin-top: 6px; color: #839087; font-size: 10px; }
/* 绝对定位对齐折线的数据点 x 坐标；两端标签靠 translate 收进可视区，避免溢出卡片 */

.chart-labels span { position: absolute; white-space: nowrap; transform: translateX(-50%); }
.chart-labels span:first-child { transform: none; }
.chart-labels span:last-child { transform: translateX(-100%); }
.bar-list { display: flex; flex-direction: column; gap: 13px; }
.bar-meta { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 6px; }
.bar-meta span { color: #4b5a51; font-size: 13px; }
.bar-meta b { color: #75827a; font-size: 11px; font-weight: 400; }
.bar-track { height: 8px; border-radius: 5px; background: #f0f4f1; overflow: hidden; }
.bar-fill { display: block; height: 100%; min-width: 2px; border-radius: 5px; transition: width .4s ease; }
.donut-wrap { display: flex; align-items: center; gap: 22px; }
.donut { position: relative; display: grid; place-items: center; width: 132px; height: 132px; flex-shrink: 0; border-radius: 50%; }
.donut-hole { display: grid; place-items: center; width: 84px; height: 84px; border-radius: 50%; background: #fff; }
.donut-hole strong { font-size: 20px; color: #1d5637; }
.donut-hole span { color: #8b978f; font-size: 11px; }
.legend { flex: 1; margin: 0; padding: 0; list-style: none; }
.legend li { display: flex; align-items: center; gap: 8px; padding: 7px 0; border-bottom: 1px dashed #eef2ef; font-size: 12px; }
.legend li:last-child { border-bottom: none; }
.legend i { width: 9px; height: 9px; border-radius: 3px; }
.legend-name { flex: 1; color: #4b5a51; }
.legend b { color: #1d5637; }
.legend small { width: 38px; color: #8b978f; text-align: right; }
@media (max-width: 1280px) { .chart-grid, .dist-grid { grid-template-columns: 1fr; } }

@media (max-width: 1000px) { .stat-grid { grid-template-columns: repeat(2, 1fr); } .todo-grid { grid-template-columns: 1fr; } }
@media (max-width: 680px) { .dash-hero { align-items: stretch; flex-direction: column; } .hero-side { justify-content: space-between; } .hero-time { text-align: left; } .stat-grid { grid-template-columns: 1fr; } .donut-wrap { flex-direction: column; } }

</style>
