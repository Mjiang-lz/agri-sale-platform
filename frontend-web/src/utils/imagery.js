/**
 * 商品图与专家头像的兜底映射。
 *
 * 为什么需要它：`t_product.image_url` 在演示数据里全是 NULL（data.sql 里写死为 NULL），
 * 而生鲜电商的价值全在图上 —— 没有实物大图的商品卡片等于一张灰底文字条，
 * 首页就退化成「个人中心的列表」。
 *
 * 图的来源（★ 2026-10-14 起全部收归后端统一存储）：
 *   - `backend/src/main/resources/seed-media/products/` 41 张真实田间/市场照片
 *   - `seed-media/experts/` 10 张（5 头像 + 5 横幅）
 *   - `seed-media/knowledge/` 32 张（18 张库内配图 + 14 张主题兜底图）
 *   源图打 jar 随包走，启动时由 `MediaSeedInitializer` 幂等铺到后端本地目录，
 *   访问地址统一是 `/api/files/<对象键>`。⇒ **下面的表存的是「对象键」，
 *   与后台「图片资源管理」里显示的键一一对应**。
 *   - `g01.jpg` ~ `g10.jpg`：为 10 个无法被现有素材精确匹配的商品配的实拍图。
 *     2026-10-12 第九轮：换掉原来的 AI 生成图（右下角带「AI生成 / WorkBuddy」水印），
 *     改为 Pexels 实拍图（免费商用、无需署名），并从 PNG 转 JPEG ⇒ 16.5MB → 1.2MB。
 *   - `knowledge/` 那 14 张**语义命名**图（cert-green-food / farm-cooperative …）是
 *     「按标题关键词配图」的兜底，与库里那 18 张哈希名配图不是同一批，两者都要留。
 *
 * 优先级（商品）：接口返回的 imageUrl > 按商品名精确匹配 > 按品类兜底 > 通用田间图
 * 优先级（专家）：接口返回的 avatarUrl（若可用）> 按 expertId 的生成头像 > 姓名首字
 * 优先级（知识）：接口返回的 imageUrl > 标题关键词匹配 > 分类渐变封面
 *
 * ★★ 出口纪律：所有素材地址**统一过 `mediaUrl()`**（见该函数注释）。
 *    **不要用 `import.meta.env.BASE_URL`** —— `/api/files/…` 是站点根路径，
 *    拼上构建 base 会变成 `/app/api/files/…` 而 404。
 *    判据：字符串是不是「以 `/` 开头且属于后端」。是 ⇒ 过 `mediaUrl()`；
 *    不是（`data:`、`http(s):` 外链、接口返回的完整地址）⇒ 原样放行。
 *    下面这些常量表**刻意保留带前导 `/` 的对象键写法** —— 可读，
 *    而且它们本身就是一份素材清单。转换只发生在**返回给调用方之前**，转换点集中。
 */

/** 后端本地存储的读地址前缀。与 `context-path: /api` + `/files/**` 映射对应。 */
const MEDIA_BASE = '/api/files'

/**
 * 把「存储对象键」拼成浏览器能取到的图片地址。
 *
 * ★★ 2026-10-14：素材出口从 `assetUrl()` 换成 `mediaUrl()`。
 *
 *    起因是图片**全部收归后端统一存储**（`backend/src/main/resources/seed-media/`，
 *    启动时由 `MediaSeedInitializer` 铺到 `<localDir>/`）。此前后端 jar 里带
 *    `application-local.yml`（写着 `rxt.obs.enabled: true`），图片走 OBS，
 *    前端 `public/` 下那 65 张只是「本地的另一份副本」—— 两份各改一次、
 *    后台「图片资源管理」还一张都列不出来。
 *
 * ★ 与 `assetUrl()` 的关键区别：**不能再拼构建 base**。
 *   `/api/files/…` 是**站点根**路径（后端 context-path 就是 `/api`，与前端
 *   挂在 `/app/` 下无关）。若过 `assetUrl()` 会被拼成 `/app/api/files/…`，
 *   nginx 找不到 → SPA 回退返回 `200 + index.html` → 图片静默变碎图。
 *   这正是本文件原来注释里记的那次事故的**同一个坑**，只是方向相反。
 *
 * @param {string} key 存储对象键，如 `products/p03.jpg`；也可直接传完整地址原样返回
 * @returns {string} 可安全放进 `<img src>` 的地址
 */
