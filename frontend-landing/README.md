# frontend-landing · 融销通官网落地页

面向客户与农户的官网首页，与 `frontend-web`（用户端）、`frontend-admin`（管理后台）
并列，三者独立部署。

## 技术栈

TanStack Start（React 19）+ Tailwind CSS 4 + Vite 8。

## 本地运行

```bash
npm install
npm run dev        # http://localhost:3000
```

## 构建

```bash
npm run build
```

⚠️ 提交前**必须**跑一次 `npm run build`。本项目的 import 路径错误在 `npm run dev` 下
可能只表现为白屏或一条警告，只有构建会明确报错 —— 见根目录 `CONTRIBUTING.md` 第五节。

## 品牌标识

标识定版于 2026-09-24。四个文件分工不同，**别用错**：

| 文件 | 用途 |
|---|---|
| `src/assets/logo.svg` | 反白版 —— **Hero 深色视频底专用** |
| `src/assets/logo-color.svg` | 彩色版 —— 浅色底使用 |
| `src/assets/logo-legacy-v1-square.svg` | v1 旧标留档，勿在新页面引用 |
| `public/favicon.svg` | 简化版 favicon，已做深色模式自适应 |
| `public/apple-touch-icon.png` | iOS 主屏图标，180×180 |

⚠️ **Hero 区必须用反白版。** 该区域背景是黑色视频 + 上下两层黑色渐变遮罩，
`logo-color.svg` 里的禾青色环在深色上会直接看不见。

标识几何：200 网格，开口圆环 R62 / 线宽 14 / 缺口 60° 朝右上 45°，
三粒麦粒 rx 12.5、10.5 间距 26，暖橙节点 R7.5 落在缺口正中。
`viewBox` 取 `20 20 160 160`，使图形占边长 87.5%，顶栏尺寸与旧标一致。

## 素材

- `public/hero-field.mp4` —— 首页背景视频成片（7.4MB，CRF30 / 16s / 已加 `+faststart`）。
- `media-src/` —— 原片与备选档，**该目录不入库**（见 `.gitignore`）。
- `public/products/`、`public/farmers/` —— 商品与农户示意配图。
- `public/landing/` —— Section 4/5/6 用的装饰贴图（噪点、侧阴影、卡片底、栏目插图），
  已从第三方 CDN 本地化并压缩（原图 16.7MB → 1.4MB，WebP 保留透明通道）。
- `public/fonts/` —— 全站字体 Inter Tight（可变字重 200–900）的 latin / latin-ext 两个
  子集，共 132KB。`@font-face` 写在 `src/styles.css`。
- `verify/` —— 页面验收截图留档。

## 已知待办

- ~~`public/` 下仍保留若干 Ceptral 模板时期的外链资源~~ ✅ **已清理（2026-10-09）**：
  删掉 71 个指向 `qclay.design` 的 `*.asset.json`，以及 10 个无人引用的模板 svg
  （`Mstar-logo.svg` / `intel.svg` / `nutanix.svg` / `oracle.svg` / `clutch.svg` /
  `In.svg` / `inst.svg` / `X.svg` / `gofound.svg` / `RedArrow.svg`）。
- ~~字体的 `fonts.googleapis.com` 靠 `media="print"` 降级~~ ✅ **已改为自托管（2026-10-09）**，
  见 `public/fonts/` 与 `src/styles.css`。原先那行是 `<head>` 里的阻塞外链，
  对方一旦挂起会**同时**阻塞渲染和 `type="module"` 脚本执行 —— 整页白屏且控制台无报错。
  **至此落地页源码里已无任何外部域名依赖**，断网也能完整渲染。
