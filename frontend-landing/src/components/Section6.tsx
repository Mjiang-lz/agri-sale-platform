import { motion } from "framer-motion";
import bgCard from "@/assets/BGCard.png.asset.json";
import articlePhoto from "@/assets/imageTower.png.asset.json";
import noise from "@/assets/Noise.png.asset.json";
import arrow from "@/assets/Arrow45.svg";
import { fetchHomeNews, formatCnDate, platformUrl, type ApiKnowledge } from "@/lib/api";
import { useRemote } from "@/lib/useRemote";

// 本地演示标题；接口 GET /api/home/knowledge?limit=6 通了就用真实资讯
// 注：后端 HomeServiceImpl.listLatestKnowledge 目前仍是 TODO 空实现（返回空数组），
// 所以本区现在稳定走演示数据，账号里那条「数据源」标签会把这一点如实标出来。
const DEMO_ITEMS = [
  "中央一号文件里的农产品流通要点",
  "秋收季主要产区价格周报（第 38 周）",
  "冷链与仓储：农产品上行的最后一公里",
  "农户融资担保方式对比：信用、联保与仓单质押",
  "病虫害高发期农事提醒与用药规范",
  "平台新功能：一键发起求购并自动匹配货源",
];

export function Section6() {
  // 接口优先、静态兜底：GET /api/home/knowledge?limit=6（公开接口）
  const { data: knowledge, source } = useRemote(
    () => fetchHomeNews(DEMO_ITEMS.length),
    [] as ApiKnowledge[],
  );

  const items = knowledge.length
    ? knowledge.map((k) => ({ key: String(k.knowledgeId), title: k.title, date: formatCnDate(k.createTime) }))
    : DEMO_ITEMS.map((t, i) => ({ key: `demo-${i}`, title: t, date: "" }));

  return (
    <section className="relative z-40 w-full min-h-screen bg-[#1b1b1b] overflow-hidden font-['Inter_Tight','PingFang_SC','Noto_Sans_SC',_sans-serif]">
      <div className="grid grid-cols-1 lg:grid-cols-2 w-full h-full">
        {/* LEFT */}
        <div className="relative col-span-1 w-full h-full flex flex-col justify-start items-center lg:items-start pt-20 pb-20 px-10 lg:pt-[140px] lg:pb-[140px] lg:px-[125px]">
          <motion.img
            src={bgCard.url}
            alt=""
            className="absolute left-0 right-0 top-20 bottom-20 lg:top-[140px] lg:bottom-[140px] w-full object-cover z-0"
            initial={{ y: 150, opacity: 0 }}
            whileInView={{ y: 0, opacity: 1 }}
            viewport={{ once: true, amount: 0.2 }}
            transition={{ duration: 0.8, ease: "easeOut" }}
          />
          <motion.div
            className="relative z-10 w-full max-w-[530px] shadow-2xl mt-10 lg:mt-20 bg-white"
            initial={{ y: 150, opacity: 0 }}
            whileInView={{ y: 0, opacity: 1 }}
            viewport={{ once: true, amount: 0.2 }}
            transition={{ duration: 0.8, delay: 0.2, ease: "easeOut" }}
          >
            <div className="flex items-center justify-between px-8 pt-8 text-[13px]">
              <span className="text-neutral-900">2025 年 9 月 22 日</span>
              <span className="text-neutral-400 tracking-[0.12em]">融销通研究院</span>
            </div>
            <h3 className="mt-14 lg:mt-24 px-8 text-[30px] lg:text-[40px] leading-[1.15] font-semibold text-neutral-400 tracking-tight">
              农产品流通的
              <br />
              数字化路径
            </h3>
            <div className="mt-8 px-8">
              <div className="w-full aspect-[793/700] overflow-hidden">
                <img
                  src={articlePhoto.url}
                  alt="麦田与风机：农产品流通数字化路径配图"
                  className="w-full h-full object-cover"
                />
              </div>
            </div>
          </motion.div>
          <div
            className="absolute left-0 right-0 top-20 bottom-20 lg:top-[140px] lg:bottom-[140px] z-20 bg-repeat opacity-[0.15] mix-blend-overlay pointer-events-none"
            style={{ backgroundImage: `url(${noise.url})` }}
          />
        </div>

        {/* RIGHT */}
        <div className="col-span-1 flex flex-col pt-20 pb-20 px-10 lg:pt-[140px] lg:pb-[140px] lg:pr-[120px] lg:pl-10 font-['Inter_Tight','PingFang_SC','Noto_Sans_SC',_sans-serif]">
          <div className="flex flex-col items-start gap-6 w-full lg:flex-row lg:justify-between lg:items-start lg:gap-8">
            <motion.h2
              className="text-3xl sm:text-4xl lg:text-[60px] leading-[1.05] font-['Inter_Tight','PingFang_SC','Noto_Sans_SC',_sans-serif] max-w-[500px] lg:max-w-[600px]"
              initial={{ opacity: 0 }}
              whileInView={{ opacity: 1 }}
              viewport={{ once: true, amount: 0.2 }}
              transition={{ duration: 0.8 }}
            >
              <span className="text-white">平台资讯，</span>{" "}
              <span className="text-stone-300">政策与农事提醒</span>
            </motion.h2>
            <motion.a
              href={platformUrl('/expert/knowledge')}
              className="bg-white text-black text-base px-7 py-4 flex items-center gap-2 whitespace-nowrap cursor-pointer font-['Inter_Tight','PingFang_SC','Noto_Sans_SC',_sans-serif] shrink-0 rounded-[2px]"
              initial={{ scale: 0 }}
              whileInView={{ scale: 1 }}
              viewport={{ once: true, amount: 0.2 }}
              transition={{ duration: 0.5, ease: "backOut" }}
            >
              查看全部
              <img src={arrow} className="w-3 h-3" alt="" />
            </motion.a>
          </div>

          <motion.div
            className="flex flex-wrap items-center gap-4 lg:gap-8 mt-10 lg:mt-16 w-full"
            initial={{ opacity: 0 }}
            whileInView={{ opacity: 1 }}
            viewport={{ once: true, amount: 0.2 }}
            transition={{ duration: 0.8 }}
          >
            <motion.span
              className="text-sm font-medium uppercase text-white"
              initial={{ opacity: 0, y: 10 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, amount: 0.2 }}
              transition={{ duration: 0.5, delay: 0 }}
            >
              全部 (17)
            </motion.span>
            <motion.span
              className="text-sm font-medium uppercase text-white/40"
              initial={{ opacity: 0, y: 10 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, amount: 0.2 }}
              transition={{ duration: 0.5, delay: 0.1 }}
            >
              政策解读 (7)
            </motion.span>
            <motion.span
              className="text-sm font-medium uppercase text-white/40"
              initial={{ opacity: 0, y: 10 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, amount: 0.2 }}
              transition={{ duration: 0.5, delay: 0.2 }}
            >
              行情周报 (4)
            </motion.span>
            <motion.span
              className="text-sm font-medium uppercase text-white/40"
              initial={{ opacity: 0, y: 10 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, amount: 0.2 }}
              transition={{ duration: 0.5, delay: 0.3 }}
            >
              农事提醒 (6)
            </motion.span>
            <motion.span
              className="ml-auto text-xs text-white/30"
              initial={{ opacity: 0 }}
              whileInView={{ opacity: 1 }}
              viewport={{ once: true, amount: 0.2 }}
              transition={{ duration: 0.5, delay: 0.4 }}
            >
              {source === "api" ? "数据源：平台接口" : "数据源：本地演示"}
            </motion.span>
          </motion.div>
          <motion.div
            className="w-full lg:w-[calc(100%+80px)] h-[1px] bg-white/25 mt-6 mb-10 lg:mb-12 origin-left"
            style={{ transformOrigin: "left" }}
            initial={{ scaleX: 0 }}
            whileInView={{ scaleX: 1 }}
            viewport={{ once: true, amount: 0.2 }}
            transition={{ duration: 0.8, ease: "easeInOut" }}
          />

          <div className="flex flex-col w-full lg:w-[calc(100%+80px)]">
            {items.map((item, index) => (
              <motion.div
                key={item.key}
                className="flex flex-col cursor-pointer group"
                initial={{ opacity: 0 }}
                whileInView={{ opacity: 1 }}
                viewport={{ once: true, amount: 0.2 }}
                transition={{ duration: 0.5, delay: index * 0.1 }}
              >
                <div className="flex justify-between items-center gap-6 pt-6 pb-[20px] px-10 -mx-10 hover:bg-white/5 transition-colors">
                  <span className="text-lg text-white/60 group-hover:text-white transition-colors font-['Inter_Tight','PingFang_SC','Noto_Sans_SC',_sans-serif]">
                    {item.title}
                  </span>
                  <span className="flex items-center gap-4 shrink-0">
                    {item.date && (
                      <span className="hidden text-xs text-white/30 sm:inline">{item.date}</span>
                    )}
                    <img src={arrow} className="w-3 h-3 brightness-0 invert opacity-100" alt="" />
                  </span>
                </div>
                <motion.div
                  className="w-full h-[1px] bg-white/25"
                  style={{ transformOrigin: "left" }}
                  initial={{ scaleX: 0 }}
                  whileInView={{ scaleX: 1 }}
                  viewport={{ once: true, amount: 0.2 }}
                  transition={{ duration: 0.8, ease: "easeInOut" }}
                />
              </motion.div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
