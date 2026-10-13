<template>
  <div class="media-manage-page">
    <!--
      页面用途：平台管理员清理存储里的图片对象（SRS 管理员职责「图片资源管理」）。
      这一页存在的理由不是"把对象列出来"，而是回答一个此前没人答得上来的问题：
      **哪一张能删**。所以列表接口会扫一遍业务表，把「是否被引用、被谁引用」标出来，
      前端据此把删除按钮置灰 —— 而不是让管理员删一次、被拒一次、再猜一次。
    -->
    <section class="manage-hero">
      <div>
        <span class="eyebrow">M6 · 图片资源治理</span>
        <h2>图片资源管理</h2>
        <p>查看存储中的图片对象、清理无人引用的孤儿文件。仍被商品、头像或知识引用的图片不允许删除，页面会列出引用方</p>
      </div>
      <div class="hero-status">
        <span>可清理</span>
        <strong>{{ orphanCount }}</strong>
        <small>个未被引用的对象</small>
      </div>
    </section>

    <section class="stat-grid">
      <article class="stat-card">
        <span>对象总数</span>
        <!--
          ★ 用全量快照而不是分页接口的 total：分页 total 是**带前缀筛选**的计数，
          而右边「已被引用 / 可清理」来自全量快照。两者混用会出现
          「总数=1、被引用=2、可清理=113」这种自相矛盾的卡片（实测踩到）。
          四张卡统一走全量口径，筛选只影响下面的表格。
        -->
        <strong>{{ totalObjects }}</strong>
        <small>全部对象，不受前缀筛选影响</small>
      </article>
      <article class="stat-card">
        <span>已被引用</span>
        <strong>{{ referencedCount }}</strong>
        <small>删除会被拒绝</small>
      </article>
      <article class="stat-card">
        <span>可清理</span>
        <strong>{{ orphanCount }}</strong>
        <small>删了不影响业务</small>
      </article>
      <article class="stat-card">
        <span>占用空间</span>
        <strong>{{ totalSize }}</strong>
        <small>仅统计已列出的对象</small>
      </article>
    </section>

    <!--
      服务端单次最多列 1000 个对象，超出会截断（只打日志）。
      这里把它变成一个看得见的提示 —— 静默截断会让管理员以为"就这么多"。
    -->
    <el-alert
      v-if="totalObjects >= MAX_LIST_SIZE"
      type="warning"
      show-icon
      :closable="false"
      class="truncate-alert"
      :title="`对象数已达单次上限 ${MAX_LIST_SIZE}`"
      description="列表被截断，统计与「可清理」数只反映前 1000 个对象。请用前缀筛选分批处理"
    />

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="toolbar">
          <div class="toolbar-filters">
            <!--
              前缀选项从前一页返回的数据里现算，不硬编码 —— 库里没有"分类"字段，
              前缀就是目录名（product/、avatar/…），而本地模式与 OBS 模式可能落在不同前缀下。
            -->
            <el-select
              v-model="filterPrefix"
              placeholder="全部前缀"
              clearable
              style="width: 200px"
              @change="handleFilterChange"
            >
              <el-option
                v-for="p in prefixOptions"
                :key="p"
                :label="p"
                :value="p"
              />
            </el-select>
          </div>
          <div class="toolbar-actions">
            <span class="record-count">共 {{ total }} 个对象</span>
            <el-button :loading="loading" @click="refreshAll">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" row-key="objectKey">
        <el-table-column label="预览" width="90">
          <template #default="{ row }">
            <el-image
              class="thumb"
              :src="row.url"
              :preview-src-list="[row.url]"
              :preview-teleported="true"
              fit="cover"
              loading="lazy"
            >
              <template #error><div class="thumb-fallback">无法预览</div></template>
            </el-image>
          </template>
        </el-table-column>
        <el-table-column prop="objectKey" label="对象键" min-width="280" show-overflow-tooltip />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column label="最后修改" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.lastModified) }}</template>
        </el-table-column>
        <el-table-column label="引用情况" min-width="260">
          <template #default="{ row }">
            <el-tag v-if="row.referenced" type="warning" effect="plain">被引用</el-tag>
            <el-tag v-else type="success" effect="plain">未引用</el-tag>
            <ul v-if="row.referenced && row.references?.length" class="ref-list">
              <li v-for="ref in row.references" :key="ref">{{ ref }}</li>
            </ul>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-tooltip
              :disabled="!row.referenced"
              content="仍被业务数据引用，需先在对应业务里换掉这张图"
              placement="top"
            >
              <!--
                disabled 的按钮在部分浏览器里不冒泡鼠标事件，tooltip 会失效 ——
                所以用 el-button 的 disabled 而不是给 <button> 加 pointer-events:none，
                并用一个 span 包住承接 hover（Element Plus 官方推荐的写法）。
              -->
              <span>
                <el-button
                  link
                  type="danger"
                  :disabled="row.referenced"
                  @click.stop="handleDelete(row)"
                >删除</el-button>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
        <template #empty><el-empty description="当前筛选条件下没有图片对象" /></template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[12, 24, 48]"
          background
          layout="sizes, prev, pager, next"
          @current-change="loadList"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteMedia, getMediaList } from '@/api/media'

/** 与后端 FileStorageService.MAX_LIST_SIZE 对齐：超过这个数服务端会截断 */
const MAX_LIST_SIZE = 1000

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(12)

