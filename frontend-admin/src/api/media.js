import request from './request'

/**
 * M6 图片资源管理接口（管理后台 · 图片资源管理页）。
 *
 * 解决三个此前没人管得住的隐患：
 *   1. 传到一半中断的孤儿文件没人清；
 *   2. 误传的敏感图（身份证、合同截图）删不掉；
 *   3. 「这张图删了会不会把别人正在用的商品图删掉」没人答得上来。
 * 第 3 点由列表接口的 `referenced` / `references` 字段回答 ——
 * 前端据此把「删除」按钮置灰，而不是让管理员试一次再说。
 *
 * - 图片列表：GET    /api/admin/media?prefix=&pageNum=&pageSize=
 * - 删除图片：DELETE /api/admin/media?objectKey=
 *
 * ⚠️ 删除**用 query 参数而不是路径变量**：对象键里含 `/`
 * （形如 `product/2026/10/x.png`），放进路径会被 Spring 当成多段路径。
 * 所以别把 `deleteMedia(key)` 改写成 `request.delete(`/admin/media/${key}`)`。
 *
 * ⚠️ prefix 是「对象键前缀」而不是「业务分类」：库里没有分类字段，
 * 前缀就是目录名（`product/`、`avatar/`、`knowledge/`…）。
 * 取值不固定 —— 本地上传和 OBS 上传可能落在不同前缀下，所以页面上的
 * 筛选项是**从前一页返回的数据里现算的**，不硬编码。
 */

/**
 * 图片列表（分页）。
 *
 * 返回 MyBatis-Plus Page：{ current, pages, records, total }，
 * records 元素为 MediaItemVO：
 * `objectKey` / `url` / `size` / `lastModified` / `referenced` / `references`。
 *
 * ⚠️ 服务端每次都**全量列对象再在内存里切页**（对象量级小，跨本地/OBS
 * 两种实现难统一分页），所以 pageSize 调大不会明显变快，别指望靠分页减负载。
 */
export function getMediaList({ prefix, pageNum = 1, pageSize = 12 } = {}) {
  return request.get('/admin/media', {
    params: {
      ...(prefix ? { prefix } : {}),
      pageNum,
      pageSize
    }
  })
}

/**
 * 删除图片对象。
 *
 * 被业务数据引用时后端返回 400，message 形如
 * 「该图片仍被业务数据引用，不能删除：商品 #24「赣南脐橙」主图、用户 #2「李官杏」 头像」
 * —— 错误提示由 request.js 统一弹出，页面不必再包一层 try/catch 提示。
 *
 * ⚠️ 删除**不可撤销**（存储层没有回收站），调用方必须先二次确认。
 */
export function deleteMedia(objectKey) {
  return request.delete('/admin/media', { params: { objectKey } })
}
