import { createFileRoute, Link } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { fetchProductPage, formatPrice, platformUrl, resolveImage, type ApiProduct } from '@/lib/api'
import { pickProductPhoto } from '@/lib/productImages'
import { useRemote } from '@/lib/useRemote'

export const Route = createFileRoute('/shop')({ component: ShopPage })

/**
 * 购物页。
 *
 * 数据优先取平台接口 GET /api/trade/products（公开，游客可访问，带
 * 关键词/品类/价格区间/产地筛选）；接口不通就回落到本地演示数据。
 *
 * 注意：详情接口 GET /api/trade/products/{id} 不在后端白名单里（游客 401），
 * 所以「立即采购」跳平台公开列表页，而不是深链详情。
 */

type Card = {
  key: string
  img: string | null
  name: string
  origin: string
  price: string
  unit: string
  seller: string
  cat: string
  tag?: string
  href: string
}

// 演示数据：品类结构与平台 `/trade/products` 保持一致，图全部本地化（public/products/）
const DEMO_PRODUCTS: Card[] = [
  { key: 'd1', img: '/products/p03.jpg', name: '赣南脐橙', origin: '江西 · 赣州', price: '3.90', unit: '斤', seller: '赣南果业合作社', cat: '水果', tag: '产地直发', href: platformUrl('/trade/products') },
  { key: 'd2', img: '/products/p11.jpg', name: '猕猴桃 5 斤装', origin: '陕西 · 周至', price: '6.80', unit: '斤', seller: '秦岭果农联合社', cat: '水果', tag: '担保交易', href: platformUrl('/trade/products') },
  { key: 'd3', img: '/products/p02.jpg', name: '露天西红柿', origin: '山东 · 寿光', price: '3.20', unit: '斤', seller: '寿光绿源合作社', cat: '蔬菜', tag: '产地直发', href: platformUrl('/trade/products') },
  { key: 'd4', img: '/products/p25.jpg', name: '大白菜', origin: '河北 · 张家口', price: '1.20', unit: '斤', seller: '坝上蔬菜基地', cat: '蔬菜', tag: '冷链直发', href: platformUrl('/trade/products') },
  { key: 'd5', img: '/products/p26.jpg', name: '大棚黄瓜', origin: '山东 · 寿光', price: '2.60', unit: '斤', seller: '寿光绿源合作社', cat: '蔬菜', tag: '担保交易', href: platformUrl('/trade/products') },
  { key: 'd6', img: '/products/p14.jpg', name: '五常大米 10kg', origin: '黑龙江 · 五常', price: '5.90', unit: '斤', seller: '五常稻香联合社', cat: '粮油', tag: '现碾现发', href: platformUrl('/trade/products') },
  { key: 'd7', img: '/products/p24.jpg', name: '农家自榨菜籽油 5L', origin: '湖北 · 荆州', price: '19.00', unit: '桶', seller: '荆楚粮油合作社', cat: '粮油', tag: '物理压榨', href: platformUrl('/trade/products') },
  { key: 'd8', img: '/products/p17.jpg', name: '散养土鸡蛋 30 枚', origin: '湖北 · 荆州', price: '1.50', unit: '枚', seller: '荆楚散养农场', cat: '畜禽产品', tag: '当日捡拾', href: platformUrl('/trade/products') },
  { key: 'd9', img: '/products/p20.jpg', name: '生态草鱼 约 1.5kg', origin: '湖北 · 荆州', price: '12.00', unit: '斤', seller: '洪湖生态渔场', cat: '水产', tag: '活鲜直发', href: platformUrl('/trade/products') },
  { key: 'd10', img: '/products/p22.jpg', name: '干香菇 500g', origin: '湖北 · 十堰', price: '38.00', unit: '斤', seller: '秦巴山货合作社', cat: '干货', tag: '产地直发', href: platformUrl('/trade/products') },
]

const DEMO_CATS = ['全部', '水果', '蔬菜', '粮油', '畜禽产品', '水产', '干货']

/**
 * 后端货源 → 卡片。
 * 后台的 24 件货源都还没传 imageUrl，所以用 pickProductPhoto 按「商品名 → 品类」配同类目实拍图。
 */
function toCard(p: ApiProduct, i: number): Card {
  const unit = p.unit || '斤'
  return {
    key: `p-${p.productId ?? i}`,
    img: resolveImage(p.imageUrl) ?? pickProductPhoto(p, i),
    name: p.productName || '平台在售货源',
    origin: p.origin || '产地待完善',
    price: formatPrice(p.price),
    unit,
    seller: p.sellerId ? `供货方 #${p.sellerId}` : '平台在售货源',
    cat: p.category || '其他',
    tag: p.stock ? `库存 ${p.stock}${unit}` : undefined,
    href: platformUrl('/trade/products'),
  }
}

const FONT = "font-['Inter_Tight','PingFang_SC','Noto_Sans_SC',_sans-serif]"

