import request from './request'

/**
 * 商品评论接口文件。
 *
 * 对应页面：
 * - frontend-web/src/views/trade/OrderList.vue    我的订单 —— 发表入口在这里
 * - frontend-web/src/views/trade/ProductDetail.vue 商品详情 —— 只读展示，
 *   数据来自商品详情接口内嵌的 comments 字段，不走本文件的列表接口
 *
 * 后端基础路径 /api/comment（CommentController）。两个接口都**要求登录**：
 * 该控制器未加入免登录白名单，与「商品详情内嵌评论」是同一份数据的两种读法。
 *
 * ⚠️ 三个容易踩的点：
 *  1. 「已购」校验的裁定权在后端，口径是**订单状态为「已完成」（= 已确认收货）**。
 *     前端只能按 `orderStatus === '已完成'` 决定要不要给入口，不能自己当裁判。
 *  2. 新评论一律「待审核」，**不会立刻出现在商品详情里**，所以提交成功的提示
 *     必须是「已提交，审核通过后展示」，不能写「评价成功」。
 *  3. 同一用户对同一商品只允许存在一条有效评论，重复提交会拿到
 *     code 400「你已评价过该商品，请等待审核结果」；评论被驳回后可以重新提交。
 */

/**
 * 发表商品评论。请求体对应 CommentPublishDTO：{ productId, content }。
 *
 * 评论人身份由后端从登录态取，**不要**传 userId —— 传了也会被忽略。
 * 正文后端要求 trim 后 2~500 字。
 *
 * @returns {Promise<object>} CommentVO：{ commentId, userId, content, auditStatus,
 *                            createTime, userName, avatarUrl }
 */
export function publishProductComment(payload) {
  return request.post('/comment/product', payload)
}

/**
 * 分页查询某商品下**已过审**的评论，按时间倒序。
 * 返回 MyBatis-Plus Page：{ records, total, current, pages }。
 */
export function getProductComments(productId, pageNum = 1, pageSize = 10) {
  return request.get('/comment/product/list', { params: { productId, pageNum, pageSize } })
}
