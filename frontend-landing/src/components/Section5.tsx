import { useEffect, useRef, useState } from "react";
import { motion, AnimatePresence, useInView, animate } from "framer-motion";
import { fetchHomeStats, platformUrl, type HomeStats } from "@/lib/api";
import { useRemote, REMOTE_STATUS_TEXT } from "@/lib/useRemote";

const CDN = "https://qclay.design/lovable/ceptral";
const noise = { url: `${CDN}/Noise.png` };
const sideShadow = { url: `${CDN}/Side-Image-Container.png` };

// 背景墙是**配图素材**（Pexels 免费可商用，已本地化到 public/farmers/，答辩不依赖外网），
// 不是数据。它不承载任何数量含义 —— 数量在中间那三个统计里，取自平台接口。
const IMAGES: string[] = Array.from(
  { length: 20 },
  (_, i) => `/farmers/f${String(i + 1).padStart(2, "0")}.jpg`,
);

const COLS = [
  { width: 78,  itemH: 118, marginTopClass: "mt-[120px]" },
  { width: 105, itemH: 158, marginTopClass: "mt-[40px]" },
  { width: 120, itemH: 182, marginTopClass: "-mt-[40px]" },
  { width: 120, itemH: 182, marginTopClass: "-mt-[40px]" },
  { width: 120, itemH: 182, marginTopClass: "-mt-[40px]", white: true },
  { width: 120, itemH: 182, marginTopClass: "-mt-[40px]", white: true },
  { width: 120, itemH: 182, marginTopClass: "-mt-[40px]" },
  { width: 120, itemH: 182, marginTopClass: "-mt-[40px]" },
  { width: 105, itemH: 158, marginTopClass: "mt-[40px]" },
  { width: 78,  itemH: 118, marginTopClass: "mt-[120px]" },
];
const ITEMS_PER_COL = 5;

const randomImage = () => IMAGES[Math.floor(Math.random() * IMAGES.length)];

/**
 * 数字滚动。只负责把「平台真实数字」从 0 滚到目标值 ——
 * 目标值由父组件从接口取，这里不产生任何数字。
 */
function CountUp({ value, className }: { value: number; className?: string }) {
  const ref = useRef<HTMLSpanElement>(null);
  const inView = useInView(ref, { once: true, margin: "-100px" });
  const [shown, setShown] = useState(0);

  useEffect(() => {
    if (!inView) return;
    const controls = animate(0, value, {
      duration: 1.2,
      ease: "easeOut",
      onUpdate: (latest) => setShown(Math.round(latest)),
    });
    return () => controls.stop();
  }, [inView, value]);

  return <span ref={ref} className={className}>{shown}</span>;
}

/** 三个统计项。数值全部来自 GET /api/home/stats，前端只负责显示。 */
const STAT_ITEMS = [
  { key: "productCount", label: "在售货源", unit: "条" },
  { key: "demandCount", label: "有效求购", unit: "条" },
  { key: "knowledgeCount", label: "农业知识", unit: "篇" },
] as const;

