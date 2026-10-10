import request from './request'

/**
 * 公共文件上传接口。
 *
 * 对应后端：POST /api/common/upload
 * 表单字段：file + bizType。bizType 合法取值只有 PRODUCT / KNOWLEDGE / AVATAR
 * （见后端 FileBizType），它同时决定服务端把文件写到哪个目录。
 *
 * 为什么要按 bizType 分而不是只传文件：
 *   - 服务端用白名单卡住写入位置，调用方无法把文件写到约定之外的目录；
 *   - 商品图/知识配图/头像的容量与生命周期不同，分开才好排查与清理。
 *
 * 返回值（后端 UploadResultVO）：{ url, objectKey, size, storage }，
 * 其中 **url 是可直接放进 <img src> 的地址**（本地模式形如 /api/files/avatar/2026/10/xxx.jpg）。
 */

function upload(file, bizType) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('bizType', bizType)
  return request.post('/common/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 农业知识配图（bizType=KNOWLEDGE） */
export function uploadKnowledgeImage(file) {
  return upload(file, 'KNOWLEDGE')
}

/** 用户头像（bizType=AVATAR） */
export function uploadAvatar(file) {
  return upload(file, 'AVATAR')
}

/** 商品货源主图（bizType=PRODUCT） */
export function uploadProductImage(file) {
  return upload(file, 'PRODUCT')
}

/**
 * 上传前的本地校验。**只是提前给用户一个即时反馈，不是安全边界。**
 *
 * 真正的校验在服务端 ImageFileValidator 里，且是按**文件头（magic number）**判类型的，
 * 不认文件名后缀与 Content-Type —— 那两者都能伪造。所以这里即便被绕过，
 * 服务端仍会拒；这里的意义是让用户在选完文件的瞬间就知道「大到 6MB 了」，
 * 而不是等上传走完一圈才报 400。
 *
 * @param {File} file 浏览器选中的文件
 * @param {{maxSizeMB?: number, label?: string}} [opts]
 * @returns {string|null} 出错原因；null 表示可以继续上传
 */
export function checkImageFile(file, opts = {}) {
  const maxSizeMB = opts.maxSizeMB ?? 5
  const label = opts.label ?? '图片'
  // 与后端 ImageFileValidator.SUPPORTED_LABEL 保持一致（jpg/jpeg/png/gif/webp）
  const allowed = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
  if (!allowed.includes(file.type)) {
    return `${label}仅支持 JPG、PNG、GIF、WEBP 格式`
  }
  if (file.size / 1024 / 1024 > maxSizeMB) {
    return `${label}不能超过 ${maxSizeMB}MB`
  }
  return null
}