export function mediaUrl(key) {
  if (!key) return key
  // data URI（渐变封面）与外链已是完整地址，不再拼前缀
  if (!key.startsWith('/')) return key
  // ★ 幂等：已经是后端读地址的原样返回。
  //   这是**真实会被踩到**的 —— `BizHome.vue` 的品类圆入口写的是
  //   `mediaUrl(CATEGORY_ENTRY_IMG[x] || productImage(...))`，
  //   而 `productImage()` 在库里 imageUrl 有值时返回的就是 `/api/files/…`，
  //   不过滤就会拼成 `/api/files/api/files/…` ⇒ 404。
  if (key.startsWith(`${MEDIA_BASE}/`)) return key
  return MEDIA_BASE + key
}

/**
 * 历史格式的对象键前缀 —— 图片收归后端存储之前，前端 `public/` 下的那三个目录。
 * 那个目录**已随本次改造删除**，所以库里若还留着这些前缀，浏览器去站点根找会拿到
 * nginx 的 **404**（那三条 `location ^~ /products/ /experts/ /knowledge/` 没有
 * `try_files`，所以老实 404 而不是回退成 index.html）。
 */
const LEGACY_PREFIXES = ['/products/', '/knowledge/', '/experts/']

/**
 * 归一化「**数据库里存的值**」—— 与 {@link mediaUrl} 的区别就在这里。
 *
 * ★ 为什么必须有这个函数（2026-10-15 实测踩到）：
 *   「图片统一收归后端」那次改造把前端 `public/{products,knowledge,experts}` 整目录删了，
 *   同时删掉了旧代码里「`/experts/` 开头要补 BASE_URL」的兼容特例。
 *   当时判断「库里已全部迁移成 `/api/files/…`，该特例不再命中」——
 *   **这个判断成立的前提是「迁移 SQL 一定被执行过」**。
 *   而迁移 SQL 是要人去服务器上手动跑的：一旦跳过（换服务器、只换 jar、忘了跑），
 *   库里仍是 `/experts/x.jpg`，前端又不再兜底 ⇒ 专家头像集体消失成纯色圆，
 *   而横幅、知识配图、商品图全都正常 —— 因为它们要么走代码表、要么走 OBS 外链，
 *   只有头像的旧值指向了一个**已经不存在**的目录。
 *
 * ★ 所以这里做「值级自愈」：识别历史前缀并映射到当前存储地址。
 *   于是**忘了跑迁移 SQL 也不会丢图**，迁移从「必须做」降级为「应该做」。
 *   判据是对**白名单前缀**做映射而不是一律加前缀 ——
 *   这三个字段只装图片地址，但显式列出前缀能避免将来某个 `/xxx/` 开头
 *   的非图片地址被误改。
 *
 * @param {string} url 数据库里的原始值
 * @returns {string} 可直接放进 `<img src>` 的地址
 */
export function storedUrl(url) {
  if (!url || typeof url !== 'string' || !url.trim()) return url
  // 外链（https://…）与 data URI 原样放行
  if (!url.startsWith('/')) return url
  // 已经是当前格式 —— 原样返回（mediaUrl 的幂等分支同义）
  if (url.startsWith(`${MEDIA_BASE}/`)) return url
  const isLegacy = LEGACY_PREFIXES.some((prefix) => url.startsWith(prefix))
  return isLegacy ? MEDIA_BASE + url : url
}