export function Section5() {
  const sectionRef = useRef<HTMLDivElement>(null);

  // 平台真实运行数据。取不到就显示失败态，不再用「3.2 万农户」这类写死的宣传数字顶着。
  const { data: stats, state } = useRemote<HomeStats | null>(() => fetchHomeStats(), null);

  const [grid, setGrid] = useState<string[][]>(() =>
    COLS.map((c) => (c.white ? [] : Array.from({ length: ITEMS_PER_COL }, randomImage)))
  );

  useEffect(() => {
    const activeCells: { col: number; row: number }[] = [];
    COLS.forEach((c, ci) => {
      if (!c.white) {
        for (let r = 0; r < ITEMS_PER_COL; r++) activeCells.push({ col: ci, row: r });
      }
    });

    const id = setInterval(() => {
      setGrid((prev) => {
        const next = prev.map((col) => col.slice());
        for (let i = 0; i < 5; i++) {
          const cell = activeCells[Math.floor(Math.random() * activeCells.length)];
          next[cell.col][cell.row] = randomImage();
        }
        return next;
      });
    }, 2000);
    return () => clearInterval(id);
  }, []);

  return (
    <section ref={sectionRef} className="sticky top-0 z-0 h-[105vh] overflow-hidden bg-gradient-to-b from-[#523426] to-[#865438] font-['Inter_Tight','PingFang_SC','Noto_Sans_SC',_sans-serif]">

      {/* 10-column grid */}
      <motion.div
        initial={{ scale: 1.4, opacity: 0 }}
        whileInView={{ scale: 1, opacity: 1 }}
        transition={{ duration: 1.5, ease: "easeOut" }}
        viewport={{ once: true, margin: "-100px" }}
        className="absolute inset-0 flex justify-center gap-[32px] z-10 w-max left-1/2 -translate-x-1/2"
      >
        <div className="flex justify-center gap-[32px] scale-[1.04]">
        {COLS.map((col, ci) => (
          <div key={ci} className={`flex flex-col gap-[20px] ${col.marginTopClass}`} style={{ width: `${col.width}px` }}>
            {Array.from({ length: ITEMS_PER_COL }).map((_, ri) => {
              const src = grid[ci]?.[ri];
              if (col.white) {
                return <div key={ri} className="w-full bg-white rounded-sm" style={{ height: `${col.itemH}px` }} />;
              }
              return (
                <div key={ri} className="relative w-full overflow-hidden rounded-sm" style={{ height: `${col.itemH}px` }}>
                  <AnimatePresence mode="wait">
                    <motion.img key={src} src={src} alt=""
                      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.8 }}
                      className="absolute inset-0 w-full h-full object-cover" />
                  </AnimatePresence>
                </div>
              );
            })}
          </div>
        ))}
        </div>
      </motion.div>

      {/* Overlays */}
      <div className="absolute inset-0 z-20 bg-[#865438]/50 mix-blend-multiply pointer-events-none" />
      <div className="absolute inset-0 z-[110] bg-gradient-to-b from-[#865438] to-[#865438] opacity-[0.05] pointer-events-none" />
      <div className="absolute inset-0 z-[100] bg-cover bg-center bg-no-repeat opacity-[0.15] mix-blend-overlay pointer-events-none" style={{ backgroundImage: `url(${noise.url})` }} />

      {/* Center Text UI */}
      <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 flex flex-col items-center z-[130] w-full px-4">
        <motion.div initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} transition={{ duration: 0.8, delay: 0.5 }} viewport={{ once: true }}
          className="text-white/80 text-lg lg:text-xl font-normal mb-8 lg:mb-12">
          平台实时运行数据
        </motion.div>

        {stats ? (
          <div className="flex flex-col items-center gap-8 sm:flex-row sm:items-end sm:gap-12 lg:gap-24">
            {STAT_ITEMS.map((item, i) => (
              <motion.div key={item.key}
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.8, delay: 0.5 + i * 0.12 }}
                viewport={{ once: true }}
                className="flex flex-col items-center">
                <div className="flex items-baseline gap-2">
                  <CountUp value={stats[item.key] ?? 0}
                    className="text-white text-[64px] sm:text-[76px] lg:text-[96px] leading-none font-[550] tabular-nums" />
                  <span className="text-white/70 text-base lg:text-xl">{item.unit}</span>
                </div>
                <div className="mt-3 text-white/75 text-base lg:text-lg">{item.label}</div>
              </motion.div>
            ))}
          </div>
        ) : (
          /* 接口没通：如实说没拿到，不拿写死的宣传数字顶上 */
          <div className="flex min-h-[110px] items-center justify-center">
            <span className="text-white/70 text-lg lg:text-xl">
              {state === "error" ? "平台接口暂不可用，数据未加载" : "正在同步平台数据…"}
            </span>
          </div>
        )}

        <motion.p initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} transition={{ duration: 0.8, delay: 0.5 }} viewport={{ once: true }}
          className="text-zinc-300 text-base lg:text-lg text-center max-w-[280px] sm:max-w-sm mt-10 lg:mt-14 leading-relaxed px-4">
          从农户发布的每一条货源，到采购方签下的每一份订单，平台上的每一次撮合，都真实发生在田间地头。
        </motion.p>

        <motion.a
          href={platformUrl("/register")}
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.5 }}
          viewport={{ once: true }}
          className="mt-8 lg:mt-12 bg-white text-black text-xl font-normal px-10 py-5 rounded-[2px] hover:bg-gray-100 transition-colors inline-block">
          加入平台
        </motion.a>

        <motion.div initial={{ opacity: 0 }} whileInView={{ opacity: 1 }} transition={{ duration: 0.8, delay: 0.7 }} viewport={{ once: true }}
          className="mt-6 text-xs text-white/45">
          {REMOTE_STATUS_TEXT[state]}
        </motion.div>
      </div>

      {/* Absolute Top Layer Side Shadow */}
      <img src={sideShadow.url} alt="" className="absolute top-0 left-1/2 -translate-x-1/2 w-[984px] h-full object-cover pointer-events-none z-[120]" />
    </section>
  );
}
