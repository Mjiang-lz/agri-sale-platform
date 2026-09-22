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
| 代码托管与构建 | 华为云 CodeArts | — |

> **持久层依赖注意**：Spring Boot 4 必须使用 `mybatis-plus-spring-boot4-starter`（3.5.15 起提供）。
> 旧的 `mybatis-plus-spring-boot3-starter` 在 Boot 4 下自动配置类路径已变更，启动会直接失败。
> 分页插件另需引入 `mybatis-plus-jsqlparser`（3.5.9 起 JSqlParser 被拆为独立模块）。

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
└── docs/
    ├── 交付文档/                # 可行性研究报告、软件需求规格说明书、概要设计说明书
    └── 设计图/                  # 架构分层图、组件图、部署图、包图、类图、顺序图
```

## 协作规范

分支命名、提交信息格式、MR 流程、结构变更通知约定，以及建议在 CodeArts 里开启的
仓库规范规则，见 **[CONTRIBUTING.md](./CONTRIBUTING.md)**。

## 本地启动

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
（若本机 8080 已被别的项目占用，可加 `-Dspring-boot.run.arguments=--server.port=18080` 换端口，
注意同时改 `frontend-web/vite.config.*` 里的代理地址。）

### 前端

```bash
cd frontend-web      # 或 frontend-admin
npm install
npm run dev
```

业务端默认 `http://localhost:5173`，管理后台默认 `http://localhost:5174`，
开发态通过 Vite 代理把 `/api` 转发到 `http://localhost:8080`。

## 分层约定

- 层间单向依赖：`controller → service → mapper → entity`，禁止跨层调用与反向依赖
- 层间以 DTO 传递入参、以 VO 返回前端、Entity 与数据库表一一映射
- `common` / `config` / `security` 为公共支撑层，被各层共用
- 所有 Controller 统一返回 `Result<T>`，分页返回 `Result<PageResult<T>>`

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

**其余接口一律要求登录**（`/admin/**` 与 `/loan-admin/**` 均在其中，且服务层再判角色）。

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
订单状态流转见 `docs/接口约定-M2订单流转.md`。

第三方支付与短信服务为**模拟通道**，不涉及真实资金。
