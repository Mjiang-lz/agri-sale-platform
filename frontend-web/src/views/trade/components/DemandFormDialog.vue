<template>
  <!--
    发布 / 编辑求购需求。

    依据需求规格说明书 2.7.3 UC-TRD-02：
      「采购方填写品类、数量、期望价格、收货地区、有效期与联系方式，提交」
      输入及约束：「品类、数量、收货地区、联系方式为必填」
      约束条件：「期望价格为正数；有效期不超过 90 天」

    每一项的校验上限都对着后端 DemandPublishDTO 的注解写，不是照着 DDL 猜的：
      category    @NotBlank + @Size(max=30)
      quantity    @NotNull + @Min(1)
      unit        @Size(max=10)
      expectPrice @DecimalMin(0.01) + @Digits(10,2)
      description @Size(max=500)
      region      @NotBlank + @Size(max=50)
      validUntil  @NotNull + @Future + 自定义「不超过90天」
      contact     @NotBlank + @Size(max=50)
    标红提示比让用户提交后吃一个后端 400 更好 —— 但**不是安全边界**，后端会再校验一遍。
  -->
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑求购需求' : '发布求购需求'"
    width="min(620px, 94vw)"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="求购品类" prop="category">
        <el-select v-model="form.category" filterable allow-create placeholder="选择或输入品类">
          <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <span class="hint">品类必填，最多 30 字。列表没有的品类可直接输入。</span>
      </el-form-item>

      <div class="form-row">
        <el-form-item label="求购数量" prop="quantity">
          <el-input-number v-model="form.quantity" :min="1" :max="999999" controls-position="right" />
        </el-form-item>
        <el-form-item label="计量单位" prop="unit">
          <el-input v-model="form.unit" maxlength="10" placeholder="斤 / 袋 / 箱，可留空" />
        </el-form-item>
        <el-form-item label="期望单价（元）" prop="expectPrice">
          <el-input v-model="form.expectPrice" placeholder="留空表示面议">
            <template #prepend>¥</template>
          </el-input>
        </el-form-item>
      </div>

      <el-form-item label="收货地区" prop="region">
        <el-select v-model="form.region" filterable allow-create placeholder="选择或输入收货地区">
          <el-option v-for="r in regionOptions" :key="r" :label="r" :value="r" />
        </el-select>
        <span class="hint">收货地区必填，最多 50 字。</span>
      </el-form-item>

      <el-form-item label="需求有效期" prop="validUntil">
        <!--
          ⚠️ value-format 必须带时分秒。
          若用 "YYYY-MM-DD"，选中当天会被解析成 00:00:00，
          而后端有 @Future（必须晚于当前时间）→ 用户明明选了今天却报
          「需求有效期必须晚于当前时间」，是典型的「选了合法日期却被拒」。
          disabledDate 挡掉今天之前的日子，是同一道约束的前置拦截。
        -->
        <el-date-picker
          v-model="form.validUntil"
          type="datetime"
          placeholder="选择有效期截止时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          format="YYYY-MM-DD HH:mm"
          :disabled-date="disablePastDate"
          style="width: 100%"
        />
        <span class="hint">
          必填。必须晚于当前时间，且不超过 90 天（后端 @Future + 自定义校验）。
        </span>
      </el-form-item>

      <el-form-item label="联系方式" prop="contact">
        <el-input v-model="form.contact" maxlength="50" placeholder="手机号或微信号，供农户主动联系你" />
        <span class="hint">联系方式必填，最多 50 字。该信息会对全部登录用户可见。</span>
      </el-form-item>

      <el-form-item label="补充说明" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="品相要求、到货时间、验收标准等，选填"
        />
      </el-form-item>
    </el-form>

    <el-alert
      v-if="mockMode"
      type="warning"
      :closable="false"
      show-icon
      title="当前为前端演示数据，不会真正入库"
      description="后端 POST /demand/publish 目前返回 500（本地时间字段反序列化失败），这里先用 mock 跑通交互。后端修好后会自动切换为真实提交。"
    />

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ isEdit ? '保存修改' : '发布需求' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { publishDemand, updateDemand, USE_DEMAND_MOCK } from '@/api/trade'
import { useCategories } from '@/composables/useCategories'

/**
 * 求购需求发布 / 编辑弹窗。
 *
 * ⚠️ 后端 publish 与 update 目前均返回 500（见 api/trade.js 的说明），
 * 所以这里调的是带 mock 降级的 publishDemand / updateDemand。
 * mockMode 用来在界面上如实告知「提交不会入库」，而不是让用户以为发布成功了。
 */
const emit = defineEmits(['saved'])

const visible = ref(false)
const submitting = ref(false)
const isEdit = ref(false)
const editingId = ref(null)
const formRef = ref()

/**
 * 品类下拉：接口驱动，与列表页共用同一个模块级单例。
 * ★ 2026-10-13 从写死的六类字符串改过来。原注释说「与列表页共用同一份
 *   全集，保证筛选与发布口径一致」，可它其实是**又抄了一遍字面量** ——
 *   两份名单各自漂移正是「筛得到、发布时选不到」的来源。
 */
const { categoryNames: categoryOptions, load: loadCategories } = useCategories()
const regionOptions = ['江西赣州', '湖北荆州', '黑龙江五常']

