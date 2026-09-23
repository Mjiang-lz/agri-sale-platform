<template>
  <el-card shadow="never" class="editor-card">
    <!-- 页面用途：专家登录后在农业知识页投稿，内容通过审核后在列表展示。 -->
    <template #header>
      <div class="editor-head">
        <div>
          <strong>专家知识投稿</strong>
          <span>新知识先进入审核队列，管理员通过后才会公开展示</span>
        </div>
        <el-button type="primary" @click="openEditor">发布知识</el-button>
      </div>
    </template>

    <div v-if="submissions.length" class="submission-list">
      <div v-for="item in submissions" :key="item.knowledgeId" class="submission-item">
        <div>
          <strong>{{ item.title }}</strong>
          <span>{{ item.category }} · {{ formatDateTime(item.createTime) }}</span>
        </div>
        <el-tag type="warning">{{ item.auditStatus }}</el-tag>
      </div>
    </div>
    <el-empty v-else description="还没有通过本页提交的知识投稿" :image-size="70" />

    <el-dialog v-model="visible" title="发布农业知识" width="min(760px, 95vw)" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon title="投稿后会进入待审核状态，审核通过后在农业知识列表公开。" />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="editor-form">
        <el-form-item label="知识标题" prop="title">
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="例如：柑橘膨果期水分管理要点" />
        </el-form-item>
        <el-form-item label="知识分类" prop="category">
          <el-select v-model="form.category" style="width: 100%" placeholder="请选择分类">
            <el-option v-for="item in categories" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="知识正文" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="10" maxlength="5000" show-word-limit placeholder="请填写完整、可执行的农业技术内容" />
        </el-form-item>
        <el-form-item label="知识配图（选填）">
          <el-upload action="#" :show-file-list="false" accept=".jpg,.jpeg,.png" :http-request="handleUpload" :before-upload="beforeUpload">
            <el-button :loading="uploading">{{ form.imageUrl ? '更换配图' : '上传配图' }}</el-button>
          </el-upload>
          <el-image v-if="form.imageUrl" class="preview" :src="form.imageUrl" fit="cover" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交审核</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadKnowledgeImage } from '@/api/common'
import { createKnowledge } from '@/api/knowledge'

defineProps({ submissions: { type: Array, default: () => [] } })
const emit = defineEmits(['created'])

const visible = ref(false)
const uploading = ref(false)
const submitting = ref(false)
const formRef = ref()
const categories = ['种植技术', '养殖技术', '病虫害防治', '市场行情', '政策解读']
const form = reactive({ title: '', category: '', content: '', imageUrl: '' })
const rules = {
  title: [{ required: true, message: '请输入知识标题', trigger: 'blur' }],
  category: [{ required: true, message: '请选择知识分类', trigger: 'change' }],
  content: [{ required: true, message: '请输入知识正文', trigger: 'blur' }]
}

function openEditor() {
  Object.assign(form, { title: '', category: '', content: '', imageUrl: '' })
  visible.value = true
}

function beforeUpload(file) {
  const allowed = ['image/jpeg', 'image/png']
  if (!allowed.includes(file.type)) {
    ElMessage.warning('配图仅支持 JPG、PNG 格式')
    return false
  }
  if (file.size / 1024 / 1024 > 5) {
    ElMessage.warning('配图不能超过 5MB')
    return false
  }
  return true
}

async function handleUpload(options) {
  uploading.value = true
  try {
    const result = await uploadKnowledgeImage(options.file)
    form.imageUrl = result.url
    options.onSuccess(result)
  } catch (error) {
    options.onError(error)
  } finally {
    uploading.value = false
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const knowledgeId = await createKnowledge({ ...form })
    emit('created', {
      knowledgeId,
      title: form.title,
      category: form.category,
      auditStatus: '待审核',
      createTime: new Date().toISOString().slice(0, 19)
    })
    visible.value = false
    ElMessage.success('知识已投稿，等待管理员审核')
  } finally {
    submitting.value = false
  }
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '--'
}
</script>

<style scoped>
.editor-card { border-radius: 16px; margin-bottom: 18px; background: linear-gradient(145deg, #f7fcf8, #eef8f1); }
.editor-head { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.editor-head strong, .editor-head span { display: block; }
.editor-head span { margin-top: 4px; color: #6f7e76; font-size: 12px; }
.submission-list { display: grid; gap: 9px; }
.submission-item { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 12px 14px; border: 1px solid #dcebe0; border-radius: 11px; background: #fff; }
.submission-item strong, .submission-item span { display: block; }
.submission-item span { margin-top: 3px; color: #75827a; font-size: 12px; }
.editor-form { margin-top: 18px; }
.preview { display: block; width: 220px; height: 130px; margin-top: 12px; border-radius: 11px; }
@media (max-width: 680px) { .editor-head { align-items: stretch; flex-direction: column; } }
</style>
