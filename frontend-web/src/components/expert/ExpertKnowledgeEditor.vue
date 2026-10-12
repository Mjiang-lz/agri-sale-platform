<template>
  <el-card shadow="never" class="editor-card">
    <!-- 页面用途：专家登录后在农业知识页投稿，内容通过审核后在列表展示。 -->
    <template #header>
      <div class="editor-head">
        <div>
          <strong>专家知识投稿</strong>
          <span>新知识先进入审核队列，管理员通过后才会公开展示</span>
        </div>
        <div class="head-actions">
          <el-button text @click="expanded = !expanded">投稿记录（{{ submissions.length }}）{{ expanded ? '收起' : '展开' }}</el-button>
          <el-button type="primary" @click="openEditor()">发布知识</el-button>
        </div>
      </div>
    </template>

    <el-collapse-transition>
      <div v-show="expanded" class="submission-list-wrap">
        <div v-if="submissions.length" class="submission-list">
          <div v-for="item in submissions" :key="item.knowledgeId" class="submission-item">
            <div>
              <strong>{{ item.title }}</strong>
              <span>{{ item.category }} · {{ formatDateTime(item.createTime) }}</span>
            </div>
            <div class="submission-actions">
              <el-tag :type="statusType(item.auditStatus)">{{ statusText(item.auditStatus) }}</el-tag>
              <el-button link type="primary" @click="openEditor(item)">编辑</el-button>
              <el-button link type="danger" @click="removeSubmission(item)">删除</el-button>
              <!--
                这个 × 是**本地隐藏**（写 localStorage 的 hiddenSubmissionIds），不删数据。
                用户上次就是把它当成了删除，所以这里必须有可见提示把两件事分开 ——
                两个按钮挨着、看起来都能让记录消失，不写清楚一定会再误会一次。
              -->
              <el-tooltip content="仅从我的列表里隐藏，不删除数据" placement="top">
                <el-button circle text class="close-button" aria-label="从我的列表隐藏（不删除数据）" @click="$emit('dismiss', item)">×</el-button>
              </el-tooltip>
            </div>
          </div>
        </div>
        <el-empty v-else description="还没有知识投稿记录" :image-size="70" />
      </div>
    </el-collapse-transition>

    <!--
      宽度：用户要求「占页面布局的 70%」⇒ 用视口比例而不是固定像素。
      1200px 是上限，只在超宽屏生效（视口 ≤1714px 时严格等于 70%，本机 1600px 视口 = 1120px）——
      不加上限的话 2560px 屏上正文会长到一行放 90 个字，反而更难读。
      top 配合调小：弹窗变宽后正文行数也加了，15vh 的默认顶部留白会让高度溢出视口。
    -->
    <el-dialog
      v-model="visible"
      class="knowledge-editor-dialog"
      :title="editingId ? '编辑农业知识' : '发布农业知识'"
      width="min(1200px, 70vw)"
      top="5vh"
      destroy-on-close
    >
      <el-alert
        :key="editingId || 'create'"
        type="info"
        :closable="false"
        show-icon
        :title="editingId
          ? '保存后该条知识将回到「待审核」状态，需管理员重新审核通过后才会再次公开。'
          : '投稿后会进入待审核状态，审核通过后在农业知识列表公开。'"
      />
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
          <!-- 行数从 10 提到 14：只把弹窗拉宽不加高，编辑区会显得被横向抻长 -->
          <el-input v-model="form.content" type="textarea" :rows="14" maxlength="5000" show-word-limit placeholder="请填写完整、可执行的农业技术内容" />
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
        <el-button type="primary" :loading="submitting" @click="submit">{{ editingId ? '保存并重新送审' : '提交审核' }}</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { uploadKnowledgeImage } from '@/api/common'
import { createKnowledge, deleteKnowledge, updateKnowledge } from '@/api/knowledge'

defineProps({ submissions: { type: Array, default: () => [] } })
const emit = defineEmits(['created', 'dismiss'])

const visible = ref(false)
const expanded = ref(false)
const uploading = ref(false)
const submitting = ref(false)
const formRef = ref()
/** 非空表示当前弹窗处于「编辑」模式，值为被编辑的知识主键 */
const editingId = ref(null)
/** 编辑模式下该条的原始审核状态，用于决定要不要二次确认 */
const editingStatus = ref('')
const categories = ['种植技术', '养殖技术', '病虫害防治', '市场行情', '政策解读']
const form = reactive({ title: '', category: '', content: '', imageUrl: '' })
const rules = {
  title: [{ required: true, message: '请输入知识标题', trigger: 'blur' }],
  category: [{ required: true, message: '请选择知识分类', trigger: 'change' }],
  content: [{ required: true, message: '请输入知识正文', trigger: 'blur' }]
}

