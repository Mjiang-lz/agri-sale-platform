# agri-sale-platform · 融销通

> 基于数字经济的农产品融销一体平台

存放项目需求文档、测试文档与源码。

## 项目简介

融销通面向农产品流通场景，打通「农户 — 采购方 — 专家 — 银行」四类角色，提供农产品交易、
融资撮合与专家助力三类核心能力：

- **农产品交易**：商品货源发布与检索、求购需求对接、购物车与订单流转
- **融资服务**：农户融资意向提交、基于经营特征的联合融资人智能推荐、银行侧审批与放款回传
- **专家助力**：农业知识管理、专家问答、专家预约
- **平台治理**：用户与角色权限、内容审核（商品 / 知识 / 评论）

## 技术栈

| 层次 | 技术 | 版本 |
| --- | --- | --- |
| 前端框架 | Vue.js | 3.x |
| 前端 UI | Element Plus | 2.x |
| 前端构建 | Vite | 5.x |
| 后端框架 | Spring Boot | 4.0.1 |
| Web 容器 | Tomcat（内嵌） | 11.x / Servlet 6.1 |
| 运行环境 | OpenJDK | 17 |
| 构建工具 | Maven | 3.9 |
| 持久层 | MyBatis-Plus | 3.5.16 |
| 数据库 | MySQL | 8.0（utf8mb4） |
| 对象存储 | 华为云 OBS | — |
| 接口文档 | springdoc-openapi（Swagger UI） | 3.0.1 |
| 代码托管与构建 | 华为云 CodeArts | — |

> **持久层依赖注意**：Spring Boot 4 必须使用 `mybatis-plus-spring-boot4-starter`（3.5.15 起提供）。
> 旧的 `mybatis-plus-spring-boot3-starter` 在 Boot 4 下自动配置类路径已变更，启动会直接失败。
> 分页插件另需引入 `mybatis-plus-jsqlparser`（3.5.9 起 JSqlParser 被拆为独立模块）。

> **接口文档依赖注意**：Swagger 用 `springdoc-openapi-starter-webmvc-ui`，版本在 `pom.xml` 的
> `<springdoc.version>` 里单独钉住（当前 `3.0.1`）。Spring Boot 4 必须用 springdoc **3.x**
> （2.x 只支持 Boot 3）；而 3.x 各小版本是分别针对不同 Boot 打出来的，
> **换 Boot 版本时必须回头核对这一项**，否则会出现 jar 编译期 API 与运行时不一致、
> 启动直接 `NoSuchMethodError`。已实测的对应关系：
>
> | springdoc | 编译时对齐的 Boot |
> | --- | --- |
> | 3.0.0 | 4.0.0 |
> | **3.0.1（本项目）** | **4.0.1** |
> | 3.0.2 | 4.0.3 |
> | 3.0.3 | 4.0.5 |
> | 3.1.x | 4.1.0 |
>
> 另：**不要**为了 Swagger 引入 `spring-boot-starter-security` —— 本项目鉴权是自实现的
> JWT 拦截器，引入 Security 会让所有接口直接 401。

## 目录结构

```
agri-sale-platform/
├── backend/                    # rxt-backend：后端服务（Spring Boot 4 + MyBatis-Plus）
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/rxt/
│       │   ├── RxtApplication.java
│       │   ├── common/         # 统一返回 Result / PageResult、业务异常、全局异常处理
│       │   ├── config/         # MyBatis-Plus 分页、字段自动填充、跨域、拦截器注册
│       │   ├── security/       # JWT 工具、登录拦截器、角色校验
│       │   ├── controller/     # 表现层，7 个业务接口入口
│       │   ├── service/        # 业务逻辑层（含 impl 子包）
│       │   ├── mapper/         # 数据访问层
│       │   ├── entity/         # 18 个持久化实体，与数据表一一映射
│       │   ├── dto/            # 层间传输对象
│       │   └── vo/             # 视图输出对象
│       └── resources/
│           ├── application.yml
│           ├── application-dev.yml
│           ├── db/schema.sql   # 18 张业务表建表脚本（含外键）
│           └── mapper/         # MyBatis XML
├── frontend-web/               # rxt-web：业务端（农户 / 采购方 / 专家 / 银行用户）
├── frontend-admin/             # rxt-admin：管理后台（平台管理员）
├── frontend-landing/           # rongxiaotong-landing：官网落地页（React 19 + TanStack Router，独立静态站点）
├── scripts/
│   └── demo-start.sh           # 演示/答辩一键启动（后端 + 三个前端，含就绪校验与统一收尾）
├── deploy/                     # 容器部署：两个 Dockerfile + Nginx 分流配置
│   ├── web.Dockerfile          # 三个前端构建 + Nginx 静态托管（一个镜像一个端口）
│   ├── backend.Dockerfile      # 后端构建与运行镜像
│   └── nginx.conf              # 路径分流 / SPA 回退 / 缓存 / /api 反代
├── docker-compose.yml          # MySQL + 后端 + 前端托管，四个容器一键起
└── docs/
    ├── 交付文档/                # 可行性研究报告、软件需求规格说明书、概要设计说明书、详细设计说明书
    ├── 设计图/                  # 架构分层图、组件图、部署图、包图、类图、顺序图
    └── 接口约定-*.md            # 各模块接口契约（路径、字段约束、错误码、实现权衡）
```

