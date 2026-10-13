<template>
  <div class="demand-list-page">
    <div class="page-head">
      <div>
        <h2>求购需求</h2>
        <p>采购方与农户发布的求购需求，可按品类和收货地区筛选</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="loadDemands">刷新</el-button>
        <!--
          发布入口仅对农户与采购方显示，依据需求规格说明书 2.1 角色与职责表
          （两类角色的职责里都列了「发布求购需求」）。
          ⚠️ 用 actualRole 而不是 activeRole：activeRole 是农户/采购方之间
          切演示视角用的可切状态，用它判断会出现「切成采购方就能发，
          但登录账号其实是农户」的错位。
        -->
        <el-button v-if="canPublishDemand" type="primary" @click="openPublish">
          + 发布需求
        </el-button>
      </div>
    </div>

    <!--
      两栏布局：左侧全平台需求，右侧「我发布的」。
      窄屏自动堆叠（见 @media）。
    -->
    <div class="demand-layout">
      <div class="demand-main">
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
      class="error-alert"
    >
      <template #default>
        <div class="error-desc">无法连接后端服务，请确认后端已启动（默认 8080 端口）后重试</div>
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
          <!--
            自己的求购才给管理按钮（「我的发布」的求购侧）。
            判据是 publisherId === 当前登录用户 id —— 服务端也会独立校验一次归属，
            这里只是不把不属于你的操作摆在你面前。
          -->
          <div v-if="isMine(d)" class="owner-actions">
            <el-button
              v-if="d.status === 1"
              size="small"
              @click.stop="handleClose(d)"
            >关闭</el-button>
            <el-button size="small" type="danger" plain @click.stop="handleRemove(d)">删除</el-button>
          </div>
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
        <el-button v-else-if="canPublishDemand" type="primary" @click="openPublish">
          发布第一条求购需求
        </el-button>
      </el-empty>
    </el-card>
      </div>

      <!--
        右栏「我发布的」。

        数据来源有两处，合并去重：
          1) myDemandsMock —— 本会话通过发布/编辑弹窗新增或修改的（后端 500 期间的 mock 数据）；
          2) demands 里 publisherId 等于当前用户的 —— 真实从后端拿到的部分。
        ⚠️ 后端 GET /demand/list 不支持按发布者过滤（只有 category / region），
        且强制 status=1 AND valid_until >= NOW()，所以「真实那一部分」天然不全：
        已关闭、已过期的不在其中；分页错位时也拿不到。
        因此这里把限制如实标在界面上，而不是让用户以为这就是全部。
      -->
      <aside v-if="canPublishDemand" class="demand-aside">
        <el-card shadow="never" class="mine-card">
          <template #header>
            <div class="mine-head">
              <strong>我发布的</strong>
              <el-tag v-if="!mineLoading" size="small" effect="plain" type="info">共 {{ allMine.length }} 条</el-tag>
            </div>
          </template>

          <el-alert
            v-if="mockMode"
            type="warning"
            :closable="false"
            show-icon
            title="演示模式"
            description="后端发布接口异常，新增与编辑仅保存在本地，刷新页面后消失。"
            class="mine-alert"
          />

          <!--
  三态：加载中 → 空 → 列表。

  ⚠️ 原来这里只判了 `!allMine.length` 就渲染骨架屏，
  于是**加载结束后 0 条时骨架屏仍然挂着** —— 表现为右栏一直显示三行灰条，
  看起来像还在转圈。用 `mineLoading` 把「还没取回来」和「确实没有」分开。
