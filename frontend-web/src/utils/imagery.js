/**
 * 商品图与专家头像的兜底映射。
 *
 * 为什么需要它：`t_product.image_url` 在演示数据里全是 NULL（data.sql 里写死为 NULL），
 * 而生鲜电商的价值全在图上 —— 没有实物大图的商品卡片等于一张灰底文字条，
 * 首页就退化成「个人中心的列表」。
 *
 * 图的来源：
 *   - `frontend-web/public/products/` 原有 27 张真实田间/市场照片
 *   - `g01.jpg` ~ `g10.jpg`：为 10 个无法被现有素材精确匹配的商品配的实拍图。
 *     2026-10-12 第九轮：换掉原来的 AI 生成图（右下角带「AI生成 / WorkBuddy」水印），
 *     改为 Pexels 实拍图（免费商用、无需署名），并从 PNG 转 JPEG ⇒ 16.5MB → 1.2MB。
 *   - 新增 `experts/zhao-guoqiang.png`、`experts/sun-lihua.png`：两位专家的 AI 生成写实头像
 *   - 新增 `public/knowledge/` 6 张**知识专题实拍图**（2026-10-10）：取自 Pexels，
 *     许可为免费商用、无需署名。用途见 `KNOWLEDGE_TITLE_IMG` 顶部注释。
 *     为什么单开一个目录：商品图是「一筐菜」，而知识文章要的是「场景」——
 *     讲绿色食品认证配一篮新鲜蔬菜合理，配一碗小米就是错的。
 *
 * 优先级（商品）：接口返回的 imageUrl > 按商品名精确匹配 > 按品类兜底 > 通用田间图
 * 优先级（专家）：接口返回的 avatarUrl（若可用）> 按 expertId 的生成头像 > 姓名首字
 * 优先级（知识）：接口返回的 imageUrl > 标题关键词匹配 > 分类渐变封面
 *
 * ★★ 2026-10-13：本文件所有素材路径**统一过 `assetUrl()` 再返回**。
 *    起因是一次真实事故 —— 路径写的是「站点根绝对路径」（`/products/g01.jpg`），
 *    而业务端构建后的 base 是 `/app/`，浏览器于是去**站点根**找图：那里只有落地页
 *    自带的 27 张，缺 `g01~g10`、缺整个 `experts/` 与 `knowledge/`。结果 nginx 的
 *    SPA 回退把「文件不存在」返回成 `200 + index.html`，`<img>` 解码失败，
 *    前端静默退化成「商品名首字色块」—— 状态码全绿，看不出任何异常。
 *
 *    ★ 关键认知：Vite 的 `base` 只作用于**它自己能看到的引用**（`import` 进来的资源、
 *      CSS 里的相对 url、index.html）。**JS 里手写的字符串它一概不管**，
 *      浏览器一律按站点根解析。所以这类字符串必须手动拼 `import.meta.env.BASE_URL`。
 *    ★ 判据：字符串开头是不是 `/`。是 ⇒ 必须过 `assetUrl()`；不是（`data:`、`http(s):`、
 *      相对地址）⇒ 原样放行。
 *
 *    下面这些常量表**刻意保留带前导 `/` 的写法** —— 可读，而且它们本身就是一份素材清单。
 *    转换只发生在**返回给调用方之前**，转换点集中，不容易漏。
 */

/** 构建 base：开发态 `/`，`RXT_BASE=/app/` 构建时为 `/app/`（与 `createWebHistory` 读同一个值）。 */
const ASSET_BASE = import.meta.env.BASE_URL || '/'

/**
 * 给「站点根绝对路径」拼上构建 base，得到浏览器能正确解析的地址。
 *
 * **本文件所有素材路径的出口都必须过这里**；其他文件（如 `BizHome.vue` 的轮播图、
 * 品类圆入口）也请复用它，不要再手写裸路径。
 *
 * @param {string} path 素材路径
 * @returns {string} 可安全放进 `<img src>` 的地址
 */