## 协作规范

分支命名、提交信息格式、MR 流程、结构变更通知约定，以及建议在 CodeArts 里开启的
仓库规范规则，见 **[CONTRIBUTING.md](./CONTRIBUTING.md)**。

## 本地启动

### 一键启动（演示 / 答辩用）

```bash
scripts/demo-start.sh                 # 起后端 + 三个前端
scripts/demo-start.sh --no-backend    # 后端已经在跑，只起三个前端
scripts/demo-start.sh --open          # 起来后顺带用浏览器打开落地页
```

拉起四个进程，并逐个**确认真的可用**再报成功 —— 判据不是「端口有响应」，
而是响应体确实是本项目的。端口被别的服务占了不会被误判成「已经起来了」，
那类假成功在演示当天最难发现。就绪后会打印全部地址和演示账号。

| 服务 | 地址 | 端口可用环境变量覆盖 |
| --- | --- | --- |
| 后端 API | `http://localhost:8080/api` | `BACKEND_PORT` |
| 业务端 | `http://localhost:5173` | `WEB_PORT` |
| 管理后台 | `http://localhost:5174` | `ADMIN_PORT` |
| 落地页 | `http://localhost:3000` | `LANDING_PORT` |

- 启动前**已经在跑**的进程（比如你自己开着的后端）会被复用，不会重复拉起，也不会被脚本关掉。
- `Ctrl-C` 退出时，只收掉本次由脚本拉起的进程，连 `mvn` / `vite` fork 出来的子进程一起收。
- 每个服务的日志在 `logs/demo/<服务名>.log`（该目录不入库）。

> 需要改数据源、单独调某个服务、或想看接口文档，看下面几节。

### 后端

```bash
# 1. 建库建表
mysql -uroot -p < backend/src/main/resources/db/schema.sql

# 2. 灌演示数据（含 9 个演示账号。不跑这步库里没有账号，登录会一直失败）
mysql -uroot -p < backend/src/main/resources/db/data.sql

# 3. 修改数据源（backend/src/main/resources/application-dev.yml）
#    默认用户名密码都是 root，本机 MySQL 若不是这个就改这里

# 4. 启动
cd backend
export JAVA_HOME=/path/to/jdk-17
mvn spring-boot:run
```

> ⚠️ **第 1、2 步的顺序不能反。** 先结构后数据，反过来跑 `data.sql` 第一条就会报表不存在。
>
> ⚠️ **`schema.sql` 是破坏性脚本**（每张表都是 `DROP TABLE` + `CREATE TABLE`，会清空数据），
> 只在**首次搭环境**或**本地数据可以丢**时用。
> 已经录了演示数据的库要更新结构，请改用 `db/migrations/` 下的增量脚本 —— 详见
> `backend/src/main/resources/db/README.md`。

服务默认监听 `http://localhost:8080/api`。
（若本机 8080 已被别的项目占用，可加 `-Dspring-boot.run.arguments=--server.port=18080` 换端口；
前端代理侧不用改配置文件，起前端时带上 `VITE_API_TARGET=http://localhost:18080` 即可，
或者直接用 `BACKEND_PORT=18080 scripts/demo-start.sh`，它会把三处变量一起对齐。）

### 接口文档（Swagger）

后端起来之后，`dev` 环境可以直接打开交互式接口文档：