/** 商品名关键词 → 精确图片。这是让「文案 / 搜索结果 / 图片」三者匹配的核心表。 */
const NAME_IMG = [
  { keys: ['赣南脐橙'], img: '/products/p03.jpg' },
  // 落地页对「脐橙次果 特惠」单独配一篮水果（p09），避免与「赣南脐橙」同图
  { keys: ['脐橙次果'], img: '/products/p09.jpg' },
  { keys: ['脐橙'], img: '/products/p03.jpg' },
  { keys: ['砂糖橘', '沙糖橘'], img: '/products/p13.jpg' },
  // p12 是挂在树上的青皮柚（通体暗绿），不像蜜柚；改用 p30（半个红心蜜柚实拍）
  { keys: ['蜜柚', '柚子'], img: '/products/p30.jpg' },
  { keys: ['猕猴桃', '奇异果'], img: '/products/p11.jpg' },
  // 与落地页对齐：「农家自种小番茄」用 p05（菜摊实拍），「露天西红柿」用 p02（藤上番茄）
  { keys: ['小番茄'], img: '/products/p05.jpg' },
  { keys: ['西红柿', '番茄'], img: '/products/p02.jpg' },
  { keys: ['干香菇', '香菇'], img: '/products/p22.jpg' },
  { keys: ['红薯', '地瓜'], img: '/products/p15.jpg' },
  // 粮油三件与落地页对齐：大米用 p14（一碗米）、玉米面用 p28（大盆玉米面）、小米用 p31（一碗黄小米）
  { keys: ['五常大米', '大米'], img: '/products/p14.jpg' },
  { keys: ['玉米面', '玉米粉'], img: '/products/p28.jpg' },
  { keys: ['小米'], img: '/products/p31.jpg' },
  { keys: ['黄瓜'], img: '/products/p26.jpg' },
  { keys: ['大白菜', '白菜'], img: '/products/p25.jpg' },
  { keys: ['土豆', '马铃薯'], img: '/products/p04.jpg' },
  { keys: ['干辣椒', '辣椒'], img: '/products/p21.jpg' },
  { keys: ['鸡蛋'], img: '/products/p17.jpg' },
  // 与落地页对齐：「老母鸡」用 p16（白条整鸡实拍），「土鸡」用 p27（草地散养活鸡）
  { keys: ['老母鸡'], img: '/products/p16.jpg' },
  { keys: ['土鸡', '母鸡'], img: '/products/p27.jpg' },
  // 与落地页对齐：腊肉 p18（挂晒腊味）、草鱼 p20（活水鱼）、莲藕 p23（白盘藕片）
  { keys: ['腊肉'], img: '/products/p18.jpg' },
  { keys: ['草鱼'], img: '/products/p20.jpg' },
  { keys: ['小龙虾'], img: '/products/p19.jpg' },
  { keys: ['莲藕'], img: '/products/p23.jpg' },
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
  // ★ 走 storedUrl 而不是原样返回：库里可能是历史格式 `/products/x.jpg`
  //   （前端 public 目录已删 ⇒ 直接 404）。详见 storedUrl 的注释。
  if (real && typeof real === 'string' && real.trim()) return storedUrl(real)

  const name = String(p?.productName || '')
  // 1) 按商品名关键词精确匹配：让搜索结果和商品文案与图片对应
  const exact = NAME_IMG.find((item) => item.keys.some((k) => name.includes(k)))
  if (exact) return mediaUrl(exact.img)

  // 2) 按品类兜底：用 productId 取模，保证同一商品永远拿到同一张图
  const cat = p?.category
  const pool = CATEGORY_IMG[cat] || CATEGORY_IMG['默认']
  const id = Number(p?.productId || p?.id || 0)
  return mediaUrl(pool[(id || 1) % pool.length])
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
 * 专家头像：库里 `t_user.avatar_url` 有值就用，否则按 expertId 落到后端存储的本地图。
 *
 * ★ 两级回落，缺一不可：
 *   1. 库里 t_user.avatar_url 有值 → 优先用它（用户自己传的头像）；
 *   2. 为空 → 用下面的本地图。库里 13 个用户有 8 个 avatar_url 是 NULL，
 *      只靠库里那列，演示时专家头像全是首字方块。
 *
 * ★ 2026-10-14：原有一段「`/experts/` 开头要补 BASE_URL」的特例，
 *   已删除 —— 素材全部收归后端后，库里存的就是 `/api/files/experts/…`，
 *   那是站点根路径，本来就不该拼构建 base（拼了必 404）。
 *
 * ⚠️ 但**保留兜底池**这个设计：库里 avatar_url 为空时仍要有图。
 *   新增专家时把图放进 `backend/src/main/resources/seed-media/experts/`，
 *   然后在下面加一行 id → 文件名映射；**不要往数据库传图**。
 */
const EXPERT_IMG = {
  1: '/experts/zhao-guoqiang.jpg',
  2: '/experts/sun-lihua.jpg',
  3: '/experts/wu-jianping.jpg',
  4: '/experts/lin-shufen.jpg',
  5: '/experts/huang-zhigang.jpg'
}

export function expertAvatar(e) {
  const url = e?.avatarUrl || e?.avatar_url
  // ★ 走 storedUrl：2026-10-15 线上事故的直接原因就在这一行。
  //   库里若是历史格式 `/experts/x.jpg`，前端 public/experts/ 已随「图片收归后端」
  //   删除 ⇒ 404 ⇒ 头像退化成纯色圆；而横幅/知识图/商品图都正常（不走这个旧值），
  //   所以现象是「只有头像没了」，极难联想到是迁移脚本没跑。
  if (url && typeof url === 'string' && url.trim()) return storedUrl(url)
  const id = Number(e?.expertId || 0)
  return mediaUrl(EXPERT_IMG[id]) || ''
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
  // g03 画面是「燕麦片 + 糙米 + 豆」的混合杂粮，与「小米」不是一回事；改用 p31（一碗黄小米实拍）
  { keys: ['小米', '谷子'], img: '/products/p31.jpg' },
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
  return hit ? mediaUrl(hit.img) : ''
}

/**
 * 取知识的**真图**（① 接口地址 → ② 标题命中的实拍图）。文章页用。
 * @param {object} k 知识对象
 * @returns {string} 图片地址；**都没有时返回空串**（调用方据此整块不渲染）
 */
export function knowledgePhoto(k) {
  const real = k?.imageUrl || k?.image_url
  // ★ 同 productImage：库里可能是历史格式 `/knowledge/x.jpg`（前端目录已删 ⇒ 404）。
  //   OBS 绝对地址（迁移前的另一种形态）不以 / 开头，storedUrl 原样放行。
  if (real && typeof real === 'string' && real.trim()) return storedUrl(real)
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
  return mediaUrl(pool[(id || 1) % pool.length])
}

export function demandFallbackBg(d) {
  const id = Number(d?.demandId || 0)
  return FALLBACK_BG[id % FALLBACK_BG.length]
}

export function demandInitial(d) {
  return String(d?.category || '购').slice(0, 1)
}

/**
 * 专家卡片顶部横幅 —— 2026-10-13 整族换素材。
 *
 * ⚠️ 老实现是**直接复用商品图池**（`DOMAIN_IMG` → `/products/*.jpg`），三个后果
 *   （2026-10-13 A/B 实测的修前观测值，不是推测）：
 *   ① 图和专家对不上：「种植与植保」的专家配的是一颗大白菜（p25）、
 *      「畜牧与水产」配一条草鱼（p20）；
 *   ② 领域关键词没命中时**一起落到同一张兜底图**：实测 5 张卡的 src 是
 *      `p25, p20, p08, p08, p08` —— **5 张里只有 3 张不同**，后三位专家共用一张；
 *   ③ 把「人」的卡片背景做成一盘菜，人物卡片本身的观感就是错的。
 *
 * 现在换成「人物在工作的实景」照片：从免费可商用图库（Pexels License）下载进
 * `public/experts/` 本地引用。★ 刻意不放外链 —— 换服务器、断网、图库改版都不受影响，
 * 也不会像 OBS 那样把绝对地址写死进库。
 *
 * ★ 取图纪律（换图/加图请照做）：优先选**人物是场景的一部分**的构图
 *   （背影 / 口罩 / 多人中景），**避免清晰陌生人正脸** —— 头像旁边多一张别人的脸，
 *   用户会以为那就是专家的照片，比配错图更糟。
 * ★ 裁剪口径 880×320（≈2.75:1），与卡片横幅 326×118 同比例。换图请按这个比例出，
 *   否则 `object-fit: cover` 会把主体裁掉。
 * ★ 素材清单与出处（Pexels License：可商用、无需署名，此处仍记录以便追溯）：
 *   banner-1 → pexels.com/photo/8442371   检测中心全景
 *   banner-2 → pexels.com/photo/9243558   化验取样特写
 *   banner-3 → pexels.com/photo/32845671  厂区持表巡检
 *   banner-4 → pexels.com/photo/31090813  车间工位作业
 *   banner-5 → pexels.com/photo/7693692   会议桌谈方案
 */
const BANNER_IMG = {
  1: '/experts/banner-1.jpg',
  2: '/experts/banner-2.jpg',
  3: '/experts/banner-3.jpg',
  4: '/experts/banner-4.jpg',
  5: '/experts/banner-5.jpg'
}

/**
 * 领域关键词 → 横幅。给「还没登记进 BANNER_IMG 的新专家」兜底。
 *
 * ★ 这是 `find` 取**第一个命中**，所以数组顺序有意义：有包含关系的词必须让
 *   更精确的排在前面，否则短词先命中，长词永远走不到（与 NAME_IMG 同一条纪律）。
 */
const DOMAIN_BANNER = [
  ['加工', '/experts/banner-4.jpg'],
  ['质量', '/experts/banner-4.jpg'],
  ['电商', '/experts/banner-5.jpg'],
  ['经营', '/experts/banner-5.jpg'],
  ['土壤', '/experts/banner-3.jpg'],
  ['肥料', '/experts/banner-3.jpg'],
  ['畜牧', '/experts/banner-2.jpg'],
  ['水产', '/experts/banner-2.jpg'],
  ['种植', '/experts/banner-1.jpg'],
  ['植保', '/experts/banner-1.jpg']
]

/** 领域也认不出来时的兜底：恒有图，不留空横幅。 */
const BANNER_DEFAULT = '/experts/banner-1.jpg'

export function expertBanner(e) {
  const byId = BANNER_IMG[Number(e?.expertId || 0)]
  if (byId) return mediaUrl(byId)
  const d = String(e?.expertDomain || '')
  const hit = DOMAIN_BANNER.find(([k]) => d.includes(k))
  return mediaUrl(hit ? hit[1] : BANNER_DEFAULT)
}

// 导出时也过 mediaUrl：调用方拿到就能直接塞进 `src`，不必再关心存储路径。
// （当前仓库内无引用，保留是为了不破坏原有对外 API。）
export const ALL_PRODUCT_IMAGES = ALL.map((p) => mediaUrl(p))
