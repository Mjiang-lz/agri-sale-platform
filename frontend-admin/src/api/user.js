import request from './request'

/**
 * M6 用户管理接口（管理后台 · 用户管理页）。
 *
 * 两条红线（后端已强制，前端也需据此做提示与置灰）：
 * - 不能停用当前登录的管理员自己；
 * - 系统至少保留一名启用状态的管理员。
 * 另外本模块不提供删除用户，只做停用 —— 业务数据都外键指向 t_user，删号会留下孤儿数据。
 *
 * - 用户分页列表：GET /api/admin/users
 * - 启用/停用账号：PUT /api/admin/users/{userId}/status
 * - 调整用户角色：PUT /api/admin/users/{userId}/role
 * - 角色下拉列表：GET /api/admin/roles
 * - 当前登录用户：GET /api/user/profile（用于判断「是不是我自己」）
 */

/** 用户分页列表。支持角色、关键字（姓名或手机号）、账号状态筛选。 */
export function getAdminUserList({ roleId, keyword, status, pageNum = 1, pageSize = 10 } = {}) {
  return request.get('/admin/users', { params: { roleId, keyword, status, pageNum, pageSize } })
}

/** 启用 / 停用账号。status 只能传 1（正常）或 0（停用）。 */
export function updateUserStatus(userId, status) {
  return request.put(`/admin/users/${userId}/status`, { status })
}

/** 调整用户角色。 */
export function updateUserRole(userId, roleId) {
  return request.put(`/admin/users/${userId}/role`, { roleId })
}

/** 角色列表，供筛选条件与「调整角色」弹窗使用；不硬编码角色。 */
export function getRoleList() {
  return request.get('/admin/roles')
}

/** 当前登录用户信息。刷新页面后 store 里的 profile 会丢失，这里重新取一次。 */
export function getCurrentProfile() {
  return request.get('/user/profile')
}