| 地址 | 用途 |
| --- | --- |
| `http://localhost:8080/api/swagger-ui.html` | 交互式页面，可直接「Try it out」发请求 |
| `http://localhost:8080/api/v3/api-docs` | OpenAPI JSON（可导入 Postman / Apifox） |
| `http://localhost:8080/api/v3/api-docs.yaml` | 同一份描述的 YAML 版 |

> 前缀是 `/api/swagger-ui.html`，不是 `/swagger-ui.html` —— 全局 `context-path: /api` 对静态资源一样生效。
> 注意 `/v3/api-docs` 与 `/v3/api-docs.yaml` 是**并列**的两个路径，不是 `/v3/api-docs/**` 的子路径。

**用之前先看页面最上面那段说明**，尤其这两条最容易误判：本项目一律返回 HTTP 200，
成败看响应体里的 `code`；除免登录接口外都要带 JWT —— 点右上角 **Authorize**，
把 `POST /auth/login` 返回的 `data.token` 填进去（**只填 token 本身，不要带 `Bearer ` 前缀**）。
演示账号见 `backend/src/main/resources/db/data.sql`。

**开关与安全**：文档默认关闭，仅 `dev` 环境默认打开，由单一开关 `rxt.api-docs.enabled`
（环境变量 `RXT_API_DOCS_ENABLED`）控制。这个开关同时管两件事 —— springdoc 是否注册端点、
`PublicEndpoints` 是否放行文档路径 —— 所以不会出现「白名单放行了、端点却不存在」这种错位。
一个交互式接口控制台等于把全部接口的请求构造能力公开出去，**不要在对外部署的 profile 里打开**；
关掉时这些地址会退回普通受保护路径（`code: 401`），而不是变成匿名可读。

```bash
# 本地临时关掉（比如排查「是不是文档影响了别的接口」）
RXT_API_DOCS_ENABLED=false mvn spring-boot:run
```

### 前端

```bash
cd frontend-web      # 或 frontend-admin
npm install
npm run dev
```

业务端默认 `http://localhost:5173`，管理后台默认 `http://localhost:5174`，
开发态通过 Vite 代理把 `/api` 转发到 `http://localhost:8080`。
代理目标可以用 `VITE_API_TARGET` 覆盖（三个前端都认这个变量），
换了后端端口时不用再改配置文件：

```bash
VITE_API_TARGET=http://localhost:18080 npm run dev
```

> 用 `scripts/demo-start.sh` 起的时候这些变量会被自动带上：三个前端拿到
> `VITE_API_TARGET=http://localhost:${BACKEND_PORT}`，落地页另外拿到
> `VITE_PLATFORM_BASE_URL=http://localhost:${WEB_PORT}`（决定页面上
> 「进入平台 / 登录 / 免费入驻」跳到哪）。所以 `BACKEND_PORT=18080 WEB_PORT=5273
> scripts/demo-start.sh` 这种换端口的玩法是全链路跟着走的。

## 部署（Docker Compose）

一套 compose 起四个容器：MySQL + 后端 + 三个前端的静态托管与分流（同一个 Nginx）。
**Nginx 只对外暴露一个端口**，按路径分流：

| 路径 | 去向 |
| --- | --- |
| `/` | 落地页（`frontend-landing`） |
| `/app/` | 业务端（`frontend-web`） |
| `/admin/` | 管理后台（`frontend-admin`） |
| `/api/**` | 反代到 `backend:8080` |

```bash
# 起（首次会自动初始化库表 + 灌演示数据）
RXT_HTTP_PORT=8088 docker compose up -d --build

# 看状态 / 跟日志
docker compose ps
docker compose logs -f backend

# 停（保留数据卷）/ 连数据一起清掉
docker compose down
docker compose down -v
```

起来后访问 `http://localhost:8088/`（落地页）、`/app/`（业务端）、`/admin/`（管理后台）。

| 文件 | 作用 |
| --- | --- |
| `docker-compose.yml` | 四个服务的编排、健康检查、卷与端口 |
| `deploy/web.Dockerfile` | 构建三个前端并打进 Nginx 镜像（三个构建阶段并行） |
| `deploy/backend.Dockerfile` | 后端构建 + 运行镜像（非 root 用户运行） |
| `deploy/nginx.conf` | 路径分流、SPA 回退、缓存策略、`/api` 反代 |
| `backend/src/main/resources/application-docker.yml` | 容器内 profile（数据源指向服务名 `mysql`，接口文档默认关） |
| `.dockerignore` | 构建上下文瘦身（排除 `node_modules` / `dist` / `target` 等） |

