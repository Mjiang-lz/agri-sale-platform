import request from './request'

/**
 * M6 专家资源管理接口（管理后台 · 专家资源管理页）。
 *
 * 与业务端的 `frontend-web/src/api/expert.js` 是不同的两组接口，别混：
 *   · 业务端 `/expert/profile` = 专家维护**自己那一份**，改不了审核状态；
 *   · 本文件 `/admin/experts`   = 管理员跨专家操作，能看全量、能改审核状态。
 *
 * - 专家档案列表：GET /api/admin/experts?auditStatus=&domain=
 * - 编辑专家档案：PUT /api/admin/experts/{expertId}
 *
 * ⚠️ 两个接口的返回契约不一样：列表直接是 `ExpertProfileVO[]`（**不返回
 * MyBatis-Plus Page**、没有 records/total 包层），编辑返回 `null`。
 * 因为入驻专家量级很小（种子数据只有 2 位），服务端明确不做分页。
 * 前端别照抄其他页面的 `data.records` 写法，那会拿到 undefined。
 *
 * ⚠️ `domain` 是**精确匹配**（后端用的是 eq 而不是 like），传「柑橘」查不到
 * 「柑橘种植」。页面上的下拉选项因此取自全量列表，而不是让人自由输入。
 */

/** 专家档案列表。两个筛选条件都可选，不传即返回全部（含待审核）。 */
export function getAdminExpertList({ auditStatus, domain } = {}) {
  return request.get('/admin/experts', {
    params: {
      // 空串会被序列化成 auditStatus= ，后端 required=false 能接住，
      // 但传 undefined 才是「不带这个参数」；统一过滤掉空值更干净
      ...(auditStatus ? { auditStatus } : {}),
      ...(domain ? { domain } : {})
    }
  })
}

/**
 * 编辑专家档案。
 *
 * payload 只放要改的字段：
 *   · expertDomain 非空才生效（空串 = 跳过，不会把领域清空）
 *   · introduction 传空串 = **清空简介**（后端判的是 != null，不是 hasText）
 *   · auditStatus  非空才生效
 *
 * 三个字段全空时后端返回 400「请至少填写一项要修改的内容」，
 * 不会假成功 —— 管理页看到「保存了但没变化」比报错更难查。
 *
 * ⚠️ 改 auditStatus 这里**不写审核留痕**，不计入审核工作量统计；见页面上的说明。
 */
export function updateAdminExpert(expertId, payload = {}) {
  return request.put(`/admin/experts/${expertId}`, payload)
}
