import { motion } from "framer-motion";

const items = [
  { title: "农产品交易", list: ["商品货源发布与检索","求购需求对接","购物车与下单","订单流转与担保结算"] },
  { title: "融资服务", list: ["农户融资意向提交","经营特征智能匹配","联合融资人推荐","银行审批与放款回传"] },
  { title: "专家助力", list: ["农业知识库管理","专家在线问答","专家预约与排期","农技经验沉淀"] },
  { title: "平台治理", list: ["用户与角色权限","商品内容审核","知识与评论审核","违规处置与申诉"] },
];

export function Section3() {
  // id="capability" —— Hero 顶部导航「平台能力」的锚点。
  // 2026-10-10 补：此前这个 id 谁也没挂，锚点 href 写了却点了页面不动。
  // ⚠️ 注释必须写在 return 之外：写成 `return ({/* ... */} <section>)`
  //    会变成两个相邻的顶层节点，JSX 不允许，整页 500。
  return (
    <section id="capability" className="relative w-full overflow-hidden bg-[#1b1b1b] px-5 py-20 font-['Inter_Tight','PingFang_SC','Noto_Sans_SC',_sans-serif] text-white lg:px-10 lg:py-32">
      <div className="grid w-full grid-cols-1 gap-12 lg:grid-cols-12 lg:gap-10">
        {/* LEFT — 4 cols */}
        <div className="col-span-1 lg:col-span-4">
          <motion.p
            className="text-white text-[18px] leading-[1.4]"
            initial={{ opacity: 0, y: 16 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-50px" }}
            transition={{ duration: 0.6, ease: "easeOut" }}
          >
            03 — 平台能力
          </motion.p>
          <motion.p
            className="mt-6 max-w-[380px] text-base leading-6 text-[#888888]"
            initial={{ opacity: 0, y: 16 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-50px" }}
            transition={{ duration: 0.6, delay: 0.12, ease: "easeOut" }}
          >
            打通农户、采购方、专家与银行四类角色，把交易、融资、知识与治理四类能力装进同一套系统。
          </motion.p>
        </div>

        {/* RIGHT — 8 cols, 2-col grid of 4 items */}
        <div className="col-span-1 grid grid-cols-1 gap-x-10 gap-y-12 sm:grid-cols-2 lg:col-span-8">
          {items.map((item, i) => (
            <div key={item.title} className="flex flex-col border-t border-white/15 pt-[10px]">
              <motion.h3
                className="text-white text-xl font-normal leading-[1.2] lg:text-[26px]"
                initial={{ opacity: 0, y: 16 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, margin: "-50px" }}
                transition={{ duration: 0.5, delay: i * 0.12, ease: "easeOut" }}
              >
                {item.title}
              </motion.h3>
              <ul className="mt-6 flex flex-col gap-[3px]">
                {item.list.map((li, j) => (
                  <motion.li
                    key={li}
                    className="text-[#888888] text-sm leading-6"
                    initial={{ opacity: 0, y: 12 }}
                    whileInView={{ opacity: 1, y: 0 }}
                    viewport={{ once: true, margin: "-50px" }}
                    transition={{ duration: 0.5, delay: i * 0.12 + 0.1 + j * 0.06, ease: "easeOut" }}
                  >
                    {li}
                  </motion.li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
