// 生成 probe.mjs 的 spec —— 探针本体写在页面里，直接在页内 fetch 接口做比对。
// 页面里的 fetch('/api/...') 走 vite dev server 代理 → 18080（我自己的后端实例）。
import { writeFileSync } from 'node:fs';

const RECT_HELPER = String.raw`
  const rectOf = (el) => {
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return { x: Math.round(r.x + window.scrollX), y: Math.round(r.y + window.scrollY),
             width: Math.round(r.width), height: Math.round(r.height) };
  };
`;

const homeProbe = String.raw`(async () => {
  // 等 §5 的数字滚动动画跑完（1.2s）再读，否则会读到中间值
  await new Promise(r => setTimeout(r, 1800));
  const t = (el) => (el && el.textContent ? el.textContent.replace(/\s+/g, ' ').trim() : '');
  const api = async (p) => (await (await fetch(p)).json()).data;
` + RECT_HELPER + String.raw`
  const products = await api('/api/home/products?limit=6');
  const knowledge = await api('/api/home/knowledge?limit=6');
  const stats = await api('/api/home/stats');
  const allProducts = await api('/api/trade/products?pageNum=1&pageSize=50');

  // §2 拼贴卡（aria-label 形如「去商城看菜籽油 5L」）
  const collage = [...new Set([...document.querySelectorAll('a[aria-label^="去商城看"]')]
    .map(a => a.getAttribute('aria-label').replace('去商城看', '')))];

  // §5 三个统计：找 label 节点，取父容器里的两个 span（数字 + 单位）
  const statRows = ['在售货源', '有效求购', '农业知识'].map(function (label) {
    const el = [...document.querySelectorAll('div')].find(d => t(d) === label);
    if (!el || !el.parentElement) return { label: label, num: null, unit: null, found: false };
    const spans = [...el.parentElement.querySelectorAll('span')].map(t);
    return { label: label, num: spans[0] || null, unit: spans[1] || null, found: true };
  });

  // §6 右侧列表行 / 品类页签 / 左卡精选标题
  const newsRows = [...document.querySelectorAll('div.cursor-pointer.group')].map(t);
  const catTabs = [...document.querySelectorAll('span.font-medium.uppercase')].map(t);
  const h3s = [...document.querySelectorAll('h3')].map(t);

  // §4 右侧卡片
  const s4 = [...document.querySelectorAll('section div')].find(d => (d.className || '').indexOf('w-[280px]') >= 0);
  const section4Card = s4 ? s4.innerText.replace(/\s+/g, ' ').trim() : null;

  const bodyText = document.body.innerText;

  // 全页残留的「假数据」特征词（都已确认不属于平台真实数据）
  const BANNED = [
    '赣南果业合作社','寿光绿源合作社','秦岭果农联合社','坝上蔬菜基地','五常稻香联合社',
    '荆楚粮油合作社','荆楚散养农场','洪湖生态渔场','秦巴山货合作社',
    '万农户与合作社','撮合中 128 笔','合作农户 46 户','认证等级 A','较昨日','今日均价',
    '融销通研究院','农产品流通的数字化路径','2025 年 9 月 22 日',
    '中央一号文件里的农产品流通要点','秋收季主要产区价格周报','农户融资担保方式对比',
    '平台新功能：一键发起求购并自动匹配货源','病虫害高发期农事提醒与用药规范',
    '全部 (17)','政策解读 (7)','行情周报 (4)','农事提醒 (6)',
    '数据源：本地演示','本地演示数据',
  ];
  const leftover = BANNED.filter(function (w) { return bodyText.indexOf(w) >= 0; });

  const productNames = products.map(p => p.productName);
  const knowledgeTitles = knowledge.map(k => k.title);
  const realCollage = collage.filter(n => n !== '暂无更多在售货源');
  const expectStats = {
    '在售货源': stats.productCount, '有效求购': stats.demandCount, '农业知识': stats.knowledgeCount,
  };
  const catCount = Object.keys(stats.knowledgeByCategory || {}).length;

  // 各区块在页面坐标系里的位置，供第二轮按区域 1:1 截图
  const secs = [...document.querySelectorAll('section')];
  const rects = secs.map((s, i) => ({
    i: i, head: s.innerText.replace(/\s+/g, ' ').slice(0, 36), rect: rectOf(s),
  }));

  return JSON.stringify({
    接口: {
      在售货源_全量: allProducts.total,
      首页推荐_条数: products.length,
      首页知识_条数: knowledge.length,
      stats: stats,
    },
    页面: {
      section2_拼贴卡名: collage,
      section5_统计: statRows,
      section6_列表行: newsRows,
      section6_品类页签: catTabs,
      section6_h3: h3s,
      section4_卡片: section4Card,
    },
    校验: {
      拼贴卡全部为真实商品名: realCollage.every(n => productNames.indexOf(n) >= 0),
      拼贴卡张数: realCollage.length,
      统计与接口一致: statRows.every(r => r.found && Number(r.num) === expectStats[r.label]),
      期望统计: expectStats,
      资讯条目全部为真实标题: newsRows.filter(r => r.length > 0).every(r => knowledgeTitles.some(k => r.indexOf(k) === 0)),
      资讯行数: newsRows.length,
      页签数量_应等于_1加分类数: { 实际: catTabs.length, 期望: 1 + catCount },
      页签内容: catTabs,
      残留假数据特征词: leftover,
      残留条数: leftover.length,
    },
    区块位置: rects,
    页面高度: document.documentElement.scrollHeight,
  });
})()`;

