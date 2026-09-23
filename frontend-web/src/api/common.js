import request from './request'

/**
 * 公共文件上传接口。
 *
 * 对应后端：POST /api/common/upload
 * 表单字段：file + bizType。
 * 知识配图使用 bizType=KNOWLEDGE。
 */
export function uploadKnowledgeImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('bizType', 'KNOWLEDGE')
  return request.post('/common/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
