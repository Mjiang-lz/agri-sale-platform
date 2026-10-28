import request from './request'
import { compressImage } from '@/utils/image-compress'

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
 *
 * ★ 2026-10-12：上传前统一做**浏览器侧压缩**（见 utils/image-compress.js）。
 *   放在这一层而不是三个调用点里，是为了**一处覆盖商品图 / 知识配图 / 头像三条链路**，
 *   将来新增上传场景也自动带上。压缩失败会静默回退原图，不会挡住上传。
 */

/**
 * 各类图片的压缩参数。
 *
 * 尺寸按**实际显示尺寸**给，不按"拍多大"给：
 *   - 商品图/知识配图在列表里约 300px、详情弹窗里约 760px，1600px 长边留足高分屏余量；
 *   - 头像是圆形小图（个人中心约 96px），512px 已经绰绰有余 ——
 *     原来一张 2048×2048 的头像是 **1.45MB**，压完能到几十 KB。
 * 头像质量给高一点：人脸对压缩痕迹比风景更敏感。
 */
const COMPRESS_OPTIONS = {
  PRODUCT: { maxDimension: 1600, quality: 0.82 },
  KNOWLEDGE: { maxDimension: 1600, quality: 0.82 },
  AVATAR: { maxDimension: 512, quality: 0.85 }
}

async function upload(file, bizType) {
  const optimized = await compressImage(file, COMPRESS_OPTIONS[bizType] || {})
  const formData = new FormData()
  formData.append('file', optimized)
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
 * 服务端仍会拒；这里的意义是让用户在选完文件的瞬间就知道「这张图太大」，
 * 而不是等上传走完一圈才报 400。
 *
 * ★ 2026-10-12：上限从 **5MB 放宽到 20MB**。
 *   因为上传前已经做了浏览器侧压缩（见 utils/image-compress.js），
 *   **传给后端的字节数由压缩结果决定，与原始文件大小无关** ——
 *   一张 8MB 的手机原图压完只有 200–400KB，完全能过服务端那个 5MB 线。
 *   继续卡在 5MB 只会白白拦掉手机原图，属于加了压缩之后反而变严的倒退。
 *   20MB 这个值是「兜底防呆」：真正的巨无霸（如未压缩的 TIFF 转存）挡在前面，
 *   免得用户在浏览器里等半天解码再失败。
 *
 * @param {File} file 浏览器选中的文件
 * @param {{maxSizeMB?: number, label?: string}} [opts]
 * @returns {string|null} 出错原因；null 表示可以继续上传
 */
export function checkImageFile(file, opts = {}) {
  const maxSizeMB = opts.maxSizeMB ?? 20
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
