import { motion } from "framer-motion";
import { useState, type ReactNode } from "react";
import lineAsset from "@/assets/Line.svg";
import { WaveGrid } from "./WaveGrid";
import {
  fetchDemandList,
  fetchHotProducts,
  formatPrice,
  platformUrl,
  resolveImage,
  type ApiDemand,
  type ApiProduct,
} from "@/lib/api";
import { pickProductPhoto } from "@/lib/productImages";
import { useRemote, REMOTE_STATUS_TEXT, type RemoteState } from "@/lib/useRemote";

// 四类角色对应四个 tab，内容与平台真实模块对齐；点击可切换
// route = 该 tab 对应的平台前端路由（「进入平台」按当前 tab 跳过去）
// 注意：这里写的是 frontend-web 的 Vue 路由（/trade/demand），不是接口路径（/api/demand/list）
//
// title = 大标题，desc = 副标题，两者都跟随当前 tab 切换。
// ⚠️ 大标题必须与同一 tab 的 desc 语义一致（改 desc 时同步改 title），且控制在 7 个汉字以内
//    —— 标题是 60px 且带 lg:whitespace-nowrap，超过 7 字会在窄屏被 section 的 overflow-hidden 裁掉。
const TABS = [
  {
    label: "找货源",
    route: "/trade/products",
    title: "货源需求对上号",
    desc: "平台按品类、产地、价格与信用画像做双边匹配，把货源和需求放进同一个池子里对上——实时更新",
    items: [
      "按品类、产地、价格区间检索货源",
      "按上市时间与起订量筛选采购机会",
      "查看供需比与近 30 天成交行情",
      "一键发起求购，或直接对接采购方",
    ],
  },
  {
    label: "找销路",
    route: "/trade/demand",
    title: "好货不等人上门",
    desc: "农户与合作社发布货源后，由平台撮合采购方、商超与批发市场，好货不再等人上门",
    items: [
      "发布货源后自动进入采购方推荐池",
      "按销量与复购率挑选优质采购方",
      "大宗订单走平台担保，分批交付验收",
      "成交自动生成台账，对账开票不操心",
    ],
  },
  {
    label: "找资金",
    route: "/finance/apply",
    title: "订单变成周转金",
    desc: "凭真实交易数据对接银行与担保公司，让每一笔订单都能变成周转资金",
    items: [
      "凭历史成交与信用记录申请授信",
      "订单融资：签了合同即可预支部分货款",
      "仓单质押，把在库存货变成周转金",
      "银行直连，利率费用透明不上中介",
    ],
  },
  {
    label: "找专家",
    route: "/expert/qa",
    title: "种到卖全程护航",
    desc: "从种到卖全程有专家护航：农技、经营、电商、政策，四类顾问在线应答",
    items: [
      "农技问诊：病虫害拍照上传即有答复",
      "产销规划：种什么、种多少用数据说话",
      "电商运营陪跑，直播带货上手即用",
      "政策与保险解读，补贴申报有人带",
    ],
  },
];

/**
 * 右侧展示卡的内容。
 *
 * 四个 tab 共用**同一套版式**（图一的样子），只有内容跟着 tab 换 ——
 * 切 tab 时右边不再是一张永远不变的菜籽油卡片。数据来源分两类：
 *
 *   ① 找货源 / 找销路 —— 平台**真实数据**（在售货源、求购需求），接口能通就用真的；
 *   ② 找资金 / 找专家 —— 平台目前没有对应的**公开**接口（融资产品、专家问答都在登录后，
 *      不在 PublicEndpoints 白名单里），所以这两张卡是**产品功能示意**：
 *      文案只描述平台已有的能力，与同 tab 的左侧要点一一对应，
 *      且**不出现任何统计数字**，不把"示意"写成"编数据"。
 */
type CardView = {
  /** 左上角小字：产地 / 期望产区 / 服务名 */
  kicker: string;
  /** 第二行主属性 */
  line1: ReactNode;
  /** 第三行次属性 */
  line2: ReactNode;
  /** 第三行下方的小字注脚 */
  footnote: string;
  /** 卡片配图（本地素材，答辩不依赖外网） */
  image: string;
  imageAlt: string;
  /** 图片左下角角标 */
  badge: string;
  /** 底部大字 */
  title: string;
  /** 底部两行说明 */
  desc: [string, string];
  /** 底部指标：数值 / 单位 / 说明 */
  metric: string;
  metricUnit: string;
  metricLabel: string;
};

