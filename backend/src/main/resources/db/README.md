# 数据库脚本目录

本目录存放数据库结构的一切定义。

**数据库实例本身（MySQL 数据文件、Navicat 等工具导出的 `.sql`）永远不进 git** ——
结构变更一律以脚本形式入库，这样每一步演进都有记录、可复现。

## 目录里有什么

| 路径 | 作用 |
|---|---|
| `schema.sql` | **唯一的结构基线**：建库、18 张表、索引、24 条外键。不含任何业务数据 |
| `data.sql` | 演示数据（角色 / 账号 / 贷款产品 / 示例商品订单等）。依赖 `schema.sql` 先执行 |
| `migrations/` | 增量变更脚本，给「已经有数据、不能清库」的环境用 |
| `migrations/_TEMPLATE.sql` | 变更脚本模板。下划线开头，不会被当成真实变更 |

除以上四个位置外不要再放 SQL 文件。不要建 `xxx_new.sql`、`test.sql`、`备份.sql`。

更完整的约定（命名规则的坑、外键推导规则、提交前自检清单）见
《融销通——数据库脚本约定》。

## 三份脚本的分工

| 文件 | 作用 | 什么时候执行 |
|---|---|---|
| `db/schema.sql` | 全量建库建表。**每张表都是 `DROP TABLE` + `CREATE TABLE`，会清空数据** | 新环境首次搭建，或本地数据可以丢弃时重建 |
| `db/data.sql` | 演示数据，依赖 schema.sql 先执行 | 想恢复成「有演示数据」的状态时 |
| `db/migrations/*.sql` | 增量变更，只用 `ALTER` / `CREATE INDEX`，**不动已有数据** | 拉到了别人的结构变更、且本地数据不想丢时 |

⚠️ **`data.sql` 是一次性初始化脚本，不能重复执行** ——
它里面 18 条 `INSERT` 全部显式写了主键，没有 `IGNORE` / `ON DUPLICATE`，
在非空库上重跑第一条就会报 `1062 Duplicate entry`。要重灌就先删库重建（见场景一/三）。

## 三种场景怎么做

**场景一 · 新环境从零搭建**（新同学加入、换电脑）

```bash
mysql -u root -p < backend/src/main/resources/db/schema.sql
mysql -u root -p < backend/src/main/resources/db/data.sql
```

**场景二 · 本地有数据，只想应用别人的结构变更**

按文件名顺序，执行自己还没执行过的那些：

```bash
mysql -u root -p rxt < backend/src/main/resources/db/migrations/2026-09-25_01_xxx.sql
```

**场景三 · 本地数据可以丢**（开发期最常用，最省事）

重跑 schema.sql + data.sql 即可，就是场景一那两条命令。

> 注意：`schema.sql` 是破坏性脚本，执行前确认本地没有不能丢的东西。
> 一旦你录了准备演示的数据，就不要再跑它了 —— 走场景二。

## 命名与写法

文件名格式：`YYYY-MM-DD_NN_简短描述.sql`，`NN` 是当天第几个变更。
本目录下的 `_TEMPLATE.sql` 是空白模板，下划线开头，不会被当成真实变更。

硬性要求：

- 一个文件只做一件事，名字要能看出做了什么
- **不要写 `DROP TABLE` / `DROP COLUMN`**。删字段先确认没人还在读它
- 加字段必须给 `DEFAULT` 或允许 `NULL`，否则表里已有数据会导致语句直接失败
- 加索引在大表上用 `ALGORITHM=INPLACE, LOCK=NONE`，避免长时间锁表
- 开头写清楚：改了什么、为什么、影响哪些模块
- **必须先在本地库上执行通过再提交**，不要提交没跑过的脚本
- 结构变了要同步改 `com.rxt.entity` 下对应的实体类，否则应用启动即报错
- 提交后在团队群公告一句：「我改了 `t_xxx`，加了 xx 字段，大家 pull 后执行 `migrations/xxx.sql`」

## 执行顺序不能反

**先结构，后数据。** 反过来跑，`data.sql` 第一条 INSERT 就会报 1146 表不存在，18 条全失败。

## 为什么要有这层约定

`schema.sql` 是「重建」而不是「升级」。如果只有它，那么任何一次结构变更都意味着
全组必须清库重来 —— 到了迭代三开始录演示数据之后，这条路就走不通了。
增量脚本让「已经有数据的库」也能跟上结构变化，同时它本身就是答辩时
「数据库如何演进」的证据。

## 待办

- [ ] **删除 `db/migrate_t_demand_20260918.sql`**（不要"改名归位"，直接删）

  原因（2026-09-20 核查）：

  1. 它要加的 `region` / `valid_until` / `contact` 三列和 `idx_demand_status_category`
     索引，**已经全部写在 `schema.sql` 里了**（第 206~208 行、第 214 行）。
     跑一遍 `schema.sql` 就已经有了，它对任何环境都多余。
  2. 它**没有 `USE \`rxt\`;`**（`schema.sql` 第 21 行、`data.sql` 第 28 行都有，只有它没有），
     直接 `mysql < 文件` 执行会报 `ERROR 1046 (3D000) No database selected`。
  3. 它的文件头写着"schema.sql 中的 CREATE TABLE 对'已存在'的表不生效" ——
     但 `schema.sql` 是 `DROP TABLE` + `CREATE TABLE`，**本来就会重建**，这句注释与现实不符。

  留着它只会让人以为是必跑步骤。**待办本身也正是"约定被违反"**：
  上面第 17 行说「除以上四个位置外不要再放 SQL 文件」，而它就是第 5 个。