-->
          <el-skeleton v-if="mineLoading && !allMine.length" :rows="2" animated />

          <ul v-else-if="allMine.length" class="mine-list">
            <li v-for="d in allMine" :key="d.demandId" class="mine-item">
              <div class="mine-row">
                <div class="mine-info">
                  <span class="mine-title">{{ categoryLabel(d) }}</span>
                  <el-tag
                    size="small"
                    :type="d.status === 1 ? 'success' : 'info'"
                    effect="plain"
                  >
                    {{ d.status === 1 ? '求购中' : '已关闭' }}
                  </el-tag>
                  <el-tag v-if="d.__mock" size="small" type="warning" effect="plain">演示</el-tag>
                </div>
                <p class="mine-meta">
                  期望 ¥{{ formatPrice(d.expectPrice) }} / {{ d.unit || '件' }}
                  · {{ d.region || '—' }}
                </p>
                <p class="mine-meta">
                  有效期至 {{ formatDate(d.validUntil) || '—' }}
                </p>
              </div>
              <div class="mine-actions">
                <el-button link type="primary" @click="openEditDemand(d)">编辑</el-button>
                <el-button
                  v-if="d.status === 1"
                  link
                  @click="handleCloseMine(d)"
                >关闭</el-button>
                <el-button link type="danger" @click="handleRemoveMine(d)">删除</el-button>
              </div>
            </li>
          </ul>

          <!-- 空态：区分「本来就没有」与「筛完没有」两种语气 -->
          <div v-else class="mine-empty">
            <p class="mine-empty-text">{{ mineEmptyText }}</p>
            <el-button
              v-if="canPublishDemand"
              size="small"
              type="primary"
              plain
              @click="openPublish"
            >发布求购需求</el-button>
          </div>

          <p class="mine-footnote">
            仅统计当前页与本次会话新增的数据；已关闭、已过期的需求不计入。
          </p>
        </el-card>
      </aside>
    </div>

    <DemandFormDialog ref="formDialogRef" @saved="handleDemandSaved" />

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

    <!-- 返回顶部按钮。样式与参数统一由 components/PageBackTop.vue 提供，各页不再各写一份。 -->
    <PageBackTop />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getDemandDetail,
  getDemandList,
  closeDemand,
  removeDemand,
  getMyDemandsMock,
  closeDemandMock,
  removeDemandMock,
  USE_DEMAND_MOCK
} from '@/api/trade'
import { useUserStore } from '@/store/user'
import { formatDateTime, summary as sliceText } from '@/utils/text'
import { useCategories } from '@/composables/useCategories'
import DemandFormDialog from './components/DemandFormDialog.vue'
import PageBackTop from '@/components/PageBackTop.vue'

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

// ★ 2026-10-13 改为接口驱动（GET /trade/categories）。
//   原注释「后端没有字典接口，用已知全集兜底；新增品类后需同步这里」
//   正是需求 7 要根治的病根：名单在前端，库里加了新品类就漏在这里。
const { categoryNames: categoryOptions, load: loadCategories } = useCategories()
const regionOptions = ['江西赣州', '湖北荆州', '黑龙江五常']

const detailVisible = ref(false)
const currentDemand = ref(null)

const userStore = useUserStore()

/**
 * 当前登录用户 id。
 *
 * 用登录返回的 userId（store/user.js 的 profile 快照），不另拉接口 ——
 * 顶栏、个人中心读的都是这一份。
 */
const myUserId = computed(() => userStore.profile?.userId ?? null)

/**
 * 能否发布求购需求。
 *
 * 依据需求规格说明书 2.1 角色与职责表：农户与采购方的职责里都列了「发布求购需求」，
 * UC-TRD-02 的操作流程也从采购方视角描述。专家、银行用户、管理员都不发布。
 *
 * ⚠️ 判断的是 actualRole（登录账号的真实角色，来自 profile.roleName），
 * 而不是 activeRole ——后者是农户/采购方之间可切的演示视角，
 * 用它判断会出现「切成采购方就能发布，实际登录账号是农户」的错位。
 *
 * ⚠️ 后端 POST /demand/publish **没有 requireRole**，任何登录用户都能调通；
 * 这里只是展示层口径，不是安全边界。
 */
const actualRole = computed(() => userStore.profile?.roleName || '')
const canPublishDemand = computed(() => ['农户', '采购方'].includes(actualRole.value))

const mockMode = USE_DEMAND_MOCK
const formDialogRef = ref()
/** mock 发布/编辑出来的需求（后端 500 期间的本地数据） */
const mockDemands = ref([])
/**
 * 右栏是否还在取数据。
 *
 * 为什么要单独一个标记：右栏的数据来自两处（list 过滤 + mock 读取），
 * 而 el-skeleton 只在「没数据」时渲染。若直接用 `!allMine.length` 判断，
 * 加载结束拿到 0 条后骨架屏不会被销毁 —— 表现为右栏一直挂着三行灰条，
 * 看起来像还在转圈。这个标记把「还没取回来」与「确实没有」分开。
 */
const mineLoading = ref(true)