/**
 * 打开投稿弹窗。传 item 即编辑，不传即新建。
 *
 * 编辑时用投稿列表里已有的字段回填，不再单独拉一次详情 ——
 * `GET /api/knowledge/my` 返回的就是 Knowledge 实体，正文（content）与配图
 * （imageUrl）都在，多打一次请求只是重复。
 */
function openEditor(item) {
  editingId.value = item?.knowledgeId ?? null
  editingStatus.value = item?.auditStatus ?? ''
  Object.assign(form, {
    title: item?.title ?? '',
    // 历史数据的 category 可能是列表页没有的取值，直接塞进 el-select 会显示成空。
    // 这里保留原值（而不是替换成默认分类）：静默改掉用户的分类比显示不安更糟。
    category: item?.category ?? '',
    content: item?.content ?? '',
    imageUrl: item?.imageUrl ?? ''
  })
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

/** 编辑「已通过」的知识要二次确认：这条当前对所有人可见，保存后会立刻从列表消失 */
async function confirmRepublish() {
  if (editingStatus.value !== '已通过') return true
  try {
    await ElMessageBox.confirm(
      '该条知识当前已审核通过并对所有人可见。保存修改后它会回到「待审核」状态，' +
      '在管理员重新审核通过之前不会出现在农业知识列表里。确定继续吗？',
      '保存后需要重新审核',
      { type: 'warning', confirmButtonText: '确定保存', cancelButtonText: '取消' }
    )
    return true
  } catch {
    return false
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (editingId.value && !(await confirmRepublish())) return

  submitting.value = true
  try {
    if (editingId.value) {
      await updateKnowledge(editingId.value, { ...form })
      emit('created', { knowledgeId: editingId.value })
      expanded.value = true
      visible.value = false
      ElMessage.success('修改已保存，该条知识回到待审核，等待管理员重新审核')
    } else {
      const knowledgeId = await createKnowledge({ ...form })
      emit('created', { knowledgeId })
      expanded.value = true
      visible.value = false
      ElMessage.success('知识已投稿，等待管理员审核')
    }
  } finally {
    submitting.value = false
  }
}

/**
 * 删除自己的一条知识投稿（真删除，不可撤销）。
 *
 * 与旁边那个 × 的区别（用户上次就是把它当成了删除）：
 *   · ×  = 本地隐藏，只往 localStorage 的 hiddenSubmissionIds 里记一笔 id，数据一条不动；
 *   · 本函数 = 调 DELETE /api/knowledge/{id}，后端物理删行。
 * 两者都会让记录从「我的投稿」里消失，所以提示文案必须把「不可撤销」说在前面。
 *
 * 为什么文案里要专门提「评论一并清除、审核记录保留」——这两件事用户看不见，
 * 但事后去后台翻审核列表时会看到那条流水的标题变成「（内容已删除）」，
 * 不提前说会被当成 bug 报回来。
 */
async function removeSubmission(item) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${item.title}」这条知识吗？此操作不可撤销，删除后无法恢复。\n\n` +
      '该知识的评论会一并清除；管理员那边的审核记录会保留，' +
      '标题显示为「（内容已删除）」。',
      '删除知识投稿',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  await deleteKnowledge(item.knowledgeId)
  ElMessage.success('知识已删除')
  // 借父组件已有的刷新入口：handleKnowledgeCreated 会重新拉「我的投稿」+ 公开列表
  emit('created', { knowledgeId: item.knowledgeId })
  expanded.value = true
}

function statusText(status) {
  if (status === '已通过') return '已通过'
  if (status === '未通过') return '未通过'
  return '待审核'
}

function statusType(status) {
  if (status === '已通过') return 'success'
  if (status === '未通过') return 'danger'
  return 'warning'
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
.head-actions { display: flex; align-items: center; gap: 8px; }
.submission-list-wrap { min-height: 80px; }
.submission-actions { display: flex; align-items: center; gap: 4px; }
.close-button { color: #8b978f; font-size: 18px; }
.submission-list { display: grid; gap: 9px; }
.submission-item { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 12px 14px; border: 1px solid #dcebe0; border-radius: 11px; background: #fff; }
.submission-item strong, .submission-item span { display: block; }
.submission-item span { margin-top: 3px; color: #75827a; font-size: 12px; }
.editor-form { margin-top: 18px; }
.preview { display: block; width: 300px; height: 176px; margin-top: 12px; border-radius: 11px; }
@media (max-width: 680px) { .editor-head { align-items: stretch; flex-direction: column; } }
</style>

<style>
/*
 * 非 scoped 块 —— el-dialog 会 teleport 到 body，scoped 的属性选择器够不到它。
 * 弹窗加宽加高后（70vw / 正文 14 行），加上配图预览一屏放不下。
 * 让内容区自己滚，标题栏和底部按钮留在原地，不然滚动时「取消/提交」会跟着滑走。
 */
.knowledge-editor-dialog .el-dialog__body {
  max-height: 66vh;
  overflow-y: auto;
}
</style>
