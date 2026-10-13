import { ref } from 'vue'
import { getExperts } from '@/api/expert'

/**
 * 平台专家列表（数据驱动）。
 *
 * ★ 2026-10-13 从「写死两条」改成接口驱动（需求 6、9、10）。
 *
 * 原来这里硬编码两位专家，还留着一句「当前 dev 后端还没有专家列表接口」——
 * 但那个接口早就存在（GET /expert/experts），只是没人调用。后果是：
 *   · 后端加了专家，这个文件不知道，页面永远只有两张卡；
 *   · 列表卡上的 <el-avatar> 没接 :src，avatarUrl 明明后端已经下发，
 *     却始终显示姓名首字（这就是需求 10「专家页头像要正常显示」的真正病因，
 *     与 OBS 桶是否公共读毫无关系）；
 *   · 同一个 expertId → 姓名的映射被复制到四个文件里，各自漂移。
 *
 * 现在统一从接口取，四处引用（问答指定专家、预约选专家、专家回答面板、
 * 预约工作台）共用这一份。
 */

/** 兜底名单：接口不可用时页面不至于空白。仍是写死的，但只作为降级用。 */
const FALLBACK = [
  {
    expertId: 1,
    userId: 7,
    realName: '赵国强',
    expertDomain: '种植与植保',
    introduction: '省农科院植保方向研究员，擅长柑橘、水稻等作物的病虫害绿色防控与施肥方案设计。',
    workloadStat: ''
  },
  {
    expertId: 2,
    userId: 8,
    realName: '孙丽华',
    expertDomain: '畜牧与水产',
    introduction: '执业兽医师，深耕家禽养殖与淡水鱼塘管理 15 年，擅长疫病防控、饲料配比与养殖尾水治理。',
    workloadStat: ''
  }
]

const list = ref([])
const loading = ref(false)
const loaded = ref(false)

async function load(force = false) {
  if (loading.value) return list.value
  if (loaded.value && !force) return list.value
  loading.value = true
  try {
    const rows = await getExperts()
    // 后端保证 realName 非 null（见 ExpertServiceImpl#listExperts 的注释），
    // 这里再兜一层：拿不到 id 的行渲染出来是张空白卡，不如丢掉
    const clean = (Array.isArray(rows) ? rows : []).filter((e) => e && e.expertId != null)
    list.value = clean.length ? clean : FALLBACK
    loaded.value = true
  } catch {
    // 静默回落：专家列表是辅助信息，不该用报错框打断用户
    list.value = FALLBACK
    loaded.value = true
  } finally {
    loading.value = false
  }
  return list.value
}

/**
 * 由 expertId 查姓名。
 *
 * ★ 必须读**响应式**的 list，不能拷一份 FALLBACK 出去 ——
 *   接口返回前调用方拿到的会是永远不更新的死数据，
 *   表现为「接口明明有第三位专家，某些地方却查不到名字」。
 */
function getExpertName(expertId) {
  if (expertId == null) return '平台分派'
  const hit = list.value.find((item) => item.expertId === Number(expertId))
  return hit?.realName || `专家 #${expertId}`
}

export function useExperts() {
  return { expertOptions: list, loading, load, getExpertName }
}

/**
 * 兼容旧写法：直接 import { expertOptions, getExpertName } 的四处
 * 拿到的就是这里的响应式数组与函数，不必改成 composable 形式也能用。
 * ★ 它们仍需各自 onMounted 调一次 load()，否则停留在兜底名单。
 */
export { getExpertName }
export const expertOptions = list
