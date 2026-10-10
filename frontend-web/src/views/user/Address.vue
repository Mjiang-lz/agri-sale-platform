<template>
  <div class="address-page">
    <div class="page-head">
      <div>
        <h2>收货地址</h2>
        <p>管理常用的收货地址。结算时会在这些地址里选一条，农户按它发货；设为默认的那条会被优先选中。</p>
      </div>
      <div class="head-actions">
        <el-button :loading="loading" @click="loadList">刷新</el-button>
        <el-button type="primary" @click="openForm(null)">+ 新增地址</el-button>
      </div>
    </div>

    <el-skeleton v-if="loading && !addresses.length" animated :rows="3" />

    <el-card v-else-if="addresses.length" shadow="never" class="content-card">
      <div class="addr-grid">
        <article
          v-for="a in addresses"
          :key="a.addressId"
          class="addr-card"
          :class="{ 'is-default': a.isDefault === 1 }"
        >
          <div class="ac-head">
            <strong>{{ a.consignee }}</strong>
            <span class="ac-phone">{{ a.phone }}</span>
            <!-- 默认标记只在「是默认」时出现。不给非默认地址加一个灰色的「设为默认」
                 ——那会变成一排同权重的按钮，看不出哪条才是当前生效的。 -->
            <el-tag v-if="a.isDefault === 1" size="small" effect="dark" class="ac-tag">默认地址</el-tag>
          </div>

          <p class="ac-addr">{{ a.fullAddress }}</p>

          <div class="ac-foot">
            <el-button v-if="a.isDefault !== 1" link type="primary" @click="markDefault(a)">设为默认</el-button>
            <span v-else class="ac-hint">结算时优先使用</span>
            <div class="ac-ops">
              <el-button link type="primary" @click="openForm(a)">编辑</el-button>
              <el-button link type="danger" @click="confirmRemove(a)">删除</el-button>
            </div>
          </div>
        </article>
      </div>
    </el-card>

    <el-card v-else shadow="never" class="content-card">
      <el-empty description="还没有收货地址">
        <el-button type="primary" @click="openForm(null)">新增地址</el-button>
      </el-empty>
    </el-card>

    <AddressFormDialog v-model="formVisible" :address="editing" @saved="loadList" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAddressList, removeAddress, setDefaultAddress } from '@/api/trade'
import AddressFormDialog from '@/components/AddressFormDialog.vue'

/**
 * 页面用途：我的收货地址（管理入口，非结算流程）。
 *
 * 接口文件：frontend-web/src/api/trade.js
 *   GET    /api/address/list                 我的地址列表（默认在前）
 *   PUT    /api/address/{id}/default         设为默认
 *   DELETE /api/address/{id}                 删除
 *   （新增 / 编辑在 AddressFormDialog 里，与结算页共用同一个组件）
 *
 * 与结算页（trade/Checkout.vue）的分工：
 *   结算页只做「选一条」，本页做「增删改」。两边共用 AddressFormDialog，
 *   所以地址字段与校验规则只有一份。
 *
 * ★ 为什么删掉默认地址不用前端兜底挑一条新的：
 *   后端在 remove 里已经处理了 —— 删的是默认地址且还有别的地址时，
 *   会把剩余最新的一条顶为默认。前端再自己算一遍会产生两个真相，
 *   而且前端算的那份在「别的标签页刚改过地址」时是错的。
 *   这里删完直接重新拉列表就行。
 */

const loading = ref(false)
const addresses = ref([])
const formVisible = ref(false)
const editing = ref(null)

onMounted(loadList)

async function loadList() {
  loading.value = true
  try {
    addresses.value = (await getAddressList()) || []
  } catch (err) {
    // 加载失败保持空数组，错误提示由 request.js 拦截器统一给出
    addresses.value = []
    console.error('收货地址加载失败：', err)
  } finally {
    loading.value = false
  }
}

function openForm(address) {
  editing.value = address
  formVisible.value = true
}

async function markDefault(a) {
  try {
    await setDefaultAddress(a.addressId)
    ElMessage.success('已设为默认地址')
    // 不在这里改本地状态：设默认会连带把原来的默认清掉（后端保证最多一条），
    // 只改被点的那一条会让页面上出现两条「默认地址」
    await loadList()
  } catch (err) {
    console.error('设为默认失败：', err)
    await loadList()
  }
}

async function confirmRemove(a) {
  const isDefault = a.isDefault === 1
  try {
    await ElMessageBox.confirm(
      isDefault
        ? `「${a.consignee} · ${a.fullAddress}」当前是默认地址，删除后会自动把剩下的最新一条设为默认。确定删除吗？`
        : `确定删除「${a.consignee} · ${a.fullAddress}」吗？`,
      '删除收货地址',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }

  try {
    await removeAddress(a.addressId)
    ElMessage.success('地址已删除')
    await loadList()
  } catch (err) {
    console.error('删除收货地址失败：', err)
    // 删除失败也重新拉一次：失败原因可能是「这条已被别的标签页删掉了」，
    // 此时本地列表是过期的，刷新才能让用户看到真实状态
    await loadList()
  }
}
</script>

<style scoped>
.address-page {
  color: var(--el-text-color-primary);
}

.addr-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.addr-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px;
  border: 1px solid var(--rxt-border);
  border-radius: var(--rxt-radius-md);
  background: var(--el-fill-color-blank);
  transition: border-color 0.15s, box-shadow 0.15s;
}
.addr-card:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: var(--rxt-shadow-sm);
}
/* 默认地址加一条左侧色条而不是整块染色：
   整块浅绿底在 3 列网格里会让「默认」这条视觉重量远高于其它两条，
   而它们的操作权限其实完全一样。 */
.addr-card.is-default {
  border-color: var(--el-color-primary-light-5);
  box-shadow: inset 3px 0 0 0 var(--rxt-primary);
}

.ac-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ac-head strong {
  font-size: 15px;
}
.ac-phone {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.ac-tag {
  margin-left: auto;
}
.ac-addr {
  margin: 0;
  flex: 1;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.7;
  word-break: break-all;
}
.ac-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--rxt-border);
}
.ac-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.ac-ops {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: none;
}

/* 业务端只做桌面端，这里仅避免窗口拖窄时三列被压得过窄 */
@media (max-width: 1100px) {
  .addr-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
