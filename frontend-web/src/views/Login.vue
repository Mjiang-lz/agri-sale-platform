<template>
  <!--
    登录页。

    改版前的样子：一个 400px 宽的裸 el-card 摆在 --el-fill-color-light 灰底正中央，
    头部是 el-card 自带的标题栏（「融销通 · 农产品融销一体平台 · 登录」一行灰字），
    卡片下方还挂着一个 el-alert「接口：POST /api/auth/login」—— 那是开发期用的，
    等于把接口路径直接印在登录页上。整页没有任何品牌标识，且**没有去注册页的入口**
    （MainLayout 的访客按钮不在这个布局里，用户点不出去）。

    改版后：外层交给 AuthShell（品牌标 + 底纹 + 页脚），卡内自带登录/注册互跳。
    调试用的 el-alert 与它暴露的接口路径一并删除。

    2026-10-10：按用户要求**去掉副标题**（原为「登录后即可发布货源、参与求购与融资申请。」）。
    删掉 <p class="auth-sub"> 会让表单贴住标题 —— 旧写法里那段间距是由
    .auth-sub 的 `margin: 8px 0 22px` 撑着的，而 .auth-sub 注册页也在用，
    不能直接改它。所以这里给 h1 多挂一个 .auth-title-solo（定义在 AuthShell 的
    非 scoped 块里）把下间距接过来，注册页不受影响。
  -->
  <AuthShell>
    <h1 class="auth-title auth-title-solo">欢迎回来</h1>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      size="large"
      @keyup.enter="onSubmit"
    >
      <el-form-item label="手机号" prop="phone">
        <el-input v-model.trim="form.phone" placeholder="请输入 11 位手机号" clearable />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          placeholder="请输入密码"
        />
      </el-form-item>
      <!-- 图形验证码：每次提交都会一次性消费掉这个 key，所以成功/失败后都要刷新 -->
      <CaptchaField ref="captchaRef" v-model:key="form.captchaKey" v-model:code="form.captchaCode" @submit="onSubmit" />
      <el-form-item class="auth-submit">
        <el-button type="primary" :loading="loading" @click="onSubmit">登 录</el-button>
      </el-form-item>
    </el-form>

    <div class="auth-switch">
      还没有账号？
      <router-link class="auth-link" :to="registerLink">立即注册</router-link>
    </div>
  </AuthShell>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { useUserStore } from '@/store/user'
import AuthShell from '@/components/AuthShell.vue'
import CaptchaField from '@/components/CaptchaField.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const captchaRef = ref()
const loading = ref(false)
// captchaKey / captchaCode 由 CaptchaField 双向绑定写进来，字段名与后端 LoginDTO 一致
const form = reactive({ phone: '', password: '', captchaKey: '', captchaCode: '' })

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

// 回跳路由守卫带过来的目的地；只认站内绝对路径，
// 挡掉 ?redirect=//evil.com 这类协议相对地址（会被浏览器当外站）
function safeRedirect() {
  const r = route.query.redirect
  return typeof r === 'string' && r.startsWith('/') && !r.startsWith('//') ? r : '/'
}

// 注册页同样把「本来要去哪」带过去：注册完再登录还能回到原地，不丢目的地
const registerLink = computed(() => {
  const dest = safeRedirect()
  return dest === '/' ? '/register' : { path: '/register', query: { redirect: dest } }
})

async function onSubmit() {
  // EP 的 validate() 在失败时 reject；当布尔用必须先把异常吃掉，
  // 否则表单没填全就直接抛出去，后面的 loading 逻辑根本不执行。
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  // 验证码单独校验：图没出来 / 没填都在这里拦，消息口径与后端一致
  if (!captchaRef.value.validate()) return

  loading.value = true
  try {
    const data = await request.post('/auth/login', form)
    userStore.saveLogin(data)
    ElMessage.success('登录成功')
    router.push(safeRedirect())
    // 这个 key 已被一次性消费，换一张新的，别让下次进页面看到一张废图
    captchaRef.value.refresh()
  } catch {
    // 失败提示由 request 拦截器统一弹出（业务错误 / 网络异常都在那儿），这里不重复弹
    // 但验证码必须刷新：key 无论对错都已被消费，不换图用户会一直卡在同一个废 key 上
    captchaRef.value.refresh()
  } finally {
    loading.value = false
  }
}
</script>
