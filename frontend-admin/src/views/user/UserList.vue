<template>
  <div class="user-list-page">
    <!--
      页面用途：平台管理员治理全平台账号。
      数据来自 GET /api/admin/users，支持按角色、姓名/手机号、账号状态筛选；
      启停用与调整角色分别走 PUT /admin/users/{userId}/status 与 /role。

      两条治理红线由后端强制（AdminUserServiceImpl），前端同步置灰按钮并给出提示：
      不能停用自己、系统至少保留一名启用状态的管理员。本模块不提供删除用户。
    -->
    <section class="user-hero">
      <div>
        <span class="eyebrow">M6 · 平台治理</span>
        <h2>用户管理</h2>
        <p>管理全平台账号的启用状态与角色。账号只做停用不做删除，业务数据（商品、订单、融资）都关联到用户</p>
      </div>
      <div class="hero-side">
        <div class="hero-stat">
          <span>当前管理员</span>
          <strong>{{ currentUserName }}</strong>
        </div>
        <el-button class="hero-refresh" :loading="loading" @click="refreshAll">刷新</el-button>
      </div>
    </section>

    <el-alert
      class="rule-alert"
      type="warning"
      show-icon
      :closable="false"
      title="治理红线"
      description="不能停用当前登录的管理员自己；系统至少保留一名启用状态的管理员。这两条由后端强制校验，前端已同步置灰按钮"
    />

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="toolbar">
          <div class="filters">
            <el-select v-model="query.roleId" placeholder="全部角色" clearable class="filter-role" @change="handleRoleChange">
              <el-option v-for="role in roles" :key="role.roleId" :label="role.roleName" :value="role.roleId" />
            </el-select>
            <el-input
              v-model="query.keyword"
              placeholder="按姓名或手机号搜索"
              clearable
              class="filter-keyword"
              @keyup.enter="handleSearch"
              @clear="handleSearch"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="query.status" placeholder="全部状态" clearable class="filter-status" @change="handleStatusFilter">
              <el-option label="正常" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
            <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </div>
          <span class="record-count">共 {{ total }} 个账号</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" row-key="userId">
        <el-table-column label="用户" min-width="200">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="36" :src="row.avatarUrl || undefined" class="user-avatar">
                {{ (row.realName || '?').slice(0, 1) }}
              </el-avatar>
              <div class="user-meta">
                <strong>
                  {{ row.realName || '--' }}
                  <el-tag v-if="row.userId === currentUserId" size="small" type="primary" effect="plain">我</el-tag>
                </strong>
                <small>{{ row.phone }}</small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="130">
          <template #default="{ row }"><el-tag :type="roleTagType(row.roleName)">{{ row.roleName || '--' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="账号状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <!-- 停用自己：后端必然拒绝，直接置灰并说明原因，不让用户白点一次 -->
            <el-button
              link
              :type="row.status === 1 ? 'danger' : 'success'"
              :disabled="isSelf(row) && row.status === 1"
              :title="isSelf(row) && row.status === 1 ? '不能停用当前登录的管理员账号' : ''"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button link type="primary" @click="openRoleDialog(row)">调整角色</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="当前筛选条件下没有账号" /></template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          background
          layout="sizes, prev, pager, next"
          @current-change="loadList"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="roleDialogVisible" title="调整用户角色" width="min(520px, 92vw)">
      <template v-if="roleTarget">
        <div class="dialog-target">
          <span>目标账号</span>
          <strong>{{ roleTarget.realName || '--' }}（{{ roleTarget.phone }}）</strong>
          <small>当前角色：{{ roleTarget.roleName || '--' }}</small>
        </div>
        <el-form label-position="top">
          <el-form-item label="新角色">
            <!-- 角色来自 /admin/roles，不硬编码；已排除当前角色，避免必然被后端拒绝的提交 -->
            <el-radio-group v-model="targetRoleId" class="role-options">
              <el-radio v-for="role in assignableRoles" :key="role.roleId" :value="role.roleId" border>
                <div class="role-option">
                  <strong>{{ role.roleName }}</strong>
                  <small>{{ role.description || '--' }}</small>
                </div>
              </el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <el-alert
          v-if="isSelf(roleTarget)"
          type="error"
          show-icon
          :closable="false"
          title="不能调整自己的角色"
          description="把自己改成非管理员会让治理入口自锁，后端会拒绝该操作"
        />
        <el-alert
          v-else-if="roleTarget.roleName === '管理员'"
          type="warning"
          show-icon
          :closable="false"
          title="该用户当前是管理员"
          description="改成其他角色前，系统必须还有其他启用状态的管理员，否则后端会拒绝"
        />
      </template>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="roleSubmitting"
          :disabled="!targetRoleId || isSelf(roleTarget)"
          @click="confirmRoleChange"
        >
          确认调整
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  getAdminUserList,
  getCurrentProfile,
  getRoleList,
  updateUserRole,
  updateUserStatus
} from '@/api/user'

