/**
 * 平台专家基础资料。
 *
 * 当前 dev 后端还没有“专家列表”查询接口，因此这里先维护与
 * backend/src/main/resources/db/data.sql 中 t_expert 一致的两条基础数据，
 * 供问答指定专家和预约专家选择使用。
 *
 * 后端新增 GET /api/expert/list 后，只需在当前文件底部增加请求函数，
 * 并在页面 onMounted 中替换 expertOptions 即可。
 */
export const expertOptions = [
  {
    expertId: 1,
    userId: 7,
    realName: '赵国强',
    expertDomain: '种植与植保',
    introduction: '省农科院植保方向研究员，擅长柑橘、水稻等作物的病虫害绿色防控与施肥方案设计，累计服务农户 300 余户。',
    workloadStat: '已回复提问 42 条；受理预约 18 次'
  },
  {
    expertId: 2,
    userId: 8,
    realName: '孙丽华',
    expertDomain: '畜牧与水产',
    introduction: '执业兽医师，深耕家禽养殖与淡水鱼塘管理 15 年，擅长疫病防控、饲料配比与养殖尾水治理。',
    workloadStat: '已回复提问 30 条；受理预约 12 次'
  }
]

/** 根据 expertId 获取专家姓名。 */
export function getExpertName(expertId) {
  if (expertId == null) return '平台分派'
  return expertOptions.find((item) => item.expertId === Number(expertId))?.realName || `专家 #${expertId}`
}
