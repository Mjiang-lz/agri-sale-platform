import { ref, computed } from 'vue'
import { getTradeCategories } from '@/api/trade'

/**
 * 商品品类字典（数据驱动）。
 *
 * ★ 为什么要抽成 composable：品类名单此前在**四个视图里各硬编码一份**
 *   （BizHome 的六个圆入口、ProductList / DemandList 的筛选下拉、
 *   DemandFormDialog 的发布表单、MyProducts 的管理表），
 *   五处字面量各写各的。新增一个品类要改五个地方，漏一处就出现
 *   「库里能搜到、下拉里选不到」的孤儿品类 —— 这正是需求 7 要根治的问题。
 *
 * ★ 模块级单例（不是组件级 state）：同一时刻多个视图共用一份，
 *   进首页拿过之后再去商品列表不必再请求一次。首次加载后缓存，
 *   失败时回落静态兜底，绝不让「品类为空」出现在界面上。
 */

/** 兜底名单：接口不可用时（后端未升级 / 网络断）至少还能筛，不会整块空白 */
const FALLBACK = [
  { category: '蔬菜', productCount: 0 },
  { category: '水果', productCount: 0 },
  { category: '粮油', productCount: 0 },
  { category: '干货', productCount: 0 },
  { category: '水产', productCount: 0 },
  { category: '畜禽产品', productCount: 0 }
]

const list = ref([])
const loading = ref(false)
const loaded = ref(false)
const usingFallback = ref(false)

async function load(force = false) {
  // 正在加载时不重复发起：首页与列表页会同时 onMounted
  if (loading.value) return list.value
  if (loaded.value && !force) return list.value
  loading.value = true
  try {
    const rows = await getTradeCategories()
    const clean = (Array.isArray(rows) ? rows : [])
      .filter((r) => r && typeof r.category === 'string' && r.category.trim())
      .map((r) => ({ category: r.category.trim(), productCount: Number(r.productCount) || 0 }))
    if (clean.length) {
      list.value = clean
      usingFallback.value = false
    } else {
      // 接口通但库里一个在架商品都没有 —— 这时用兜底名单，
      // 否则首页六个圆入口会整块消失，比「显示六个空品类」更糟
      list.value = FALLBACK
      usingFallback.value = true
    }
    loaded.value = true
  } catch {
    // 失败**不弹提示**：品类是辅助筛选项，不该用一个红色报错框打断用户。
    // 静默回落兜底名单即可 —— 筛选功能弱一点，好过整个模块报错。
    list.value = FALLBACK
    usingFallback.value = true
    loaded.value = true
  } finally {
    loading.value = false
  }
  return list.value
}

export function useCategories() {
  return {
    categories: list,
    loading,
    usingFallback,
    /** 纯名称数组，直接喂给 el-option 的 v-for */
    categoryNames: computed(() => list.value.map((c) => c.category)),
    load
  }
}
