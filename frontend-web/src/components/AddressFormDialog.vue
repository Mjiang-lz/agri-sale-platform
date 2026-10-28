<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑收货地址' : '新增收货地址'"
    width="min(560px, 94vw)"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <!--
      为什么把地址表单单独抽成一个组件：
      结算页（Checkout）与地址管理页（user/Address）都要「新增 / 编辑地址」。
      若各自写一份，省市区三个输入框与手机号正则就会存在两份 ——
      后端 AddressDTO 的规则一旦收紧，两份都要改，漏一份就会出现
      「地址管理里能存，结算页里报错」这种只在一侧复现的问题。

      校验规则与后端 AddressDTO 的注解逐条对齐：
      consignee ≤50 必填 / phone 必填且 ^1[3-9]\d{9}$ / province ≤50 必填 /
      city ≤50 必填 / district ≤50 选填 / detail ≤200 必填。
      ⚠️ 这里只是提前给反馈，不是安全边界 —— 后端仍会再校验一遍。
      改后端注解时这里必须同步改，否则用户会在提交后才看到错误。
    -->
    <el-form ref="formRef" :model="form" :rules="rules" label-width="88px" @submit.prevent>
      <el-form-item label="收货人" prop="consignee">
        <el-input v-model="form.consignee" maxlength="50" show-word-limit placeholder="请填写收货人姓名" />
      </el-form-item>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model="form.phone" maxlength="20" placeholder="11 位手机号，用于快递联系" />
      </el-form-item>
      <!-- 外层这一项不带 prop：prop 只挂在真正承载字段的三个内层 form-item 上，
           外层挂一个 prop="province" 会和内层重复注册同一个字段，
           校验消息会弹两次、且第一次清不掉。 -->
      <el-form-item label="所在地区">
        <!--
          省 / 市 / 区县做成三个平铺输入框，而不是 el-cascader 的地区选择器：
          项目的数据库里 t_farmer_profile.credit_record 已经用「江西赣州」这种
          自由文本存地区，全站没有地区码字典表。上选择器要么引入一份外部数据，
          要么自造一套编码，两者都与现有口径对不上。纯文本输入与既有数据保持同一粒度。
        -->
        <div class="region-row">
          <el-form-item prop="province" class="region-item">
            <el-input v-model="form.province" maxlength="50" placeholder="省 / 直辖市" />
          </el-form-item>
          <el-form-item prop="city" class="region-item">
            <el-input v-model="form.city" maxlength="50" placeholder="市" />
          </el-form-item>
          <el-form-item prop="district" class="region-item">
            <el-input v-model="form.district" maxlength="50" placeholder="区 / 县（选填）" />
          </el-form-item>
        </div>
      </el-form-item>
      <el-form-item label="详细地址" prop="detail">
        <el-input
          v-model="form.detail"
          type="textarea"
          :rows="2"
          maxlength="200"
          show-word-limit
          placeholder="街道、门牌号、市场摊位等"
        />
      </el-form-item>
      <el-form-item label="默认地址">
        <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
        <span class="form-tip">设为默认后，结算页会优先选中它</span>
      </el-form-item>
    </el-form>

    <!--
      保存失败的行内提示。
      必须放在弹窗里而不是只靠 toast：toast 两三秒就没了，
      而弹窗还开着、用户正准备改输入 —— 这时候他需要知道**到底哪里错了**。
    -->
    <el-alert
      v-if="saveError"
      type="error"
      show-icon
      :closable="false"
      :title="saveError"
      class="save-error"
    />

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createAddress, updateAddress } from '@/api/trade'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 传入地址对象即编辑模式；传 null / 不传为新增 */
  address: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const formRef = ref(null)
const submitting = ref(false)
/**
 * 保存失败时的行内错误文案。
 *
 * 与 request.js 的全局 toast 是互补关系，不是重复：
 * toast 会消失，而这个弹窗还开着、用户正要改输入 —— 他需要一句话告诉他哪儿错了，
 * 而不是凭记忆回想三秒前飘过的那行字。
 */
const saveError = ref('')

const isEdit = computed(() => Boolean(props.address?.addressId))

const form = reactive({
  consignee: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: 0
})

const rules = {
  consignee: [
    { required: true, message: '请填写收货人姓名', trigger: 'blur' },
    { max: 50, message: '收货人姓名不能超过 50 字', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请填写收货人手机号', trigger: 'blur' },
    // 与后端 AddressDTO.phone 的 @Pattern 完全一致：
    // 1 开头 + 3~9 + 9 位数字。放宽或收紧都必须两边一起改。
    { pattern: /^1[3-9]\d{9}$/, message: '请填写正确的手机号', trigger: 'blur' }
  ],
  province: [{ required: true, message: '请填写省份', trigger: 'blur' }],
  city: [{ required: true, message: '请填写城市', trigger: 'blur' }],
  detail: [{ required: true, message: '请填写详细地址', trigger: 'blur' }]
}

/**
 * 每次打开时按传入的 address 重置表单。
 *
 * 用 watch 而不是在打开时调一次 reset：弹窗带 destroy-on-close，
 * 表单 DOM 会被销毁重建，但这里 form 是 reactive 对象、不会被销毁 ——
 * 不重置的话，先编辑 A 再新增，新增表单里会残留 A 的内容。
 */
watch(
  () => [props.modelValue, props.address],
  ([visible]) => {
    if (!visible) return
    const a = props.address
    form.consignee = a?.consignee || ''
    form.phone = a?.phone || ''
    form.province = a?.province || ''
    form.city = a?.city || ''
    form.district = a?.district || ''
    form.detail = a?.detail || ''
    form.isDefault = a?.isDefault ?? 0
    formRef.value?.clearValidate()
    // 每次打开都清掉上一次的保存失败提示，
    // 否则「上次失败的原因」会挂在这次新建的表单上方，完全对不上。
    saveError.value = ''
  },
  { immediate: true }
)

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  // district 传空串而不是 undefined：后端把它归一化成 null，
  // 但传 undefined 会让 axios 直接不带这个字段 —— 编辑时那就成了「部分更新」，
  // 用户明明清空了区县却清不掉。
  const payload = {
    consignee: form.consignee,
    phone: form.phone,
    province: form.province,
    city: form.city,
    district: form.district,
    detail: form.detail,
    isDefault: form.isDefault
  }

  submitting.value = true
  saveError.value = ''
  try {
    const addressId = isEdit.value
      ? (await updateAddress(props.address.addressId, payload), props.address.addressId)
      : await createAddress(payload)
    // toast 统一由 request.js 的拦截器弹，这里不重复弹成功提示
    emit('saved', addressId)
    emit('update:modelValue', false)
  } catch (err) {
    // 失败时保持弹窗打开，用户的输入不丢；
    // 但**必须在弹窗内留一句话** —— toast 一闪而过，用户没看清就得重填一遍。
    // 用rawMessage（后端原文）而不是 err.message（翻译后的长句），
    // 前者更紧凑，弹窗里放得下。
    saveError.value = err?.rawMessage || err?.message || '保存失败，请稍后重试'
    console.error('保存收货地址失败：', err)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
/* 保存失败的行内提示：贴住表单底部，视觉上属于表单的一部分 */
.save-error {
  margin-top: 4px;
}

.region-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  width: 100%;
}
/* 三格本身是 el-form-item，默认有下外边距，会把这一行撑得比其他行高。
   这里去掉，只保留外层「所在地区」那一项的间距。 */
.region-item :deep(.el-form-item__content) {
  line-height: normal;
}
.region-item {
  margin-bottom: 0;
}
.form-tip {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
