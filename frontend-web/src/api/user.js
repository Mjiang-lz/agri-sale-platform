import request from './request'

/**
 * 个人中心接口封装。
 *
 * 对应后端 UserController（基础路径 /api/user）：
 *   GET /api/user/profile          查询当前登录用户资料（农户角色附带经营档案）
 *   PUT /api/user/profile          更新昵称、头像
 *   PUT /api/user/farmer-profile   维护农户经营档案（仅农户角色）
 *
 * 用户主键一律由后端从登录态（UserContext）取，前端不传 userId，
 * 因此这里没有 id 类入参，也拿不到别人的资料。
 */

/** 查询当前登录用户的个人资料；农户角色会带回 farmerProfile，未建档或非农户时为 null。 */
export function getProfile() {
  return request.get('/user/profile')
}

/**
 * 更新当前登录用户的基本资料。
 *
 * 后端约定：字段为 null / undefined 表示「本次不修改」，传空串才是「清空」。
 * 因此调用方应当只提交真正改动过的字段，而不是把整个表单原样回传。
 *
 * @param {{ realName?: string, avatarUrl?: string }} payload
 */
export function updateProfile(payload) {
  return request.put('/user/profile', payload)
}

/**
 * 维护当前登录用户的农户经营档案（仅农户角色，其余角色后端返回 403）。
 * 后端语义为「不存在则建档、存在则更新」，首次维护即自动建档。
 *
 * @param {{ mainProduct?: string, scaleDesc?: string, creditRecord?: string }} payload
 */
export function updateFarmerProfile(payload) {
  return request.put('/user/farmer-profile', payload)
}
