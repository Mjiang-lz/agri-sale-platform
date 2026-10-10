<template>
  <div class="my-products-page">
    <div class="page-head">
      <div>
        <h2>我的商品</h2>
        <p>发布并管理你自己上架的农产品。新发布的商品先进入审核队列，管理员通过后自动上架，之后你可以随时手动下架。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="loadList">刷新</el-button>
        <el-button type="primary" @click="openForm(null)">+ 发布商品</el-button>
      </div>
    </div>

    <el-card shadow="never" class="content-card">
      <div class="filter-bar">
        <el-radio-group v-model="auditFilter" @change="handleFilterChange">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="待审核">待审核</el-radio-button>
          <el-radio-button value="已通过">已通过</el-radio-button>
          <el-radio-button value="未通过">未通过</el-radio-button>
        </el-radio-group>
        <span class="filter-hint">共 {{ total }} 件商品</span>
      </div>

      <el-alert
        v-if="errorState"
        type="error"
        show-icon
        :closable="false"
        title="商品列表加载失败"
        description="无法连接后端服务，或当前账号不是农户角色（本接口仅农户可用）。"
        class="error-alert"
      >
        <template #default>
          <el-button type="danger" size="small" @click="loadList">重新加载</el-button>
        </template>
      </el-alert>

      <template v-else>
        <el-table v-loading="loading" :data="products" row-key="productId">
          <el-table-column label="商品" min-width="250">
            <template #default="{ row }">
              <div class="goods-cell">
                <div
                  class="thumb"
                  :class="coverClass(row)"
                  :style="{ background: productFallbackBg(row) }"
                >
                  <span class="thumb-fallback">{{ productFallbackText(row) }}</span>
                  <img :src="productImage(row)" :alt="row.productName" @error="onImgError" />
                </div>
                <div class="pinfo">
                  <h4>{{ row.productName }}</h4>
                  <small>
                    {{ row.origin || '未填产地' }}
                    <template v-if="!row.imageUrl">
                      <el-tag size="small" type="info" effect="plain" class="no-img-tag">无主图</el-tag>
                    </template>
                  </small>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column prop="category" label="品类" width="100" />

          <el-table-column label="单价" width="130" align="right">
            <template #default="{ row }">
              ¥{{ formatPrice(row.price) }}<span class="unit">/{{ row.unit || '件' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="库存" width="110" align="right">
            <template #default="{ row }">
              <span :class="{ 'stock-zero': Number(row.stock || 0) === 0 }">{{ row.stock ?? 0 }}</span>
              {{ row.unit || '件' }}
            </template>
          </el-table-column>

          <el-table-column label="审核状态" width="110">
            <template #default="{ row }">
              <el-tag :type="auditType(row.auditStatus)">{{ row.auditStatus }}</el-tag>
            </template>
          </el-table-column>

          <el-table-column label="上架状态" width="110">
            <template #default="{ row }">
              <el-tag v-if="row.status === 1" type="success" effect="plain">在售</el-tag>
              <el-tag v-else type="info" effect="plain">已下架</el-tag>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openForm(row)">编辑</el-button>
              <!--
                只有审核通过的商品才给上下架按钮：
                后端对未过审的商品上架会报 400「通过审核后才能上架」。
                与其让用户点了再吃一个报错，不如把按钮置灰并说明原因 ——
                但**不隐藏**它，否则用户会以为「这功能对我没有」。
              -->
              <el-tooltip
                v-if="row.auditStatus !== '已通过'"
                content="通过审核后才能上下架"
                placement="top"
              >
                <span class="op-disabled">
                  <el-button link disabled>{{ row.status === 1 ? '下架' : '上架' }}</el-button>
                </span>
              </el-tooltip>
              <el-button
                v-else
                link
                :type="row.status === 1 ? 'warning' : 'success'"
                @click="toggleStatus(row)"
              >
                {{ row.status === 1 ? '下架' : '上架' }}
              </el-button>
            </template>
          </el-table-column>

          <template #empty>
            <el-empty :description="emptyText">
              <el-button v-if="!auditFilter" type="primary" @click="openForm(null)">发布第一件商品</el-button>
              <el-button v-else @click="auditFilter = ''">查看全部商品</el-button>
            </el-empty>
          </template>
        </el-table>

        <div v-if="products.length" class="pagination">
          <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            :total="total"
            layout="total, sizes, prev, pager, next"
            background
            @current-change="loadList"
            @size-change="handleSizeChange"
          />
        </div>
      </template>
    </el-card>

    <!-- ============ 发布 / 编辑 ============ -->
    <el-dialog
      v-model="formVisible"
      :title="editing ? '编辑商品' : '发布商品'"
      width="min(680px, 94vw)"
      destroy-on-close
    >
      <el-alert
        v-if="editing && editing.auditStatus !== '待审核'"
        type="warning"
        show-icon
        :closable="false"
        class="form-alert"
        title="改动商品内容会重新进入审核"
        :description="`修改商品名称、品类、描述、产地或主图后，商品会被打回「待审核」并自动下架，需管理员重新审核通过才会上架。只调价格和库存不触发重新审核。`"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" @submit.prevent>
        <el-form-item label="商品名称" prop="productName">
          <el-input v-model="form.productName" maxlength="100" show-word-limit placeholder="如「赣南脐橙 特级果」" />
        </el-form-item>

        <el-form-item label="商品品类" prop="category">
          <el-select v-model="form.category" style="width: 100%" placeholder="请选择品类">
            <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>

        <div class="form-row">
          <el-form-item label="单价" prop="price" class="row-item">
            <el-input-number v-model="form.price" :min="0.01" :precision="2" :step="1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="库存" prop="stock" class="row-item">
            <el-input-number v-model="form.stock" :min="0" :precision="0" :step="10" style="width: 100%" />
          </el-form-item>
        </div>

        <div class="form-row">
          <el-form-item label="计量单位" class="row-item">
            <el-select v-model="form.unit" style="width: 100%" placeholder="请选择" clearable>
              <el-option v-for="u in UNITS" :key="u" :label="u" :value="u" />
            </el-select>
          </el-form-item>
          <el-form-item label="产地" class="row-item">
            <el-input v-model="form.origin" maxlength="50" placeholder="到市，如 江西赣州" />
          </el-form-item>
        </div>

        <el-form-item label="商品描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="品种、规格、采摘时间、包装方式等"
          />
        </el-form-item>

        <el-form-item label="商品主图">
          <div class="upload-block">
            <el-upload
              action="#"
              :show-file-list="false"
              accept=".jpg,.jpeg,.png,.gif,.webp"
              :http-request="handleUpload"
              :before-upload="beforeUpload"
            >
              <el-button :loading="uploading">{{ form.imageUrl ? '更换主图' : '上传主图' }}</el-button>
            </el-upload>
            <el-button v-if="form.imageUrl" link type="danger" @click="form.imageUrl = ''">移除</el-button>
            <el-image v-if="form.imageUrl" class="upload-preview" :src="form.imageUrl" fit="cover" />
            <!-- 主图不是必填：没有图时前台会按商品名匹配实拍图兜底（见 utils/imagery.js），
                 不会出现碎图。所以这里如实说明，不逼用户先去找图 -->
            <span v-if="!form.imageUrl" class="upload-tip">选填。不上传时前台会用同类实拍图兜底展示。</span>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">
          {{ editing ? '保存修改' : '提交审核' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMyProducts,
  publishProduct,
  updateMyProduct,
  updateMyProductStatus
} from '@/api/trade'
import { checkImageFile, uploadProductImage } from '@/api/common'
import { productImage, productFallbackBg, productFallbackText } from '@/utils/imagery'

/**
 * 页面用途：我的商品（农户视角）。
 *
 * ★ 这一页是补缺口建出来的。后端四个接口（publish / update / status / my-products）
 *   早在骨架期就实现了，前端却一直没有页面 —— 结果是「农户登录后没有任何入口
 *   能发布自己的农产品」，首页展示的商品全是 data.sql 里的种子数据，
 *   整条供给侧闭环在演示上是断的。
 *
 * 接口文件：frontend-web/src/api/trade.js
 *   GET  /api/trade/my-products?pageNum&pageSize&auditStatus   我发布的（不过滤审核状态）
 *   POST /api/trade/product/publish                            发布
 *   PUT  /api/trade/products/{id}                              编辑
 *   PUT  /api/trade/products/{id}/status?status=0|1            上下架
 *
 * ⚠️ 这四个接口在后端都 requireRole(FARMER)，采购方/专家/银行用户调会拿到
 *    HTTP 200 + body code 403。导航里也按真实角色隐藏了入口（见 MainLayout）。
 *
 * ★ 三条必须留意的后端行为（前端据此设计，不要"顺手优化"掉）：
 *   1. 新发布的商品 auditStatus=待审核 且 status=0（下架），
 *      由管理员审核通过时置为在架。所以这里的按钮写「提交审核」而不是「发布上架」。
 *   2. 编辑是**部分更新**：字段为 null 表示本次不修改。见 buildPayload() 的说明 ——
 *      这是本页最容易写错的地方。
 *   3. 编辑命中「内容变更」会被打回待审核并下架；只改价格/库存不会。
 *      ContentChanged 的比较范围 = 名称 / 品类 / 描述 / 产地 / 主图 / 多图。
 */

const CATEGORIES = ['蔬菜', '水果', '粮油', '干货', '水产', '畜禽产品']
/** 与 data.sql 里既有商品的单位取值保持一致，避免同一个意思出现「斤 / 市斤 / 500g」三种写法 */
const UNITS = ['斤', '公斤', '箱', '袋', '盒', '桶', '个', '只']

const loading = ref(false)
const errorState = ref(false)
const products = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const auditFilter = ref('')

const formVisible = ref(false)
const editing = ref(null)
const submitting = ref(false)
const uploading = ref(false)
const formRef = ref(null)

const form = reactive({
  productName: '',
  category: '',
  price: 1,
  stock: 0,
  unit: '斤',
  origin: '',
  description: '',
  imageUrl: ''
})

const emptyText = computed(() =>
  auditFilter.value ? `没有「${auditFilter.value}」的商品` : '还没有发布过商品'
)

/**
 * 校验规则与后端 ProductPublishDTO 的注解逐条对齐
 * （productName ≤100 必填 / category ≤30 必填 / price ≥0.01 必填 /
 *   stock ≥0 必填 / unit ≤10 / description ≤500 / origin ≤50 / imageUrl ≤255）。
 * 这里只是提前给反馈，不是安全边界 —— 后端会再校验一遍。
 */
const rules = {
  productName: [
    { required: true, message: '请填写商品名称', trigger: 'blur' },
    { max: 100, message: '商品名称不能超过 100 字', trigger: 'blur' }
  ],
  category: [{ required: true, message: '请选择商品品类', trigger: 'change' }],
  price: [{ required: true, message: '请填写单价', trigger: 'blur' }],
  stock: [{ required: true, message: '请填写库存', trigger: 'blur' }]
}

onMounted(loadList)

async function loadList() {
  loading.value = true
  errorState.value = false
  try {
    const data = await getMyProducts({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      auditStatus: auditFilter.value || undefined
    })
    products.value = data?.records || []
    total.value = Number(data?.total ?? products.value.length)
  } catch (err) {
    products.value = []
    total.value = 0
    errorState.value = true
    console.error('我的商品加载失败：', err)
  } finally {
    loading.value = false
  }
}

function handleFilterChange() {
  // 换筛选条件必须把页码拨回第 1 页：否则在第 3 页筛出 0 条时，
  // 用户会以为「一件都没有」，其实数据在第 1 页
  pageNum.value = 1
  loadList()
}

function handleSizeChange() {
  pageNum.value = 1
  loadList()
}

function auditType(status) {
  if (status === '已通过') return 'success'
  if (status === '未通过') return 'danger'
  return 'warning'
}

function openForm(row) {
  editing.value = row
  if (row) {
    form.productName = row.productName || ''
    form.category = row.category || ''
    form.price = row.price === null || row.price === undefined ? 1 : Number(row.price)
    form.stock = Number(row.stock || 0)
    form.unit = row.unit || ''
    form.origin = row.origin || ''
    form.description = row.description || ''
    form.imageUrl = row.imageUrl || ''
  } else {
    form.productName = ''
    form.category = ''
    form.price = 1
    form.stock = 0
    form.unit = '斤'
    form.origin = ''
    form.description = ''
    form.imageUrl = ''
  }
  formRef.value?.clearValidate()
  formVisible.value = true
}

/**
 * 组装提交体。
 *
 * ★ 这是本页最需要小心的一处。后端 updateProduct 把字段为 null 当作
 *   「本次不修改该字段」，而 isContentChanged 的判据是
 *   `incoming != null && !Objects.equals(incoming, existing)`。
 *   两者叠加会产生一个陷阱：
 *
 *     库里 description = NULL，表单里描述框是空的（读出来就是 ''），
 *     如果照直发 description: ''，则 '' != null 成立、且 '' 与 null 不相等，
 *     于是「内容变更 = true」→ 商品被打回待审核并自动下架。
 *     也就是说，农户只是打开编辑弹窗、原样点一下保存，商品就会离奇下架。
 *
 *   所以编辑时只发「真正被改动过的」可选字段：
 *     · 没动过的可选字段 → 不出现在 payload 里（等价于 null = 不修改）；
 *     · 用户主动清空的字段 → 发 ''，这是有意的清空，触发重新审核是合理的。
 *   必填字段（名称/品类/价格/库存）照常全发 —— 它们没变时 differs 自然为 false。
 */
function buildPayload() {
  const base = {
    productName: form.productName.trim(),
    category: form.category,
    price: form.price,
    stock: form.stock
  }

  const optionals = {
    unit: (form.unit || '').trim(),
    origin: (form.origin || '').trim(),
    description: (form.description || '').trim(),
    imageUrl: (form.imageUrl || '').trim()
  }

  if (!editing.value) {
    // 新建：空值不发，让库里存 NULL 而不是空字符串 ——
    // 否则同一种「没填」在库里会有 '' 和 NULL 两种表示，后续按 IS NULL 筛会漏数据
    for (const [key, value] of Object.entries(optionals)) {
      if (value) base[key] = value
    }
    return base
  }

  for (const [key, value] of Object.entries(optionals)) {
    const before = String(editing.value[key] ?? '').trim()
    if (value !== before) base[key] = value
  }
  return base
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (editing.value) {
      await updateMyProduct(editing.value.productId, buildPayload())
      ElMessage.success('已保存，如涉及内容改动请等待重新审核')
    } else {
      await publishProduct(buildPayload())
      ElMessage.success('已提交审核，管理员通过后将自动上架')
    }
    formVisible.value = false
    await loadList()
  } catch (err) {
    // 失败保持弹窗打开，输入不丢；错误提示已由 request.js 拦截器给出
    console.error('保存商品失败：', err)
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row) {
  const toOn = row.status !== 1
  try {
    await ElMessageBox.confirm(
      toOn
        ? `把「${row.productName}」重新上架？上架后采购方可立即看到并下单。`
        : `把「${row.productName}」下架？下架后采购方在货源列表里看不到它，已生成的订单不受影响。`,
      toOn ? '上架商品' : '下架商品',
      { type: 'warning', confirmButtonText: toOn ? '确认上架' : '确认下架', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }

  try {
    await updateMyProductStatus(row.productId, toOn ? 1 : 0)
    ElMessage.success(toOn ? '已上架' : '已下架')
  } catch (err) {
    console.error('上下架失败：', err)
  }
  // 无论成败都重新拉列表：失败原因可能是「商品刚好被管理员改了审核状态」，
  // 此时本地那一行是过期的，刷新才能让用户看到真实状态。
  // 不做乐观更新 —— 状态以后端为准。
  await loadList()
}

function beforeUpload(file) {
  const err = checkImageFile(file, { label: '商品主图' })
  if (err) {
    ElMessage.warning(err)
    return false
  }
  return true
}

/**
 * 自定义上传：走项目的 request 封装而不是 el-upload 自身的 action，
 * 这样才带得上 Authorization 头，也能吃到统一的错误处理。
 */
async function handleUpload(options) {
  uploading.value = true
  try {
    const result = await uploadProductImage(options.file)
    form.imageUrl = result.url
    options.onSuccess(result)
  } catch (error) {
    options.onError(error)
  } finally {
    uploading.value = false
  }
}

/** 图片 404 时隐藏 img，露出下层首字（与商品列表/购物车同一策略） */
function onImgError(e) {
  e.target.style.opacity = '0'
}

function formatPrice(value) {
  const n = Number(value)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

/** 与 Cart.vue / Checkout.vue 一致的首字哈希取色 */
function coverClass(item) {
  const h = String(item.productName || item.productId || '').split('').reduce((a, c) => a + c.charCodeAt(0), 0)
  return `t-${h % 6}`
}
</script>

<style scoped>
.my-products-page {
  color: var(--el-text-color-primary);
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}
.filter-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.error-alert {
  margin-bottom: 8px;
}

/* ===== 商品单元格 ===== */
.goods-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.thumb {
  position: relative;
  flex: none;
  width: 48px;
  height: 48px;
  border-radius: 9px;
  overflow: hidden;
}
.thumb-fallback {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
}
.thumb img {
  position: relative;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.pinfo h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
}
.pinfo small {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.no-img-tag {
  transform: scale(0.9);
  transform-origin: left center;
}
.unit {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.stock-zero {
  color: var(--el-color-danger);
  font-weight: 600;
}
/* 禁用态的上下架按钮外层：tooltip 需要包一层才能在有 disabled 子元素时收到鼠标事件 */
.op-disabled {
  display: inline-block;
  cursor: not-allowed;
}

/* ===== 表单 ===== */
.form-alert {
  margin-bottom: 16px;
}
.form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.row-item {
  margin-bottom: 18px;
}
.upload-block {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.upload-preview {
  width: 76px;
  height: 76px;
  border-radius: 10px;
  border: 1px solid var(--rxt-border);
}
.upload-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

/* 缩略图兜底底色，值与 Cart.vue / Checkout.vue 一致 */
.t-0 { background: linear-gradient(135deg, #2d7a4c, #73b586); }
.t-1 { background: linear-gradient(135deg, #91602a, #d4a164); }
.t-2 { background: linear-gradient(135deg, #2c6679, #78adba); }
.t-3 { background: linear-gradient(135deg, #674b7d, #a991ba); }
.t-4 { background: linear-gradient(135deg, #8b5b24, #c8a15d); }
.t-5 { background: linear-gradient(135deg, #2f6b6e, #7cb5b0); }
</style>