const filterPrefix = ref('')

/**
 * 全量快照：只用来算统计卡与前缀下拉，不参与表格渲染。
 *
 * 服务端本来就是「全量列对象 → 内存切页」，所以这次额外查询不会多扫一遍存储，
 * 只是多传一份 JSON。好处是「可清理数」是**全局**口径 —— 若从当前页算，
 * 翻到第二页数字就跳了，那不是统计，是噪音。
 */
const overview = ref([])

const referencedCount = computed(() => overview.value.filter((item) => item.referenced).length)
const orphanCount = computed(() => overview.value.filter((item) => !item.referenced).length)
/** 统计卡口径 = 全量快照；表格的 `total` 是带筛选的分页计数，两者用途不同别互换 */
const totalObjects = computed(() => overview.value.length)

const totalSize = computed(() => {
  const bytes = overview.value.reduce((sum, item) => sum + Number(item.size || 0), 0)
  return formatSize(bytes)
})

const prefixOptions = computed(() => {
  const set = new Set()
  for (const item of overview.value) {
    const key = item.objectKey || ''
    const slash = key.indexOf('/')
    if (slash > 0) set.add(key.slice(0, slash + 1))
  }
  return Array.from(set).sort()
})

function formatSize(bytes) {
  const n = Number(bytes)
  if (!Number.isFinite(n) || n <= 0) return '--'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(2)} MB`
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '--'
}

/** 当前页列表 */
async function loadList() {
  loading.value = true
  try {
    const data = await getMediaList({
      prefix: filterPrefix.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    rows.value = data?.records || []
    total.value = Number(data?.total || 0)
  } finally {
    loading.value = false
  }
}

/**
 * 全量快照。筛选状态下也照常拉全量（不带 prefix），
 * 否则选了某个前缀之后，前缀下拉里其余选项会自己消失、切不回去。
 */
async function loadOverview() {
  const data = await getMediaList({ pageNum: 1, pageSize: MAX_LIST_SIZE })
  overview.value = data?.records || []
}

async function refreshAll() {
  // 两个请求互不依赖，并行发；串行会让页面白等一轮往返
  await Promise.all([loadList(), loadOverview()])
}

function handleFilterChange() {
  pageNum.value = 1
  loadList()
}

function handleSizeChange() {
  pageNum.value = 1
  loadList()
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除对象「${row.objectKey}」吗？删除后无法恢复，引用它的页面会显示破图`,
      '删除图片对象',
      {
        type: 'warning',
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return // 用户取消
  }

  try {
    await deleteMedia(row.objectKey)
    ElMessage.success('图片对象已删除')
    // 删的可能是本页最后一条，删完退回上一页，避免停在空页
    if (rows.value.length === 1 && pageNum.value > 1) {
      pageNum.value -= 1
    }
    await refreshAll()
  } catch {
    // 被引用时后端返回 400，错误提示已由 request.js 统一弹出（含"被谁引用"），
    // 这里只需保持列表不变，不额外弹一次
  }
}

onMounted(refreshAll)
</script>

<style scoped>
.media-manage-page { color: #24332b; }
.manage-hero { display: flex; justify-content: space-between; align-items: center; gap: 24px; padding: 28px; border-radius: 18px; color: #fff; background: linear-gradient(125deg, #173f2a, #2b8154); box-shadow: 0 16px 34px rgba(23, 63, 42, .16); }
.eyebrow { color: #ccebd5; font-size: 13px; }
.manage-hero h2 { margin: 9px 0 7px; font-size: 26px; }
.manage-hero p { margin: 0; max-width: 780px; color: rgba(255,255,255,.72); line-height: 1.7; }
.hero-status { min-width: 150px; padding: 14px 20px; text-align: center; border: 1px solid rgba(255,255,255,.18); border-radius: 14px; background: rgba(255,255,255,.1); }
.hero-status span, .hero-status small { display: block; color: rgba(255,255,255,.66); }
.hero-status strong { display: block; margin: 4px 0; font-size: 28px; }

.stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin: 18px 0; }
.stat-card { padding: 18px; border: 1px solid #e1eae4; border-radius: 14px; background: #fff; }
.stat-card span, .stat-card small { display: block; color: #75827a; }
.stat-card strong { display: block; margin: 7px 0 4px; font-size: 25px; color: #1d5637; }
.stat-card small { font-size: 11px; }

.truncate-alert { margin-bottom: 16px; }

.table-card { border-radius: 15px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.toolbar-filters { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.toolbar-actions { display: flex; align-items: center; gap: 12px; }
.record-count { color: #75827a; font-size: 12px; }

.thumb { width: 56px; height: 56px; border-radius: 8px; display: block; cursor: zoom-in; border: 1px solid #e1eae4; }
.thumb-fallback { width: 56px; height: 56px; display: flex; align-items: center; justify-content: center; font-size: 11px; color: #839087; background: #f4f7f4; border-radius: 8px; }

.ref-list { margin: 6px 0 0; padding-left: 16px; color: #839087; font-size: 12px; line-height: 1.7; }

.pagination-wrap { display: flex; justify-content: flex-end; padding-top: 18px; }

@media (max-width: 1000px) { .stat-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 680px) {
  .manage-hero, .toolbar { align-items: stretch; flex-direction: column; }
  .hero-status { width: 100%; }
  .stat-grid { grid-template-columns: 1fr; }
}
</style>
