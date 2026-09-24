import { motion } from "framer-motion";
import { useState } from "react";
import cardPhoto from "@/assets/image-29.png.asset.json";
import lineAsset from "@/assets/Line.svg.asset.json";
import { WaveGrid } from "./WaveGrid";
import { platformUrl } from "@/lib/api";

// 四类角色对应四个 tab，内容与平台真实模块对齐；点击可切换
// route = 该 tab 对应的平台前端路由（「进入平台」按当前 tab 跳过去）
// 注意：这里写的是 frontend-web 的 Vue 路由（/trade/demand），不是接口路径（/api/demand/list）
const TABS = [
  {
    label: "找货源",
    route: "/trade/products",
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
    desc: "从种到卖全程有专家护航：农技、经营、电商、政策，四类顾问在线应答",
    items: [
      "农技问诊：病虫害拍照上传即有答复",
      "产销规划：种什么、种多少用数据说话",
      "电商运营陪跑，直播带货上手即用",
      "政策与保险解读，补贴申报有人带",
    ],
  },
];

export function Section4() {
  const [active, setActive] = useState(0);
  const tab = TABS[active];
  return (
    <section className="w-full min-h-[90vh] bg-[#1b1b1b] text-white px-5 py-12 lg:px-10 lg:py-20 flex flex-col overflow-hidden font-['Inter_Tight','PingFang_SC','Noto_Sans_SC',_sans-serif]">
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
          <motion.h2
            initial={{ opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.2, ease: "easeOut" }}
            viewport={{ once: true, margin: "-50px" }}
            className="text-[36px] sm:text-5xl lg:text-[60px] leading-tight mb-4 lg:whitespace-nowrap"
          >
            精准找到对的人
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
              覆盖主要农产品产区，支持按品类、产地、价格区间与上市时间组合筛选，全流程走担保交易。
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

            {/* Card — 原为 CDN 上的 Card.png（英文天气卡），改为按平台业务信息重建 */}
            <div className="relative z-20 w-[280px] lg:w-[340px]">
              <div className="block w-full shadow-2xl rounded-lg overflow-hidden bg-[#2C2C2C]">
                {/* 顶部：产地 + 行情 */}
                <div className="px-5 pt-6 pb-5 lg:px-6">
                  <div className="text-white/55 text-[11px] tracking-[0.18em]">山东 · 寿光</div>
                  <div className="mt-4 text-white text-[13px]">
                    今日均价 <span className="font-medium">¥3.62</span> / 斤
                  </div>
                  <div className="mt-2 text-white text-[13px]">
                    较昨日 <span className="text-[#DE7D4D]">+2.4%</span>
                  </div>
                  <div className="mt-2 text-white/40 text-[11px]">撮合中 128 笔 · 09:41 更新</div>
                </div>

                {/* 中部：负责人照片 + 取景准星（原素材池是人物肖像，此处按"合作社负责人"用） */}
                <div className="relative h-[300px] lg:h-[340px] w-full overflow-hidden">
                  <img
                    src={cardPhoto.url}
                    alt="寿光绿源合作社负责人"
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
                    合作社负责人
                  </div>
                </div>

                {/* 底部：卖家与库存 */}
                <div className="px-5 pt-5 pb-6 lg:px-6 bg-[#3A3A3A]">
                  <div className="text-white text-[20px] lg:text-[22px] leading-tight">寿光绿源合作社</div>
                  <div className="mt-2 text-white/45 text-[12px] leading-[1.5]">
                    已完成实名认证与产地核验
                    <br />
                    合作农户 46 户 · 认证等级 A
                  </div>
                  <div className="mt-8 flex items-end justify-between">
                    <div>
                      <div className="text-white text-[24px] leading-none">
                        128 <span className="text-[12px] text-white/50">吨</span>
                      </div>
                      <div className="mt-1 text-white/40 text-[11px]">在售库存</div>
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
              </div>

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
            <img src={lineAsset.url} alt="" className="relative z-20 mt-[40px] opacity-0" />
          </div>
        </motion.div>
      </div>
    </section>
  );
}