export function assetUrl(path) {
  if (!path) return path
  // data URI（渐变封面）与外链已是完整地址，不再拼前缀
  if (!path.startsWith('/')) return path
  // base 为 '/' 时去尾斜杠得空串；为 '/app/' 时得 '/app'
  return ASSET_BASE.replace(/\/$/, '') + path
}

/** 商品名关键词 → 精确图片。这是让「文案 / 搜索结果 / 图片」三者匹配的核心表。 */
const NAME_IMG = [
  { keys: ['赣南脐橙', '脐橙'], img: '/products/p03.jpg' },
  { keys: ['砂糖橘', '沙糖橘'], img: '/products/p13.jpg' },
  // p12 是挂在树上的青皮柚（通体暗绿），不像蜜柚；改用 p30（半个红心蜜柚实拍）
  { keys: ['蜜柚', '柚子'], img: '/products/p30.jpg' },
  { keys: ['猕猴桃', '奇异果'], img: '/products/p11.jpg' },
  { keys: ['小番茄', '西红柿', '番茄'], img: '/products/p02.jpg' },
  { keys: ['干香菇', '香菇'], img: '/products/g04.jpg' },
  { keys: ['红薯', '地瓜'], img: '/products/p15.jpg' },
  { keys: ['五常大米', '大米'], img: '/products/g02.jpg' },
  { keys: ['玉米面', '玉米粉'], img: '/products/g07.jpg' },
  { keys: ['小米'], img: '/products/g03.jpg' },
  { keys: ['黄瓜'], img: '/products/p26.jpg' },
  { keys: ['大白菜', '白菜'], img: '/products/p25.jpg' },
  { keys: ['土豆', '马铃薯'], img: '/products/p04.jpg' },
  { keys: ['干辣椒', '辣椒'], img: '/products/p21.jpg' },
  { keys: ['鸡蛋'], img: '/products/p17.jpg' },
  { keys: ['土鸡', '老母鸡', '母鸡'], img: '/products/p27.jpg' },
  { keys: ['腊肉'], img: '/products/g05.jpg' },
  { keys: ['草鱼'], img: '/products/g06.jpg' },
  { keys: ['小龙虾'], img: '/products/p19.jpg' },
  { keys: ['莲藕'], img: '/products/g10.jpg' },
  { keys: ['菜籽油', '菜油'], img: '/products/p29.jpg' },
  { keys: ['大闸蟹', '螃蟹'], img: '/products/g08.jpg' },
  { keys: ['松茸'], img: '/products/g09.jpg' },
  // 兜底：当关键词没命中时，用更宽泛的品类池
  { keys: ['水果'], img: '/products/p09.jpg' },
  { keys: ['蔬菜'], img: '/products/p05.jpg' },
  { keys: ['干货'], img: '/products/p22.jpg' },
  { keys: ['粮油'], img: '/products/p08.jpg' },
  { keys: ['畜禽产品', '畜禽'], img: '/products/p16.jpg' },
  { keys: ['水产'], img: '/products/p20.jpg' }
]

/** 品类 → 图片池。当商品名没有精确匹配时，按品类给一张稳定的图。 */
const CATEGORY_IMG = {
  水果: ['/products/p03.jpg', '/products/p13.jpg', '/products/p01.jpg', '/products/p11.jpg', '/products/p12.jpg', '/products/p09.jpg'],
  蔬菜: ['/products/p04.jpg', '/products/p26.jpg', '/products/p25.jpg', '/products/p02.jpg', '/products/p06.jpg', '/products/p05.jpg'],
  干货: ['/products/g04.jpg', '/products/p22.jpg', '/products/p21.jpg'],
  粮油: ['/products/g02.jpg', '/products/p29.jpg', '/products/g03.jpg', '/products/g07.jpg', '/products/p14.jpg', '/products/p08.jpg'],
  畜禽产品: ['/products/p27.jpg', '/products/p16.jpg', '/products/p17.jpg', '/products/g05.jpg'],
  水产: ['/products/g06.jpg', '/products/p19.jpg', '/products/g08.jpg', '/products/p20.jpg'],
  // data.sql 里出现过的其他品类，兜到最通用的田间图
  默认: ['/products/p08.jpg']
}

