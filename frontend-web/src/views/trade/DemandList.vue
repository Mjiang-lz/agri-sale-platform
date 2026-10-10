<template>
  <div class="demand-list-page">
    <div class="page-head">
      <div>
        <h2>求购需求</h2>
        <p>采购方与农户发布的求购需求，可按品类和收货地区筛选</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="loadDemands">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <el-select v-model="query.category" placeholder="全部品类" clearable style="width: 160px" @change="handleSearch">
          <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="query.region" placeholder="全部地区" clearable style="width: 160px" @change="handleSearch">
          <el-option v-for="r in regionOptions" :key="r" :label="r" :value="r" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
    </el-card>

    <el-skeleton v-if="loading && !demands.length" animated :rows="5" />

    <!-- 接口不可用：行内错误态 + 重试。toast 已由 request.js 统一弹过，不重复弹 -->
    <el-alert
      v-else-if="errorState"
      type="error"
      show-icon
      :closable="false"
      title="求购需求加载失败"
      description="无法连接后端服务，请确认后端已启动（默认 8080 端口）后重试。"
      class="error-alert"
    >
      <template #default>
        <div class="error-actions">
          <el-button type="danger" size="small" @click="loadDemands">重新加载</el-button>
        </div>
      </template>
    </el-alert>

    <template v-else-if="demands.length">
      <el-card
        v-for="d in demands"
        :key="d.demandId"
        shadow="never"
        class="demand-card"
        @click="openDetail(d)"
      >
        <div class="demand-main">
          <div class="demand-title-row">
            <h4>{{ categoryLabel(d) }}</h4>
            <el-tag :type="d.status === 1 ? 'success' : 'info'" size="small" effect="plain">
              {{ d.status === 1 ? '求购中' : '已关闭' }}
            </el-tag>
          </div>
          <p class="demand-desc">{{ summary(d.description) || '发布者未填写补充说明' }}</p>
          <ul class="demand-meta">
            <li><span>求购数量：</span>{{ d.quantity ?? 0 }} {{ d.unit || '件' }}</li>
            <li><span>收货地区：</span>{{ d.region || '—' }}</li>
            <li><span>有效期至：</span>{{ formatDate(d.validUntil) || '—' }}</li>
            <li><span>发布时间：</span>{{ formatDate(d.createTime) || '—' }}</li>
          </ul>
        </div>
        <div class="demand-side">
          <div class="expect-price">
            <span class="label">期望单价</span>
            <span class="num">¥{{ formatPrice(d.expectPrice) }}</span>
            <span class="unit">/ {{ d.unit || '件' }}</span>
          </div>
          <small class="contact">联系方式：{{ d.contact || '—' }}</small>
          <el-button type="primary" size="small" @click.stop="openDetail(d)">查看详情</el-button>
        </div>
      </el-card>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadDemands"
          @size-change="handleSizeChange"
        />
      </div>
    </template>

    <el-card v-else shadow="never">
      <el-empty :description="emptyText">
        <el-button v-if="hasFilter" type="primary" @click="resetQuery">清空筛选条件</el-button>
      </el-empty>
    </el-card>

    <el-drawer v-model="detailVisible" title="求购需求详情" size="min(560px, 96%)">
      <template v-if="currentDemand">
        <div class="detail-head">
          <h3>{{ categoryLabel(currentDemand) }}</h3>
          <el-tag :type="currentDemand.status === 1 ? 'success' : 'info'" effect="plain">
            {{ currentDemand.status === 1 ? '求购中' : '已关闭' }}
          </el-tag>
        </div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="求购数量">
            {{ currentDemand.quantity ?? 0 }} {{ currentDemand.unit || '件' }}
          </el-descriptions-item>
          <el-descriptions-item label="期望单价">
            ¥{{ formatPrice(currentDemand.expectPrice) }} / {{ currentDemand.unit || '件' }}
          </el-descriptions-item>
          <el-descriptions-item label="收货地区">{{ currentDemand.region || '—' }}</el-descriptions-item>
          <el-descriptions-item label="联系方式">{{ currentDemand.contact || '—' }}</el-descriptions-item>
          <el-descriptions-item label="有效期至">{{ formatDate(currentDemand.validUntil) || '—' }}</el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ formatDate(currentDemand.createTime) || '—' }}</el-descriptions-item>
          <el-descriptions-item label="补充说明">
            {{ currentDemand.description || '—' }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getDemandDetail, getDemandList } from '@/api/trade'
import { formatDateTime, summary as sliceText } from '@/utils/text'