/**
 * 「我发布的」完整集合 = 本次会话新增的 mock + 当前页里真实属于我的。
 *
 * 用 demandId 去重：mock 里的 id 是 9000 起的，与真实 id 不会撞，
 * 但仍按 id 合并，避免编辑 mock 数据后与真实数据重复出现两条。
 */
const allMine = computed(() => {
  const fromList = demands.value.filter((d) => isMine(d))
  const merged = [...mockDemands.value, ...fromList]
  const seen = new Set()
  return merged.filter((d) => {
    const k = String(d.demandId)
    if (seen.has(k)) return false
    seen.add(k)
    return true
  })
})

function openPublish() {
  formDialogRef.value?.open()
}

function openEditDemand(row) {
  formDialogRef.value?.openEdit(row)
}

/** 发布/编辑成功后：拉一次真实列表 + 刷新 mock 列表。 */
async function handleDemandSaved() {
  await Promise.all([loadDemands(), loadMockDemands()])
}

async function loadMockDemands() {
  if (!mockMode) {
    mockDemands.value = []
    mineLoading.value = false
    return
  }
  try {
    mockDemands.value = await getMyDemandsMock()
  } catch (err) {
    console.error('本地演示数据读取失败：', err)
  } finally {
    mineLoading.value = false
  }
}

/**
 * 空态文案。
 *
 * 有筛选条件时不说「你还没发布过」—— 那是在指责用户，而事实是他筛掉了自己的需求。
 * 这种情况引导他清筛选；没筛选条件才是真正的「还没有」。
 */
const mineEmptyText = computed(() =>
  hasFilter.value
    ? '当前筛选条件下没有你发布的需求'
    : '你还没有发布过求购需求'
)

