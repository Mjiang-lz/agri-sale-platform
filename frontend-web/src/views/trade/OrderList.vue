<template>
  <div class="order-list-page">
    <div class="page-head">
      <div>
        <h2>我的订单</h2>
        <p>查看订单状态流转，支持支付、确认收货与取消</p>
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
        class="error-alert"
      >
        <template #default>
          <div class="error-desc">无法连接后端服务，请确认后端已启动（默认 8080 端口）后重试</div>
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
              <div class="mini-thumb" :style="{ background: productFallbackBg({ productId: row.productId }) }">
                <span class="mini-fallback">{{ productFallbackText({ productName: row.productName }) }}</span>
                <img :src="productImage({ productId: row.productId, productName: row.productName, imageUrl: row.imageUrl })" :alt="row.productName" @error="onImgError" />
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
        <el-table-column label="操作" width="215" fixed="right">
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
              <!--
                评价入口。两个判据缺一不可：
                · orderStatus === '已完成' —— 后端只认「已确认收货」的订单可评（见
                  CommentServiceImpl#PURCHASED_ORDER_STATUSES），其他状态给按钮
                  等于让用户去撞一次 403
                · !commentStatus —— commentStatus 由后端按「待审核 / 已通过」回填，
                  有值时说明评过了，不再给第二次机会（评论被驳回的不在返回值里，
                  所以仍会显示「评价」，允许重新提交）
              -->
              <el-button
                v-if="row.orderStatus === '已完成' && !row.commentStatus"
                link
                type="primary"
                @click="openComment(row)"
              >评价</el-button>
              <el-button v-else-if="row.commentStatus === '待审核'" link disabled>审核中</el-button>
              <el-button v-else-if="row.commentStatus === '已通过'" link disabled>已评价</el-button>
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
          <!--
            收货信息：农户按它发货，买家核对自己填的对不对，所以买卖双方都要看得到。
            ⚠️ 2026-10-10 之前创建的订单这几列为 NULL（当时订单表还没有这些字段），
            这里必须按「未填写收货信息」整段跳过，不能渲染成空行，
            否则一批历史订单详情里会多出三行空白，看着像加载失败。
          -->
          <el-descriptions-item v-if="currentOrder.receiverAddress" label="收货人">
            {{ currentOrder.consignee || '—' }}
            <span v-if="currentOrder.consigneePhone" class="recv-phone">{{ currentOrder.consigneePhone }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="currentOrder.receiverAddress" label="收货地址">
            {{ currentOrder.receiverAddress }}
          </el-descriptions-item>
          <el-descriptions-item v-else label="收货信息">
            <span class="recv-missing">该订单创建于收货信息功能上线前，未记录收货地址</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="currentOrder.orderRemark" label="买家备注">
            {{ currentOrder.orderRemark }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <!--
      发表评价弹窗。用 el-dialog 而不是 el-drawer：这是一次性的短表单，
      与「订单详情」（要展开长内容、和列表对照着看）性质不同。
      ⚠️ el-dialog 默认 teleport 到 body，scoped 样式够不着，所以下面的
      .comment-* 样式写在文件末尾的**非 scoped** 块里。
    -->
    <el-dialog v-model="commentVisible" title="发表商品评价" width="520px" class="comment-dialog">
      <div v-if="commentTarget" class="comment-target">
        <span class="comment-target-name">{{ commentTarget.productName || `商品 #${commentTarget.productId}` }}</span>
        <span class="comment-target-no">订单 {{ commentTarget.orderNo }}</span>
      </div>
      <el-input
        v-model="commentContent"
        type="textarea"
        :rows="5"
        maxlength="500"
        show-word-limit
        placeholder="说说这次购买的感受，比如品质、品相、包装与发货速度"
      />
      <div class="comment-hint">评价提交后需经平台审核，审核通过后才会展示在商品详情页</div>
      <template #footer>
        <el-button @click="commentVisible = false">取消</el-button>
        <el-button type="primary" :loading="commentSubmitting" @click="submitComment">提交评价</el-button>
      </template>
    </el-dialog>
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
import { publishProductComment } from '@/api/comment'
import { formatDateTime } from '@/utils/text'
import { productImage, productFallbackBg, productFallbackText } from '@/utils/imagery'

/**
 * 页面用途：我的订单（可切换「我买的 / 我卖的」两个视角）。
 * 接口文件：frontend-web/src/api/trade.js、frontend-web/src/api/comment.js
 * 主要接口：
 *   GET  /api/trade/order/list    我买的（按状态筛选 + 分页）
 *   GET  /api/trade/order/sold    我卖的（农户视角，发货入口）
 *   GET  /api/trade/order/{id}    订单详情
 *   POST /api/trade/order/{id}/pay|cancel|receive   买家可操作
 *   POST /api/trade/order/{id}/deliver               农户可操作
 *   POST /api/comment/product     发表商品评价（「我买的」列表 → 评价按钮）
 *
 * ⚠️ 路径是单数 order —— 旧注释里的 /api/trade/orders 在后端不存在。
 *
 * 后端返回 MyBatis-Plus Page，records 元素为 OrderVO：
 * orderId、orderNo、buyerId、sellerId、productId、productName、imageUrl、
 * unit、quantity、unitPrice、orderAmount、consignee、consigneePhone、
 * receiverAddress、orderRemark、orderStatus、commentStatus、
 * payTime、deliverTime、receiveTime、createTime。
 *
 * 收货信息（consignee / consigneePhone / receiverAddress / orderRemark）是
 * 下单时的**快照**，不是指向地址簿的外键 —— 用户后来改了或删了地址都不影响这里。
 * 2026-10-10 之前创建的订单这几列为 NULL，详情里按「未记录」展示。
 *
 * 订单状态取值：待支付 / 待发货 / 待收货 / 已完成 / 已取消（后端是中文枚举，
 * 不是数字），因此状态筛选直接把中文串传给后端的 orderStatus 参数。
 *
 * commentStatus 是**买家的**评论状态，取值 待审核 / 已通过；null 表示还没评过
 * （含评论被驳回，允许重新提交）。只在「我买的」列表有值。前端据此决定显示
 * 「评价」按钮还是禁用的「审核中 / 已评价」，但**裁定权始终在后端** ——
 * 这里只决定要不要给入口，不能替代后端那道「已完成订单方可评价」的校验。
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

/* 发表评价弹窗：目标订单 + 正文 + 提交中标记（防连点） */
const commentVisible = ref(false)
const commentTarget = ref(null)
const commentContent = ref('')
const commentSubmitting = ref(false)

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

/** 图片 404 时隐藏 img，露出下层首字 */
function onImgError(e) { e.target.style.opacity = '0' }

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

/* ------------------------------------------------------------------ *
 * 发表评价
 * ------------------------------------------------------------------ */

function openComment(row) {
  commentTarget.value = row
  commentContent.value = ''
  commentVisible.value = true
}

async function submitComment() {
  // 后端也会校验（trim 后 ≥2 字），这里先拦一道：既省一次往返，
  // 也避免让用户看到后端那句「评论内容至少 2 个字」时误以为是系统故障
  const content = commentContent.value.trim()
  if (content.length < 2) {
    ElMessage.warning('评价内容至少 2 个字')
    return
  }

  commentSubmitting.value = true
  try {
    await publishProductComment({ productId: commentTarget.value.productId, content })
    // 新评价一律「待审核」，不会立刻出现在商品详情里，所以不能提示「评价成功」
    ElMessage.success('评价已提交，审核通过后展示')
    commentVisible.value = false
  } catch (err) {
    // request.js 已统一弹过错误提示（例如「你已评价过该商品，请等待审核结果」），
    // 不再补弹，只记日志
    console.error('发表评价失败：', err)
  } finally {
    commentSubmitting.value = false
    // 成败都重新拉列表：成功后 commentStatus 应变成「待审核」；
    // 失败也可能是「其实早就评过了」，两种情况都得让页面回到后端真实状态
    await loadOrders()
  }
}

onMounted(loadOrders)
</script>

<style scoped>
.order-list-page { color: var(--el-text-color-primary); }


.table-card { border-radius: 14px; }

.goods-cell { display: flex; align-items: center; gap: 10px; min-width: 0; }
.mini-thumb {
  position: relative;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(20, 48, 43, 0.35);
  font-size: 14px;
  overflow: hidden;
}
.mini-thumb .mini-fallback { position: absolute; z-index: 0; }
.mini-thumb img { position: relative; z-index: 1; width: 100%; height: 100%; object-fit: cover; }
.goods-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.amount { color: #f56c6c; font-size: 15px; }


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

/* 收货人后面的手机号：与姓名同一行，字号小一档做区分 */
.recv-phone { margin-left: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
/* 历史订单（收货信息功能上线前创建）没有地址时的说明文案 */
.recv-missing { color: var(--el-text-color-secondary); font-size: 12px; }

.error-alert { margin-bottom: 14px; }
.error-alert :deep(.el-alert__content) { width: 100%; }
.error-actions { margin-top: 8px; }
</style>

<!--
  el-dialog 默认 teleport 到 body，上面的 scoped 选择器带 data-v 属性、够不着
  它内部，所以评价弹窗的样式只能写在非 scoped 块里。用 .comment-dialog 前缀
  收窄作用域，避免影响全局其他弹窗。
-->
<style>
.comment-dialog .comment-target {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
}
.comment-dialog .comment-target-name {
  font-weight: 600;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.comment-dialog .comment-target-no {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.comment-dialog .comment-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
