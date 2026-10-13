/**
 * 商品配图策略。
 *
 * 现状：平台在售的 24 件货源**都没有 imageUrl**（农户还没传实拍图）。
 * 直接按顺序拿素材图顶上去，会出现「老母鸡配苹果图」这种硬伤，
 * 所以这里按**商品名 → 品类**两级匹配挑同类目实拍图：
 *
 *   1. NAME_PHOTO   —— 平台现有 24 件货源逐一精确对齐（最准）
 *   2. KEYWORD_PHOTO—— 名字里命中关键词的兜底（后端上新货时仍能配到合适的图）
 *   3. CATEGORY_PHOTOS —— 只认得品类的最后兜底
 *   4. 全部落空返回 null —— 由调用方渲染中性占位块，宁可不放图也不放错图
 *
 * 注意这是**配图素材**而不是数据：商品的名称、价格、产地、库存全部来自接口，
 * 这里只解决「没实拍图时用哪张同类目照片」的问题，不产生任何商品信息。
 * 后端补上 imageUrl 后这些兜底会自动失效（调用方先试 resolveImage(row.imageUrl)）。
 */

/** 商品名归一化：去空白，避免后端 "菜籽油 5L" / "菜籽油5L" 这类空格差异漏配 */
const normalize = (name: string) => name.replace(/\s+/g, '')

/** ① 平台现有货源逐一精确配图（键为去掉空格后的商品名） */
const NAME_PHOTO: Record<string, string> = {
  // 水果
  '赣南脐橙5kg装': '/products/p03.jpg',
  '砂糖橘10斤装': '/products/p13.jpg',
  // p12 是挂在树上的青皮柚（偏暗绿），不像蜜柚；改用 p30（半个红心蜜柚实拍）
  '蜜柚单果约2.5kg': '/products/p30.jpg',
  '猕猴桃5斤装': '/products/p11.jpg',
  '脐橙次果特惠': '/products/p09.jpg',
  // 蔬菜
  '莲藕': '/products/p23.jpg',
  '土豆': '/products/p04.jpg',
  '大白菜': '/products/p25.jpg',
  '露天西红柿': '/products/p02.jpg',
  '大棚黄瓜': '/products/p26.jpg',
  '农家自种小番茄': '/products/p05.jpg',
  // 粮油
  // p24 是橄榄油静物（画面里有橄榄果与橄榄枝），与菜籽油语义不符，改用 p29（散装黄色食用油实拍）
  '菜籽油5L': '/products/p29.jpg',
  '有机小米5斤': '/products/p08.jpg',
  // p07 是「田间/农事」场景图（frontend-web imagery.js 也在用），玉米面另配 p28
  '现磨玉米面5斤': '/products/p28.jpg',
  '五常大米10kg': '/products/p14.jpg',
  '红薯10斤装': '/products/p15.jpg',
  // 畜禽产品
  '老母鸡净重约2kg': '/products/p16.jpg',
  '土鸡净重约1.5kg': '/products/p27.jpg',
  '农家腊肉500g': '/products/p18.jpg',
  '散养土鸡蛋30枚': '/products/p17.jpg',
  // 水产
  '小龙虾4-6钱': '/products/p19.jpg',
  '生态草鱼约1.5kg': '/products/p20.jpg',
  // 干货
  '干辣椒500g': '/products/p21.jpg',
  '干香菇500g': '/products/p22.jpg',
}

/** ② 关键词兜底。**顺序即优先级**，把更具体的词放前面：`鸡蛋` 必须先于 `鸡`。 */
const KEYWORD_PHOTO: Array<[RegExp, string]> = [
  [/鸡蛋|鸭蛋|咸蛋|禽蛋|蛋/, '/products/p17.jpg'],
  [/腊肉|腊肠|香肠|腊味|火腿/, '/products/p18.jpg'],
  [/老母鸡|白条鸡|整鸡|鸡胸|鸡腿|鸡翅|鸭|鹅|鸽|猪|牛|羊|肉/, '/products/p16.jpg'],
  [/土鸡|柴鸡|笨鸡|鸡/, '/products/p27.jpg'],
  [/小龙虾|龙虾|虾|蟹|螺|贝/, '/products/p19.jpg'],
  [/鱼|鳝|泥鳅/, '/products/p20.jpg'],
  [/香菇|木耳|银耳|菌|笋干|腐竹/, '/products/p22.jpg'],
  [/辣椒|花椒|胡椒|八角/, '/products/p21.jpg'],
  [/菜油|花生油|香油|食用油|调和油|油/, '/products/p29.jpg'],
  [/莲藕|藕/, '/products/p23.jpg'],
  [/红薯|地瓜|紫薯|山药/, '/products/p15.jpg'],
  [/土豆|马铃薯|芋/, '/products/p04.jpg'],
  [/白菜|甘蓝|卷心菜/, '/products/p25.jpg'],
  [/黄瓜|丝瓜|苦瓜|西葫芦/, '/products/p26.jpg'],
  [/西红柿|番茄/, '/products/p02.jpg'],
  [/玉米/, '/products/p28.jpg'],
  [/猕猴桃|奇异果/, '/products/p11.jpg'],
  [/柚/, '/products/p30.jpg'],
  [/砂糖橘|砂糖桔|蜜橘|橘|桔|柑|橙/, '/products/p03.jpg'],
  [/桃|李|杏|梨|枣|莓|葡萄|樱桃|荔枝|龙眼|苹果/, '/products/p09.jpg'],
  [/米|谷|麦|豆|面/, '/products/p14.jpg'],
]

/** ③ 品类兜底池（按索引轮取） */
const CATEGORY_PHOTOS: Record<string, string[]> = {
  水果: ['/products/p03.jpg', '/products/p13.jpg', '/products/p12.jpg', '/products/p11.jpg', '/products/p09.jpg'],
  蔬菜: ['/products/p02.jpg', '/products/p04.jpg', '/products/p25.jpg', '/products/p26.jpg', '/products/p05.jpg'],
  粮油: ['/products/p14.jpg', '/products/p08.jpg', '/products/p29.jpg', '/products/p15.jpg'],
  畜禽产品: ['/products/p16.jpg', '/products/p27.jpg', '/products/p17.jpg', '/products/p18.jpg'],
  水产: ['/products/p19.jpg', '/products/p20.jpg'],
  干货: ['/products/p21.jpg', '/products/p22.jpg'],
}

/** 该品类是否有可用实拍兜底图 */
export const hasCategoryPhoto = (category?: string) => !!category && !!CATEGORY_PHOTOS[category]?.length

/** 品类素材池里按索引轮取一张；没有该品类素材返回 null */
export function pickCategoryPhoto(category: string | undefined, index: number): string | null {
  if (!category) return null
  const pool = CATEGORY_PHOTOS[category]
  if (!pool?.length) return null
  return pool[index % pool.length]
}

/**
 * 给一件没有实拍图的货源挑一张同类目配图。
 * 调用方应当先试真实图：`resolveImage(row.imageUrl) ?? pickProductPhoto(row, i)`。
 */
export function pickProductPhoto(
  product: { productName?: string; category?: string },
  index: number,
): string | null {
  const key = normalize(product.productName ?? '')
  if (key) {
    const exact = NAME_PHOTO[key]
    if (exact) return exact
    for (const [pattern, photo] of KEYWORD_PHOTO) {
      if (pattern.test(key)) return photo
    }
  }
  return pickCategoryPhoto(product.category, index)
}
