import request from './request'

export function getKnowledgeList(category) {
  return request.get('/knowledge/list', {
    params: category && category !== '全部' ? { category } : {}
  })
}

export function getKnowledgeDetail(id) {
  return request.get(`/knowledge/${id}`)
}