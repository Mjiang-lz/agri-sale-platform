import request from './request'

/**
 * 农业知识页接口文件。
 *
 * 页面：frontend-web/src/views/expert/Knowledge.vue
 * 1. getKnowledgeList(category)：GET /api/knowledge/list?category=分类
 * 2. getKnowledgeDetail(id)：GET /api/knowledge/{id}
 *
 * 数据源：后端连接本地 MySQL 的 rxt.t_knowledge。
 * 列表接口返回 Knowledge 数组，页面只展示 auditStatus = "已通过" 的记录。
 */

/** 农业知识页：按分类查询知识列表，分类为空时查询全部。 */
export function getKnowledgeList(category) {
  return request.get('/knowledge/list', {
    params: category && category !== '全部' ? { category } : {}
  })
}

/** 农业知识页：查询知识详情。 */
export function getKnowledgeDetail(id) {
  return request.get(`/knowledge/${id}`)
}

/**
 * 农业知识页：专家新增知识。
 * 新知识由后端以“待审核”状态写入 t_knowledge，审核通过后才会公开。
 */
export function createKnowledge(payload) {
  return request.post('/knowledge', payload)
}

/**
 * 农业知识页：专家编辑自己的知识。
 *
 * 请求体与新增完全一致（KnowledgeCreateDTO，标题 / 分类 / 正文 / 配图）。
 *
 * ⚠️ 后端会**无条件**把该条知识的审核状态置回「待审核」并下架，
 * 哪怕是刚审核通过的也一样 —— 知识口径是「审核通过后才公开」，
 * 改了正文就必须重新过审，否则等于开了一条绕过审核的改稿通道。
 * 所以调用方在提交前应当二次确认。
 *
 * 越权 / 不存在的情况后端分别返回 403 / 404（body 里的 code，HTTP 恒为 200）。
 */
export function updateKnowledge(id, payload) {
  return request.put(`/knowledge/${id}`, payload)
}

/** 农业知识页：查询当前专家自己的全部知识投稿及审核状态。 */
export function getMyKnowledge() {
  return request.get('/knowledge/my')
}

/**
 * 农业知识页：专家删除自己的知识投稿。
 *
 * ⚠️ **不可撤销**，调用方必须先二次确认。
 *
 * 与商品删除不同，这里后端**一律物理删行**，没有「被引用就改判下架」的例外 ——
 * 全库没有外键指向 t_knowledge，知识也不参与下单链路。判定依据：
 *   · t_knowledge 只有 author_id 一条**指出去**的外键（→ t_expert）
 *   · t_audit.target_id / t_comment.target_id 是多态关联，按设计不建外键
 *
 * 副作用（后端已处理，前端提示里要写清）：
 *   · 该知识的评论会被一并清除（否则会留下再也查不到的孤儿评论行）
 *   · 审核流水**保留** —— 审核确实发生过，删掉等于篡改管理员的工作量统计。
 *     代价是后台「知识审核 → 已通过/未通过」列表里那条流水标题会显示成
 *     「（内容已删除）」，这是预期行为，不是故障。
 *
 * 只能删自己发布的，删他人返回 403（body 里的 code，HTTP 恒为 200）；不存在返回 404。
 */
export function deleteKnowledge(id) {
  return request.delete(`/knowledge/${id}`)
}
