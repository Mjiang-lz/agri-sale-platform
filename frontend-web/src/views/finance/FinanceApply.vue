<template>
  <div class="finance-apply-page">
    <!-- 页面用途：农户选择贷款产品、填写融资申请、查看联保推荐并提交组队。 -->
    <div class="page-head">
      <div>
        <h2>融资申请</h2>
        <p>选择贷款产品，填写额度、期限和资金用途。提交后系统将推荐经营特征相近的联保候选人。</p>
      </div>
      <div class="head-tags">
        <el-tag type="success" effect="plain">接口：POST /api/finance/apply</el-tag>
        <el-tag v-if="demoMode" type="warning" effect="dark">演示数据</el-tag>
      </div>
    </div>

    <el-row :gutter="18">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="content-card">
          <template #header>
            <div class="card-title">
              <strong>申请信息</strong>
              <span>带 * 的字段为必填项</span>
            </div>
          </template>

          <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
            <el-form-item label="贷款产品" prop="loanProductId">
              <div class="product-list">
                <button
                  v-for="product in productList"
                  :key="product.loanProductId"
                  type="button"
                  class="product-card"
                  :class="{ active: form.loanProductId === product.loanProductId }"
                  @click="selectProduct(product)"
                >
                  <span class="product-name">{{ product.productName }}</span>
                  <strong>{{ formatMoney(product.maxAmount) }} 元</strong>
                  <small>年利率 {{ formatRate(product.annualRate) }} · 最长 {{ product.termMonths }} 个月</small>
                  <em>最低 {{ product.minGroupSize }} 人申请</em>
                </button>
              </div>
            </el-form-item>

            <el-row :gutter="16">
              <el-col :xs="24" :sm="12">
                <el-form-item label="申请额度（元）" prop="applyAmount">
                  <el-input-number
                    v-model="form.applyAmount"
                    :min="1000"
                    :max="Number(selectedProduct?.maxAmount || 500000)"
                    :step="1000"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="12">
                <el-form-item label="申请期限" prop="applyTerm">
                  <el-select v-model="form.applyTerm" style="width: 100%">
                    <el-option
                      v-for="term in termOptions"
                      :key="term"
                      :label="`${term} 个月`"
                      :value="term"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="资金用途" prop="applyPurpose">
              <el-input
                v-model="form.applyPurpose"
                type="textarea"
                :rows="4"
                maxlength="200"
                show-word-limit
                placeholder="例如：采购农资、升级大棚、购置烘干设备等"
              />
            </el-form-item>

            <el-form-item label="申请资料（本地选择）">
              <!-- 当前 FinanceApplyDTO 尚未接收文件字段；这里保留资料上传交互，后端扩展后可直接接入。 -->
              <el-upload
                v-model:file-list="fileList"
                action="#"
                multiple
                :auto-upload="false"
                :limit="5"
                accept=".pdf,.jpg,.jpeg,.png"
                on-exceed="handleFileExceed"
              >
                <el-button>选择资料</el-button>
                <template #tip>
                  <div class="el-upload__tip">支持 PDF/JPG/PNG，最多 5 个。当前后端未提供文件上传接口，因此只在前端展示文件名。</div>
                </template>
              </el-upload>
            </el-form-item>

            <div class="form-actions">
              <el-button type="primary" size="large" :loading="submitting" @click="handleSubmit">
                提交融资申请
              </el-button>
              <el-button size="large" @click="resetForm">重置</el-button>
            </div>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="content-card">
          <template #header><strong>产品说明</strong></template>
          <template v-if="selectedProduct">
            <h3>{{ selectedProduct.productName }}</h3>
            <p class="description">{{ selectedProduct.description }}</p>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="最高额度">{{ formatMoney(selectedProduct.maxAmount) }} 元</el-descriptions-item>
              <el-descriptions-item label="年利率">{{ formatRate(selectedProduct.annualRate) }}</el-descriptions-item>
              <el-descriptions-item label="最长期限">{{ selectedProduct.termMonths }} 个月</el-descriptions-item>
              <el-descriptions-item label="联保人数">{{ selectedProduct.minGroupSize }} 人起</el-descriptions-item>
            </el-descriptions>
          </template>
          <el-empty v-else description="请选择贷款产品" />
        </el-card>

        <el-alert
          class="policy-alert"
          type="info"
          :closable="false"
          show-icon
          title="申请规则"
          description="同一农户同时最多参与一个进行中的联合贷款小组；额度和期限必须在所选产品允许范围内。"
        />
      </el-col>
    </el-row>

    <el-dialog v-model="recommendVisible" title="推荐联保人" width="min(760px, 94vw)" :close-on-click-modal="false">
      <p class="dialog-tip">
        按经营特征匹配度排序。已经在小队里的农户不会出现在这里；
        若某位候选人本次未能入组，创建小组时会告诉你具体原因。
      </p>

      <!-- 推荐接口失败时如实呈现，不再用演示数据顶替：
           把「后端没给出结果」换成看起来正常的假名单，正是这次要修掉的那类静默故障。 -->
      <el-alert
        v-if="recommendError"
        class="recommend-alert"
        type="error"
        :closable="false"
        show-icon
        title="推荐联保人获取失败"
        :description="recommendError"
      />

      <el-checkbox-group
        v-else
        v-model="selectedFarmerIds"
        class="candidate-list"
        :max="maxSelectable"
      >
        <el-checkbox
          v-for="candidate in candidates"
          :key="candidate.userId"
          :value="candidate.userId"
          class="candidate-item"
        >
          <span class="candidate-main">
            <strong>{{ candidate.realName }}</strong>
            <small>
              {{ candidate.mainProduct || '农产品经营' }}
              <template v-if="candidate.region"> · {{ candidate.region }}</template>
            </small>
            <em v-if="candidate.matchReason" class="candidate-reason">{{ candidate.matchReason }}</em>
          </span>
          <el-tag size="small" type="success">{{ candidate.matchRate ?? 0 }}% 匹配</el-tag>
        </el-checkbox>
      </el-checkbox-group>

      <el-empty
        v-if="!recommendError && !candidates.length"
        description="暂时没有可推荐的联保候选人（可能其他农户都已在小组中，或尚未提交该产品的融资申请）"
      />

      <el-alert
        v-if="selectedProduct && !recommendError"
        class="group-size-alert"
        :type="groupSizeEnough ? 'success' : 'warning'"
        :closable="false"
        show-icon
        :title="`${selectedProduct.productName} 起贷需 ${selectedProduct.minGroupSize} 户，当前 ${selectedFarmerIds.length + 1} 户（含你）`"
        :description="groupSizeEnough
          ? '人数已达标，可以直接创建小组。'
          : '人数不足也可以先创建：小组会停留在「组建中」，其他农户提交同产品申请后即可加入。'"
      />

      <template #footer>
        <el-button @click="recommendVisible = false">稍后组队</el-button>
        <el-button type="primary" :loading="groupSubmitting" @click="handleCreateGroup">确认创建小组</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createLoanGroup,
  getLoanProducts,
  getRecommendedGuarantors,
  submitFinanceApply
} from '@/api/finance'