**几个刻意为之的地方，改之前先看这里：**

- **子路径是构建期决定的。** 业务端必须用 `RXT_BASE=/app/` 构建、管理后台用 `RXT_BASE=/admin/`；
  落地页要用 `VITE_PLATFORM_BASE_URL=/app`。这三件事 Dockerfile 里都写好了，
  但**手工构建时漏掉任何一个，症状都是白屏或按钮指向 `localhost:5173`**，
  而不是明显的报错。`RXT_BASE` 同时喂给 Vite 的 `base` 和前端路由的 `createWebHistory`，
  两边同源同一个值。
- **`nginx.conf` 里刻意不写正则 location。** nginx 的匹配优先级是
  「精确 `=` > 前缀 `^~` > 正则 `~` > 最长前缀」，一旦写了
  `location ~* \.(js|css|png|jpg)$` 这类缓存规则，它会**盖过前缀 location**，
  把 `/api/files/xxx.jpg` 也当静态文件去磁盘上找 —— 上传的图片全 404，
  而且只有「有图的地方」才看得出来。所以缓存规则一律写成前缀 + `^~`。
- **`proxy_pass` 后面不带尾斜杠。** 后端 `context-path=/api`，原始 URI 要原样透传；
  写成 `http://backend:8080/` 会把 `/api` 前缀替掉，全盘 404。
- **`client_max_body_size 20m`**，与后端 `spring.servlet.multipart.max-request-size` 对齐。
  nginx 默认只有 1MB，不改的话传图直接 413，而且**后端日志里一条都不留**，
  很容易被误判成「前端根本没调接口」。
- **`RXT_JWT_SECRET` 必须非空。** 留空会让 JJWT 在签发时抛异常，症状是
  「容器起来了、一登录就 500」。compose 里给了一个**明确标注为不安全**的默认值，
  对外部署时用环境变量覆盖。
- **MySQL 的健康检查查的是真实数据**（`SELECT COUNT(*) FROM rxt.t_user`），
  不是 `mysqladmin ping` —— ping 只能证明进程活着，而 `docker-entrypoint-initdb.d`
  下的初始化脚本是 mysqld 起来**之后**才执行的，用 ping 会提前判定「已就绪」，
  后端抢跑就会连到还没建表的库。

> 本机没有 Docker 时，用 `scripts/demo-start.sh` 跑本地四进程版本即可，功能一致
> （区别是本地版三个前端各占一个端口，没有路径分流）。

## 分层约定

- 层间单向依赖：`controller → service → mapper → entity`，禁止跨层调用与反向依赖
- 层间以 DTO 传递入参、以 VO 返回前端、Entity 与数据库表一一映射
- `common` / `config` / `security` 为公共支撑层，被各层共用
- 所有 Controller 统一返回 `Result<T>`
- 分页接口返回 `Result<IPage<T>>` —— **响应体里分页字段是 `current` / `size` / `total` / `records`**
  （`IPage` 的序列化结果），前端取列表要用 `data.records`、总条数用 `data.total`。
  现行 15 个分页接口中 13 个是 `Result<IPage<XxxVO>>`（参数 `pageNum` / `pageSize`），
  另有 2 个（`KnowledgeController` 的 `/knowledge/list`、`LoanProductController` 的
  `/finance/loan-products` —— 注意后者类级前缀是 `/finance`，不是 `/loan-products`）
  直接用 MyBatis-Plus 的具体类 `Result<Page<实体>>`，**参数名是 `page` / `size` 而不是
  `pageNum` / `pageSize`，返回的也是实体而非 VO**（`PageParamInterceptor` 两种写法都认，
  所以校验不会报错，但前端会拿错字段）。
  ⚠️ `common/PageResult` 这个类目前**没有任何接口在用**（历史遗留），别照它的字段名写前端。

## 当前状态

骨架已就绪：目录结构、实体映射、建表脚本、统一返回与异常处理、JWT 认证链路均已打通，
主要业务模块已按功能点逐个补齐；仅首页聚合（`HomeServiceImpl`）与智能推荐
（`RecommendServiceImpl`）仍为待实现骨架。

