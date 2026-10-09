# syntax=docker/dockerfile:1
#
# 静态资源 + 反向代理层。
#
# 把三个前端分别构建成静态文件，交给 Nginx 按路径分流（/ 落地页、/app/ 业务端、
# /admin/ 管理后台），并把 /api/** 反代到后端容器。分流规则见 deploy/nginx.conf。
#
# 构建上下文是**仓库根目录**（见 docker-compose.yml），排除规则见 .dockerignore。

# ---------------------------------------------------------------------------
# 业务端：构建时 base=/app/
# ---------------------------------------------------------------------------
FROM node:22-alpine AS build-web
WORKDIR /src
# 锁文件不入库（原因见 .gitignore 里的说明），所以只能用 npm install ——
# npm ci 要求有 package-lock.json，会直接失败。
COPY frontend-web/package.json ./
RUN npm install --no-audit --no-fund
COPY frontend-web/ ./
# RXT_BASE 决定 vite 的 base 与资源的引用前缀，路由侧读的是同一个值
# （import.meta.env.BASE_URL）。不传就按根路径构建，挂到 /app/ 下会白屏。
RUN RXT_BASE=/app/ npm run build

# ---------------------------------------------------------------------------
# 管理后台：构建时 base=/admin/
# ---------------------------------------------------------------------------
FROM node:22-alpine AS build-admin
WORKDIR /src
COPY frontend-admin/package.json ./
RUN npm install --no-audit --no-fund
COPY frontend-admin/ ./
RUN RXT_BASE=/admin/ npm run build

# ---------------------------------------------------------------------------
# 落地页：挂在根路径
# ---------------------------------------------------------------------------
FROM node:22-alpine AS build-landing
WORKDIR /src
COPY frontend-landing/package.json ./
RUN npm install --no-audit --no-fund
COPY frontend-landing/ ./
# ⚠️ VITE_PLATFORM_BASE_URL 决定落地页上「进入平台 / 登录 / 免费入驻」跳到哪。
# 不传的话前端里的默认值是 http://localhost:5173 —— 部署到服务器后，
# 这些按钮会指向**访问者自己机器**的 5173，点下去必然打不开。
# 部署层把业务端挂在 /app/ 下，所以这里传相对路径 /app。
RUN VITE_PLATFORM_BASE_URL=/app npm run build

# ---------------------------------------------------------------------------
# 运行：只留 Nginx 与三份静态产物
# ---------------------------------------------------------------------------
FROM nginx:1.27-alpine
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build-landing /src/dist /usr/share/nginx/html
COPY --from=build-web     /src/dist /usr/share/nginx/html/app
COPY --from=build-admin   /src/dist /usr/share/nginx/html/admin
EXPOSE 80
