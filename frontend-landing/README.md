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
- `verify/` —— 页面验收截图留档。

## 已知待办

- `public/` 下仍保留若干 Ceptral 模板时期的外链资源
  （`gather.svg`、`Mstar-logo.svg`、`intel.svg` 等，`*.asset.json` 里指向 `qclay.design`）。
  这些是装饰性资源，与品牌无关，待清理。