const ALL = [
  '/products/p01.jpg', '/products/p02.jpg', '/products/p03.jpg', '/products/p04.jpg',
  '/products/p05.jpg', '/products/p06.jpg', '/products/p08.jpg', '/products/p09.jpg',
  '/products/p11.jpg', '/products/p12.jpg', '/products/p13.jpg', '/products/p14.jpg',
  '/products/p15.jpg', '/products/p16.jpg', '/products/p17.jpg', '/products/p19.jpg',
  '/products/p20.jpg', '/products/p21.jpg', '/products/p22.jpg', '/products/p25.jpg',
  '/products/p26.jpg', '/products/p27.jpg',
  '/products/g01.jpg', '/products/g02.jpg', '/products/g03.jpg', '/products/g04.jpg',
  '/products/g05.jpg', '/products/g06.jpg', '/products/g07.jpg', '/products/g08.jpg',
  '/products/g09.jpg', '/products/g10.jpg'
]

/** 文字兜底：图挂了就显示商品名首字，而不是碎图 */
const FALLBACK_BG = ['#EAF3DE', '#E6F1FB', '#FAEEDA', '#EEEDFE', '#FBEAF0', '#E1F5EE']

/**
 * 取商品主图。
 * @param {object} p 商品对象（ProductVO）
 * @returns {string} 图片地址；恒非空
 */
export function productImage(p) {
  const real = p?.imageUrl || p?.image_url
  if (real && typeof real === 'string' && real.trim()) return real

  const name = String(p?.productName || '')
  // 1) 按商品名关键词精确匹配：让搜索结果和商品文案与图片对应
  const exact = NAME_IMG.find((item) => item.keys.some((k) => name.includes(k)))
  if (exact) return assetUrl(exact.img)

  // 2) 按品类兜底：用 productId 取模，保证同一商品永远拿到同一张图
  const cat = p?.category
  const pool = CATEGORY_IMG[cat] || CATEGORY_IMG['默认']
  const id = Number(p?.productId || p?.id || 0)
  return assetUrl(pool[(id || 1) % pool.length])
}

/** 图片加载失败时的占位底色（按 id 稳定取色，避免整页一个颜色） */
export function productFallbackBg(p) {
  const id = Number(p?.productId || p?.id || 0)
  return FALLBACK_BG[id % FALLBACK_BG.length]
}

export function productFallbackText(p) {
  const n = p?.productName || p?.category || '农'
  return String(n).slice(0, 1)
}

/**
 * 专家头像：演示数据里 `t_user.avatar_url` 大多为 NULL 或死链，
 * 所以为两位专家生成写实头像，按 expertId 映射。
 */
const EXPERT_IMG = {
  1: '/experts/zhao-guoqiang.png',
  2: '/experts/sun-lihua.png'
}

export function expertAvatar(e) {
  const id = Number(e?.expertId || 0)
  return assetUrl(EXPERT_IMG[id]) || ''
}

export function expertInitial(name) {
  return String(name || '专').slice(0, 1)
}

export function expertBg(id) {
  return FALLBACK_BG[Number(id || 0) % FALLBACK_BG.length]
}

