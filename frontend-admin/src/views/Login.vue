<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <template #header>融销通 · 管理后台 · 登录</template>
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
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { ADMIN_ROLE, useUserStore } from '@/store/user'

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

// 被守卫弹回来时（token 有效但不是管理员）给一句解释，
// 否则用户只会看到自己「莫名其妙回到了登录页」。
onMounted(() => {
  if (route.query.denied === '1') {
    ElMessage.warning('仅平台管理员可进入管理后台')
  }
})

async function onSubmit() {
  await formRef.value.validate()
  loading.value = true
  try {
    // ★ 走后台专用入口 /auth/admin/login，不是业务端的 /auth/login（2026-10-14 改）。
    //   后台登录页没有验证码输入框，而 /auth/login 会强制校验图形验证码 ——
    //   共用它的话，这里每次提交都会被「验证码不正确或已过期」挡下。
    //   后台入口免验证码，并在服务端就把角色闸口收紧到管理员。
    const data = await request.post('/auth/admin/login', form)

    // ★ 必须先校角色再落盘（2026-10-10 补；2026-10-14 后台改用 /auth/admin/login）。
    // 后端已在 /auth/admin/login 里把角色闸口收紧到管理员（非管理员直接 403），
    // 前端这道是第二道 —— token 本身不区分角色，历史上拿到的、或手工写进
    // localStorage 的 token 根本不会经过那道服务端校验。不校这一步的话，
    // 任何角色的账号都能一路登进后台看到管理界面（数据会被后端 403 挡住，但人已经进来了）。
    if (data?.roleName !== ADMIN_ROLE) {
      ElMessage.error('该账号不是平台管理员，无法进入管理后台')
      return
    }

    userStore.saveLogin(data)
    ElMessage.success('登录成功')
    router.push('/')
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
