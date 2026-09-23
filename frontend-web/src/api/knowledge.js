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
