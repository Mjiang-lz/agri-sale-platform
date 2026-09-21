<template>
  <div class="finance-apply-page">
    <!-- 页面用途：农户选择贷款产品、填写融资申请、查看联保推荐并提交组队。 -->
    <div class="page-head">
      <div>
        <h2>融资申请</h2>
        <p>选择贷款产品，填写额度、期限和资金用途。提交后系统将推荐经营特征相近的联保候选人。</p>
      </div>
      <el-tag type="success" effect="plain">接口：POST /api/finance/apply</el-tag>
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
      <!-- 推荐数据优先使用 GET /api/finance/recommend，后端暂未返回时使用演示候选人。 -->
      <p class="dialog-tip">请选择希望邀请的农户。当前后端推荐算法尚未完成，演示数据与初始用户表保持一致。</p>
      <el-checkbox-group v-model="selectedFarmerIds" class="candidate-list">
        <el-checkbox
          v-for="candidate in candidates"
          :key="candidate.userId"
          :value="candidate.userId"
          class="candidate-item"
        >
          <span class="candidate-main">
            <strong>{{ candidate.realName }}</strong>
            <small>{{ candidate.farmerProfile?.mainProduct || candidate.mainProduct || '农产品经营' }}</small>
          </span>
          <el-tag size="small" type="success">
            {{ Math.round((candidate.matchRate || 0.8) * 100) }}% 匹配
          </el-tag>
        </el-checkbox>
      </el-checkbox-group>
      <el-alert
        v-if="selectedProduct"
        type="warning"
        :closable="false"
        show-icon
        :title="`当前产品最低需要 ${selectedProduct.minGroupSize} 人，已选择 ${selectedFarmerIds.length + 1} 人（含申请人）`"
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
import { ElMessage } from 'element-plus'
import {
  createLoanGroup,
  getLoanProducts,
  getRecommendedGuarantors,
  submitFinanceApply
} from '@/api/financeP5'

/**
 * 页面用途：融资申请。
 * 调用 financeP5.js：
 * - getLoanProducts 获取贷款产品；
 * - submitFinanceApply 提交申请；
 * - getRecommendedGuarantors 获取联保推荐；
 * - createLoanGroup 创建联合贷款小组。
 */
const router = useRouter()
const formRef = ref()
const submitting = ref(false)
const groupSubmitting = ref(false)
const productList = ref([])
const fileList = ref([])
const recommendVisible = ref(false)
const candidates = ref([])
const selectedFarmerIds = ref([])

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

/** 后端分页对象统一转成数组。 */
function normalizePage(data) {
  if (Array.isArray(data)) return data
  return data?.records || data?.list || []
}

function formatMoney(value) {
  return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
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
 * 后端 LoanProductService 正常时使用真实产品；
 * 若服务尚未启动或返回空列表，则展示 data.sql 中的 5 款产品，方便前端单独调试。
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
    if (!productList.value.length) productList.value = demoProducts
  } catch {
    productList.value = demoProducts
  }
  if (productList.value.length) selectProduct(productList.value[0])
}

/** 演示推荐人数据，仅在后端推荐接口暂时没有算法结果时使用。 */
function demoCandidates() {
  return [
    { userId: 3, realName: '王建国', farmerProfile: { mainProduct: '水稻种植' }, matchRate: 0.92 },
    { userId: 4, realName: '张桂芳', farmerProfile: { mainProduct: '家禽养殖' }, matchRate: 0.87 },
    { userId: 2, realName: '李官杏', farmerProfile: { mainProduct: '柑橘种植' }, matchRate: 0.84 }
  ]
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
    try {
      const recommended = await getRecommendedGuarantors()
      candidates.value = Array.isArray(recommended) && recommended.length ? recommended : demoCandidates()
    } catch {
      candidates.value = demoCandidates()
    }
    selectedFarmerIds.value = []
    recommendVisible.value = true
  } finally {
    submitting.value = false
  }
}

async function handleCreateGroup() {
  if (!selectedFarmerIds.value.length) {
    ElMessage.warning('请至少选择一位推荐联保人')
    return
  }
  const names = candidates.value
    .filter((item) => selectedFarmerIds.value.includes(item.userId))
    .map((item) => item.realName)
    .join('、')

  groupSubmitting.value = true
  try {
    await createLoanGroup({
      groupName: `${selectedProduct.value?.productName || '联合贷款'}小组`,
      // 当前后端 DTO 尚未提供 memberIds，先把成员名写入描述；后端扩展后替换为 memberIds 数组。
      description: `发起人陈永春，拟邀请：${names}`
    })
    recommendVisible.value = false
    ElMessage.success('联合贷款小组已创建')
    router.push('/finance/my')
  } finally {
    groupSubmitting.value = false
  }
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
.candidate-main strong, .candidate-main small { display: block; }
.candidate-main small { margin-top: 4px; color: var(--el-text-color-secondary); }
@media (max-width: 900px) { .product-list { grid-template-columns: 1fr; } }
@media (max-width: 680px) { .page-head { flex-direction: column; } .form-actions { flex-direction: column; } .form-actions .el-button { width: 100%; margin-left: 0; } }
</style>