/**
 * 页面用途：融资申请。
 * 调用 finance.js：
 * - getLoanProducts 获取贷款产品；
 * - submitFinanceApply 提交申请；
 * - getRecommendedGuarantors 获取联保推荐；
 * - createLoanGroup 创建联合贷款小组。
 */
const router = useRouter()
const useDemo = import.meta.env.VITE_USE_DEMO === 'true'
const demoMode = ref(false)
const formRef = ref()
const submitting = ref(false)
const groupSubmitting = ref(false)
const productList = ref([])
const fileList = ref([])
const recommendVisible = ref(false)
const candidates = ref([])
const selectedFarmerIds = ref([])
/** 推荐接口的失败原因。非空时弹窗内展示错误态，而不是展示一份空名单 */
const recommendError = ref('')

const form = reactive({
  loanProductId: null,
  applyAmount: 10000,
  applyTerm: 12,
  applyPurpose: ''
})

const rules = {
  loanProductId: [{ required: true, message: '请选择贷款产品', trigger: 'change' }],
  applyAmount: [{ required: true, message: '请填写申请额度', trigger: 'change' }],
  applyTerm: [{ required: true, message: '请选择申请期限', trigger: 'change' }],
  applyPurpose: [
    { required: true, message: '请填写资金用途', trigger: 'blur' },
    { max: 200, message: '资金用途不能超过 200 个字', trigger: 'blur' }
  ]
}

const selectedProduct = computed(() =>
  productList.value.find((item) => Number(item.loanProductId) === Number(form.loanProductId))
)

const termOptions = computed(() => {
  const max = Number(selectedProduct.value?.termMonths || 36)
  return [6, 12, 24, 36].filter((item) => item <= max)
})

/**
 * 本产品最多还能邀请几位候选人。
 *
 * <p>起贷人数同时也是人数上限（t_loan_product 没有「最大组队人数」列，
 * 可贷总额 = 起贷人数 × 单户额度，凑满即封组 —— 见后端 LoanGroupServiceImpl）。
 * 组长本人占一个名额，所以候选人上限要减 1。</p>
 */
const maxSelectable = computed(() =>
  Math.max(0, Number(selectedProduct.value?.minGroupSize || 1) - 1)
)