function ShopPage() {
  // 接口优先、静态兜底：GET /api/trade/products
  const { data: page, source } = useRemote(
    () => fetchProductPage({ pageNum: 1, pageSize: 50 }),
    { records: [] as ApiProduct[], total: 0 },
    (value) => !value?.records?.length,
  )

  const list = useMemo<Card[]>(() => {
    const remote = page?.records?.length ? page.records.map(toCard) : []
    return remote.length ? remote : DEMO_PRODUCTS
  }, [page])

  // 分类从数据里推导，避免平台品类取值与写死的标签对不上
  const cats = useMemo(() => {
    const values = Array.from(new Set(list.map((p) => p.cat).filter(Boolean)))
    return values.length && source === 'api' ? ['全部', ...values] : DEMO_CATS
  }, [list, source])

  const [cat, setCat] = useState('全部')

  // 切换数据源后旧的分类可能不存在，兜回「全部」
  const activeCat = cats.includes(cat) ? cat : '全部'
  const visible = activeCat === '全部' ? list : list.filter((p) => p.cat === activeCat)

  return (
    <div className={`min-h-screen bg-[#1b1b1b] text-white ${FONT}`}>
      {/* 顶栏 */}
      <header className="sticky top-0 z-40 border-b border-white/10 bg-[#1b1b1b]/85 backdrop-blur-md">
        <div className="mx-auto flex w-full max-w-[1728px] items-center justify-between px-5 py-4 lg:px-10">
          <Link to="/" className="flex items-center gap-2 hover:opacity-80 transition-opacity" aria-label="返回融销通首页">
            <span className="inline-block h-6 w-6 rounded-[6px] border-2 border-[#DE7D4D] relative">
              <span className="absolute left-1/2 bottom-[3px] h-[8px] w-[2px] -translate-x-1/2 bg-[#DE7D4D] rounded" />
            </span>
            <span className="text-lg font-medium tracking-wide">融销通</span>
            <span className="ml-1 hidden text-xs text-white/40 sm:inline">农产品商城</span>
          </Link>
          <div className="flex items-center gap-3">
            <a
              href={platformUrl('/trade/products')}
              className="hidden rounded-sm border border-white/20 px-4 py-2 text-sm text-white/70 transition-colors hover:border-white/50 hover:text-white sm:inline-block"
            >
              进入平台 →
            </a>
            <Link
              to="/"
              className="rounded-sm border border-white/20 px-4 py-2 text-sm text-white/70 transition-colors hover:border-white/50 hover:text-white"
            >
              ← 返回首页
            </Link>
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-[1728px] px-5 py-10 lg:px-10 lg:py-14">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="text-4xl font-light sm:text-5xl">今日货源</h1>
            <p className="mt-3 text-white/50">
              {source === 'api'
                ? `共 ${page?.total ?? list.length} 条在售货源 · 数据来自平台 /trade/products 接口`
                : '本地演示数据 · 启动后端后自动切换为平台真实货源'}
            </p>
          </div>
          <span
            className={`rounded-sm px-3 py-1.5 text-xs ${
              source === 'api' ? 'bg-[#DE7D4D]/15 text-[#DE7D4D]' : 'bg-white/10 text-white/50'
            }`}
          >
            {source === 'api' ? '数据源：平台接口' : '数据源：本地演示'}
          </span>
        </div>

        {/* 分类筛选 */}
        <div className="mt-8 flex flex-wrap gap-3">
          {cats.map((c) => (
            <button
              key={c}
              onClick={() => setCat(c)}
              className={`rounded-sm px-5 py-2 text-sm transition-colors ${
                c === activeCat ? 'bg-[#DE7D4D] text-black' : 'bg-neutral-800 text-white/60 hover:text-white'
              }`}
            >
              {c}
            </button>
          ))}
        </div>

        {/* 商品网格 */}
        {visible.length === 0 ? (
          <p className="mt-16 text-center text-white/40">该分类下暂无货源</p>
        ) : (
          <div className="mt-8 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
            {visible.map((p) => (
              <div
                key={p.key}
                className="group overflow-hidden rounded-md bg-[#242424] transition-colors hover:bg-[#2c2c2c]"
              >
                <div className="relative aspect-[4/3] overflow-hidden">
                  {p.img ? (
                    <img
                      src={p.img}
                      alt={p.name}
                      loading="lazy"
                      className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                    />
                  ) : (
                    // 该货源既没有实拍图、也匹配不到同类目素材：放中性占位块，不拿错图硬凑
                    <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-[#2b2b2b] to-[#3a2f28] px-4 text-center">
                      <span className="text-sm leading-snug text-white/60">{p.name}</span>
                    </div>
                  )}                  {p.tag && (
                    <span className="absolute left-3 top-3 rounded-sm bg-black/55 px-2 py-1 text-[11px] tracking-wide text-white/90 backdrop-blur-sm">
                      {p.tag}
                    </span>
                  )}
                </div>
                <div className="p-4">
                  <div className="truncate text-[15px] font-medium">{p.name}</div>
                  <div className="mt-1 truncate text-xs text-white/40">
                    {p.origin} · {p.seller}
                  </div>
                  <div className="mt-3 flex items-end justify-between gap-3">
                    <div className="text-[#DE7D4D]">
                      <span className="text-sm">¥</span>
                      <span className="text-xl font-medium">{p.price}</span>
                      <span className="text-xs text-white/40"> / {p.unit}</span>
                    </div>
                    <a
                      href={p.href}
                      className="shrink-0 rounded-sm bg-zinc-200 px-4 py-1.5 text-sm text-black transition-colors hover:bg-[#DE7D4D]"
                    >
                      立即采购
                    </a>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        <p className="mt-12 text-center text-xs text-white/30">
          「立即采购」与「进入平台」跳转到平台业务界面（Vue 应用），下单、担保结算与物流跟踪在那里完成
        </p>
      </main>
    </div>
  )
}
