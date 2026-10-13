import { createFileRoute, Link } from '@tanstack/react-router'

export const Route = createFileRoute('/privacy')({ component: PrivacyPage })

/**
 * 隐私政策页（2026-10-12 新增）。
 *
 * 此前页脚只有一个 `<a href="#">隐私政策</a>` —— 点下去只是回到页顶，
 * 是「看起来有、实际没有」的链接。这里补一个真实页面。
 *
 * 内容按平台**实际收集的字段**写，不是套模板：
 *   · 注册登录 → 手机号、密码（后端只存哈希）
 *   · 实名与角色 → 真实姓名；农户 / 专家另有所属档案
 *   · 交易 → 收货人姓名、手机号、收货地址（下单时快照进 t_order）
 *   · 图片 → 头像、商品图、知识配图（存对象存储）
 * 「信息会提供给谁」一节必须写明融资申请会把资料给到对接的银行用户 ——
 * 这是需求里明确的功能，回避它反而容易在答辩时被追问。
 *
 * ⚠️ 这里刻意**保留正文句号**：项目那条「不写句号」的约定针对的是 UI 提示类
 * 文案（alert / tip / placeholder / 按钮），而这是一篇需要连续阅读的文档，
 * 去掉句号会让段落读起来像机器拼接。标题与链接仍然不带句号。
 */

/** 政策最近更新日期。改内容时一并更新这里 */
const UPDATED_AT = '2026-10-12'

type Clause = { title: string; paragraphs: string[] }

const CLAUSES: Clause[] = [
  {
    title: '一、我们收集哪些信息',
    paragraphs: [
      '注册与登录：手机号，以及经哈希处理的登录密码。密码不以明文保存，平台无法反推出原始密码。',
      '身份与角色：真实姓名，以及你选择的角色（农户 / 采购方 / 专家 / 银行用户）。农户与专家还会填写主营农产品、经营规模或专业领域等档案信息，用于展示与撮合。',
      '交易信息：下单时填写的收货人姓名、收货人手机号与收货地址。这些内容会作为快照写入订单，之后修改或删除地址簿不会影响已经产生的订单。',
      '图片内容：你主动上传的头像、商品图片与知识配图。',
      '浏览记录：知识文章的阅读次数。该计数只累加，不记录具体是谁读过。',
    ],
  },
  {
    title: '二、这些信息用来做什么',
    paragraphs: [
      '完成账号注册、登录与角色权限判定；',
      '撮合农产品交易：把商品展示给采购方、把求购需求展示给农户、把订单流转给对应的一方；',
      '展示专家资源与预约记录，供农户与专家对接；',
      '将融资申请连同必要的资质信息提交给平台合作的银行用户进行审核；',
      '内容审核：商品、知识与评论在公开展示之前需要经过平台审核。',
    ],
  },
  {
    title: '三、信息会提供给谁',
    paragraphs: [
      '交易相对方：下单之后，农户需要看到收货人姓名、电话与地址才能发货；采购方需要看到所购商品与卖家信息。',
      '融资环节的银行用户：当你提交融资申请时，平台会把申请材料与相关的经营信息提供给受理该申请的银行用户。不提交申请则不会被提供。',
      '平台管理员：为了审核商品、知识与评论内容，管理员可以看到待审核的内容及其发布者。',
      '除此之外，平台不会向任何第三方出售、出租或提供你的个人信息。',
    ],
  },
  {
    title: '四、存储与保护',
    paragraphs: [
      '账号与业务数据存放在平台的数据库中，上传的图片存放在对象存储服务中。',
      '账号密码经哈希后存储，不保存明文；访问个人资料需要先登录，管理端操作另有独立的权限校验。',
      '本平台为演示系统，数据的保留期限依演示需要而定。停止使用后，相关信息可按下一节所述方式申请删除。',
    ],
  },
  {
    title: '五、你的权利',
    paragraphs: [
      '查看与更正：在「个人中心」可以查看并修改姓名、联系方式等资料。',
      '删除：可以删除自己发布的商品、求购需求与知识内容。已被订单引用的商品会改为下架而不是物理删除，以保证历史订单可追溯。',
      '撤回授权：退出登录后平台不再使用你的登录态；如需彻底清除账号数据，可通过下一节的方式提出。',
    ],
  },
  {
    title: '六、联系我们',
    paragraphs: [
      '对这份政策有疑问，或者在个人信息的使用上有任何诉求，请通过平台内的联系方式与运营方取得联系。',
    ],
  },
]

function PrivacyPage() {
  return (
    <div className="min-h-screen w-full bg-[#1b1b1b] text-white font-['Inter_Tight','PingFang_SC','Noto_Sans_SC',_sans-serif]">
      <div className="mx-auto w-full max-w-3xl px-5 py-12 lg:px-10 lg:py-20">
        <Link to="/" className="inline-flex items-center gap-2 text-sm text-white/60 transition-colors hover:text-white">
          ← 返回首页
        </Link>

        <h1 className="mt-8 text-4xl font-normal lg:text-5xl">隐私政策</h1>
        <p className="mt-4 text-sm text-white/50">最近更新：{UPDATED_AT}</p>

        <div className="mt-12 flex flex-col gap-10">
          {CLAUSES.map((clause) => (
            <section key={clause.title} className="flex flex-col gap-4">
              <h2 className="text-lg font-medium lg:text-xl">{clause.title}</h2>
              {clause.paragraphs.map((text, i) => (
                <p key={i} className="text-sm leading-relaxed text-white/70 lg:text-[15px]">{text}</p>
              ))}
            </section>
          ))}
        </div>

        <div className="mt-16 border-t border-white/10 pt-8">
          <Link to="/" className="text-sm text-white/60 transition-colors hover:text-white">返回首页</Link>
        </div>
      </div>
    </div>
  )
}
