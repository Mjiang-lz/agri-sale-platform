<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <template #header>融销通 · 农产品融销一体平台 · 登录</template>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="72px" @keyup.enter="onSubmit">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%" @click="onSubmit">登录</el-button>
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" title="接口：POST /api/auth/login" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const form = reactive({ phone: '', password: '' })
const rules = {
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

// 回跳路由守卫带过来的目的地；只认站内绝对路径，
// 挡掉 ?redirect=//evil.com 这类协议相对地址（会被浏览器当外站）
function safeRedirect() {
  const r = route.query.redirect
  return typeof r === 'string' && r.startsWith('/') && !r.startsWith('//') ? r : '/'
}

async function onSubmit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const data = await request.post('/auth/login', form)
    userStore.saveLogin(data)
    ElMessage.success('登录成功')
    router.push(safeRedirect())
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color-light);
}
.login-card {
  width: 400px;
}
</style>
