<template>
  <div class="order-list-page">
    <div class="page-head">
      <div>
        <h2>我的订单</h2>
        <p>查看订单状态流转，支持支付、确认收货与取消。</p>
      </div>
      <div class="head-actions">
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="buy">我买的</el-radio-button>
          <el-radio-button value="sell">我卖的</el-radio-button>
        </el-radio-group>
        <el-button :loading="loading" @click="loadOrders">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <template #header>
        <el-tabs v-model="activeStatus" @tab-change="handleStatusChange">
          <el-tab-pane label="全部" name="全部" />
          <el-tab-pane label="待支付" name="待支付" />
          <el-tab-pane label="待发货" name="待发货" />
          <el-tab-pane label="待收货" name="待收货" />
          <el-tab-pane label="已完成" name="已完成" />
          <el-tab-pane label="已取消" name="已取消" />
        </el-tabs>
      </template>

      <!-- 接口不可用：行内错误态 + 重试。toast 已由 request.js 统一弹过，不重复弹 -->
      <el-alert
        v-if="errorState"
        type="error"
        show-icon
        :closable="false"
        title="订单加载失败"
        description="无法连接后端服务，请确认后端已启动（默认 8080 端口）后重试。"
        class="error-alert"
      >
        <template #default>
          <div class="error-actions">
            <el-button type="danger" size="small" @click="loadOrders">重新加载</el-button>
          </div>
        </template>
      </el-alert>

      <el-table v-loading="loading" :data="orders" row-key="orderId">
        <el-table-column prop="orderNo" label="订单编号" min-width="165" />
        <el-table-column label="商品" min-width="180">
          <template #default="{ row }">
            <div class="goods-cell">
              <div class="mini-thumb">
                <img v-if="row.imageUrl" :src="row.imageUrl" :alt="row.productName" />
                <span v-else>{{ (row.productName || '农产').slice(0, 2) }}</span>
              </div>
              <span class="goods-name">{{ row.productName || `商品 #${row.productId}` }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="单价 / 数量" min-width="140">
          <template #default="{ row }">
            ¥{{ formatPrice(row.unitPrice) }} × {{ row.quantity }} {{ row.unit || '件' }}
          </template>
        </el-table-column>
        <el-table-column label="订单金额" min-width="120" align="right">
          <template #default="{ row }">
            <strong class="amount">¥{{ formatPrice(row.orderAmount) }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="交易对象" min-width="110">
          <template #default="{ row }">
            {{ viewMode === 'buy' ? `农户 #${row.sellerId}` : `买家 #${row.buyerId}` }}
          </template>
        </el-table-column>
        <el-table-column label="下单时间" min-width="155">
          <template #default="{ row }">{{ formatDateTime(row.createTime) || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="105">
          <template #default="{ row }">
            <el-tag :type="statusType(row.orderStatus)">{{ row.orderStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="185" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <template v-if="viewMode === 'buy'">
              <el-button v-if="row.orderStatus === '待支付'" link type="primary" @click="doPay(row)">支付</el-button>
              <el-button v-if="row.orderStatus === '待收货'" link type="primary" @click="doReceive(row)">确认收货</el-button>
              <el-button
                v-if="row.orderStatus === '待支付' || row.orderStatus === '待发货'"
                link
                type="danger"
                @click="doCancel(row)"
              >取消</el-button>
            </template>
            <el-button v-else-if="row.orderStatus === '待发货'" link type="primary" @click="doDeliver(row)">发货</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="activeStatus === '全部' ? '暂无订单' : `暂无${activeStatus}的订单`">
            <el-button v-if="activeStatus !== '全部'" type="primary" @click="activeStatus = '全部'">查看全部订单</el-button>
          </el-empty>
        </template>
      </el-table>

      <div v-if="orders.length" class="pagination">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="loadOrders"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="订单详情" size="min(560px, 96%)">
      <template v-if="currentOrder">
        <div class="detail-head">
          <div>
            <span class="detail-no">{{ currentOrder.orderNo }}</span>
            <h3>{{ currentOrder.productName || '订单详情' }}</h3>
          </div>
          <el-tag :type="statusType(currentOrder.orderStatus)">{{ currentOrder.orderStatus }}</el-tag>
        </div>

        <el-descriptions :column="1" border>
          <el-descriptions-item label="单价">¥{{ formatPrice(currentOrder.unitPrice) }} / {{ currentOrder.unit || '件' }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{ currentOrder.quantity }} {{ currentOrder.unit || '件' }}</el-descriptions-item>
          <el-descriptions-item label="订单金额">
            <strong class="amount">¥{{ formatPrice(currentOrder.orderAmount) }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="买家">用户 #{{ currentOrder.buyerId }}</el-descriptions-item>
          <el-descriptions-item label="农户">用户 #{{ currentOrder.sellerId }}</el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ formatDateTime(currentOrder.createTime) || '—' }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ formatDateTime(currentOrder.payTime) || '未支付' }}</el-descriptions-item>
          <el-descriptions-item label="发货时间">{{ formatDateTime(currentOrder.deliverTime) || '未发货' }}</el-descriptions-item>
          <el-descriptions-item label="收货时间">{{ formatDateTime(currentOrder.receiveTime) || '未收货' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelOrder,
  deliverOrder,
  getOrderDetail,
  getOrderList,
  getSoldOrderList,
  payOrder,
  receiveOrder
} from '@/api/trade'
import { formatDateTime } from '@/utils/text'

/**
 * 页面用途：我的订单（可切换「我买的 / 我卖的」两个视角）。
 * 接口文件：frontend-web/src/api/trade.js
 * 主要接口：
 *   GET  /api/trade/order/list    我买的（按状态筛选 + 分页）
 *   GET  /api/trade/order/sold    我卖的（农户视角，发货入口）
 *   GET  /api/trade/order/{id}    订单详情
 *   POST /api/trade/order/{id}/pay|cancel|receive   买家可操作
 *   POST /api/trade/order/{id}/deliver               农户可操作
 *
 * ⚠️ 路径是单数 order —— 旧注释里的 /api/trade/orders 在后端不存在。
 *
 * 后端返回 MyBatis-Plus Page，records 元素为 OrderVO：
 * orderId、orderNo、buyerId、sellerId、productId、productName、imageUrl、
 * unit、quantity、unitPrice、orderAmount、orderStatus、
 * payTime、deliverTime、receiveTime、createTime。
 *
 * 订单状态取值：待支付 / 待发货 / 待收货 / 已完成 / 已取消（后端是中文枚举，
 * 不是数字），因此状态筛选直接把中文串传给后端的 orderStatus 参数。
 *
 * 失败处理：流转失败一律弹提示并重新拉取列表，保证页面状态与后端一致，
 * 不做乐观更新 —— 库存与状态都由后端裁定，前端猜错会造成状态漂移。
 */
const loading = ref(false)
const orders = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const activeStatus = ref('全部')
const viewMode = ref('buy')
/** 接口失败标记：与「暂无订单」区分开 */
const errorState = ref(false)

const detailVisible = ref(false)
const currentOrder = ref(null)

const STATUS_TYPE = {
  待支付: 'warning',
  待发货: 'primary',
  待收货: 'success',
  已完成: 'info',
  已取消: 'danger'
}

function statusType(status) {
  return STATUS_TYPE[status] || 'info'
}

function formatPrice(value) {
  const n = Number(value)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

async function loadOrders() {
  loading.value = true
  errorState.value = false
  const params = {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    orderStatus: activeStatus.value === '全部' ? '' : activeStatus.value
  }
  try {
    const data = viewMode.value === 'buy'
      ? await getOrderList(params)
      : await getSoldOrderList(params)
    const records = data?.records || []
    orders.value = records
    total.value = Number(data?.total ?? records.length)
  } catch (err) {
    orders.value = []
    total.value = 0
    errorState.value = true
    console.error('订单加载失败：', err)
  } finally {
    loading.value = false
  }
}

function handleStatusChange() {
  pageNum.value = 1
  loadOrders()
}

function handleSizeChange() {
  pageNum.value = 1
  loadOrders()
}

// 切换「我买的 / 我卖的」要回到第一页，否则可能停在越界页码上看到空白
watch(viewMode, () => {
  pageNum.value = 1
  loadOrders()
})

async function openDetail(row) {
  currentOrder.value = row
  detailVisible.value = true
  try {
    const detail = await getOrderDetail(row.orderId)
    if (detail) currentOrder.value = { ...row, ...detail }
  } catch (err) {
    console.warn('订单详情获取失败，回退到列表数据：', err)
  }
}

/**
 * 状态流转统一入口：失败必弹提示，且无论成败都重新拉列表，
 * 让页面显示的始终是后端真实状态。
 */
async function runAction(row, action, successText, confirmText) {
  if (confirmText) {
    try {
      await ElMessageBox.confirm(confirmText, '操作确认', {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消'
      })
    } catch {
      return
    }
  }
  try {
    await action(row.orderId)
    ElMessage.success(successText)
  } catch (err) {
    ElMessage.error(`${successText.replace('成功', '')}失败，请稍后重试`)
    console.error('订单操作失败：', err)
  } finally {
    await loadOrders()
  }
}

function doPay(row) {
  return runAction(row, payOrder, '支付成功', `确认支付订单 ${row.orderNo}？`)
}

function doReceive(row) {
  return runAction(row, receiveOrder, '已确认收货', `确认已收到订单 ${row.orderNo} 的货物？`)
}

function doDeliver(row) {
  return runAction(row, deliverOrder, '发货成功', `确认订单 ${row.orderNo} 已发货？`)
}

function doCancel(row) {
  return runAction(
    row,
    cancelOrder,
    '订单已取消',
    `取消后库存将回滚，确定取消订单 ${row.orderNo}？`
  )
}

onMounted(loadOrders)
</script>

<style scoped>
.order-list-page { color: var(--el-text-color-primary); }

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
  margin-bottom: 18px;
}
.page-head h2 { margin: 0; font-size: 24px; }
.page-head p  { margin: 8px 0 0; color: var(--el-text-color-secondary); line-height: 1.7; }
.head-actions { display: flex; align-items: center; gap: 12px; }

.table-card { border-radius: 14px; }

.goods-cell { display: flex; align-items: center; gap: 10px; min-width: 0; }
.mini-thumb {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #2d7a4c, #73b586);
  color: #fff;
  font-size: 14px;
  overflow: hidden;
}
.mini-thumb img { width: 100%; height: 100%; object-fit: cover; }
.goods-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.amount { color: #f56c6c; font-size: 15px; }

.pagination {
  display: flex;
  justify-content: center;
  padding-top: 14px;
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.detail-no {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.detail-head h3 { margin: 6px 0 0; font-size: 18px; }

.error-alert { margin-bottom: 14px; }
.error-alert :deep(.el-alert__content) { width: 100%; }
.error-actions { margin-top: 8px; }
</style>
