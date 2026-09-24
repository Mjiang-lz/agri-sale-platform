import { motion, useMotionValue, useSpring } from "framer-motion";
import { useRef, useState, useMemo } from "react";
import { fetchCollageProducts, resolveImage, type ApiProduct } from "@/lib/api";
import { pickProductPhoto } from "@/lib/productImages";
import { useRemote } from "@/lib/useRemote";

// 商品图来自 Pexels（免费可商用），已下载到 public/products/ 本地，答辩时不依赖外网
// 运行时优先用平台接口的真实货源；接口不通则回落到这些本地演示图

function AnimatedWords({ text, className, delayStart = 0, stagger = 0.06 }:
  { text: string; className?: string; delayStart?: number; stagger?: number }) {
  const words = text.split(" ");
  return (
    <span className={className}>
      {words.map((word, i) => (
        <span key={i} className="inline-block overflow-hidden align-bottom">
          <motion.span className="inline-block"
            initial={{ y: "100%", opacity: 0 }}
            whileInView={{ y: "0%", opacity: 1 }}
            viewport={{ once: true, margin: "-50px" }}
            transition={{ duration: 0.6, delay: delayStart + i * stagger, ease: [0.25, 1, 0.5, 1] }}>
            {word}{i < words.length - 1 ? "\u00A0" : ""}
          </motion.span>
        </span>
      ))}
    </span>
  );
}

const INITIAL_STATES = [
  { width: "22%", y: 160, opacity: 1, marginRight: "40px", height: "320px" },
  { width: "28%", y: -20, opacity: 1, marginRight: "40px", height: "420px" },
  { width: "22%", y: 0,   opacity: 1, marginRight: "40px", height: "320px" },
  { width: "18%", y: 40,  opacity: 1, marginRight: "40px", height: "300px" },
  { width: "0%",  y: 40,  opacity: 0, marginRight: "0px",  height: "300px" },
  { width: "0%",  y: 40,  opacity: 0, marginRight: "0px",  height: "300px" },
];

const WHILE_IN_VIEW_STATES = [
  { width: ["22%", "22%", "0%"], opacity: [1,1,0], marginRight: ["40px","40px","0px"], height: ["320px","320px","320px"] },
  { width: ["28%", "28%", "22%"], y: [-20,-20,140], height: ["420px","420px","320px"] },
  { width: ["22%", "22%", "28%"], y: [0,0,-75], height: ["320px","320px","420px"] },
  { width: ["18%", "18%", "22%"], height: ["300px","300px","450px"], y: [40,40,-15] },
  { width: ["0%",  "0%",  "24%"], height: ["300px","300px","300px"], opacity: [0,0,1] },
  { width: "0%", height: "300px", y: 40, opacity: 0, marginRight: "0px" },
];

const FINAL_SLOTS = [
  { width: "0%",  y: 160, opacity: 0, marginRight: "0px",  height: "320px" },
  { width: "22%", y: 140, opacity: 1, marginRight: "40px", height: "320px" },
  { width: "28%", y: -75, opacity: 1, marginRight: "40px", height: "420px" },
  { width: "22%", y: -15, opacity: 1, marginRight: "40px", height: "450px" },
  { width: "24%", y: 40,  opacity: 1, marginRight: "0px",  height: "300px" },
  { width: "0%",  y: 40,  opacity: 0, marginRight: "0px",  height: "300px" },
];

const IMG_CLASS = "w-full h-full object-cover rounded-[1px] pointer-events-none";

// 拼贴卡：本地演示图（接口不通时的兜底），点击跳转 /shop 购物页
const DEMO_PRODUCTS = [
  { src: "/products/p01.jpg", alt: "烟台红富士苹果" },
  { src: "/products/p02.jpg", alt: "寿光水果番茄" },
  { src: "/products/p08.jpg", alt: "当季新米" },
  { src: "/products/p04.jpg", alt: "高山土豆" },
  { src: "/products/p03.jpg", alt: "赣南脐橙" },
  { src: "/products/p06.jpg", alt: "时令蔬菜组合" },
];

type CollageCard = { id: string; src: string | null; alt: string };

const CARD_COUNT = 6; // 动画状态表按 6 个槽位写死，卡片数必须补足到 6

/**
 * 后端货源 → 拼贴卡。
 * 真实图优先；后台没传图的货源用 pickProductPhoto 按「商品名 → 品类」配同类目实拍图，
 * 避免出现「老母鸡配苹果图」这类错配。
 */