const mockMode = USE_DEMAND_MOCK

const form = reactive({
  category: '',
  quantity: 100,
  unit: '斤',
  expectPrice: '',
  region: '',
  validUntil: '',
  contact: '',
  description: ''
})

/**
 * 校验规则，与后端 DemandPublishDTO 的注解逐条对齐。
 *
 * expectPrice 用字符串型校验而不是 number：el-input 拿到的是字符串，
 * 用 type:'number' 时「」与「abc」都会被当成非法，提示文案会失真。
 * 真正的数值合法性交给后端（@DecimalMin / @Digits）。
 */
const rules = {
  category: [
    { required: true, message: '请选择或输入求购品类', trigger: 'change' },
    { max: 30, message: '品类不能超过 30 字', trigger: 'change' }
  ],
  quantity: [{ required: true, message: '请填写求购数量', trigger: 'blur' }],
  region: [
    { required: true, message: '请选择或输入收货地区', trigger: 'change' },
    { max: 50, message: '收货地区不能超过 50 字', trigger: 'change' }
  ],
  validUntil: [
    { required: true, message: '请选择需求有效期', trigger: 'change' },
    {
      validator: (_rule, value, callback) => {
        if (!value) return callback()
        const t = new Date(String(value).replace(/-/g, '/')).getTime()
        if (Number.isNaN(t)) return callback(new Error('有效期格式不正确'))
        if (t <= Date.now()) return callback(new Error('有效期必须晚于当前时间'))
        // 90 天上限对齐后端 isValidUntilWithin90Days。
        // 这里留 1 分钟容差：前端与后端各算一次「现在」，相差几毫秒就会让
        // 恰好卡在边界上的合法值被判非法。
        const maxTime = Date.now() + 90 * 24 * 60 * 60 * 1000 + 60 * 1000
        if (t > maxTime) return callback(new Error('有效期不能超过 90 天'))
        callback()
      },
      trigger: 'change'
    }
  ],
  contact: [
    { required: true, message: '请填写联系方式', trigger: 'blur' },
    { max: 50, message: '联系方式不能超过 50 字', trigger: 'blur' }
  ]
}

/** 禁用今天之前的日期，避免用户先选一个非法值再改。 */
function disablePastDate(date) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}

/**
 * 默认有效期：30 天后。
 * 直接给一个合法值而不是留空 —— 用户至少改个时间就能提交，
 * 比对着空日期框猜格式友好。
 */
function defaultValidUntil() {
  const d = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} 23:59:59`
}

/** 打开发布弹窗（新建）。 */
function open() {
  isEdit.value = false
  editingId.value = null
  // 弹窗可能从任何入口被打开（不保证列表页先进过），这里兜一下，
  // 否则一个从没加载过的会话点开弹窗，下拉里会是兜底名单而非真实品类
  loadCategories()
  resetForm()
  visible.value = true
}

/** 打开编辑弹窗（回填已有数据）。 */
function openEdit(row) {
  isEdit.value = true
  editingId.value = row.demandId
  loadCategories()
  resetForm()
  // 逐字段显式回填，不整体 Object.assign：
  // row 里带着列表额外字段（__mock、publisherId 等），整体覆盖会把它们带进表单 payload。
  form.category = row.category || ''
  form.quantity = row.quantity ?? 100
  form.unit = row.unit || '斤'
  form.expectPrice = row.expectPrice ?? ''
  form.region = row.region || ''
  // mock 数据的 validUntil 已经是 'YYYY-MM-DD HH:mm:ss' 字符串，date-picker 能直接接
  form.validUntil = row.validUntil || ''
  form.contact = row.contact || ''
  form.description = row.description || ''
  visible.value = true
}

function resetForm() {
  form.category = ''
  form.quantity = 100
  form.unit = '斤'
  form.expectPrice = ''
  form.region = ''
  form.validUntil = defaultValidUntil()
  form.contact = ''
  form.description = ''
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = {
      category: form.category,
      quantity: Number(form.quantity),
      unit: form.unit?.trim() || undefined,
      // 留空表示面议 —— 传 undefined 而不是 ''，否则后端 @DecimalMin 会拒
      expectPrice: form.expectPrice === '' || form.expectPrice == null
        ? undefined
        : Number(form.expectPrice),
      description: form.description?.trim() || undefined,
      region: form.region,
      validUntil: form.validUntil,
      contact: form.contact
    }

    if (isEdit.value) {
      await updateDemand(editingId.value, payload)
      ElMessage.success('求购需求已更新')
    } else {
      await publishDemand(payload)
      ElMessage.success(mockMode ? '已提交（演示数据，未入库）' : '求购需求发布成功')
    }

    visible.value = false
    emit('saved')
  } catch (err) {
    // request.js 已统一弹过后端 message（err.notified），这里不再弹第二次
    console.error('提交求购需求失败：', err)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open, openEdit })
</script>

<style scoped>
.form-row {
  display: flex;
  gap: 12px;
}
.form-row :deep(.el-form-item) {
  flex: 1;
}
.form-row :deep(.el-input-number),
.form-row :deep(.el-select) {
  width: 100%;
}
.hint {
  display: block;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}
:deep(.el-form-item__content) {
  flex-wrap: wrap;
}
</style>