const shopProbe = String.raw`(async () => {
  const t = (el) => (el && el.textContent ? el.textContent.replace(/\s+/g, ' ').trim() : '');
  const api = async (p) => (await (await fetch(p)).json()).data;
` + RECT_HELPER + String.raw`
  const page = await api('/api/trade/products?pageNum=1&pageSize=50');

  const cards = [...document.querySelectorAll('main .grid > div')].map(t);
  const catButtons = [...document.querySelectorAll('main button')].map(t);
  const headerText = t(document.querySelector('main p'));
  const cardRects = [...document.querySelectorAll('main .grid > div')].slice(0, 8).map(rectOf);

  const productNames = page.records.map(p => p.productName);
  // 注意：「赣南脐橙 5kg 装」「猕猴桃 5 斤装」是**真实商品名**（与旧演示数据同名），不能当残留特征词
  const BANNED = ['赣南果业合作社','寿光绿源合作社','五常稻香联合社','荆楚粮油合作社',
                  '本地演示数据','数据源：本地演示','供货方 #','京欣西瓜','麒麟瓜'];

  return JSON.stringify({
    接口: { total: page.total, 返回条数: page.records.length },
    页面: { 卡片数: cards.length, 前3张: cards.slice(0, 3), 分类按钮: catButtons, 顶部说明: headerText },
    校验: {
      张数一致: cards.length === page.records.length,
      卡片名全部为真实货源: cards.every(c => productNames.some(n => c.indexOf(n) >= 0)),
      非真实货源卡: cards.filter(c => !productNames.some(n => c.indexOf(n) >= 0)),
      分类数_应等于1加品类数: { 实际: catButtons.length, 期望: 1 + new Set(page.records.map(p => p.category)).size },
      残留假数据特征词: BANNED.filter(w => document.body.innerText.indexOf(w) >= 0),
    },
    卡片位置: cardRects,
    页面高度: document.documentElement.scrollHeight,
  });
})()`;

const spec = {
  port: 9333,
  keepChrome: true,
  targets: [
    {
      label: '落地页首页',
      url: 'http://127.0.0.1:3010/',
      width: 1440,
      height: 1000,
      wait: 2500,
      scrollReveal: true,
      evalTimeoutMs: 40000,
      probe: homeProbe,
    },
    {
      label: '商城页',
      url: 'http://127.0.0.1:3010/shop',
      width: 1440,
      height: 1000,
      wait: 2500,
      scrollReveal: true,
      evalTimeoutMs: 40000,
      probe: shopProbe,
    },
  ],
};

writeFileSync('/tmp/landing_spec.json', JSON.stringify(spec, null, 2));
console.log('spec 已写入 /tmp/landing_spec.json');
