import { useState } from "react";
import { motion } from "framer-motion";

const LETTERS = ["融", "销", "通"];

function HoverLetter({ char }: { char: string }) {
  const [weight, setWeight] = useState(500);

  return (
    <motion.span
      className="inline-block"
      animate={{ fontWeight: weight }}
      transition={{ duration: 0.3 }}
      onMouseMove={(e) => {
        if (e.movementX > 0) setWeight(200);
        else if (e.movementX < 0) setWeight(900);
      }}
    >
      {char}
    </motion.span>
  );
}

/** 融销通字标：鼠标横向划过时逐字变化字重（原 GeptralLogo 的交互保留） */
export function RxtLogo({ className = "" }: { className?: string }) {
  return (
    <span className={className}>
      {LETTERS.map((char, i) => (
        <HoverLetter key={i} char={char} />
      ))}
    </span>
  );
}
