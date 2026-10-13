<script setup>
/**
 * 图形验证码输入组（登录 / 注册共用）。
 *
 * ★ 为什么单独抽成组件而不是两页各写一遍：验证码有「取图 → 回显 → 随表单提交 →
 *   失败后刷新」四步状态，两页各写一份必然慢慢漂开（其中一页忘了失败时刷新图，
 *   用户就永远卡在同一张已失效的图上反复报错）。
 *
 * 父组件通过 v-model:key / v-model:code 两个双向绑定拿值，通过 ref 调 validate() 做提交前校验。
 * 提交时把这两个字段并进表单体即可（字段名与后端 LoginDTO/RegisterDTO 一致）。
 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getCaptcha } from '@/api/auth'

const captchaKey = defineModel('key', { type: String, default: '' })
const captchaCode = defineModel('code', { type: String, default: '' })
const captchaImage = ref('')
const loading = ref(false)

async function refresh() {
  loading.value = true
  try {
    const d = await getCaptcha()
    captchaKey.value = d.captchaKey
    captchaImage.value = d.captchaImage
    // 换图必须同时清空已输入内容：旧图上的字符对新图无效，
    // 留着会让用户以为「我明明输的是上一张图的字」
    captchaCode.value = ''
  } catch {
    // 取图失败时把 key 清掉 —— 带着一个空 key 提交只会得到「验证码不正确」，
    // 那个提示会把用户引向「我字看错了」，而真实原因是图根本没出来
    captchaKey.value = ''
    captchaImage.value = ''
    ElMessage.warning('验证码加载失败，请点击图片重试')
  } finally {
    loading.value = false
  }
}

/** 提交前校验。空图/空码都在这里拦，消息与后端口径一致 */
function validate() {
  if (!captchaKey.value) {
    refresh()
    return false
  }
  if (!captchaCode.value.trim()) {
    ElMessage.warning('请输入图形验证码')
    return false
  }
  return true
}

onMounted(refresh)
defineExpose({ validate, refresh })
</script>

<template>
  <el-form-item label="图形验证码" prop="captchaCode">
    <div class="captcha-field">
      <el-input
        v-model.trim="captchaCode"
        placeholder="请输入图中字符"
        maxlength="4"
        class="captcha-input"
        @keyup.enter="$emit('submit')"
      />
      <img
        v-if="captchaImage"
        :src="captchaImage"
        alt="图形验证码"
        title="点击刷新"
        class="captcha-img"
        @click="refresh"
      />
      <!-- 图还没到时占住同一块地方，避免输入框被挤得跳一下 -->
      <div v-else class="captcha-img is-ph" @click="refresh">加载中</div>
    </div>
  </el-form-item>
</template>

<style scoped>
.captcha-field { display: flex; align-items: center; gap: 10px; }
.captcha-input { flex: 1; min-width: 0; }
/* 与后端 CaptchaService 的 120×42 等比，稍放大一点让字符更好认 */
.captcha-img {
  width: 132px; height: 46px; flex: none;
  border: 1px solid var(--rxt-border); border-radius: 8px;
  background: #f6f3ec; cursor: pointer;
}
.captcha-img.is-ph {
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; color: var(--rxt-ink-weak);
}
</style>
