import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import logoUrl from "@/assets/logo.svg";
import arrowDownUrl from "@/assets/ArrowDown.svg";
import arrowRightUrl from "@/assets/ArrowRight.svg";
import { RxtLogo } from "./RxtLogo";
import { platformUrl } from "@/lib/api";

// Hero 背景换成田间素材（Pexels 航拍稻田，免费可商用）
// 当前成片：1920x1080 / 30fps / CRF30 / 16s / 7.4MB（H.264，-movflags +faststart）。
// 压这么狠是安全的：本组件下方叠了两道黑渐变遮罩（顶部 22%、底部 52% 压到 75% 黑），
// 且视频是动态循环播放，CRF30 的轻微软化在实机上基本不可感知。
// 原片（1080p60 / 31MB）与更省的 CRF32 档留在 media-src/（已在 .gitignore 中）。
const heroVideo = { url: "/hero-field.mp4" };

export function Hero() {
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <section className="relative flex min-h-screen w-full flex-col overflow-hidden bg-black text-white lg:min-h-[100vh]">
      <video
        className="absolute inset-0 h-full w-full object-cover object-center"
        style={{ objectPosition: "center 20%" }}
        src={heroVideo.url}
        autoPlay
        loop
        muted
        playsInline
        aria-hidden
      />

      {/* 视频帧明暗跨度很大（有的帧接近纯白），加上下两道渐变遮罩保证文字可读。
          纯白底的浅色帧下，白色正文原本几乎读不出来 —— 中文比英文更密，问题更明显。 */}
      <div className="absolute inset-x-0 top-0 h-[22%] bg-gradient-to-b from-black/55 via-black/15 to-transparent pointer-events-none" />
      <div className="absolute inset-x-0 bottom-0 h-[52%] bg-gradient-to-t from-black/75 via-black/30 to-transparent pointer-events-none" />

      <div className="relative z-10 flex min-h-screen flex-col lg:min-h-[100vh]">
        {/* HEADER — DESKTOP */}
        <header className="hidden w-full items-center justify-between px-10 pt-[26px] pb-[14px] lg:flex">
          <div className="flex items-center gap-4">
            <motion.img src={logoUrl} alt="融销通" className="h-9 w-9"
              initial={{ scale: 0 }} animate={{ scale: 1 }}
              transition={{ duration: 0.5, ease: "backOut" }} />
            <motion.span className="text-2xl font-medium"
              initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.1, ease: "easeOut" }}>
              <RxtLogo />
            </motion.span>
          </div>

          <nav className="flex items-center gap-[60px] text-xs font-semibold uppercase">
            <motion.a href="#capability" className="flex items-center gap-1"
              initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: 0.2, ease: "easeOut" }}>
              平台能力 <img src={arrowDownUrl} alt="" />
            </motion.a>
            <motion.a href="#solution" className="flex items-center gap-1"
              initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: 0.3, ease: "easeOut" }}>
              解决方案 <img src={arrowDownUrl} alt="" />
            </motion.a>
            <motion.a href="#news"
              initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: 0.4, ease: "easeOut" }}>
              资讯中心
            </motion.a>
          </nav>

          <div className="flex items-center gap-[50px]">
            <motion.a href={platformUrl("/login")} className="flex items-center gap-2 text-base font-medium"
              initial={{ scale: 0 }} animate={{ scale: 1 }}
              transition={{ duration: 0.5, delay: 0.4, ease: "backOut" }}>
              登录 <img src={arrowRightUrl} alt="" />
            </motion.a>
            <motion.a href={platformUrl("/register")}
              className="bg-white px-5 py-4 text-base font-normal text-zinc-800 rounded-sm"
              initial={{ scale: 0 }} animate={{ scale: 1 }}
              transition={{ duration: 0.5, delay: 0.4, ease: "backOut" }}>
              免费入驻
            </motion.a>
          </div>
        </header>

        {/* HEADER — MOBILE/TABLET */}
        <header className="flex w-full items-center justify-between px-5 pt-5 pb-2 lg:hidden">
          <div className="flex items-center gap-3">
            <img src={logoUrl} alt="融销通" className="h-8 w-8" />
            <span className="text-xl font-medium"><RxtLogo /></span>
          </div>
          <button type="button"
            aria-label={menuOpen ? "关闭菜单" : "打开菜单"}
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((v) => !v)}
            className="relative flex h-10 w-10 flex-col items-center justify-center gap-[6px]">
            <span className={`block h-[2px] w-6 bg-white transition-transform duration-300 ${menuOpen ? "translate-y-[4px] rotate-45" : ""}`} />
            <span className={`block h-[2px] w-6 bg-white transition-transform duration-300 ${menuOpen ? "-translate-y-[4px] -rotate-45" : ""}`} />
          </button>
        </header>

        <AnimatePresence>
          {menuOpen && (
            <motion.div key="mobile-menu"
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ duration: 0.25, ease: "easeOut" }}
              className="lg:hidden flex flex-col gap-5 px-5 pb-6 text-sm font-semibold uppercase">
              <a href="#capability" className="flex items-center gap-1">平台能力 <img src={arrowDownUrl} alt="" /></a>
              <a href="#solution" className="flex items-center gap-1">解决方案 <img src={arrowDownUrl} alt="" /></a>
              <a href="#news">资讯中心</a>
              <a href={platformUrl("/login")} className="flex items-center gap-2 normal-case text-base font-medium">登录 <img src={arrowRightUrl} alt="" /></a>
              <a href={platformUrl("/register")} className="bg-white px-5 py-3 text-base font-normal normal-case text-zinc-800 w-fit">免费入驻</a>
            </motion.div>
          )}
        </AnimatePresence>

        {/* TOP DIVIDER */}
        <motion.div
          className="mx-auto h-[1px] w-[calc(100%-40px)] bg-white/25 lg:w-[calc(100%-80px)]"
          initial={{ scaleX: 0 }} animate={{ scaleX: 1 }}
          transition={{ duration: 0.8, delay: 0.5, ease: "easeInOut" }}
          style={{ transformOrigin: "center" }} />

        {/* MIDDLE TAGS —— 两侧白字已按需求移除；保留这层 flex-1 占位，hero 垂直排版不变 */}
        <div className="relative flex flex-1 items-center py-10 lg:py-0" />

        {/* BOTTOM MAIN CONTENT */}
        <div className="flex w-full flex-col items-start gap-6 px-5 pb-8 lg:flex-row lg:items-end lg:justify-between lg:px-10 lg:pb-10 lg:gap-10">
          <motion.h1
            className="text-[36px] leading-[1.05] font-normal sm:text-5xl lg:text-[100px] lg:leading-[1] lg:whitespace-nowrap"
            initial={{ opacity: 0, y: 30 }} animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.7, ease: "easeOut" }}>
            让农产品<br />从田间直达市场
          </motion.h1>
          <motion.p
            className="w-full text-sm font-normal text-white/75 lg:w-96 lg:text-base"
            initial={{ opacity: 0, y: 30 }} animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.8, ease: "easeOut" }}>
            融销通打通农户、采购方、专家与金融机构之间的信息壁垒，把农产品交易、融资撮合与专家助力装进同一套系统
          </motion.p>
        </div>

        <motion.div
          className="mx-auto h-[1px] w-[calc(100%-40px)] bg-white/25 lg:w-[calc(100%-80px)]"
          initial={{ scaleX: 0 }} animate={{ scaleX: 1 }}
          transition={{ duration: 0.8, delay: 1, ease: "easeInOut" }}
          style={{ transformOrigin: "center" }} />

        <div className="flex w-full flex-col gap-2 px-5 py-4 text-[10px] font-normal uppercase tracking-wider sm:flex-row sm:items-center sm:justify-between lg:px-10 lg:py-6 lg:text-xs">
          <motion.span className="text-stone-300"
            initial={{ opacity: 0, y: 30 }} animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.9, ease: "easeOut" }}>
            四类角色 · 一个平台 · 全链路打通
          </motion.span>
          <motion.span className="text-neutral-400"
            initial={{ opacity: 0, y: 30 }} animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.9, ease: "easeOut" }}>
            向下滚动了解平台
          </motion.span>
        </div>
      </div>
    </section>
  );
}