/* ==================================================================
 * 知识条目封面 —— 2026-10-10 重写。
 *
 * ⚠️ 老实现有真实缺陷：
 *      const pool = KNOWLEDGE_IMG[category]; return pool[knowledgeId % pool.length]
 *    —— 按分类选图池、再用 id 取模轮换。于是稳定复现出这些配对：
 *      「畜禽粪污资源化利用路径」 → p14（一碗小米）
 *      「饲料配比中的蛋白能量平衡」 → g05（腊肉）
 *      「某地小龙虾价格行情速递」 → p03（一树柑橘）
 *    图和标题毫无关系。而且不是偶发，是每次刷新都错。
 *
 *    两个根因：
 *      1) `t_knowledge.image_url` 18 行全 NULL，封面只能由前端「猜」；
 *      2) 「猜」的维度选错了 —— 分类只有 5 个，同分类下平均 3 条共用一个池，
 *         而池子里全是**农产品商品照**。「分类」粒度太粗，
 *         且「商品照」和「技术/政策文章」本来就不是一类内容。
 *
 *    现规则（优先级从高到低）：
 *      1) 接口给的 imageUrl —— 数据库将来回填真图后自然接管；
 *      2) 标题关键词 → 实拍图 —— 标题里出现「柑橘 / 小龙虾 / 鱼塘」这类
 *         **具象**词时才配真实照片，做到图与文案对得上；
 *      3) 分类渐变封面（data URI SVG）—— 「政策解读 / 绿色食品认证 / 扶持政策」
 *         这类抽象主题本就没有对应实物，硬塞一张农产品照只会更违和。
 *         改用与「知识库列表页」同一套配色的渐变封面：宁可没有图，也不要错图。
 *
 *    ★ 2026-10-12：第 3 级**只给卡片用**，不给文章页用。详见 `knowledgePhoto()`
 *      的注释 —— 渐变封面在文章页是纯噪音，文章页没有真图就不显示配图。
 *
 *    ★★ 2026-10-12 同日补：**第 1 级终于有值了** —— `t_knowledge.image_url`
 *      的 1~18 行已回填真图（12 个 OBS 对象，同一张可被多条知识复用，
 *      所以 18 行只对应 12 个对象）。上面这些兜底对现网数据基本不再触发，
 *      **但仍要保留**：① 新发的知识若专家没传图，还得靠它们撑住卡片；
 *      ② 断网 / 对象被删时，本文件末尾那两个「本地」函数要接住。
 * ================================================================== */

/**
 * 标题关键词 → 实拍图。
 *
 * ★ **数组顺序就是优先级**（`find` 取第一个命中项）：
 *   具象词（小龙虾、柑橘、鱼塘）必须排在宽泛词（土壤、田间、管理）之前，
 *   否则「果树秋施基肥」会被宽泛的「基肥」抢走，配成一张土壤图。
 */
