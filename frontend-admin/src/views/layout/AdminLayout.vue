<template>
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="brand">
        <RxtLogo :size="28" />
        <div class="brand-text">
          <strong>融销通</strong>
          <small>管理后台</small>
        </div>
      </div>
      <el-menu :default-active="$route.path" router>
        <el-menu-item index="/dashboard">
          <RxtIcon name="ico-dashboard" />
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/user/list">
          <RxtIcon name="ico-user" />
          <span>用户管理</span>
        </el-menu-item>
        <el-sub-menu index="audit">
          <template #title>
            <RxtIcon name="ico-audit" />
            <span>内容审核</span>
          </template>
          <el-menu-item index="/audit/product">
            <RxtIcon name="ico-source" />
            <span>商品审核</span>
          </el-menu-item>
          <el-menu-item index="/audit/knowledge">
            <RxtIcon name="ico-knowledge" />
            <span>知识审核</span>
          </el-menu-item>
          <el-menu-item index="/audit/comment">
            <RxtIcon name="ico-qa" />
            <span>评论审核</span>
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/finance/manage">
          <RxtIcon name="ico-finance" />
          <span>融资管理</span>
        </el-menu-item>
        <el-sub-menu index="resource">
          <template #title>
            <RxtIcon name="ico-resource" />
            <span>资源管理</span>
          </template>
          <el-menu-item index="/expert/manage">
            <RxtIcon name="ico-expert" />
            <span>专家资源管理</span>
          </el-menu-item>
          <el-menu-item index="/media/manage">
            <RxtIcon name="ico-media" />
            <span>图片资源管理</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="header-title">{{ $route.meta.title || '融销通 · 管理后台' }}</span>
        <!--
          ★ 2026-10-12 补：「退出登录」入口此前**完全不存在** ——
          store/user.js 的 logout() 一直写着却无人调用，管理员登录后除了手改
          localStorage 没有任何办法退出。这里对齐业务端 MainLayout 的顶栏下拉。
          后台只有管理员一种角色，所以不放「个人中心」项（管理员没有资料页）。
        -->
        <el-dropdown trigger="click" @command="handleUserCommand">
          <button class="user-button" type="button">
            <el-avatar :size="30" :src="avatarUrl">{{ nameInitial }}</el-avatar>
            <span class="ub-name">{{ displayName }}</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>账号身份：{{ actualRole || '未同步' }}</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
      <el-footer class="footer">
        <span class="footer-text">融销通 · 农产品产销融资管理平台</span>
        <span class="footer-text">Copyright © {{ year }} 融销通 · 管理后台</span>
      </el-footer>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import RxtIcon from '@/components/RxtIcon.vue'
import RxtLogo from '@/components/RxtLogo.vue'

const router = useRouter()
const userStore = useUserStore()

/*
 * ★ 2026-10-12 修复：这两个组件此前**既没 import、也没全局注册**。
 *   <script setup> 是空的，main.js 里也没有 app.component(...)，
 *   于是模板里的 <RxtLogo> / <RxtIcon> 全部解析失败 —— Vue 把它们当未知自定义元素
 *   渲染，控制台刷「Failed to resolve component」，侧边栏**从头到尾没有图标**。
 *   同一处还有 `{{ year }}` 未定义（页脚年份位置一直是空的）。
 *   修法就是最直白的那种：显式 import + 补一个 year。
 */
const year = computed(() => new Date().getFullYear())

/** 顶栏显示当前管理员的姓名与首字头像；整页刷新后 profile 还没回来时兜底 */
const displayName = computed(() => userStore.profile?.realName || '管理员')
const nameInitial = computed(() => displayName.value.slice(0, 1))
const actualRole = computed(() => userStore.profile?.roleName || '')
/** 头像地址写 `|| undefined` 而不是 `|| ''` —— el-avatar 拿到空串会去请求当前页面 URL */
const avatarUrl = computed(() => userStore.profile?.avatarUrl || undefined)

/**
 * 顶栏用户下拉的唯一命令：退出登录。
 *
 * 二次确认是必须的 —— 在编辑审核意见或填表的中途误点一下就会丢登录态，
 * 而这里没有草稿恢复机制，未提交的内容直接没了。
 */
async function handleUserCommand(command) {
  if (command !== 'logout') return
  try {
    await ElMessageBox.confirm('退出后需要重新登录，确定退出？', '退出登录', {
      type: 'warning',
      confirmButtonText: '确定退出',
      cancelButtonText: '取消'
    })
  } catch {
    // 点了取消 —— 这是正常分支，不是错误
    return
  }
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.layout {
  /* 不锁高度：让内容自然撑开，页脚跟在内容末尾、滚到底才出现 */
  min-height: 100vh;
}
.aside {
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
}
.brand {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  color: #14302B;
  border-bottom: 1px solid var(--el-border-color-light);
  overflow: hidden;
}
.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}
.brand-text strong {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 1px;
}
.brand-text small {
  margin-top: 2px;
  font-size: 10px;
  letter-spacing: .5px;
  color: var(--el-text-color-secondary);
}
/* 图标与文字间距。:deep() 是因为 RxtIcon 是子组件，
   其根元素带的是它自己的 scope id，父组件普通选择器选不稳。 */
.aside :deep(.rxt-icon) {
  margin-right: 8px;
}
.header {
  display: flex;
  align-items: center;
  /* 标题靠左、用户下拉靠右 */
  justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color-light);
}
/* 顶栏右侧的用户下拉，观感对齐业务端 MainLayout 的 .user-button */
.user-button {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  border: none;
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  color: var(--el-text-color-primary);
  font-size: 13px;
}
.user-button:hover { background: var(--el-fill-color-light); }
.ub-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.main {
  /* 让滚动交给整个页面：el-main 默认 overflow:auto 会自己开一个滚动区，
     那样 footer 会被固定在框内。改成 visible 后页面整体滚动，页脚自然落在内容末尾 */
  overflow: visible;
  min-height: 0;
}
.footer {
  flex-shrink: 0;
  height: auto;
  padding: 12px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
  border-top: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.footer-text + .footer-text::before {
  content: '|';
  margin-right: 12px;
  color: var(--el-border-color);
}
</style>
