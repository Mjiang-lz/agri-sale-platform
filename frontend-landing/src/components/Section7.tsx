import { useState } from "react";
import { motion } from "framer-motion";
const letters = ["融", "销", "通"];

function HoverLetter({ char }: { char: string }) {
  const [weight, setWeight] = useState(900);
  const handleMouseMove = (e: React.MouseEvent) => {
    if (e.movementX > 0) setWeight(300);
    else if (e.movementX < 0) setWeight(900);
  };
  return (
    <motion.span onMouseMove={handleMouseMove}
      className="font-['Inter_Tight','PingFang_SC','Noto_Sans_SC',_sans-serif] text-[24vw] xl:text-[300px] leading-none text-white cursor-default"
      animate={{ fontWeight: weight }}
      transition={{ duration: 0.3, ease: "easeOut" }}>
      {char}
    </motion.span>
  );
}

export function Section7() {
  return (
    <section className="relative z-50 w-full h-[35vh] bg-[#1b1b1b] flex items-center justify-center overflow-hidden">
      <div className="flex flex-row items-center justify-center gap-[2vw] xl:gap-[40px] w-full max-w-[1728px] px-10 mx-auto relative z-0">
        {letters.map((char, index) => (<HoverLetter key={`${char}-${index}`} char={char} />))}
      </div>
      <div className="absolute inset-x-0 bottom-0 h-full pointer-events-none z-10"
        style={{
          background:
            "linear-gradient(to top, #1b1b1b 0%, rgba(27,27,27,0.98) 15%, rgba(27,27,27,0.88) 35%, rgba(27,27,27,0.65) 55%, rgba(27,27,27,0.35) 75%, rgba(27,27,27,0.1) 90%, transparent 100%)",
        }} />
    </section>
  );
}
