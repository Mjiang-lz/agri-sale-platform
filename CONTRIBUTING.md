# 协作规范

> 本文是团队约定的最小集合。凡与本文冲突的旧习惯，以本文为准。
> 建立时间：2026-09-21

---

## 一、分支命名

```
<type>/<模块或编号>-<简短说明>
```

- `type` 只用：`feat` / `fix` / `docs` / `refactor` / `test` / `chore`
- **一律小写英文与数字**，单词之间用 `-` 连接
- ❌ **不要用中文**，包括「-李东阳」这种人名后缀

### 为什么不用中文分支名

分支名会出现在这些地方，每一处都要做百分号编码：

| 场景 | 后果 |
|---|---|
| 浏览器 URL（MR / 分支页面） | 一长串 `%E6%9D%8E...`，发链接给别人基本没法看 |
| `git push origin --delete <分支>` | 终端里容易因编码/复制出错 |
| CI 流水线参数与日志 | 日志乱码，排查困难 |
| 跨平台（Windows 终端 / macOS 归档工具） | 已知会出现乱码与命令失败 |

需要区分归属时用**拼音**或**模块编号**，不要用本名。

✅ 好例子

```
feat/M6-governance
fix/M0-whitelist-path-and-method
feat/product-manage
docs/repo-conventions
```

❌ 反例

```
feat/audit-李东阳        ← 中文
feature/new-thing        ← type 不在清单里（用 feat）
zhangsan-dev             ← 缺 type 前缀
```

---

## 二、提交信息

标题格式：

```
<type>(<scope>): <简述>
```

| 位置 | 要求 |
|---|---|
| `type` | `feat` / `fix` / `docs` / `refactor` / `test` / `chore` |
| `scope` | 模块名，小写：`auth` / `trade` / `finance` / `expert` / `audit` / `governance` / `user` / `M0`…`M6` |
| 简述 | 写「做了什么」，不超过 50 字，结尾不加句号 |

**标题与正文之间空一行，正文写「为什么」。**

这是本项目目前最有价值的部分，务必保持 —— 后来的人（包括三个月后的你自己）
看的是「为什么这么写」，而不是「改了哪几行」。

✅ 例（项目真实提交，可作范本）

```
feat(trade): 新增商品发布/编辑/上下架接口

为什么发布接口用 POST /trade/product/publish 而不是 POST /trade/products：
鉴权白名单按路径匹配、**不区分 HTTP 方法**。复用 /trade/products 会让
同路径下的写接口连拦截器都不进，UserContext 恒为空。
```

### 关于合并提交

`merge feat/xxx into dev` 是 CodeArts 合并 MR 时**自动生成**的，
不遵循上面的格式。配置提交规则时请把合并提交排除（见「四」）。

---

## 三、合并请求（MR）

- 源分支 → 目标分支**固定为 `dev`**。任何人都不要直接推 `dev`。
- **一个 MR 只做一件事。** 顺手改了无关文件，拆成另一个 MR。
- 建 MR **之前**先同步主干，减少合并时的冲突面：

  ```bash
  git fetch origin
  git merge origin/dev      # 有冲突此时就暴露，别留到合并那一方去解
  ```

- MR 描述里写清三件事：**改了什么 / 怎么验证的 / 有没有别人要执行的操作**。

### 数据库结构变更必须单独醒目提示

如果 MR 包含 `backend/src/main/resources/db/migrations/` 下的新脚本，
**必须在 MR 描述顶部**加一段这样的提示：

> 【结构变更】`t_product` 新增 `origin`、`images` 两列。
> 合并后请执行：
> `mysql -u root -p rxt < backend/src/main/resources/db/migrations/2026-09-20_02_product_origin_images.sql`
> 不执行的后果：商品列表与详情**全部 500**。

原因：结构变更脚本不会自动执行，**谁 pull 了没跑，谁的本地就是坏的**，
而且报错信息（`Unknown column 'xxx' in 'field list'`）不指向真正的原因。

---

## 四、建议在 CodeArts 里开启的仓库规范

> 路径：仓库首页 → `设置` → `策略设置` → `提交规则` / `合并请求`
> 项目级：`设置` → `仓库管理` → `仓库设置`

| 规则 | 建议值 | 说明 |
|---|---|---|
| 分支名匹配 | `^(feat\|fix\|docs\|refactor\|test\|chore)/[a-z0-9._-]+$` | 直接禁掉中文分支名 |
| 提交信息匹配 | `^(feat\|fix\|docs\|refactor\|test\|chore)(\([a-z0-9-]+\))?: .{4,}` | 标题格式 |
| 合并提交 | **排除**（不校验） | 否则 CodeArts 自动生成的 merge message 会被拦 |
| 合并请求 | 目标分支限制为 `dev` | 防止误合到主干 |

---

## 五、提交前自查

| 项目 | 命令 |
|---|---|
| 后端编译 | `mvn -o -q compile` |
| **前端构建** | `npm run build` |
| 确认没带上不该提交的文件 | `git status --short` |

> ⚠️ **前端只跑 `npm run dev` 是不够的。**
> import 路径写错（例如文件叫 `api/finance.js`、却 import `@/api/financeP5`）
> 在 dev server 下可能只是页面白屏或警告，**只有 `npm run build` 会明确报错**：

```
[vite:load-fallback] Could not load .../src/api/financeP5: ENOENT
```

`.gitignore` 已覆盖 `target/`、`node_modules/`、`dist/`、`.DS_Store`、
`application-local.yml`、数据库导出文件、以及各前端目录的**锁文件**
（`package-lock.json` / `yarn.lock` / `pnpm-lock.yaml`，本项目不跟踪锁文件），
正常情况下不会误提交。
