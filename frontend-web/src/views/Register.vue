<template>
  <!--
    注册页。

    改版前的问题不止是样式：
      · 一个 420px 的裸 el-card，与登录页同款灰底，无品牌标识；
      · 挂着一个 el-alert「接口：POST /api/auth/register」，把接口路径印给用户看；
      · **四个字段一个校验都没有** —— el-form 连 :rules 都没传，
        手机号填「abc」也能提交，错误只能等后端返回；
      · onSubmit 没有 loading 态、没有 try/finally，请求失败后按钮还能继续点，
        连点几次就是连发几次注册请求；
      · 没有回登录页的入口。
    改版后逐条补齐：AuthShell 外壳、:rules 校验（含手机号正则与密码长度）、
    loading + try/finally、登录/注册互跳。
  -->
  <AuthShell>
    <h1 class="auth-title auth-title-solo">注册融销通账号</h1>

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
          placeholder="至少 6 位"
        />
      </el-form-item>
      <el-form-item label="真实姓名" prop="realName">
        <el-input v-model.trim="form.realName" placeholder="请输入真实姓名" />
      </el-form-item>
      <el-form-item label="注册身份" prop="roleName">
        <el-select v-model="form.roleName" placeholder="请选择注册身份">
          <el-option v-for="r in ROLES" :key="r" :label="r" :value="r" />
        </el-select>
      </el-form-item>
      <!-- ★ 刻意不接 @submit：CaptchaField 的输入框自己 emit('submit')，而它就在本表单
           内部，回车还会冒泡到 el-form 的 @keyup.enter —— 两边都接 onSubmit 会
           **同一瞬间提交两次**。注册接口的验证码同样是一次性凭据，第二次必被判
           「验证码不正确」，会出现「注册成功」旁边又弹红色报错的怪象。
           表单级 @keyup.enter 已覆盖所有输入框，只留它一个入口。 -->
      <CaptchaField ref="captchaRef" v-model:captcha-key="form.captchaKey" v-model:captcha-code="form.captchaCode" />
      <el-form-item class="auth-submit">
        <el-button type="primary" :loading="loading" @click="onSubmit">注 册</el-button>
      </el-form-item>
    </el-form>

    <div class="auth-switch">
      已有账号？
      <router-link class="auth-link" :to="loginLink">去登录</router-link>
    </div>
  </AuthShell>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import AuthShell from '@/components/AuthShell.vue'
import CaptchaField from '@/components/CaptchaField.vue'

// 取值必须与后端 t_role 一致（注册接口按 roleName 反查角色），别自己造词
const ROLES = ['农户', '采购方', '专家', '银行用户']

const route = useRoute()
const router = useRouter()
const formRef = ref()
const captchaRef = ref()
const loading = ref(false)
// captchaKey / captchaCode 由 CaptchaField 双向绑定写进来，字段名与后端 RegisterDTO 一致
const form = reactive({ phone: '', password: '', realName: '', roleName: '', captchaKey: '', captchaCode: '' })

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
    { min: 2, max: 20, message: '姓名长度 2~20 个字符', trigger: 'blur' },
  ],
  roleName: [{ required: true, message: '请选择注册身份', trigger: 'change' }],
}

// 只认站内绝对路径，挡住 ?redirect=//evil.com（浏览器会当外站跳走）
const loginLink = computed(() => {
  const r = route.query.redirect
  const dest = typeof r === 'string' && r.startsWith('/') && !r.startsWith('//') ? r : ''
  return dest && dest !== '/' ? { path: '/login', query: { redirect: dest } } : '/login'
})

/** 防重入闸门。理由同 Login.vue：验证码是一次性凭据，重复提交必然第二次失败。 */
let submitting = false

async function onSubmit() {
  if (submitting) return
  submitting = true
  try {
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) return
    if (!captchaRef.value.validate()) return

    loading.value = true
    try {
      await request.post('/auth/register', form)
      ElMessage.success('注册成功，请登录')
      router.push(loginLink.value)
      // key 已被一次性消费，换一张新的（此时往往紧接着就要填登录表单）
      captchaRef.value.refresh()
    } catch {
      // 失败提示由 request 拦截器统一弹出（如「该手机号已注册」），这里不重复弹
      captchaRef.value.refresh()
    } finally {
      loading.value = false
    }
  } finally {
    submitting = false
  }
}
</script>
