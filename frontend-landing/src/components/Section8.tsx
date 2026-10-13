import { motion } from "framer-motion";
import { Link } from "@tanstack/react-router";

export function Section8() {
  // 版权行按空格切词，每个词一个 mr-[5px]（原实现的分词间距机制保留）
  const copyWords = "© 融销通 2026. 保留所有权利".split(" ");
  // 按要求移除：「关注我们：」+ LinkedIn/Instagram/Clutch/X 社交图标、
  // 「联系我们」栏（Mail us）、「预约演示」栏（Book a call）。
  // 左栏因此改为整幅宽度。

  return (
    <footer className="relative z-50 w-full bg-[#1b1b1b] text-white pt-12 pb-8 px-5 lg:pt-16 lg:pb-10 lg:px-10 font-['Inter_Tight','PingFang_SC','Noto_Sans_SC',_sans-serif]">
      <motion.div
        className="absolute top-0 left-0 w-full h-[1px] bg-white/20"
        initial={{ scaleX: 0 }}
        whileInView={{ scaleX: 1 }}
        transition={{ duration: 0.8, ease: "easeInOut" }}
        style={{ transformOrigin: "center" }}
      />

      <div className="grid grid-cols-1 lg:grid-cols-10 gap-8 lg:gap-10 w-full max-w-[1728px] mx-auto">
        {/* LEFT COLUMN —— 原为 lg:col-span-6，中间/右侧两栏移除后改为整幅 */}
        <div className="col-span-1 lg:col-span-10 flex flex-col justify-between font-['Inter_Tight','PingFang_SC','Noto_Sans_SC',_sans-serif]">
          {/* 原为 5 个英文品牌 logo（intel/oracle/...）；融销通没有对应图形资产，
              改为文字形式的生态合作方，沿用同一行的透明度与逐项淡入时序 */}
          <div className="flex flex-row flex-wrap items-center justify-start lg:justify-between w-full gap-x-6 gap-y-4 opacity-40 mb-12 lg:mb-24">
            {[
              "农村信用合作社",
              "农业担保公司",
              "供销合作社",
              "农产品批发市场",
              "冷链物流企业",
            ].map((name, i) => (
              <motion.span
                key={name}
                className="shrink whitespace-nowrap text-[13px] lg:text-[15px] font-medium tracking-wide text-white"
                initial={{ opacity: 0 }}
                whileInView={{ opacity: 1 }}
                viewport={{ once: true, margin: "-50px" }}
                transition={{ duration: 0.6, delay: i * 0.2, ease: "easeOut" }}
              >
                {name}
              </motion.span>
            ))}
          </div>

          <div className="flex flex-wrap items-center text-sm text-white/60 font-['Inter_Tight','PingFang_SC','Noto_Sans_SC',_sans-serif]">
            {copyWords.map((word, index) => (
              <motion.span
                key={index}
                initial={{ opacity: 0 }}
                whileInView={{ opacity: 1 }}
                transition={{ duration: 0.4, delay: index * 0.05 }}
                className="mr-[5px]" // Ideal size of normal space
              >
                {word}
              </motion.span>
            ))}
            <motion.span
              initial={{ opacity: 0 }}
              whileInView={{ opacity: 1 }}
              transition={{ duration: 0.5, delay: 0.6 }}
            >
              •{" "}
              <Link
                to="/privacy"
                className="text-[#A2A2A2] hover:text-white transition-colors underline-offset-4 hover:underline ml-1"
              >
                隐私政策
              </Link>
            </motion.span>
          </div>
        </div>
      </div>
    </footer>
  );
}
