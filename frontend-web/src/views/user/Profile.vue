<template>
  <div class="profile-page">
    <!-- 页面用途：个人中心。查看并维护本人基本资料；农户角色额外维护经营档案。 -->

    <!-- ================= 问候 + 待办 =================
         这两块原来在业务端首页的头部。它们都是「我的东西」，
         所以搬到个人中心，首页头部让给了平台资讯。
         问候字号刻意压到 16px：下面 .page-head 的「个人信息」是 24px 主标题，
         这里再放一个同级标题会让两层标题打架。 -->
    <div class="todo-head">
      <div class="th-left">
        <h2 class="th-greet">{{ greeting }}</h2>
        <!-- 身份展示（2026-10-10 从顶栏挪过来）：
             原来「当前身份：农户」和昵称挤在顶栏右侧同一个位置，
             现在顶栏只放昵称，身份归位到「我的资料」这一页。
             标签配色复用页面内 roleTagType()，与下方基本信息里的
             「登录角色」保持一致；旁边那行只在**切过视角**时才出现，
             没切过就显示出来只是把同一件事说了两遍。 -->
        <div class="th-role">
          <el-tag :type="roleTagType(actualRole)" effect="light" size="small" round>
            {{ actualRole || '未同步' }}
          </el-tag>
          <span v-if="viewRole" class="th-view">当前视角：{{ viewRole }}</span>
        </div>
      </div>
      <el-popover placement="bottom-end" :width="360" trigger="click">
        <template #reference>
          <button type="button" class="todo-pill" :class="{ 'is-zero': todoTotal === 0 }">
            <span class="todo-num">{{ todoTotal }}</span>
            <span class="todo-label">件待办</span>
          </button>
        </template>
        <div class="todo-panel">
          <h4>待你处理</h4>
          <ul v-if="todos.length">
            <li v-for="t in todos" :key="t.text" @click="go(t.path)">
              <span class="td-label">{{ t.label }}</span>
              <span class="td-text">{{ t.text }}</span>
              <span class="td-arrow">→</span>
            </li>
          </ul>
          <p v-else class="todo-clear">当前没有待处理事项</p>
        </div>
      </el-popover>
    </div>

    <div class="page-head">
      <div>
        <h2>个人信息</h2>
        <p>维护本人昵称与头像；农户角色可在此维护经营档案，档案将作为融资智能匹配的输入特征。</p>
      </div>
      <div class="head-actions">
        <!-- 收货地址入口：顶部导航的「个人中心」子菜单里也有一个，
             这里再放一个是给「刚在个人中心看完资料、顺手去改地址」这条路径用的。
             地址数据本身不在这页编辑，所以是跳转而不是就地展开。 -->
        <el-button @click="go('/user/address')">收货地址</el-button>
        <el-button :loading="loading" @click="loadProfile">重新加载</el-button>
      </div>
    </div>

    <el-skeleton v-if="loading && !profile" :rows="6" animated class="loading-block" />

    <template v-else-if="profile">
      <!-- ================= 基本信息 ================= -->
      <el-card shadow="never" class="section-card">
        <template #header>
          <div class="card-head">
            <span>基本信息</span>
          </div>
        </template>

        <div class="identity">
          <!-- 尺寸取 56：与 el-avatar 在本仓库的既有用量一致（MainLayout 34 / Qa 36·46 /
               Appointment 46·56 / ExpertAnswerPanel 38）。Element Plus 的文字型头像字号固定
               14px 不随 size 缩放，圆太大时首字占比过低，看起来会像"头像未加载"的占位。 -->
          <el-avatar :size="56" :src="avatarPreview">{{ nameInitial }}</el-avatar>
          <el-descriptions :column="2" border class="identity-desc">
            <el-descriptions-item label="手机号">{{ profile.phone || '--' }}</el-descriptions-item>
            <el-descriptions-item label="登录角色">
              <el-tag :type="roleTagType(profile.roleName)" effect="light">{{ profile.roleName || '--' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="账号状态">
              <el-tag :type="profile.status === 1 ? 'success' : 'danger'" effect="light">
                {{ profile.status === 1 ? '正常' : '已停用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="用户编号">{{ profile.userId ?? '--' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <el-alert
          class="tip"
          type="info"
          :closable="false"
          show-icon
          title="手机号是登录账号，变更需短信验证，本页不提供修改；角色与账号状态由平台统一管理。"
        />

        <el-form ref="baseFormRef" :model="baseForm" :rules="baseRules" label-width="100px" class="edit-form">
          <el-form-item label="昵称" prop="realName">
            <el-input v-model="baseForm.realName" maxlength="50" show-word-limit placeholder="请输入昵称" />
          </el-form-item>
          <el-form-item label="头像" prop="avatarUrl">
            <!--
              上传控件走 el-upload 的自定义 http-request，而不是让它自己 action 上传：
              项目的 request 拦截器统一附带 JWT、统一解包 Result 并弹错，走它才能拿到
              后端返回的 { url, objectKey, ... } 并复用同一套错误提示。
            -->
            <div class="avatar-field">
              <el-upload
                action="#"
                :show-file-list="false"
                :http-request="handleAvatarUpload"
                :before-upload="beforeAvatarUpload"
                accept=".jpg,.jpeg,.png,.gif,.webp"
              >
                <el-button :loading="uploadingAvatar" size="small">
                  {{ baseForm.avatarUrl ? '更换头像' : '上传头像' }}
                </el-button>
              </el-upload>
              <el-button
                v-if="baseForm.avatarUrl"
                size="small"
                type="danger"
                plain
                :disabled="savingBase"
                @click="clearAvatar"
              >
                移除
              </el-button>
              <!-- 上传成功后立刻回显，让用户确认存的是哪张图，不必等保存基本信息。
                   ★ 用 el-avatar 而不是 el-image（2026-10-10 实测改）：el-image
                   图加载失败时会渲染 Element Plus 自带的「加载失败」占位（文案来自
                   locale 的 el.image.error），而**这里加载失败是正常情况** ——
                   王建国/张桂芳/赵国强 这 3 条 seed 行的 avatar_url 是 2026-08 写死的，
                   对应文件从未落盘（uploads/ 里没有它们，只有真实上传的文件）。
                   实测赵国强（专家）进本页就会看到一个「加载失败」红框。
                   el-avatar 则与上方第 76 行、顶栏 MainLayout.vue 完全一致：
                   图能加载就显示图，失败自动回退到昵称首字，不露报错文案。 -->
              <el-avatar
                v-if="avatarPreview"
                :size="56"
                :src="avatarPreview"
                class="avatar-preview"
              >{{ nameInitial }}</el-avatar>
            </div>
            <div class="field-tip">
              支持 JPG / PNG / GIF / WEBP，不超过 5MB。未上传时显示昵称首字。
              <!-- 保留下面的手工输入框作为兜底：内网/离线环境下 OBS 不可用时仍可贴地址 -->
            </div>
            <el-input
              v-model="baseForm.avatarUrl"
              maxlength="255"
              placeholder="或直接填写图片地址"
              class="avatar-url-input"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingBase" :disabled="!baseDirty" @click="saveBase">
              保存基本信息
            </el-button>
            <el-button :disabled="savingBase || !baseDirty" @click="resetBaseForm">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- ================= 农户经营档案 ================= -->
      <el-card v-if="isFarmer" shadow="never" class="section-card">
        <template #header>
          <div class="card-head">
            <span>农户经营档案</span>
          </div>
        </template>

        <el-alert
          v-if="!hasFarmerProfile"
          class="tip"
          type="warning"
          :closable="false"
          show-icon
          title="尚未建档：填写并保存后将自动创建经营档案。"
        />

        <el-form ref="farmerFormRef" :model="farmerForm" :rules="farmerRules" label-width="130px" class="edit-form">
          <el-form-item label="主营农产品" prop="mainProduct">
            <el-input v-model="farmerForm.mainProduct" maxlength="50" show-word-limit placeholder="如：柑橘类水果" />
          </el-form-item>
          <el-form-item label="经营规模" prop="scaleDesc">
            <el-input
              v-model="farmerForm.scaleDesc"
              maxlength="50"
              show-word-limit
              placeholder="如：种植面积 60 亩，年产量约 120 吨"
            />
          </el-form-item>
          <el-form-item label="信用与经营特征" prop="creditRecord">
            <el-input
              v-model="farmerForm.creditRecord"
              type="textarea"
              :rows="4"
              maxlength="1000"
              show-word-limit
              placeholder="记录信用与经营特征，作为融资智能匹配的输入"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingFarmer" :disabled="!farmerDirty" @click="saveFarmer">
              {{ hasFarmerProfile ? '保存经营档案' : '创建经营档案' }}
            </el-button>
            <el-button :disabled="savingFarmer || !farmerDirty" @click="resetFarmerForm">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 非农户角色：档案区不渲染编辑表单，与后端 403 的权限边界保持一致 -->
      <el-card v-else shadow="never" class="section-card">
        <template #header>农户经营档案</template>
        <el-empty
          :image-size="80"
          :description="`经营档案仅农户角色可维护，当前登录角色为「${profile.roleName || '未知'}」`"
        />
      </el-card>
    </template>

    <el-empty v-else class="loading-block" description="未能获取个人资料，请重新登录后再试">
      <el-button type="primary" @click="loadProfile">重新加载</el-button>
    </el-empty>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import request from '@/api/request'
import { useUserStore } from '@/store/user'
import { getProfile, updateProfile, updateFarmerProfile } from '@/api/user'
import { checkImageFile, uploadAvatar } from '@/api/common'
import { APPLY_STATUS, isPendingApply } from '@/constants/finance'

/**
 * 页面用途：个人中心（对应《软件需求规格说明书》2.8 个人信息 - 基本信息管理 / 农户经营档案）。
 * 接口文件：frontend-web/src/api/user.js
 * 主要接口：GET /api/user/profile、PUT /api/user/profile、PUT /api/user/farmer-profile
 *
 * 后端 UserVO 返回：userId、phone、realName、avatarUrl、roleName、status、roleId、
 * menuPerms；农户角色额外带回 farmerProfile，未建档或非农户时为 null。
 *
 * 按项目约定：本页只展示真实接口结果，不做演示数据回退 —— 个人资料与登录者绑定，
 * 回退到假数据没有意义，反而会掩盖接口故障。
 */
/* ====================================================================
 * 问候 + 待办（从业务端首页搬过来）
 *
 * 权限口径与首页、MainLayout 的 menuPerms 完全一致：
 * 没有 trade 权限就不去拉订单接口，没有 finance 权限就不去拉融资接口，
 * 避免无权限角色每次进个人中心都收到一串 403。
 * ==================================================================== */
const router = useRouter()
const userStore = useUserStore()

const actualRole = computed(() => userStore.profile?.roleName || '')

// 顶栏不再显示身份之后，这一页要负责说清「我现在以什么身份在操作」。
// 农户/采购方可以在顶栏切视角，切过之后 activeRole 就与登录角色不一致 ——
// 只有这种情况才提示，没切过时显示「当前视角：农户」等于把同一件事说两遍。
const viewRole = computed(() => {
  const v = userStore.activeRole
  return v && v !== actualRole.value ? v : ''
})
const menuPerms = computed(() => String(userStore.menuPerms || '').split(',').filter(Boolean))
const can = (k) => menuPerms.value.includes(k)
const showTrade = computed(() => can('trade'))
const showFinance = computed(() => can('finance'))
const showBankReview = computed(() => actualRole.value === '银行用户')

const greeting = computed(() => {
  const h = new Date().getHours()
  const period = h < 6 ? '凌晨好' : h < 12 ? '早上好' : h < 14 ? '中午好' : h < 18 ? '下午好' : '晚上好'
  // 用登录返回的 realName 而不是本页的 profile：本页的 profile 是异步拉的，
  // 首帧还没有值，名字会闪一下。
  const name = userStore.profile?.realName || ''
  return name ? `${period}，${name}` : period
})

const buyOrders = ref([])
const soldOrders = ref([])
const applies = ref([])
const bankApplies = ref([])

/** 待办清单：只统计「现在需要我动手」的，不把"已被驳回"这类历史也算成待办 */
const todos = computed(() => {
  const list = []
  if (showFinance.value) {
    const source = showBankReview.value ? bankApplies.value : applies.value
    // ⚠️ 银行侧待办只有一种状态：「已推送银行」。
    // 原来这里统一用 /待|审批中|审核中/ 匹配 —— 「已推送银行」四个字里
    // **没有「待」字**，所以银行用户的「待审批」永远算出来是 0，
    // 待办清单上一条都不显示。银行视角必须走 isPendingApply()。
    // 农户视角则相反，在途状态较多（待匹配 / 已组建小组 / 已推送银行），
    // 保留原来的宽口径匹配。
    const pending = showBankReview.value
      ? source.filter((a) => isPendingApply(a.applyStatus))
      : source.filter((a) => /待|审批中|审核中/.test(a.applyStatus || ''))
    if (pending.length) {
      list.push({
        label: '融资申请',
        text: showBankReview.value
          ? `${pending.length} 笔申请待审批`
          : `${pending.length} 笔申请处理中`,
        path: showBankReview.value ? '/finance/bank-review' : '/finance/my'
      })
    }
    const rejected = source.filter((a) => a.applyStatus === APPLY_STATUS.REJECTED)
    if (rejected.length) {
      list.push({
        label: '融资申请',
        // 银行是出结论的一方，说「被驳回」语义反了
        text: showBankReview.value
          ? `${rejected.length} 笔已给出「不通过」结论`
          : `${rejected.length} 笔被驳回，可查看原因`,
        path: showBankReview.value ? '/finance/bank-review' : '/finance/my'
      })
    }
  }
  if (showTrade.value) {
    const toDeliver = [...buyOrders.value, ...soldOrders.value]
      .filter((o) => /待发货|待支付|待收货/.test(o.orderStatus || o.status || ''))
    if (toDeliver.length) {
      list.push({ label: '订单', text: `${toDeliver.length} 笔订单待处理`, path: '/trade/orders' })
    }
  }
  return list
})
const todoTotal = computed(() => todos.value.length)

function go(p) {
  if (p) router.push(p)
}

function listOf(d) {
  return Array.isArray(d) ? d : (d?.records || d?.list || [])
}

async function loadTodos() {
  const jobs = []
  if (showTrade.value) {
    jobs.push(
      request.get('/trade/order/list', { params: { pageNum: 1, pageSize: 5 }, silent: true })
        .then((d) => { buyOrders.value = listOf(d) }).catch(() => { buyOrders.value = [] })
    )
    jobs.push(
      request.get('/trade/order/sold', { params: { pageNum: 1, pageSize: 5 }, silent: true })
        .then((d) => { soldOrders.value = listOf(d) }).catch(() => { soldOrders.value = [] })
    )
  }
  if (showFinance.value) {
    // 银行用户要「别人提交的申请」，农户要「自己提交的申请」——同一模块两个视角。
    // ⚠️ 银行侧原来是 '/loan/applies'，后端**没有这个路径**（真实的是
    // /loan-admin/applies），404 被 catch 吞掉后待办恒为 0。
    // 该接口返回的是数组且只认 status 参数，pageNum/pageSize 会被后端忽略，
    // 保留只是为了和下面农户分支的写法一致。
    const ep = showBankReview.value ? '/loan-admin/applies' : '/finance/apply/my'
    jobs.push(
      request.get(ep, { params: { pageNum: 1, pageSize: 5 }, silent: true })
        .then((d) => {
          if (showBankReview.value) bankApplies.value = listOf(d)
          else applies.value = listOf(d)
        })
        .catch(() => {
          if (showBankReview.value) bankApplies.value = []
          else applies.value = []
        })
    )
  }
  // 待办拉取失败不能影响个人资料本身的展示，所以这里吞掉异常，
  // 由 request 拦截器负责提示；页面照常显示「0 件待办」。
  await Promise.all(jobs)
}

const loading = ref(false)
const profile = ref(null)
const savingBase = ref(false)
const savingFarmer = ref(false)
const baseFormRef = ref(null)
const farmerFormRef = ref(null)

// 编辑态表单
const baseForm = reactive({ realName: '', avatarUrl: '' })
const farmerForm = reactive({ mainProduct: '', scaleDesc: '', creditRecord: '' })

// 服务端返回值的快照，既是「重置」的目标，也是「是否有改动」的判定基准。
// ⚠️ 必须用 ref（响应式）：baseDirty / farmerDirty 是 computed，只有依赖变化才会重算。
// 若快照用普通变量，则「表单值不变、快照变了」的场景（例如刚保存完，快照更新为表单当前值）
// 不会触发重算 —— 表现为保存成功后按钮仍是可用状态，像是一直有未保存的改动。
const baseSnapshot = ref({ realName: '', avatarUrl: '' })
const farmerSnapshot = ref({ mainProduct: '', scaleDesc: '', creditRecord: '' })

// 长度上限与后端 DTO 的 @Size 及建表列宽保持一致（realName/avatarUrl 见 UserProfileUpdateDTO，
// 其余见 FarmerProfileUpdateDTO：main_product / scale_desc 为 VARCHAR(50)，credit_record 为 TEXT）。
const baseRules = {
  realName: [
    { required: true, message: '昵称不能为空', trigger: 'blur' },
    { max: 50, message: '昵称长度不能超过 50 个字符', trigger: 'blur' }
  ],
  avatarUrl: [{ max: 255, message: '头像地址长度不能超过 255 个字符', trigger: 'blur' }]
}

const farmerRules = {
  mainProduct: [{ max: 50, message: '主营产品长度不能超过 50 个字符', trigger: 'blur' }],
  scaleDesc: [{ max: 50, message: '经营规模描述不能超过 50 个字符', trigger: 'blur' }],
  creditRecord: [{ max: 1000, message: '信用记录长度不能超过 1000 个字符', trigger: 'blur' }]
}

const isFarmer = computed(() => profile.value?.roleName === '农户')
const hasFarmerProfile = computed(() => Boolean(profile.value?.farmerProfile))
// 头像预览跟随输入实时变化，便于填完地址立刻看到效果
const avatarPreview = computed(() => baseForm.avatarUrl.trim() || undefined)
const nameInitial = computed(() => (baseForm.realName.trim() || profile.value?.realName || '用').slice(0, 1))

const uploadingAvatar = ref(false)

/**
 * 上传前的本地校验，只是为了即时反馈；真正的类型/大小判定在服务端
 * ImageFileValidator（按文件头 magic number 判，不认后缀与 Content-Type）。
 */
function beforeAvatarUpload(file) {
  const err = checkImageFile(file, { label: '头像' })
  if (err) {
    ElMessage.warning(err)
    return false
  }
  return true
}

/**
 * 自定义上传：走项目的 request 封装而不是 el-upload 自身的 action，
 * 这样 JWT、Result 解包、错误 ElMessage 都与其它接口一致。
 *
 * ★ 注意后端 PUT 的约定是「null＝不修改、空串＝清空」，所以这里**不能**顺手调保存接口：
 * 上传只拿到地址，是否入库由用户点「保存基本信息」决定，避免"刚选完图就被自动保存"。
 */
async function handleAvatarUpload(options) {
  uploadingAvatar.value = true
  try {
    const result = await uploadAvatar(options.file)
    baseForm.avatarUrl = result.url
    options.onSuccess(result)
    ElMessage.success('头像已上传，别忘了点「保存基本信息」')
  } catch (error) {
    // request 拦截器已统一弹过错误文案，这里不重复提示
    options.onError(error)
  } finally {
    uploadingAvatar.value = false
  }
}

/** 移除头像：置空而非删除文件对象 —— 服务端没有删除接口，且旧图可能仍被别处引用 */
function clearAvatar() {
  baseForm.avatarUrl = ''
}

const baseDirty = computed(
  () => baseForm.realName !== baseSnapshot.value.realName || baseForm.avatarUrl !== baseSnapshot.value.avatarUrl
)

/**
 * 是否有改动。
 * 注意未建档（farmerProfile 为 null）时基准是全空，此时只要任一字段有内容就允许提交 ——
 * 否则「空 == 空」会判定为无改动，农户将永远无法创建第一条档案。
 */
const farmerDirty = computed(() => {
  const snap = farmerSnapshot.value
  if (!hasFarmerProfile.value) {
    return Boolean(farmerForm.mainProduct || farmerForm.scaleDesc || farmerForm.creditRecord)
  }
  return (
    farmerForm.mainProduct !== snap.mainProduct ||
    farmerForm.scaleDesc !== snap.scaleDesc ||
    farmerForm.creditRecord !== snap.creditRecord
  )
})

function roleTagType(roleName) {
  const types = { '农户': 'success', '采购方': 'primary', '专家': 'warning', '银行用户': 'info', '管理员': 'danger' }
  return types[roleName] || 'info'
}

/** 用接口返回值刷新页面状态与快照。注意 Jackson 配了 non_null，空字段会直接不出现在 JSON 里。 */
function applyProfile(data) {
  profile.value = data

  baseSnapshot.value = { realName: data.realName || '', avatarUrl: data.avatarUrl || '' }
  Object.assign(baseForm, baseSnapshot.value)

  const fp = data.farmerProfile || {}
  farmerSnapshot.value = {
    mainProduct: fp.mainProduct || '',
    scaleDesc: fp.scaleDesc || '',
    creditRecord: fp.creditRecord || ''
  }
  Object.assign(farmerForm, farmerSnapshot.value)

  baseFormRef.value?.clearValidate()
  farmerFormRef.value?.clearValidate()
}

async function loadProfile() {
  loading.value = true
  try {
    applyProfile(await getProfile())
  } catch {
    // 错误提示已由 request 拦截器统一弹出，这里只保证页面停在可重试的状态
  } finally {
    loading.value = false
  }
}

async function saveBase() {
  const valid = await baseFormRef.value?.validate().catch(() => false)
  if (!valid) return

  savingBase.value = true
  try {
    // 后端按「null 表示不修改、空串表示清空」处理，因此只提交真正改动过的字段，
    // 避免把未改动的字段一并回传造成无意义的写入。
    const payload = {}
    if (baseForm.realName !== baseSnapshot.value.realName) payload.realName = baseForm.realName.trim()
    if (baseForm.avatarUrl !== baseSnapshot.value.avatarUrl) payload.avatarUrl = baseForm.avatarUrl.trim()

    await updateProfile(payload)
    // 同步本地登录态：顶栏那处「昵称」读的就是这份缓存（store/user.js 的 profile），
    // 不更新的话改完名字顶栏还显示旧的，得重新登录才变 —— 用户会以为没保存成功
    userStore.setProfile({
      realName: baseForm.realName.trim(),
      avatarUrl: baseForm.avatarUrl.trim()
    })
    ElMessage.success('基本信息已保存')
    await loadProfile()
  } catch {
    // 同上，错误提示已在拦截器统一处理
  } finally {
    savingBase.value = false
  }
}

async function saveFarmer() {
  const valid = await farmerFormRef.value?.validate().catch(() => false)
  if (!valid) return

  savingFarmer.value = true
  try {
    await updateFarmerProfile({
      mainProduct: farmerForm.mainProduct.trim(),
      scaleDesc: farmerForm.scaleDesc.trim(),
      creditRecord: farmerForm.creditRecord.trim()
    })
    ElMessage.success(hasFarmerProfile.value ? '经营档案已保存' : '经营档案已创建')
    await loadProfile()
  } catch {
    // 同上
  } finally {
    savingFarmer.value = false
  }
}

function resetBaseForm() {
  Object.assign(baseForm, baseSnapshot.value)
  baseFormRef.value?.clearValidate()
}

function resetFarmerForm() {
  Object.assign(farmerForm, farmerSnapshot.value)
  farmerFormRef.value?.clearValidate()
}

onMounted(() => {
  loadProfile()
  loadTodos()
})
</script>

<style scoped>
/* ---------- 问候 + 待办（从首页搬过来） ---------- */
.todo-head {
  display: flex; align-items: center; justify-content: space-between;
  gap: 24px; margin-bottom: 12px;
}
/* 左侧从「单行问候」变成「问候 + 身份」两行；右侧待办胶囊仍垂直居中 */
.th-left { display: flex; flex-direction: column; gap: 6px; }
.th-greet {
  margin: 0; font-size: 16px; font-weight: 600; line-height: 1.4;
  color: var(--rxt-primary);
}
.th-role { display: flex; align-items: center; gap: 8px; }
.th-view { font-size: 12px; color: var(--el-text-color-secondary); }
.todo-pill {
  display: inline-flex; align-items: baseline; gap: 6px;
  padding: 8px 18px;
  border: 1px solid var(--rxt-border);
  border-radius: var(--rxt-radius-pill);
  background: var(--el-color-primary-light-9);
  font-family: inherit; cursor: pointer;
  transition: border-color 0.2s;
}
.todo-pill:hover { border-color: var(--rxt-primary); }
.todo-pill.is-zero { background: transparent; }
.todo-num {
  font-size: 20px; font-weight: 600; line-height: 1;
  color: var(--rxt-primary); font-variant-numeric: tabular-nums;
}
.todo-pill.is-zero .todo-num { color: var(--el-text-color-placeholder); }
.todo-label { font-size: 14px; color: var(--el-text-color-secondary); }

.todo-panel h4 { margin: 0 0 12px; font-size: 15px; font-weight: 600; }
.todo-panel ul { margin: 0; padding: 0; list-style: none; }
.todo-panel li {
  display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 10px;
  padding: 11px 4px; border-top: 1px solid var(--el-border-color-lighter); cursor: pointer;
}
.todo-panel li:hover .td-arrow { transform: translateX(3px); }
.td-label {
  padding: 2px 8px; border-radius: 4px;
  background: var(--el-color-primary-light-9); color: var(--rxt-primary-deep); font-size: 12px;
}
.td-text { font-size: 14px; color: var(--el-text-color-primary); }
.td-arrow { color: var(--rxt-primary); transition: transform 0.2s; }
.todo-clear { margin: 0; font-size: 14px; color: var(--el-text-color-secondary); }

.loading-block { padding: 24px; border-radius: 14px; background: #fff; }
.section-card { margin-bottom: 18px; }
.card-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; font-weight: 600; }
.identity { display: flex; align-items: center; gap: 24px; margin-bottom: 18px; flex-wrap: wrap; }
.identity-desc { flex: 1; min-width: 320px; }
.tip { margin-bottom: 20px; }
.edit-form { max-width: 680px; }
.field-tip { margin-top: 6px; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }

/* 头像上传行：按钮 + 移除 + 预览横向排布。预览固定 56×56 与上方 el-avatar 同尺寸，
   尺寸跟着 el-avatar 走而不是写死，避免以后调整大小时两处不一致。 */
.avatar-field { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.avatar-preview { width: 56px; height: 56px; border-radius: 50%; }
/* 手工填地址的兜底输入框：与主表单拉开距离，避免和上传按钮抢视觉焦点 */
.avatar-url-input { margin-top: 8px; }
@media (max-width: 760px) {
  .page-head { flex-direction: column; align-items: stretch; }
  .identity { gap: 16px; }
}
</style>
