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
`service.impl` 中各业务方法留有 `TODO`，按功能点逐个补齐即可。

**已完成**：M1 登录 / 注册后端（`AuthServiceImpl`：手机号唯一校验、BCrypt 加密入库、JWT 签发、
角色白名单）；M2 农产品交易；M2 求购需求；M4 融资服务部分接口；
**M1 鉴权白名单收口** —— 交易与求购模块的用户身份一律改由登录态（`UserContext`）获取，
不再接受客户端传入的 `buyerId` / `publisherId`。

**未完成**：M1 个人资料（`UserServiceImpl.getProfile` 仍是 `TODO`）、M1 前端登录页、
M1 角色权限校验（`PermissionChecker` 已实现但尚未接入，越权暂不返回 403）。

`config/WebMvcConfig.java` 当前仅放行无需身份的只读接口：
`/auth/login`、`/auth/register`、`/home/**`、`/trade/products`、`/demand/list`、`/error`，
**其余接口一律要求登录**。新增放行路径前请先确认该接口是否真的允许游客访问；
联调请带 `Authorization: Bearer <token>`，不要往白名单里加。

第三方支付与短信服务为**模拟通道**，不涉及真实资金。