const KNOWLEDGE_TITLE_IMG = [
  // ================= 专题场景图（2026-10-10 新增）=================
  // ★ 这一组必须排在最前。它们拍的是**文章主题**（认证、协作、堆肥、稻田、
  //   温室、土壤），而下面那批是**农产品商品照**。主题图永远比商品照贴题：
  //   「绿色食品认证的申报条件」配一篮新鲜蔬菜是合理的，配一碗小米就是错的。
  //
  // 素材来源：Pexels（https://www.pexels.com/photo/<id>）
  // 许可：Pexels License —— 免费商用、**无需署名**。文件已裁成卡片比例
  //       （900×396 ≈ 2.27:1）后存入 `frontend-web/public/knowledge/`。
  { keys: ['绿色食品', '有机食品', '有机认证', '认证申报', '三品一标'], img: '/knowledge/cert-green-food.jpg' }, // pexels 37765149
  { keys: ['经营主体', '家庭农场', '合作社', '龙头企业', '适度规模'], img: '/knowledge/farm-cooperative.jpg' }, // pexels 34204275
  { keys: ['粪污', '沼气', '堆肥', '有机肥', '资源化'], img: '/knowledge/manure-compost.jpg' },                // pexels 31110988
  { keys: ['连作', '设施蔬菜', '大棚', '温室'], img: '/knowledge/greenhouse-veg.jpg' },                        // pexels 36812130
  { keys: ['酸化', '土壤改良', '土壤健康'], img: '/knowledge/soil-health.jpg' },                               // pexels 3696170
  { keys: ['稻瘟', '水稻', '稻田', '育秧'], img: '/knowledge/rice-paddy.jpg' },                                // pexels 9272061

  // —— 2026-10-12 第八轮补的 8 张 ——
  // 动因：这 8 个主题原先只能落到**农产品商品照**上，而且多组共用同一张
  //   （1/3/5 全是 p03 柑橘树、11/15/17 全是 p20 鲤鱼、12/18 全是 p19 螃蟹），
  //   卡片列表上「同一张图出现三次」，且 #12/#18 配螃蟹是**物种就不对**。
  // 现在每个主题有自己的图；关键词从下面的商品照分组里**移走**，不要两处都留。
  // 全部 Pexels License（免费商用、无需署名），已裁成 900×396。
  { keys: ['修剪', '剪枝', '整形', '嫁接'], img: '/knowledge/orchard-pruning.jpg' },          // pexels 11257617
  { keys: ['基肥', '秋施', '施肥'], img: '/knowledge/orchard-base-fertilizer.jpg' },          // pexels 33995853
  { keys: ['家禽', '鸡群', '禽舍', '呼吸道'], img: '/knowledge/poultry-flock.jpg' },           // pexels 34795500
  { keys: ['饲料', '日粮', '育肥', '蛋白能量'], img: '/knowledge/animal-feed.jpg' },           // pexels 7781984
  { keys: ['尾水', '生态化处理', '人工湿地', '养殖废水'], img: '/knowledge/aquaculture-tailwater.jpg' }, // pexels 36345997
  { keys: ['越冬', '冬管'], img: '/knowledge/fish-pond.jpg' },                                 // pexels 5774415
  { keys: ['水草', '浮萍'], img: '/knowledge/duckweed-pond.jpg' },                             // pexels 38289921
  { keys: ['小龙虾', '龙虾'], img: '/knowledge/crayfish.jpg' },                                // pexels 34486954

  // ================= 农产品实拍图（按标题里的具象词匹配）=================
  // —— 果园 / 果树 ——（修剪/嫁接 已上移到专题组）
  { keys: ['柑橘', '脐橙', '柚子', '蜜柚', '裂果', '果园', '果树'], img: '/products/p03.jpg' },
  { keys: ['猕猴桃', '奇异果'], img: '/products/p11.jpg' },
  // —— 蔬菜 ——
  { keys: ['大白菜', '白菜'], img: '/products/p25.jpg' },
  { keys: ['黄瓜'], img: '/products/p26.jpg' },
  { keys: ['番茄', '西红柿'], img: '/products/p02.jpg' },
  { keys: ['土豆', '马铃薯'], img: '/products/p04.jpg' },
  // —— 采后处理 ——
  { keys: ['采收', '预冷', '保鲜', '分级', '贮运', '冷链'], img: '/products/p06.jpg' },
  // —— 大田 / 粮油 ——（水稻/稻田/稻瘟 已上移到专题组）
  { keys: ['麦田', '大田'], img: '/products/p08.jpg' },
  { keys: ['大米', '稻米'], img: '/products/g02.jpg' },
  { keys: ['小米', '谷子'], img: '/products/g03.jpg' },
  { keys: ['玉米'], img: '/products/g07.jpg' },
  { keys: ['菜籽油', '榨油', '油菜'], img: '/products/p29.jpg' },
  // —— 水产 ——（小龙虾 已上移到专题组；原先这里指向的是「螃蟹」，物种就不对）
  { keys: ['大闸蟹', '螃蟹'], img: '/products/g08.jpg' },
  { keys: ['鱼塘', '淡水鱼', '缺氧', '水质'], img: '/products/p20.jpg' },
  // —— 畜禽 ——（家禽 已上移到专题组）
  { keys: ['土鸡', '散养', '鸡'], img: '/products/p27.jpg' },
  { keys: ['蛋鸡', '鸡蛋'], img: '/products/p17.jpg' },
  // —— 食用菌 ——
  { keys: ['香菇', '食用菌', '菌菇'], img: '/products/g04.jpg' },
  { keys: ['松茸', '牛肝菌'], img: '/products/g09.jpg' },
  // —— 通用农事（宽泛词，必须垫底）——
  // 注意：`大棚`/`连作` 已上移到专题组，`酸化`/`盐碱` 同理，这里不再重复定义，
  // 避免「同一关键词出现在两组、靠数组顺序决定结果」这种隐式依赖。
  { keys: ['蔬菜'], img: '/products/p05.jpg' },
  { keys: ['土壤', '整地'], img: '/products/p15.jpg' },
  { keys: ['田间', '农事', '栽培', '管理'], img: '/products/p07.jpg' }
]

