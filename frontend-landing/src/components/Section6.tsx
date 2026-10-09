import { motion } from "framer-motion";
import arrow from "@/assets/Arrow45.svg";
import { fetchHomeNews, fetchHomeStats, formatCnDate, platformUrl, type ApiKnowledge, type HomeStats } from "@/lib/api";
import { useRemote, REMOTE_STATUS_TEXT } from "@/lib/useRemote";

// 本区所有内容都来自平台接口，**没有本地演示标题**：
//   左卡精选  ← GET /api/home/knowledge 的第 1 条（标题与日期都是真实发布内容）
//   右侧列表  ← 同上接口的 6 条
//   品类页签  ← GET /api/home/stats 的 knowledgeByCategory（真实分类与条数）
// 接口没通时显示「未加载」，不再摆一批编出来的资讯标题。
export function Section6() {
  const { data: knowledge, state } = useRemote(() => fetchHomeNews(6), [] as ApiKnowledge[]);
  const { data: stats, state: statsState } = useRemote<HomeStats | null>(() => fetchHomeStats(), null);

  const featured = knowledge[0];

  const items = knowledge.map((k) => ({
    key: String(k.knowledgeId),
    title: k.title,
    date: formatCnDate(k.createTime),
  }));

  // 品类页签：真实分类 + 真实条数（后端已按条数倒序），前面加一个「全部」
  const catTabs = stats
    ? [
        { label: "全部", count: stats.knowledgeCount ?? 0 },
        ...Object.entries(stats.knowledgeByCategory ?? {}).map(([label, count]) => ({ label, count })),
      ]
    : [];

  const listEmptyText =
    state === "error" ? "平台接口暂不可用，知识条目未加载" : "平台当前暂无已发布的农业知识";

  return (
    <section className="relative z-40 w-full min-h-screen bg-[#1b1b1b] overflow-hidden font-['Inter_Tight','PingFang_SC','Noto_Sans_SC',_sans-serif]">
      <div className="grid grid-cols-1 lg:grid-cols-2 w-full h-full">
        {/* LEFT */}
        <div className="relative col-span-1 w-full h-full flex flex-col justify-start items-center lg:items-start pt-20 pb-20 px-10 lg:pt-[140px] lg:pb-[140px] lg:px-[125px]">
          <motion.img
            src="/landing/bg-card.webp"
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
              {/* 日期取自该条知识的真实发布时间；没有数据时为空白，不编日期 */}
              <span className="text-neutral-900">{featured ? formatCnDate(featured.createTime) : ""}</span>
              <span className="text-neutral-400 tracking-[0.12em]">平台农业知识库</span>
            </div>
            <h3 className="mt-14 lg:mt-24 px-8 text-[30px] lg:text-[40px] leading-[1.15] font-semibold text-neutral-400 tracking-tight">
              {featured ? featured.title : listEmptyText}
            </h3>
            <div className="mt-8 px-8">
              <div className="w-full aspect-[793/700] overflow-hidden">
                {/* 装饰插图：知识条目在库里没有配图（image_url 全为空），这里不假装成该条的实拍图 */}
                <img
                  src="/landing/article-tower.webp"
                  alt="农业知识栏目装饰插图"
                  className="w-full h-full object-cover"
                />
              </div>
            </div>
            <div className="px-8 pb-8 text-[12px] text-neutral-400">
              {REMOTE_STATUS_TEXT[state]}
            </div>
          </motion.div>
          <div
            className="absolute left-0 right-0 top-20 bottom-20 lg:top-[140px] lg:bottom-[140px] z-20 bg-repeat opacity-[0.15] mix-blend-overlay pointer-events-none"
            style={{ backgroundImage: `url(/landing/noise.webp)` }}
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
            {catTabs.map((tab, i) => (
              <motion.span
                key={tab.label}
                className={`text-sm font-medium uppercase ${i === 0 ? "text-white" : "text-white/40"}`}
                initial={{ opacity: 0, y: 10 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, amount: 0.2 }}
                transition={{ duration: 0.5, delay: i * 0.1 }}
              >
                {tab.label} ({tab.count})
              </motion.span>
            ))}
            <motion.span
              className="ml-auto text-xs text-white/30"
              initial={{ opacity: 0 }}
              whileInView={{ opacity: 1 }}
              viewport={{ once: true, amount: 0.2 }}
              transition={{ duration: 0.5, delay: 0.4 }}
            >
              {REMOTE_STATUS_TEXT[statsState]}
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
            {items.length === 0 && (
              <div className="flex items-center py-8 text-white/50 text-lg">{listEmptyText}</div>
            )}
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
