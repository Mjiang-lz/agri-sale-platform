// 后端不可用路径：代理指向死端口的 dev server（3011），验证页面显示空态而不是演示数据
import { writeFileSync } from 'node:fs';

const probeFor = (y, waitMs, label) => `(async () => {
  window.scrollTo(0, ${y});
  await new Promise(r => setTimeout(r, ${waitMs}));
  const bt = document.body.innerText;
  const BANNED = ['数据源：本地演示','本地演示数据','赣南果业合作社','寿光绿源合作社',
                  '万农户与合作社','融销通研究院','供货方 #','中央一号文件里的农产品流通要点',
                  '合作农户 46 户','撮合中 128 笔'];
  return JSON.stringify({
    label: '${label}',
    scrollY: window.scrollY,
    残留演示特征词: BANNED.filter(w => bt.indexOf(w) >= 0),
    含接口不可用提示: bt.indexOf('平台接口暂不可用') >= 0,
    正文摘要: bt.replace(/\\s+/g, ' ').slice(0, 700),
  });
})()`;

const spec = {
  port: 9333,
  keepChrome: true,
  targets: [
    { label: '后端不可用 · 首页§2', url: 'http://127.0.0.1:3011/', width: 1440, height: 1000, wait: 3000, probe: probeFor(1050, 3000, '首页§2空态'), evalTimeoutMs: 40000, shot: '/tmp/shot-down-sec2.png' },
    { label: '后端不可用 · 首页§6', url: 'http://127.0.0.1:3011/', width: 1440, height: 1000, wait: 3000, probe: probeFor(4900, 3000, '首页§6空态'), evalTimeoutMs: 40000, shot: '/tmp/shot-down-sec6.png' },
    { label: '后端不可用 · 商城页', url: 'http://127.0.0.1:3011/shop', width: 1440, height: 1000, wait: 3000, probe: probeFor(180, 3000, '商城页空态'), evalTimeoutMs: 40000, shot: '/tmp/shot-down-shop.png' },
  ],
};

writeFileSync('/tmp/down_spec.json', JSON.stringify(spec, null, 2));
console.log('down_spec 已写入');