/**
 * 知识分类 → 渐变配色。
 * 与「知识库列表页」`Knowledge.vue` 的 `cover-0` ~ `cover-4` 用同一套色，
 * 两个页面的知识封面风格保持一致，不另造一套。
 */
const KNOWLEDGE_COVER = {
  种植技术: ['#2d7a4c', '#73b586'],
  养殖技术: ['#91602a', '#d4a164'],
  病虫害防治: ['#2c6679', '#78adba'],
  市场行情: ['#674b7d', '#a991ba'],
  政策解读: ['#8b5b24', '#c8a15d']
}
const KNOWLEDGE_COVER_FALLBACK = ['#2d7a4c', '#73b586']

/**
 * 分类渐变封面（data URI SVG）。
 *
 * ⚠️ 画布比例有讲究：卡片封面区是 `height:150px` + 栅格宽度，
 * 实测 1600px 视口下是 341×150 ≈ **2.27 : 1**，比 2:1 更扁。
 * `object-fit: cover` 会按宽度铺满、把上下各裁掉约 10px。
 * 所以这里用 600×300（2:1）并把内容**收在 y ∈ [18, 282] 的安全区内**，
 * 否则贴底的大字水印会被裁平（第一版就是这么被裁的）。
 * @param {object} k 知识对象
 * @returns {string} data URI
 */