function toCollageCards(products: ApiProduct[]): CollageCard[] {
  return products.map((p, i) => ({
    id: `api-${p.productId ?? i}`,
    src: resolveImage(p.imageUrl) ?? pickProductPhoto(p, i),
    alt: p.productName || '平台在售货源',
  }));
}

/** 拼贴卡内容：有图放图，没图放中性占位块（宁可不放图也不放错图） */
function CardMedia({ card, onClick }: { card: CollageCard; onClick?: (e: React.MouseEvent) => void }) {
  return (
    <a href="/shop" className="block w-full h-full" aria-label={`去商城看${card.alt}`} onClick={onClick}>
      {card.src ? (
        <img src={card.src} alt={card.alt} className={IMG_CLASS} />
      ) : (
        <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-[#2b2b2b] to-[#3a2f28] px-3 text-center">
          <span className="text-sm leading-snug text-white/70">{card.alt}</span>
        </div>
      )}
    </a>
  );
}

export function Section2() {
  const collageRef = useRef<HTMLDivElement>(null);
  const [isHovering, setIsHovering] = useState(false);
  const [shift, setShift] = useState(0);

  // 接口优先、静态兜底：GET /api/home/products?limit=6（公开，游客可访问）
  // 后端推荐位目前为空 → fetchCollageProducts 会自动改调 /trade/products 取最新货源
  const { data: hotProducts, source } = useRemote(
    () => fetchCollageProducts(CARD_COUNT),
    [] as ApiProduct[],
  );

  const cards = useMemo<CollageCard[]>(() => {
    const list = toCollageCards(hotProducts);
    // 数量不足时用演示图补足，否则动画状态表的下标会落空
    for (let i = list.length; i < CARD_COUNT; i++) {
      const demo = DEMO_PRODUCTS[i % DEMO_PRODUCTS.length];
      list.push({ id: `demo-${i}`, src: demo.src, alt: demo.alt });
    }
    return list.slice(0, CARD_COUNT);
  }, [hotProducts]);

  const CURSOR_SIZE = 130;
  const mx = useMotionValue(-9999);
  const my = useMotionValue(-9999);
  const sx = useSpring(mx, { stiffness: 400, damping: 40, mass: 0.5 });
  const sy = useSpring(my, { stiffness: 400, damping: 40, mass: 0.5 });

  const isDesktop = () => typeof window !== "undefined" && window.matchMedia("(min-width: 1024px)").matches;

  const handleCardPointerMove = (e: React.PointerEvent) => {
    if (!isDesktop()) return;
    mx.set(e.clientX - CURSOR_SIZE / 2);
    my.set(e.clientY - CURSOR_SIZE / 2);
  };
  const handleCardPointerEnter = (e: React.PointerEvent) => {
    if (!isDesktop()) return;
    mx.jump(e.clientX - CURSOR_SIZE / 2);
    my.jump(e.clientY - CURSOR_SIZE / 2);
    setIsHovering(true);
  };
  const handleCardPointerLeave = () => setIsHovering(false);

  const visibleCards = useMemo(() => {
    const list = [...cards];
    const normalizedShift = ((shift % list.length) + list.length) % list.length;
    return [...list.slice(normalizedShift), ...list.slice(0, normalizedShift)];
  }, [shift, cards]);

  const handleDragEnd = (_e: any, { offset }: any) => {
    const swipeThreshold = 50;
    if (offset.x < -swipeThreshold) setShift((p) => p + 1);
    else if (offset.x > swipeThreshold) setShift((p) => p - 1);
    // click 在 dragEnd 之后触发，延迟复位避免拖拽后误跳转
    setTimeout(() => { draggedRef.current = false; }, 80);
  };

  // 拖拽换卡不应当成点击：拖拽真正发生时置位，click 里拦截一次
  const draggedRef = useRef(false);
  const handleCardClick = (e: React.MouseEvent) => {
    if (draggedRef.current) {
      e.preventDefault();
    }
  };

  const mobileCards = cards.slice(0, 4);

  return (
    <section className="relative w-full overflow-hidden bg-[#1b1b1b] px-5 pt-16 pb-20 font-['Inter_Tight','PingFang_SC','Noto_Sans_SC',_sans-serif] font-light text-white lg:px-10 lg:pt-24 lg:pb-32">
      <div className="relative z-20 flex w-full flex-col items-start gap-6 lg:flex-row lg:justify-between lg:gap-10">
        <h2 className="flex flex-col text-[40px] leading-[1.05] font-light sm:text-5xl lg:text-[81px] lg:leading-[85px] lg:max-w-[900px]">
          <span className="whitespace-nowrap"><AnimatedWords text="产销两端" /></span>
          <span className="whitespace-nowrap">
            <AnimatedWords text="高效" delayStart={0.18} />{" "}
            <span className="opacity-50"><AnimatedWords text="匹配" delayStart={0.3} /></span>
          </span>
        </h2>
        <div className="flex flex-col items-start lg:items-end">
          <p className="text-left text-base font-light leading-6 bg-gradient-to-b from-white to-white/50 bg-clip-text text-transparent lg:mt-4 lg:text-right lg:text-xl lg:leading-7">
            <AnimatedWords text="农户发布货源， 采购方按需检索" delayStart={0.2} stagger={0.04} /><br />
            <AnimatedWords text="从下单、担保结算到物流跟踪" delayStart={0.4} stagger={0.04} /><br />
            <AnimatedWords text="每一步都有平台在托底" delayStart={0.55} stagger={0.04} />
          </p>
          <p className="mt-2 text-xs text-white/40 lg:text-right">
            {source === "api" ? "数据源：平台接口" : "数据源：本地演示"} · 点击图片进入商城页 →
          </p>
        </div>
      </div>

      {/* MOBILE / TABLET — 2x2 grid */}
      <div className="mt-10 grid grid-cols-2 gap-4 lg:hidden">
        {mobileCards.map((c) => (
          <motion.div key={c.id}
            className="w-full aspect-square overflow-hidden rounded-[2px]"
            initial={{ opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-50px" }}
            transition={{ duration: 0.6, ease: "easeOut" }}>
            <CardMedia card={c} />
          </motion.div>
        ))}
      </div>

      {/* DESKTOP COLLAGE */}
      <div className="relative hidden lg:block" ref={collageRef}>
        <motion.div
          className="relative z-10 mt-[-22px] flex w-full flex-row items-center justify-between cursor-grab active:cursor-grabbing"
          drag="x"
          dragConstraints={{ left: 0, right: 0 }}
          dragElastic={0.05}
          onDragStart={() => { draggedRef.current = true; }}
          onDragEnd={handleDragEnd}
          onPointerMove={handleCardPointerMove}>
          {visibleCards.map((card, index) => {
            const isInitial = shift === 0;
            return (
              <motion.div key={card.id}
                className="overflow-hidden shrink-0"
                onPointerEnter={handleCardPointerEnter}
                onPointerLeave={handleCardPointerLeave}
                initial={isInitial ? INITIAL_STATES[index] : false}
                whileInView={isInitial ? WHILE_IN_VIEW_STATES[index] : undefined}
                animate={!isInitial ? FINAL_SLOTS[index] : undefined}
                viewport={{ once: true }}
                transition={isInitial
                  ? { duration: 2.5, times: [0, 0.3, 1], ease: "easeInOut" }
                  : { duration: 0.8, ease: "easeInOut" }}>
                {/* img 是 pointer-events-none，点击会落到这层 <a> 上 → /shop */}
                <CardMedia card={card} onClick={handleCardClick} />
              </motion.div>
            );
          })}
        </motion.div>

        <motion.div
          className="fixed top-0 left-0 z-50 hidden lg:flex items-center justify-center w-[130px] h-[130px] rounded-full bg-white/10 backdrop-blur-md shadow-2xl border border-white/20 pointer-events-none"
          style={{ x: sx, y: sy }}
          animate={{ opacity: isHovering ? 1 : 0, scale: isHovering ? 1 : 0 }}
          transition={{ duration: 0.25, ease: "easeOut" }}>
          <img src="/Arrow45.svg" className="w-[40px] h-[40px] brightness-0 invert" alt="" />
        </motion.div>
      </div>

      <div className="relative z-20 mt-10 flex w-full lg:mt-16 lg:translate-y-[-96px]">
        <div className="hidden lg:block lg:w-[22%]" />
        <p className="w-full text-[26px] font-light leading-[1.1] bg-gradient-to-b from-white to-white/40 bg-clip-text text-transparent sm:text-3xl lg:ml-[40px] lg:w-[75%] lg:text-[48px] lg:leading-[54px] lg:whitespace-nowrap">
          <AnimatedWords text="让每一次农产品流通" /><br />
          <AnimatedWords text="都有据可查、有价可比" delayStart={0.25} />
        </p>
      </div>
    </section>
  );
}