**已完成**

| 模块 | 范围 | 状态 |
| --- | --- | --- |
| M0 项目基础设施 | 建表脚本、统一返回与异常、分页、字段填充、跨域、JWT 认证链路 | 已就绪 |
| **M0 参数校验** | **全局参数校验**：路径不存在 404、缺必填参数 / 类型不匹配 400、分页下界拦截 | 已完成（P1） |
| **M0 日志规范** | **链路标识**：每请求一个 traceId，写入 MDC 与 `X-Trace-Id` 响应头 | 已完成（P1） |
| **M0 鉴权白名单** | 放行规则改为「**HTTP 方法 + 路径**」双条件，写接口不再被只读规则顺带放过 | 已完成（P1） |
| **M0 接口文档** | **Swagger / OpenAPI**：dev 环境可交互调试，非 dev 默认关闭（单一开关） | 已完成（P1） |
| M1 用户与权限 | 注册 / 登录 / 登出、个人资料与农户经营档案、角色权限校验（`PermissionChecker`） | 已完成 |
| M1 角色菜单 | 登录与资料接口返回 `menuPerms`（取自 `t_role.menu_perms`），供前端按角色渲染菜单 | 已完成 |
| M2 农产品交易 | 商品、购物车、订单（含支付 / 发货 / 收货状态流转） | 已完成 |
| M2 求购需求 | 求购需求发布与检索 | 已完成 |
| M4 融资服务 | 融资申请、我的融资、银行侧审批 | 接口在（原路由冲突已修，见下方「已修记录」） |
| M5 专家助力 | 知识、问答、预约 | 部分完成 |
| M6 平台治理 | 内容审核（商品 / 知识 / 评论） | 已完成 |
| **M6 平台治理** | **用户管理**：分页检索、启停用、调整角色、角色下拉 | 已完成（P1） |
| **M6 平台治理** | **融资管理**：银行侧待办与审批、管理员只读台账 | 已完成（P1） |
| **M6 平台治理** | **数据看板**：总量、待办、分布、近 7 日趋势 | 已完成（P1） |

**已修记录（2026-09-22 核对）**

- 原「已知问题 1」：`FinanceApplyController` 的 `@RequestMapping` 与 `context-path: /api`
  叠加成 `/api/api/finance/**` —— **已修**，现为 `@RequestMapping("/finance")`，
  实际路径 `/api/finance/**`。
- 原「已知问题 2」：`FinanceController` 与 `FinanceApplyController` 覆盖同一组业务路径，
  `POST /api/finance/apply` 命中空壳实现返回 `data: null` —— **已修**，
  重复的 `FinanceController` 已删除，现由 `FinanceApplyController` 单独承接。

**已知问题（未修，需对应模块负责人确认）**

1. `FinanceApplyServiceImpl.bankApprove` 把申请状态写成「已放款」/「审批未通过」，
   与概要设计 3.4 数据字典的 `apply_status` 词表（审批通过 / 审批未通过 / 已放款）的
   对应关系需确认（「审批通过」这一中间态是否保留）。银行侧审批请走 `/api/loan-admin/**`。