function knowledgeCover(k) {
  const category = String(k?.category || '农业知识')
  const [c1, c2] = KNOWLEDGE_COVER[category] || KNOWLEDGE_COVER_FALLBACK
  const short = category.slice(0, 2) || '农业'
  const font = 'PingFang SC,Hiragino Sans GB,Microsoft YaHei,sans-serif'
  const svg =
    '<svg xmlns="http://www.w3.org/2000/svg" width="600" height="300" viewBox="0 0 600 300">' +
    '<defs><linearGradient id="s" x1="0" y1="0" x2="1" y2="1">' +
    `<stop offset="0" stop-color="${c1}"/><stop offset="1" stop-color="${c2}"/>` +
    '</linearGradient></defs>' +
    '<rect width="600" height="300" fill="url(#s)"/>' +
    `<text x="588" y="268" font-size="140" text-anchor="end" fill="#ffffff" opacity="0.16" font-family="${font}">${short}</text>` +
    `<text x="44" y="136" font-size="46" fill="#ffffff" opacity="0.95" font-family="${font}">${category}</text>` +
    `<text x="44" y="176" font-size="19" fill="#ffffff" opacity="0.7" font-family="${font}">农技知识</text>` +
    '</svg>'
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

/**
 * 知识配图的取图函数。**四个函数，按「拿哪几级兜底」分，别混用。**
 *
 * 三个来源，按优先级：
 *   ① 接口 `imageUrl`（真配图，2026-10-12 起库里已有值，OBS 绝对地址）
 *   ② 标题关键词命中的**本地实拍图**（`KNOWLEDGE_TITLE_IMG`）
 *   ③ 分类**渐变封面**（data URI SVG，纯装饰）
 *
 * | 函数 | 取哪几级 | 空不空 | 谁用 |
 * |---|---|---|---|
 * | `knowledgePhoto(k)`       | ① → ②       | 可能空 | 文章页 `/knowledge/:id` |
 * | `knowledgeKeywordPhoto(k)` | ②          | 可能空 | 上面两个「失败回退」用 |
 * | `knowledgeImage(k)`       | ① → ② → ③  | 恒非空 | 卡片（首页 / 知识库列表） |
 * | `knowledgeLocalImage(k)`  | ② → ③       | 恒非空 | 卡片的「加载失败」回退 |
 *
 * **为什么文章页不含第 ③ 级**：渐变封面是「色块 + 分类大字」的装饰图，与正文、
 * 与标题都不发生信息关系。读者点进来是要看内容，塞一张 900×396 的渐变横幅只是
 * 噪音；若同时显示「点击图片可放大」更荒谬（放大看什么？）。
 * **卡片能接受占位装饰，文章页不能** —— 同一条「宁可没有图，也不要错图」。
 *
 * **为什么还要「本地」那两个**：2026-10-12 把 image_url 回填成 OBS 绝对地址后，
 * 图文就依赖公网了（本机 demo / 答辩若断网，全部知识封面会同时失效 —— 改前它们
 * 是前端静态资源，反而不受影响）。所以 `@error` 时退到本地素材：
 * 卡片退 `knowledgeLocalImage()`（格子不能空），文章页退 `knowledgeKeywordPhoto()`。
 * 注意回退**必须跳过第 ① 级**，否则会拿同一个坏 URL 再试一次，陷入死循环。
 */

/**
 * 只按标题关键词取本地实拍图。**没有命中时返回空串。**
 * @param {object} k 知识对象
 * @returns {string} 本地图片路径（如 `/knowledge/manure-compost.jpg`）或空串
 */
export function knowledgeKeywordPhoto(k) {
  const title = String(k?.title || '')
  const hit = KNOWLEDGE_TITLE_IMG.find((item) => item.keys.some((key) => title.includes(key)))
  return hit ? assetUrl(hit.img) : ''
}

/**
 * 取知识的**真图**（① 接口地址 → ② 标题命中的实拍图）。文章页用。
 * @param {object} k 知识对象
 * @returns {string} 图片地址；**都没有时返回空串**（调用方据此整块不渲染）
 */
export function knowledgePhoto(k) {
  const real = k?.imageUrl || k?.image_url
  if (real && typeof real === 'string' && real.trim()) return real
  return knowledgeKeywordPhoto(k)
}

/**
 * 取知识封面（**卡片专用**）：① → ② → ③，恒非空。
 * @param {object} k 知识对象（KnowledgeVO）
 * @returns {string} 图片地址（相对路径 或 data URI），**恒非空**
 */
export function knowledgeImage(k) {
  return knowledgePhoto(k) || knowledgeCover(k)
}

/**
 * 卡片图片**加载失败**时的本地兜底：跳过 ①，只走 ② → ③，恒非空。
 * 用途见本段开头「为什么还要本地那两个」。
 * @param {object} k 知识对象
 * @returns {string} 本地图片路径 或 渐变封面 data URI，**恒非空**
 */
export function knowledgeLocalImage(k) {
  return knowledgeKeywordPhoto(k) || knowledgeCover(k)
}

/* ------------------------------------------------------------------
 * 求购需求同样没有图（t_demand 无图片字段），但它有品类和商品名，
 * 优先按品类精确匹配，复用商品图池。
 * --------------------------------------------------------------- */
export function demandImage(d) {
  const cat = d?.category
  const pool = CATEGORY_IMG[cat] || ALL
  const id = Number(d?.demandId || 0)
  return assetUrl(pool[(id || 1) % pool.length])
}

export function demandFallbackBg(d) {
  const id = Number(d?.demandId || 0)
  return FALLBACK_BG[id % FALLBACK_BG.length]
}

export function demandInitial(d) {
  return String(d?.category || '购').slice(0, 1)
}

/**
 * 专家横幅：用其专业领域对应的农业实景图。
 * 这是真实田间照片，不用插画或纯色块冒充。
 */
const DOMAIN_IMG = {
  种植: '/products/p25.jpg',
  蔬菜: '/products/p26.jpg',
  果树: '/products/p11.jpg',
  养殖: '/products/p17.jpg',
  水产: '/products/p20.jpg',
  粮油: '/products/p08.jpg',
  食用菌: '/products/p22.jpg'
}

export function expertBanner(e) {
  const d = String(e?.expertDomain || '')
  if (!d) return assetUrl('/products/p08.jpg')
  const hit = Object.keys(DOMAIN_IMG).find((k) => d.includes(k))
  return assetUrl(hit ? DOMAIN_IMG[hit] : '/products/p08.jpg')
}

// 导出时也过 assetUrl：调用方拿到就能直接塞进 `src`，不必再关心构建 base。
// （当前仓库内无引用，保留是为了不破坏原有对外 API。）
export const ALL_PRODUCT_IMAGES = ALL.map((p) => assetUrl(p))