/** 找货源 → 在售货源里最新的一条。字段全部来自接口，没有一处写死 */
function productCard(p: ApiProduct, state: RemoteState): CardView {
  return {
    kicker: p.origin || "产地待完善",
    line1: (
      <>
        {p.category || "农产品"} · 在售价{" "}
        <span className="font-medium">¥{formatPrice(p.price)}</span>
        {p.unit ? ` / ${p.unit}` : ""}
      </>
    ),
    line2: (
      <>
        在售库存{" "}
        <span className="font-medium">
          {p.stock ?? 0} {p.unit || ""}
        </span>
      </>
    ),
    footnote: REMOTE_STATUS_TEXT[state],
    image: resolveImage(p.imageUrl) ?? pickProductPhoto(p, 0) ?? "",
    imageAlt: p.productName,
    badge: p.category || "在售货源",
    title: p.productName,
    desc: ["已通过平台内容审核", "发布者为平台注册农户"],
    metric: String(p.stock ?? 0),
    metricUnit: p.unit || "",
    metricLabel: "在售库存",
  };
}

/**
 * 找销路 → 最新一条求购需求。
 *
 * 农户视角下「谁在买、要多少、什么价」就是销路本身。
 * 刻意**不展示 description**：那是用户自由填写的文本，而落地页是对外展示位。
 */
function demandCard(d: ApiDemand, state: RemoteState): CardView {
  const price = formatPrice(d.expectPrice);
  const negotiable = price === "面议";
  return {
    kicker: d.region || "产区待完善",
    line1: `${d.category || "农产品"} · 采购需求`,
    line2: (
      <>
        期望价{" "}
        <span className="font-medium">{negotiable ? price : `¥${price}`}</span>
        {!negotiable && d.unit ? ` / ${d.unit}` : ""}
      </>
    ),
    footnote: REMOTE_STATUS_TEXT[state],
    image: "/solution/sales.jpg",
    imageAlt: "农户把分装好的粮食装车外运",
    badge: d.category || "求购需求",
    title: `求购 ${d.category || "农产品"}`,
    desc: ["采购方正在平台找货", "担保交易，批量交付验收"],
    metric: String(d.quantity ?? 0),
    metricUnit: d.unit || "",
    metricLabel: "求购数量",
  };
}

/**
 * 找资金 / 找专家的展示卡（功能示意，按 tab 名称索引）。图与文指向同一个主题：
 *   找资金 —— 麻袋里的现货就是可变现的资产（对应左侧「仓单质押」）
 *   找专家 —— 田间看长势就是问诊现场（对应左侧「农技问诊」）
 * 「3 种融资方式」「4 类在线顾问」的条数取自同 tab 的要点，不是统计数字。
 */
const STATIC_CARDS: Record<string, CardView> = {
  找资金: {
    kicker: "资金服务",
    line1: "凭真实交易数据对接银行",
    line2: "银行与担保公司直连",
    footnote: "以平台订单与成交记录为依据",
    image: "/solution/finance.jpg",
    imageAlt: "农户从麻袋中捧起刚收的粮食",
    badge: "资金服务",
    title: "订单融资",
    desc: ["签合同即可预支部分货款", "仓单质押，在库存货变周转金"],
    metric: "3",
    metricUnit: "种",
    metricLabel: "融资方式",
  },
  找专家: {
    kicker: "专家服务",
    line1: "农技、经营、电商、政策",
    line2: "四类顾问在线应答",
    footnote: "提问与答复全程留痕",
    image: "/solution/expert.jpg",
    imageAlt: "农技人员在田间查看作物长势",
    badge: "专家服务",
    title: "农技问诊",
    desc: ["病虫害拍照上传即有答复", "产销规划与电商运营陪跑"],
    metric: "4",
    metricUnit: "类",
    metricLabel: "在线顾问",
  },
};