/** 人数是否已达起贷下限（组长本人也算一户） */
const groupSizeEnough = computed(() =>
  selectedFarmerIds.value.length + 1 >= Number(selectedProduct.value?.minGroupSize || 1)
)

/** 后端分页对象统一转成数组。 */
function normalizePage(data) {
  if (Array.isArray(data)) return data
  return data?.records || data?.list || []
}

function formatMoney(value) {
  const number = Number(value || 0)
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: Number.isInteger(number) ? 0 : 2,
    maximumFractionDigits: 2
  }).format(number)
}

function formatRate(value) {
  return `${(Number(value || 0) * 100).toFixed(2)}%`
}

function selectProduct(product) {
  form.loanProductId = product.loanProductId
  form.applyAmount = Math.min(Number(form.applyAmount || 10000), Number(product.maxAmount))
  if (!termOptions.value.includes(form.applyTerm)) {
    form.applyTerm = termOptions.value.at(-1) || product.termMonths
  }
}

function handleFileExceed() {
  ElMessage.warning('最多选择 5 个申请资料')
}

/**
 * 贷款产品只使用真实接口结果。
 * 只有显式设置 VITE_USE_DEMO=true 时，接口失败或返回空列表才启用演示数据，
 * 并在页面顶部显示“演示数据”，避免把接口故障误判为功能正常。
 */
async function loadProducts() {
  const demoProducts = [
    { loanProductId: 1, productName: '惠农小额信用贷', maxAmount: 100000, annualRate: 0.045, termMonths: 12, minGroupSize: 1, description: '面向个体农户的小额信用贷款，凭经营流水与信用记录申请。' },
    { loanProductId: 2, productName: '联合助农贷', maxAmount: 300000, annualRate: 0.038, termMonths: 24, minGroupSize: 3, description: '3 户及以上组成联合小组共同申请，利率较信用贷下浮。' },
    { loanProductId: 3, productName: '乡村振兴产业贷', maxAmount: 500000, annualRate: 0.042, termMonths: 36, minGroupSize: 2, description: '支持大棚改造、冷链仓储等农业产业项目。' },
    { loanProductId: 4, productName: '农机购置专项贷', maxAmount: 200000, annualRate: 0.035, termMonths: 24, minGroupSize: 1, description: '用于购置农机具、烘干及分选设备。' },
    { loanProductId: 5, productName: '春耕备耕周转贷', maxAmount: 80000, annualRate: 0.05, termMonths: 6, minGroupSize: 1, description: '满足种子、化肥、农药等短期周转需求。' }
  ]
  try {
    const data = await getLoanProducts()
    productList.value = normalizePage(data).filter((item) => item.status !== 0)
    if (!productList.value.length && useDemo) {
      productList.value = demoProducts
      demoMode.value = true
    }
  } catch {
    productList.value = []
    if (useDemo) {
      productList.value = demoProducts
      demoMode.value = true
    }
  }
  if (productList.value.length) selectProduct(productList.value[0])
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await submitFinanceApply({
      loanProductId: form.loanProductId,
      applyAmount: form.applyAmount,
      applyTerm: form.applyTerm,
      applyPurpose: form.applyPurpose
    })
    ElMessage.success('融资申请提交成功')

    // 个体贷款（起贷人数 ≤ 1）没有联保的必要：小组只会有一位成员，
    // 后端也会直接拒绝建组，所以这里不进入组队流程
    const minGroupSize = Number(selectedProduct.value?.minGroupSize || 1)
    if (minGroupSize <= 1) {
      ElMessage.info(`${selectedProduct.value?.productName || '该产品'} 为个体贷款，无需组建联保小组`)
      router.push('/finance/my')
      return
    }

    recommendError.value = ''
    try {
      const recommended = await getRecommendedGuarantors()
      candidates.value = Array.isArray(recommended) ? recommended : normalizePage(recommended)
    } catch (err) {
      // 这里<不再>回退到 demoCandidates()：把一个失败的后端请求替换成
      // 看起来正常的假名单，会让「推荐功能挂了」表现为「推荐功能好着呢」。
      // 错误提示已由 request.js 统一弹过，弹窗内再给一份可读的说明。
      candidates.value = []
      recommendError.value = err?.message || '推荐联保人获取失败，请稍后重试'
    }
    selectedFarmerIds.value = []
    recommendVisible.value = true
  } finally {
    submitting.value = false
  }
}