/**
 * 页面用途：求购需求列表（公开接口，游客可访问）。
 * 接口文件：frontend-web/src/api/trade.js
 * 主要接口：GET /api/demand/list?pageNum=&pageSize=&category=&region=
 *
 * ⚠️ 路径纠正：旧模板注释写的 /api/trade/demands 在后端**不存在**，
 * 求购需求挂在 DemandController（/api/demand）下，不是 /api/trade。
 *
 * 后端返回 MyBatis-Plus Page，records 元素为 DemandVO：
 * demandId、publisherId、category、quantity、unit、expectPrice、description、
 * region、validUntil、contact、status、createTime、updateTime。
 *
 * 失败处理：接口异常弹提示，区分「无数据」与「取不到数据」。
 */
const loading = ref(false)
const demands = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const query = reactive({ category: '', region: '' })

/** 接口失败标记：与「暂无需求」区分开 */
const errorState = ref(false)

// 后端没有字典接口，用已知全集兜底；新增品类后需同步这里
const categoryOptions = ['水果', '粮油', '蔬菜', '干货', '水产', '畜禽产品']
const regionOptions = ['江西赣州', '湖北荆州', '黑龙江五常']

const detailVisible = ref(false)
const currentDemand = ref(null)

const hasFilter = computed(() => Boolean(query.category || query.region))
const emptyText = computed(() =>
  hasFilter.value ? '没有符合条件的求购需求' : '暂无求购需求'
)

function formatPrice(value) {
  const n = Number(value)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

/** 只取日期部分，列表里精确到秒没有意义 */
function formatDate(value) {
  return formatDateTime(value).slice(0, 10)
}

function summary(text) {
  return sliceText(text, 80)
}

/** 标题没有单独字段，用「品类 + 数量」拼一个可读标题 */
function categoryLabel(d) {
  const parts = [d.category || '未分类需求']
  if (d.quantity) parts.push(`求购 ${d.quantity}${d.unit || '件'}`)
  return parts.join(' · ')
}

async function loadDemands() {
  loading.value = true
  errorState.value = false
  try {
    const data = await getDemandList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      category: query.category,
      region: query.region
    })
    const records = data?.records || []
    demands.value = records
    total.value = Number(data?.total ?? records.length)
    if (!records.length) {
      ElMessage.info(hasFilter.value ? '没有符合条件的求购需求' : '暂无求购需求')
    }
  } catch (err) {
    demands.value = []
    total.value = 0
    errorState.value = true
    console.error('求购需求加载失败：', err)
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  currentDemand.value = row
  detailVisible.value = true
  // 详情接口要求登录，拉不到就沿用列表数据，不让抽屉空着
  try {
    const detail = await getDemandDetail(row.demandId)
    if (detail) currentDemand.value = { ...row, ...detail }
  } catch (err) {
    console.warn('求购详情获取失败，回退到列表数据：', err)
  }
}

function handleSearch() {
  pageNum.value = 1
  loadDemands()
}

function resetQuery() {
  query.category = ''
  query.region = ''
  handleSearch()
}

function handleSizeChange() {
  pageNum.value = 1
  loadDemands()
}

onMounted(loadDemands)
</script>

<style scoped>
.demand-list-page { color: var(--el-text-color-primary); }


.filter-card { margin-bottom: 18px; border-radius: 14px; }
.filter-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.demand-card {
  margin-bottom: 14px;
  border-radius: 14px;
  cursor: pointer;
  transition: 0.2s;
  display: flex;
  align-items: stretch;
  gap: 16px;
}
.demand-card:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 8px 24px rgba(29, 70, 46, .08);
}

.demand-main { flex: 1; min-width: 0; }

.demand-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.demand-title-row h4 { margin: 0; font-size: 16px; }

.demand-desc {
  margin: 0 0 10px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.7;
  min-height: 22px;
}

.demand-meta {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: 6px 20px;
  font-size: 13px;
  color: #606266;
}
.demand-meta span { color: #909399; margin-right: 4px; }

.demand-side {
  width: 190px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: 8px;
  padding-left: 16px;
  border-left: 1px dashed var(--el-border-color-light);
}
.expect-price { display: flex; align-items: baseline; gap: 4px; }
.expect-price .label { font-size: 12px; color: #909399; }
.expect-price .num   { font-size: 22px; font-weight: 700; color: #f56c6c; }
.expect-price .unit  { font-size: 12px; color: #909399; }
.contact { color: var(--el-text-color-secondary); font-size: 12px; }


.error-alert { margin-top: 4px; }
.error-alert :deep(.el-alert__content) { width: 100%; }
.error-actions { margin-top: 8px; }

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.detail-head h3 { margin: 0; font-size: 18px; }

@media (max-width: 760px) {
  .demand-card { flex-direction: column; }
  .demand-side {
    width: 100%;
    align-items: flex-start;
    padding-left: 0;
    padding-top: 12px;
    border-left: none;
    border-top: 1px dashed var(--el-border-color-light);
  }
}
</style>