/** 列表与筛选状态。 */
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const roles = ref([])
const currentUserId = ref(null)
const currentUserName = ref('--')

/** 调整角色弹窗状态。 */
const roleDialogVisible = ref(false)
const roleSubmitting = ref(false)
const roleTarget = ref(null)
const targetRoleId = ref(null)

const query = reactive({
  roleId: null,
  keyword: '',
  status: null,
  pageNum: 1,
  pageSize: 10
})

/** 调整角色时排除当前角色：选中等于没改，后端会直接拒绝。 */
const assignableRoles = computed(() => {
  const current = roleTarget.value
  if (!current) return roles.value
  return roles.value.filter((role) => Number(role.roleId) !== Number(current.roleId))
})

function isSelf(row) {
  return currentUserId.value != null && Number(row.userId) === Number(currentUserId.value)
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '--'
}

/** 管理员用红色区分，其他角色按序取绿/蓝，与仪表盘的配色一致。 */
function roleTagType(roleName) {
  if (roleName === '管理员') return 'danger'
  if (roleName === '农户') return 'success'
  if (roleName === '采购方') return 'warning'
  if (roleName === '专家') return 'primary'
  return 'info'
}

async function loadList() {
  loading.value = true
  try {
    // roleId / status 为空时不传，Element Plus 的 null 传过去后端也能当无筛选处理
    const data = await getAdminUserList({
      roleId: query.roleId || undefined,
      keyword: query.keyword?.trim() || undefined,
      status: query.status ?? undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    rows.value = data?.records || []
    total.value = Number(data?.total || 0)
  } finally {
    loading.value = false
  }
}

/** 当前登录者只用于「不能停用自己 / 不能改自己角色」的判断，取不到不影响主流程。 */
async function loadCurrentUser() {
  try {
    const profile = await getCurrentProfile()
    currentUserId.value = profile?.userId ?? null
    currentUserName.value = profile?.realName || '--'
  } catch {
    currentUserId.value = null
  }
}

async function refreshAll() {
  await Promise.all([loadList(), loadRoleList()])
}

function handleSearch() {
  query.pageNum = 1
  loadList()
}

function handleRoleChange() {
  handleSearch()
}

function handleStatusFilter() {
  handleSearch()
}

function handleSizeChange() {
  query.pageNum = 1
  loadList()
}

function handleReset() {
  query.roleId = null
  query.keyword = ''
  query.status = null
  query.pageNum = 1
  loadList()
}

async function loadRoleList() {
  try {
    roles.value = (await getRoleList()) || []
  } catch {
    roles.value = []
  }
}

async function toggleStatus(row) {
  const next = row.status === 1 ? 0 : 1
  const verb = next === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(
      next === 1
        ? `确认启用「${row.realName}」的账号吗？启用后该用户可以重新登录`
        : `确认停用「${row.realName}」的账号吗？停用后该用户将无法登录，但历史数据保留`,
      `${verb}确认`,
      { type: next === 1 ? 'info' : 'warning', confirmButtonText: `确认${verb}`, cancelButtonText: '取消' }
    )
  } catch {
    // 用户点了取消，不做任何事
    return
  }
  await updateUserStatus(row.userId, next)
  ElMessage.success(`账号已${verb}`)
  await loadList()
}

function openRoleDialog(row) {
  roleTarget.value = row
  // 预选一个不同于当前角色的值，省去用户再点一次
  const firstOther = roles.value.find((role) => Number(role.roleId) !== Number(row.roleId))
  targetRoleId.value = firstOther?.roleId ?? null
  roleDialogVisible.value = true
}

async function confirmRoleChange() {
  if (!roleTarget.value || !targetRoleId.value) return
  const target = roles.value.find((role) => Number(role.roleId) === Number(targetRoleId.value))
  roleSubmitting.value = true
  try {
    await updateUserRole(roleTarget.value.userId, targetRoleId.value)
    roleDialogVisible.value = false
    ElMessage.success(`已将「${roleTarget.value.realName}」调整为「${target?.roleName || '--'}」`)
    await loadList()
  } finally {
    roleSubmitting.value = false
  }
}

onMounted(refreshAll)
loadCurrentUser()
</script>

<style scoped>
.user-list-page { color: #24332b; }
.user-hero { display: flex; justify-content: space-between; align-items: center; gap: 24px; padding: 28px; border-radius: 18px; color: #fff; background: linear-gradient(125deg, #173f2a, #2b8154); box-shadow: 0 16px 34px rgba(23, 63, 42, .16); }
.eyebrow { color: #ccebd5; font-size: 13px; }
.user-hero h2 { margin: 9px 0 7px; font-size: 26px; white-space: nowrap; }
.user-hero p { margin: 0; max-width: 700px; color: rgba(255,255,255,.72); line-height: 1.7; }
.user-hero > div:first-child { flex: 1; min-width: 0; }
.hero-side { display: flex; align-items: center; gap: 14px; flex-shrink: 0; }
.hero-stat { padding: 12px 18px; text-align: right; border: 1px solid rgba(255,255,255,.18); border-radius: 14px; background: rgba(255,255,255,.1); }
.hero-stat span { display: block; color: rgba(255,255,255,.66); font-size: 12px; }
.hero-stat strong { display: block; margin-top: 5px; font-size: 16px; }
.hero-refresh { color: #173f2a; font-weight: 600; background: #ccebd5; border-color: transparent; }
.hero-refresh:hover { color: #173f2a; background: #e3f5ea; border-color: transparent; }
.rule-alert { margin: 18px 0; }
.table-card { border-radius: 15px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.filters { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.filter-role { width: 130px; }
.filter-keyword { width: 220px; }
.filter-status { width: 120px; }
.record-count { color: #75827a; font-size: 12px; }
.user-cell { display: flex; align-items: center; gap: 11px; }
.user-avatar { flex-shrink: 0; color: #fff; font-weight: 600; background: linear-gradient(140deg, #5b9c7c, #2f8a5b); }
.user-meta strong, .user-meta small { display: block; }
.user-meta strong { display: flex; align-items: center; gap: 6px; }
.user-meta small { margin-top: 3px; color: #8b978f; font-size: 12px; }
.pagination-wrap { display: flex; justify-content: flex-end; padding-top: 18px; }
.dialog-target { margin-bottom: 18px; padding: 14px 16px; border-radius: 12px; background: #f4f8f5; }
.dialog-target span, .dialog-target strong, .dialog-target small { display: block; }
.dialog-target span { color: #75827a; font-size: 12px; }
.dialog-target strong { margin: 5px 0 3px; }
.dialog-target small { color: #8b978f; font-size: 12px; }
.role-options { width: 100%; display: grid; gap: 10px; }
.role-options :deep(.el-radio) { width: 100%; height: auto; margin-right: 0; padding: 12px 14px; }
.role-options :deep(.el-radio__label) { width: 100%; }
.role-option strong, .role-option small { display: block; }
.role-option small { margin-top: 4px; color: #8b978f; font-size: 11px; line-height: 1.5; }
@media (max-width: 1000px) { .user-hero { align-items: stretch; flex-direction: column; } .hero-side { justify-content: space-between; } .hero-stat { text-align: left; } }
@media (max-width: 680px) { .filters { width: 100%; } .filter-role, .filter-keyword, .filter-status { flex: 1; width: auto; min-width: 110px; } .hero-side { flex-direction: column; align-items: stretch; } }
</style>