async function handleCreateGroup() {
  if (!selectedFarmerIds.value.length) {
    ElMessage.warning('请至少选择一位推荐联保人；若暂时无人可选，可点「稍后组队」')
    return
  }
  groupSubmitting.value = true
  try {
    // 传 memberIds 而不是把成员姓名写进 description：
    // 后者在 t_loan_group 里根本没有对应列，收下之后无处可写
    const result = await createLoanGroup({
      groupName: `${selectedProduct.value?.productName || '联合贷款'}小组`,
      memberIds: [...selectedFarmerIds.value],
      loanProductId: form.loanProductId
    })
    recommendVisible.value = false
    await showCreateResult(result)
    router.push('/finance/my')
  } catch (err) {
    // 失败原因（例如「你已在一个进行中的联合贷款小组中」）由 request.js 统一提示
    console.error('创建联合贷款小组失败：', err)
  } finally {
    groupSubmitting.value = false
  }
}

/**
 * 如实展示建组结果。
 *
 * <p>「部分候选人没进来」不算失败（组长的组队动作本身合法），但绝不能静默 ——
 * 不说明的话发起人会以为小组已经凑齐，实际小组里可能只有自己一个人。</p>
 *
 * <p>用纯文本而不是 HTML 拼接：候选人姓名与未入组原因虽然来自自家后端，
 * 但姓名是用户可填字段，走 HTML 渲染就等于开了一个注入口。</p>
 */
async function showCreateResult(result) {
  const required = Number(result?.minGroupSize ?? selectedProduct.value?.minGroupSize ?? 1)
  const count = Number(result?.memberCount ?? 1)
  const joined = (result?.joined || [])
    .map((member) => member.realName)
    .filter(Boolean)
    .join('、')
  const skipped = result?.skipped || []

  let text = `小组「${result?.groupName || '联合贷款小组'}」已创建：当前 ${count} 户，起贷需 ${required} 户。`
  if (joined) {
    text += ` 已入组：${joined}。`
  }
  if (skipped.length) {
    const details = skipped
      .map((item) => `${item.realName || `用户 ${item.userId}`}：${item.reason}`)
      .join('；')
    text += ` 未入组 ${skipped.length} 人 —— ${details}。`
  }

  await ElMessageBox.alert(text, skipped.length ? '小组已创建，部分候选人未入组' : '小组创建成功', {
    confirmButtonText: '去我的融资查看'
  })
}

function resetForm() {
  formRef.value?.resetFields()
  fileList.value = []
  if (productList.value.length) selectProduct(productList.value[0])
}

onMounted(loadProducts)
</script>

<style scoped>
.finance-apply-page { color: var(--el-text-color-primary); }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; margin-bottom: 18px; }
.page-head h2 { margin: 0; font-size: 24px; }
.page-head p { margin: 8px 0 0; color: var(--el-text-color-secondary); line-height: 1.7; }
.head-tags { display: flex; align-items: center; gap: 8px; }
.content-card { border-radius: 14px; }
.card-title { display: flex; justify-content: space-between; align-items: center; }
.card-title span { color: var(--el-text-color-secondary); font-size: 12px; }
.product-list { width: 100%; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.product-card { padding: 14px; border: 1px solid var(--el-border-color); border-radius: 12px; background: #fff; text-align: left; cursor: pointer; transition: .2s; }
.product-card:hover, .product-card.active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); box-shadow: 0 0 0 1px var(--el-color-primary-light-7); }
.product-card span, .product-card strong, .product-card small, .product-card em { display: block; }
.product-card strong { margin: 8px 0; color: #d84b39; font-size: 18px; }
.product-card small { color: var(--el-text-color-secondary); line-height: 1.6; }
.product-card em { margin-top: 8px; color: #9a6b24; font-size: 12px; font-style: normal; }
.product-card .product-name { font-weight: 600; }
.form-actions { display: flex; gap: 10px; padding-top: 4px; }
.description { color: var(--el-text-color-secondary); line-height: 1.8; }
.policy-alert { margin-top: 14px; }
.dialog-tip { margin: 0 0 14px; color: var(--el-text-color-secondary); line-height: 1.7; }
.candidate-list { display: grid; gap: 10px; margin-bottom: 16px; }
.candidate-item { width: 100%; height: auto; margin: 0; padding: 12px; border: 1px solid var(--el-border-color); border-radius: 10px; }
.candidate-item :deep(.el-checkbox__label) { width: 100%; display: flex; justify-content: space-between; align-items: center; }
.candidate-main strong, .candidate-main small, .candidate-main em { display: block; }
.candidate-main em { margin-top: 5px; color: var(--el-text-color-secondary); font-size: 12px; font-style: normal; line-height: 1.6; }
.recommend-alert, .group-size-alert { margin-top: 4px; }
.group-size-alert { margin-top: 14px; }
.candidate-main small { margin-top: 4px; color: var(--el-text-color-secondary); }
@media (max-width: 900px) { .product-list { grid-template-columns: 1fr; } }
@media (max-width: 680px) { .page-head { flex-direction: column; } .form-actions { flex-direction: column; } .form-actions .el-button { width: 100%; margin-left: 0; } }
</style>