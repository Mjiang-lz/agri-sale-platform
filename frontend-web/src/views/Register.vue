<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <template #header>注册融销通账号</template>
      <el-form :model="form" label-width="72px">
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="真实姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="注册身份">
          <el-select v-model="form.roleName" placeholder="请选择">
            <el-option label="农户" value="农户" />
            <el-option label="采购方" value="采购方" />
            <el-option label="专家" value="专家" />
            <el-option label="银行用户" value="银行用户" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width: 100%" @click="onSubmit">注册</el-button>
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" title="接口：POST /api/auth/register" />
    </el-card>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const router = useRouter()
const form = reactive({ phone: '', password: '', realName: '', roleName: '' })

async function onSubmit() {
  await request.post('/auth/register', form)
  ElMessage.success('注册成功，请登录')
  router.push('/login')
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
  width: 420px;
}
</style>