`config/WebMvcConfig.java` 的放行规则已迁到 `security/PublicEndpoints`，
按「**HTTP 方法 + 路径**」双条件判定，不再用 `excludePathPatterns`：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/auth/login`、`/auth/register` | 登录注册（写操作，不放 GET） |
| GET | `/home/**` | 首页推荐商品、农业知识 |
| GET | `/trade/products` | 商品货源列表 |
| GET | `/demand/list` | 求购需求列表 |
| 不限 | `/error` | 框架内部错误转发 |
| GET | `/v3/api-docs`、`/v3/api-docs.yaml`、`/v3/api-docs/**` | Swagger 描述（**仅 `rxt.api-docs.enabled=true` 时存在**） |
| GET | `/swagger-ui.html`、`/swagger-ui/**` | Swagger UI 页面与静态资源（同上） |

**其余接口一律要求登录**（`/admin/**` 与 `/loan-admin/**` 均在其中，且服务层再判角色）。

> 表格最后两行的文档规则**不是常量**：它们由 `rxt.api-docs.enabled` 决定 ——
> 关掉时既不在白名单里、端点也不注册，请求会退回普通受保护路径。做成条件而非固定放行，
> 是为了让「白名单放行的集合」在任何环境下都正好等于「真的能被匿名接住的集合」，
> 同时不给生产环境留一条常驻的匿名接口。改这两行请连同上面的开关说明一起改。

> **为什么不用 `excludePathPatterns`**：那个 API 只按路径匹配、**没有方法概念**。
> 一旦为了放行商品详情写上 `/trade/products/**`，同前缀下的
> `PUT /trade/products/{id}`（编辑）、`PUT /trade/products/{id}/status`（上下架）
> 会被**一起放行** —— 写接口的登录校验被静默摘掉，而且从代码上一眼看不出。
> 现在的规则表里每条都带方法，写接口不可能被只读规则顺带放过。
>
> 匹配用 `PathPatternParser`（不是 `AntPathMatcher`）：后者更宽松，
> 会把尾斜杠 `/trade/products/` 也判为命中，于是出现「白名单放行了、
> 路由其实匹配不上」的错位。用同一套 PathPattern 语义，
> 白名单放行的请求集合正好等于「真能被公开接口接住」的请求集合。

白名单**不等于忽略身份**：命中白名单的请求仍会尽力解析令牌，带且有效就写入 `UserContext`，
没带或无效则当游客放行 —— 这样「游客能看的接口」在已登录用户手里也能拿到自己的身份。

新增放行规则前请先确认该接口是否真的允许游客访问；
联调请带 `Authorization: Bearer <token>`，不要往白名单里加。

> **已修（2026-09-21）**：`LoanGroupController` 原本写了 `@RequestMapping("/api/finance")`，
> 与 `context-path: /api` 叠加后三个接口全部落在 `/api/api/finance/group` 上。
> 现已改为 `/finance`，并把字段注入换成构造器注入以对齐项目约定。

### 全局参数校验与链路标识（M0）

**坏请求怎么返回。** 本项目**不用 HTTP 状态码表达业务结果** —— 一律返回 HTTP 200，
真正的状态在 `Result.code` 里。前端 `api/request.js` 正是按 body 的 `code` 判断；
若这里改成真 404/400，axios 会落到错误分支弹「网络异常」，反而把真实提示盖掉。

| 情况 | `code` | `message` |
| --- | --- | --- |
| 路径写错 / 控制器前缀重复（如多写一个 `/api`） | 404 | 接口不存在，请核对请求路径 |
| 缺必填的 `@RequestParam` | 400 | 缺少必填参数：productId |
| 参数类型不对（如 `?pageNum=abc`） | 400 | 参数「pageNum」格式不正确 |
| 分页参数小于 1（如 `?pageSize=-5`） | 400 | 每页条数必须大于 0，当前为 -5 |
| 请求方法不对（如对只收 POST 的接口发 GET） | 400 | 请求方法 GET 不被该接口支持 |

> 分页下界由 `common/PageParamInterceptor` 一处收口（`pageNum` / `pageSize` / `page` / `size`
> 四种写法都覆盖），**不要**在每个 Controller 上重复加 `@Min(1)`；
> 上界仍由 `MybatisPlusConfig` 的 `setMaxLimit(200)` 负责。

**排查日志先按 traceId 过滤。** 每个请求进来时，`common/TraceIdFilter` 会分配一个 traceId
（客户端带了合法的 `X-Trace-Id` 就沿用），写进日志行首的 `[traceId]`，并回写同名响应头。
定位问题时拿响应头里的 traceId 去日志里搜，一次请求散落在 Controller / Service / Mapper
各层的日志就串成一条线：

```
2026-09-21 10:07:56.439 INFO  [m0d3verify0001] [http-nio-127.0.0.1-18080-exec-3] c.rxt.service.impl.AuthServiceImpl - 登录成功 userId=1 phone=13800000001 role=管理员
```

M6 新增接口的请求 / 响应结构见 `docs/接口约定-M6平台治理.md`；
内容审核与审核统计见 `docs/接口约定-M6内容审核.md`；
订单状态流转见 `docs/接口约定-M2订单流转.md`；
求购需求的发布 / 浏览 / 编辑 / 关闭（含字段长度约束与错误码）见 `docs/接口约定-求购需求.md`；
图片上传（三类图片共用一个接口，靠 `bizType` 区分，含类型白名单与存储实现差异）见
`docs/接口约定-公共文件上传.md`。

第三方支付与短信服务为**模拟通道**，不涉及真实资金。