/** 右栏关闭：mock 走本地同步，非mock 走真实接口。 */
async function handleCloseMine(row) {
  try {
    await ElMessageBox.confirm(
      `确定要关闭「${categoryLabel(row)}」吗？关闭后该需求不再展示给农户，可以重新编辑再开。`,
      '关闭求购需求',
      { type: 'warning', confirmButtonText: '确定关闭', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    if (row.__mock) {
      await closeDemandMock(row.demandId)
    } else {
      await closeDemand(row.demandId)
    }
    ElMessage.success(
      row.__mock
        ? '已关闭（演示数据，未入库）'
        : '已关闭。该需求已从公开列表移除，但可在「我发布的」中重新编辑开启'
    )
    // 后端 list 强制 status=1，关闭后那条不会自己从 demands 里消失，
    // 必须显式剔除，否则左栏还会显示一条「已关闭」的行。
    demands.value = demands.value.filter((d) => String(d.demandId) !== String(row.demandId))
    await loadMockDemands()
  } catch (err) {
    console.error('关闭求购需求失败：', err)
  }
}

/** 右栏删除：不可逆，二次确认。 */
async function handleRemoveMine(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除「${categoryLabel(row)}」吗？删除后不可恢复。`,
      '删除求购需求',
      { type: 'warning', confirmButtonClass: 'el-button--danger', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    if (row.__mock) {
      await removeDemandMock(row.demandId)
    } else {
      await removeDemand(row.demandId)
    }
    ElMessage.success('已删除')
    demands.value = demands.value.filter((d) => String(d.demandId) !== String(row.demandId))
    await loadMockDemands()
  } catch (err) {
    console.error('删除求购需求失败：', err)
  }
}

/**
 * 这条求购是不是我发的。
 *
 * 用 == 而不是 === 是有意的：后端 JSON 里 userId 是数字、demand 的 publisherId
 * 也是数字，取到 undefined 时两边都是 undefined 会被误判成"是我的"，
 * 所以先做 null 判断再比。这里显式排除 null，避免未登录/未加载完时
 * 把别人的求购都标成自己的。
 */
function isMine(demand) {
  if (myUserId.value == null || demand?.publisherId == null) return false
  return String(demand.publisherId) === String(myUserId.value)
}

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
  // 左栏重取时右栏也跟着进入加载态：allMine 里有一部分来自 demands，
  // 不置位会出现「骨架屏已销毁但列表还是旧的」这一帧闪动。
  mineLoading.value = true
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
    // 兜底：异常路径下也要结束右栏加载态，否则骨架屏会一直挂着。
    mineLoading.value = false
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

/**
 * 关闭自己的求购。
 *
 * 这是**可逆**操作：状态置 0（「已关闭」）后行还在，重新编辑提交就能再开。
 * 所以确认文案不能吓唬人，也别用 danger 色调。
 */
async function handleClose(row) {
  try {
    await ElMessageBox.confirm(
      `确定要关闭「${categoryLabel(row)}」这条求购需求吗？关闭后不再展示给供应商，可以重新编辑再开`,
      '关闭求购需求',
      { type: 'warning', confirmButtonText: '确定关闭', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户点了取消，不是错误
  }

  try {
    await closeDemand(row.demandId)
    ElMessage.success('求购需求已关闭')
    await loadDemands()
  } catch (err) {
    console.error('关闭求购需求失败：', err)
  }
}

/**
 * 删除自己的求购。
 *
 * 与「关闭」是两个不同的动作，不能合并按钮：
 *   关闭 = 可逆的撤下；删除 = 不可逆的移除记录。
 * 后端对求购一律物理删行（全库无外键指向 t_demand），删了就找不回来，
 * 所以这里必须二次确认且明确写出「不可撤销」。
 */
async function handleRemove(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除「${categoryLabel(row)}」这条求购需求吗？删除后无法恢复，需要重新发布`,
      '删除求购需求',
      {
        type: 'error',
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return
  }

  try {
    await removeDemand(row.demandId)
    ElMessage.success('求购需求已删除')
    // 删的可能是本页最后一条，删完必须回到第 1 页重新拉，否则会停在空页
    if (demands.value.length === 1 && pageNum.value > 1) {
      pageNum.value -= 1
    }
    await loadDemands()
  } catch (err) {
    console.error('删除求购需求失败：', err)
  }
}

onMounted(() => {
  loadCategories()
  loadDemands()
  loadMockDemands()
})
</script>

<style scoped>
.demand-list-page { color: var(--el-text-color-primary); }

/*
  两栏：左侧全平台需求，右侧「我发布的」。
  用 grid 而非 flex —— 需求卡片高度随内容变化，flex 下两栏会因align-items 默认
  拉伸而出现「右栏被拉高但内容只占一半」的白边。grid 配合 align-items:start 更稳。

  ⚠️ 用 auto-fit 而不是 `1fr 320px` 固定两列：
  管理员没有右栏（canPublishDemand 为 false，aside 不渲染），
  固定两列会让左栏只占到 2/3 宽度、右侧空出 320px 的大片空白。
  auto-fit 会根据实际子元素数量自动决定列数 ——
  只有一个子元素时就是单栏全宽，两个时才分成1fr + 320px。
*/
.demand-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}
.demand-layout:has(.demand-aside) {
  grid-template-columns: minmax(0, 1fr) 320px;
}
.demand-main { min-width: 0; }
.demand-aside { min-width: 0; }

.mine-card :deep(.el-card__header) { padding: 14px 16px; }
.mine-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.mine-alert { margin-bottom: 12px; }
.mine-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.mine-item {
  padding: 12px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}
.mine-item:last-child { border-bottom: 0; }
.mine-row { min-width: 0; }
.mine-info {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}
.mine-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
}
.mine-meta {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
  word-break: break-all;
}
.mine-actions {
  margin-top: 6px;
  display: flex;
  gap: 4px;
}
.mine-empty {
  padding: 18px 4px;
  text-align: center;
}
.mine-empty-text {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
.mine-footnote {
  margin: 12px 0 0;
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-tertiary);
}

/* 窄屏：右栏落到左栏下方，不做横向压缩。
   选择器要与上面的 :has() 同特异性，否则窄屏下右栏不会堆叠
   —— :has() 的特异性是 (0,2,0)，这里写成 .demand-layout:has(.demand-aside) 才压得住。 */
@media (max-width: 1100px) {
  .demand-layout,
  .demand-layout:has(.demand-aside) {
    grid-template-columns: minmax(0, 1fr);
  }
}

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

/*
 * 自己的求购才出现的「关闭 / 删除」。用虚线分隔，把它和上面的
 * 报价、联系方式区分开 —— 上面是需求内容，下面是管理动作。
 */
.owner-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  width: 100%;
  padding-top: 8px;
  border-top: 1px dashed var(--el-border-color-lighter);
}
/* Element Plus 给相邻按钮加了 margin-left，和这里的 gap 会叠加成 20px */
.owner-actions :deep(.el-button + .el-button) { margin-left: 0; }


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
  .owner-actions { justify-content: flex-start; }
}
</style>
