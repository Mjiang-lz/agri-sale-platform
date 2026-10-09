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
    <h1 class="auth-title">注册融销通账号</h1>
    <p class="auth-sub">按真实身份注册，平台会据此分配可见的功能菜单。</p>

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

// 取值必须与后端 t_role 一致（注册接口按 roleName 反查角色），别自己造词
const ROLES = ['农户', '采购方', '专家', '银行用户']

const route = useRoute()
const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ phone: '', password: '', realName: '', roleName: '' })

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

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await request.post('/auth/register', form)
    ElMessage.success('注册成功，请登录')
    router.push(loginLink.value)
  } catch {
    // 失败提示由 request 拦截器统一弹出（如「该手机号已注册」），这里不重复弹
  } finally {
    loading.value = false
  }
}
</script>