/** 用真数据的两个 tab，没有数据时说什么（按 tab 名称索引） */
const EMPTY_TEXT: Record<string, { error: string; empty: string; hint: string }> = {
  找货源: {
    error: "平台接口暂不可用",
    empty: "平台当前暂无在售货源",
    hint: "本卡片展示的是平台真实在售货源，没有数据时保持为空",
  },
  找销路: {
    error: "平台接口暂不可用",
    empty: "平台当前暂无求购需求",
    hint: "本卡片展示的是平台真实求购需求，没有数据时保持为空",
  },
};

export function Section4() {
  const [active, setActive] = useState(0);
  const tab = TABS[active];

  // 右侧卡片绑定平台真实数据：
  //   找货源 → 在售货源里最新的一条（GET /home/products?limit=1）
  //   找销路 → 最新一条求购需求     （GET /demand/list?pageSize=1）
  // 两个都是白名单里的公开只读接口，游客可访问；其余两个 tab 用 STATIC_CARDS。
  const { data: products, state: productsState } = useRemote(
    () => fetchHotProducts(1),
    [] as ApiProduct[],
  );
  const { data: demands, state: demandsState } = useRemote(
    () => fetchDemandList(1),
    { records: [] as ApiDemand[], total: 0 },
  );

  const card: CardView | null =
    active === 0
      ? products[0]
        ? productCard(products[0], productsState)
        : null
      : active === 1
        ? demands.records[0]
          ? demandCard(demands.records[0], demandsState)
          : null
        : STATIC_CARDS[tab.label] ?? null;

  const emptyText = EMPTY_TEXT[tab.label] ?? {
    error: "平台接口暂不可用",
    empty: "暂无数据",
    hint: "本卡片展示的是平台真实数据，没有数据时保持为空",
  };
  const dataState: RemoteState = active === 0 ? productsState : demandsState;

  // id="solution" —— Hero 顶部导航「解决方案」的锚点。2026-10-10 补。
  // ⚠️ 注释写在 return 之外：放进 return 内且与根元素并列会让 JSX 有两个顶层节点，整页 500。
  return (
    <section id="solution" className="w-full min-h-[90vh] bg-[#1b1b1b] text-white px-5 py-12 lg:px-10 lg:py-20 flex flex-col overflow-hidden font-['Inter_Tight','PingFang_SC','Noto_Sans_SC',_sans-serif]">
      {/* Top Tabs —— 点击切换 */}
      <div className="flex flex-col sm:flex-row sm:flex-wrap w-full gap-3 lg:gap-5 mb-8 lg:mb-10">
        {TABS.map((t, index) => (
          <motion.button
            key={t.label}
            type="button"
            onClick={() => setActive(index)}
            aria-pressed={index === active}
            initial={{ scale: 0 }}
            whileInView={{ scale: 1 }}
            transition={{ duration: 0.5, ease: "backOut", delay: index * 0.1 }}
            viewport={{ once: true, margin: "-50px" }}
            className={`uppercase text-sm lg:text-base px-4 py-3 lg:px-6 text-center w-full sm:flex-1 transition-colors ${
              index === active ? "bg-[#DE7D4D] text-black" : "bg-neutral-800 text-white/60 hover:text-white"
            }`}
          >
            {t.label}
          </motion.button>
        ))}
      </div>

      {/* Main Content */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-16 w-full max-w-[1728px] mx-auto">
        {/* Left Column */}
        <div className="col-span-1 lg:col-span-6 flex flex-col">
          {/* key 里带 active：切 tab 时强制重建，让 whileInView 的入场动画重播一遍
              （副标题同样用 key={`desc-${active}`} 达到这个效果） */}
          <motion.h2
            key={`title-${active}`}
            initial={{ opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.2, ease: "easeOut" }}
            viewport={{ once: true, margin: "-50px" }}
            className="text-[36px] sm:text-5xl lg:text-[60px] leading-tight mb-4 lg:whitespace-nowrap"
          >
            {tab.title}
          </motion.h2>
          <motion.p
            key={`desc-${active}`}
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, ease: "easeOut" }}
            className="text-white/50 text-base max-w-md mb-8"
          >
            {tab.desc}
          </motion.p>
          <motion.a
            href={platformUrl(tab.route)}
            initial={{ scale: 0 }}
            whileInView={{ scale: 1 }}
            transition={{ duration: 0.5, delay: 0.4, ease: "backOut" }}
            viewport={{ once: true, margin: "-50px" }}
            className="bg-zinc-300 text-black text-base px-8 py-3 w-fit mb-24 rounded-sm inline-block"
          >
            进入平台 →
          </motion.a>

          <div className="mt-auto max-w-[384px]">
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: 0.5, ease: "easeOut" }}
              viewport={{ once: true, margin: "-50px" }}
              className="text-sm text-white mb-1"
            >
              货源、需求、行情一屏尽览
            </motion.div>
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: 0.55, ease: "easeOut" }}
              viewport={{ once: true, margin: "-50px" }}
              className="text-sm text-white/50 mb-6"
            >
              覆盖主要农产品产区，支持按品类、产地、价格区间与上市时间组合筛选，全流程走担保交易
            </motion.div>
            {(tab.items as string[]).map((item, i) => (
              <div key={`${active}-${item}`} className="relative py-4">
                <motion.div
                  initial={{ width: "0%" }}
                  animate={{ width: "82%" }}
                  transition={{ duration: 0.5, delay: i * 0.08, ease: "easeInOut" }}
                  className="absolute top-0 left-0 h-px bg-neutral-700"
                />
                <motion.div
                  initial={{ opacity: 0, y: 12 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.4, delay: i * 0.08, ease: "easeOut" }}
                  className="text-white/30 text-sm"
                >
                  {item}
                </motion.div>
              </div>
            ))}
          </div>
        </div>

        {/* Right Column */}
        <motion.div
          initial={{ opacity: 0, y: 150 }}
          whileInView={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.9, delay: 0.3, ease: [0.25, 1, 0.5, 1] }}
          viewport={{ once: true, margin: "-50px" }}
          className="col-span-1 lg:col-span-6 relative flex items-center justify-center"
        >
          <div className="flex flex-col items-center pt-[30px] pb-[15px] relative w-full h-fit self-center overflow-hidden bg-[#2F2F2F]">
            {/* 3D Wave Grid */}
            <div
              className="absolute inset-0 z-0 overflow-hidden pointer-events-none"
              style={{
                WebkitMaskImage: "radial-gradient(ellipse at center, black 0%, transparent 70%)",
                maskImage: "radial-gradient(ellipse at center, black 0%, transparent 70%)",
              }}
            >
              <WaveGrid />
            </div>

            {/* Layer 2: Static iPhone Camera Grid Overlay */}
            <div className="absolute inset-0 pointer-events-none z-10">
              <div className="grid grid-cols-3 grid-rows-3 w-full h-full opacity-10">
                {Array.from({ length: 9 }).map((_, i) => {
                  const col = i % 3;
                  const row = Math.floor(i / 3);
                  const borderR = col < 2 ? "border-r border-white" : "";
                  const borderB = row < 2 ? "border-b border-white" : "";
                  return <div key={i} className={`${borderR} ${borderB}`} />;
                })}
              </div>
            </div>

            {/* Card —— 内容跟着上面的 tab 走：图与文一起换。
                  找货源 / 找销路 用平台真实数据，找资金 / 找专家 用功能示意卡；
                  卡里每个字段要么来自接口，要么是产品功能说明 ——
                  不写死「今日均价 ¥3.62」「撮合中 128 笔」这类平台上并不存在的数据。
                  key 里带 active：切 tab 时整卡重建（淡入），配图随之更换。 */}
            <div className="relative z-20 w-[280px] lg:w-[340px]">
              <motion.div
                key={`card-${active}`}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.45, ease: "easeOut" }}
                className="block w-full shadow-2xl rounded-lg overflow-hidden bg-[#2C2C2C]"
              >
                {card ? (
                  <>
                    {/* 顶部：小字 + 两行属性 + 数据状态 */}
                    <div className="px-5 pt-6 pb-5 lg:px-6">
                      <div className="text-white/55 text-[11px] tracking-[0.18em]">
                        {card.kicker}
                      </div>
                      <div className="mt-4 text-white text-[13px]">{card.line1}</div>
                      <div className="mt-2 text-white text-[13px]">{card.line2}</div>
                      <div className="mt-2 text-white/40 text-[11px]">{card.footnote}</div>
                    </div>

                    {/* 中部：该 tab 的主题配图 */}
                    <div className="relative h-[300px] lg:h-[340px] w-full overflow-hidden">
                      <img
                        src={card.image}
                        alt={card.imageAlt}
                        className="absolute inset-0 w-full h-full object-cover"
                      />
                      <div className="absolute inset-0 bg-gradient-to-b from-black/25 via-transparent to-black/45" />
                      {/* 取景准星 */}
                      <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2">
                        <div className="h-[1px] w-[112px] bg-white/70" />
                        <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 h-[13px] w-[13px] border border-white/90 rounded-[2px]" />
                        <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 h-[22px] w-[1px] bg-white/70" />
                        <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 h-[1px] w-[22px] bg-white/70" />
                      </div>
                      <div className="absolute left-5 bottom-4 text-[11px] tracking-[0.14em] text-white/85">
                        {card.badge}
                      </div>
                    </div>

                    {/* 底部：大字 + 两行说明 + 指标 */}
                    <div className="px-5 pt-5 pb-6 lg:px-6 bg-[#3A3A3A]">
                      <div className="text-white text-[20px] lg:text-[22px] leading-tight">
                        {card.title}
                      </div>
                      <div className="mt-2 text-white/45 text-[12px] leading-[1.5]">
                        {card.desc[0]}
                        <br />
                        {card.desc[1]}
                      </div>
                      <div className="mt-8 flex items-end justify-between">
                        <div>
                          <div className="text-white text-[24px] leading-none">
                            {card.metric}{" "}
                            <span className="text-[12px] text-white/50">{card.metricUnit}</span>
                          </div>
                          <div className="mt-1 text-white/40 text-[11px]">{card.metricLabel}</div>
                        </div>
                        <div className="relative w-[74px] h-[74px] flex items-center justify-center">
                          {Array.from({ length: 14 }).map((_, i) => (
                            <span
                              key={i}
                              className="absolute left-1/2 top-1/2 h-[6px] w-[1px] bg-white/30"
                              style={{ transform: `translate(-50%,-50%) rotate(${i * 24 - 156}deg) translateY(-30px)` }}
                            />
                          ))}
                          <div className="relative w-[46px] h-[46px] rounded-full bg-white/25 flex items-center justify-center">
                            <span className="text-white text-[22px] leading-none">+</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </>
                ) : (
                  /* 接口没通 / 确实没有数据：卡片保持版式但如实空着，不填假数据 */
                  <div className="flex h-[520px] flex-col items-center justify-center gap-3 px-8 text-center">
                    <div className="text-white/80 text-[15px]">
                      {dataState === "error" ? emptyText.error : emptyText.empty}
                    </div>
                    <div className="text-white/40 text-[12px] leading-[1.6]">{emptyText.hint}</div>
                  </div>
                )}
              </motion.div>

              {/* Equalizer Bars — dotted columns matching reference shape */}
              <div className="absolute z-30 left-[12px] right-[12px] bottom-[38%] h-[8%] flex justify-between items-end gap-[3px] overflow-hidden pointer-events-none">
                {[
                  10, 15, 20, 15, 10, 15, 20, 15, 10, 15, 10, 5, 15, 25, 40, 55, 70, 85, 90, 95, 90, 85, 70, 55, 40, 25,
                  15, 10, 15, 20, 30, 20, 15, 10, 15, 20, 25, 15, 10, 15,
                ].map((peak, i) => {
                  const low = Math.max(8, peak * 0.35);
                  const duration = 0.5 + (i % 5) * 0.12;
                  const delay = (i % 7) * 0.08;
                  return (
                    <motion.div
                      key={i}
                      className="flex-1 w-[3px] bg-transparent border-l-[3px] border-dotted border-white/80"
                      initial={{ height: `${low}%` }}
                      animate={{
                        height: [`${low}%`, `${peak}%`, `${low}%`],
                        opacity: [0.6, 1, 0.6],
                      }}
                      transition={{
                        duration,
                        delay,
                        repeat: Infinity,
                        repeatType: "mirror",
                        ease: "easeInOut",
                      }}
                    />
                  );
                })}
              </div>
            </div>

            {/* Pagination Line — 40px below card */}
            <img src={lineAsset} alt="" className="relative z-20 mt-[40px] opacity-0" />
          </div>
        </motion.div>
      </div>
    </section>
  );
}
