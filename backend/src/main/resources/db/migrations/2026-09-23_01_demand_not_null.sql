-- ===========================================================================
-- t_demand 补齐 5 列 NOT NULL 约束（先清洗 NULL，再收紧列定义）
-- ---------------------------------------------------------------------------
-- 日期：      2026-09-23
-- 作者：      李东阳
-- 影响模块：  M3 农产品求购
-- 原因：      `DemandPublishDTO` 把 category / quantity / region / valid_until /
--            contact 五列定为必填，但 DDL 里这五列都是可空，种子数据
--            （data.sql 的 t_demand 段）又漏写了 region / valid_until / contact
--            三列的值，于是产生了一批"接口永远造不出来、SQL 却能造出来"的行：
--              · valid_until 为 NULL -> 被 `valid_until >= NOW()` 静默排除，
--                求购大厅看不到（NULL >= NOW() 结果是 NULL 而非 TRUE）
--              · region 为 NULL      -> 按地区检索时同样静默排除（LIKE 不匹配 NULL）
--              · contact 为 NULL     -> 详情页没有联系方式，供货方联系不上采购方
--            三种情况都没有报错、没有日志，只能靠比对行数发现。
--            本次把 DDL 的可空性与 DTO 的必填集合对齐，让"错数据"在写入时就失败，
--            而不是在读取时静默消失。
-- 前提：      无
-- 执行：      mysql -u root -p rxt < backend/src/main/resources/db/migrations/2026-09-23_01_demand_not_null.sql
-- 回滚：      见文件末尾。结构可回滚，但被清洗的 NULL 值无法还原（单向变更）。
--
-- 幂等：      可重复执行。步骤 1 的 WHERE 只命中"仍有 NULL 的行"（重复执行时命中 0 行），
--            步骤 2 重复收紧同一约束无害。因此**脚本内容更新后不需要重跑**，
--            已执行过的库也不会被本脚本回改（见下面「相对时间 ≠ 永久有效」一段）。
--
-- ⚠️ 本变更必须与下面两个文件在**同一个提交**里一起改，否则全新导入会失败：
--      backend/src/main/resources/db/schema.sql   （5 列改 NOT NULL）
--      backend/src/main/resources/db/data.sql     （补 region / valid_until / contact）
--    原因：本机 sql_mode 含 STRICT_TRANS_TABLES，若 schema.sql 已是 NOT NULL 而
--    data.sql 仍不提供这三列的值，导入会直接报
--      ERROR 1048 (23000): Column 'valid_until' cannot be null
--    —— 这是好事：把"静默少 7 行"换成"要么全对、要么直接报错"。
-- ===========================================================================

USE `rxt`;

-- ---------------------------------------------------------------------------
-- 步骤 1 · 清洗（必须先于步骤 2）
-- ---------------------------------------------------------------------------
-- 顺序不可调换：列上还有 NULL 时执行 `MODIFY ... NOT NULL` 会失败
--   ERROR 1138 (22004): Invalid use of NULL value
-- 反过来，在列仍允许 NULL 时执行本 UPDATE 是安全的。
--
-- 用 COALESCE(原值, 兜底值)：只填 NULL，不覆盖已有数据。
--
-- `valid_until` 用 NOW() + 89 天，而不是写死一个日期（例如 2027-12-31）：
--   1) 写死的日期总有一天会变成"过去时间"，那批需求会在列表里静默消失 ——
--      正是本次要修的那类问题，不该用同一手法再埋一颗雷；
--   2) 89 天落在需求 §2.7.3「有效期不超过 90 天」的约束内，而写死到 2027 年底
--      反而超出了这条约束。
--   取 89 而不是 90：给"导入时刻"与"约束判定时刻"之间留一天余量，
--   避免以后有人拿边界值做文章（"正好 90 天到底算不算"）。
--
-- ⚠️ 相对时间 ≠ 永久有效（这一点务必记住）：
--   DATE_ADD(NOW(), INTERVAL 89 DAY) 在**执行本脚本那一刻**就被算成一个固定时间戳，
--   之后不再变化 ⇒ 它给的是「导入后 89 天的跑道」，不是"导入多久之后都有效"。
--   满 89 天后，列表口径 `status = 1 AND valid_until >= NOW()` 会再次返回 0 条，
--   **表现与本次要修的 bug 一模一样**（大厅空、不报错、不打日志）。
--   到期后的恢复办法（注意：重跑本脚本、或只重跑 data.sql 都救不了 —— 本脚本步骤 1
--   只对"仍有 NULL 的行"生效，已过期的行三列都非 NULL；只重跑 data.sql 会撞 1062 主键冲突）：
--     · 最轻（推荐）：UPDATE `t_demand` SET `valid_until` = DATE_ADD(NOW(), INTERVAL 89 DAY)
--                     WHERE `valid_until` < NOW();
--     · 或者重跑 schema.sql + data.sql 回到干净种子态（注意：会清空全部表）。
--   演示前自检：SELECT COUNT(*) FROM `t_demand` WHERE `status` = 1 AND `valid_until` >= NOW();
--              期望 = 库里 status=1 的行数（各家种子数据行数不同）；返回 0 就跑上面那条 UPDATE。
--   说明：本脚本**不会**回头修改已经迁移过的库（步骤 1 只填 NULL），
--        所以想"立刻把 30 天跑道换成 89 天"的库，需要单独执行一次上面的 UPDATE（按主键限定范围）。
--
-- `region` 按品类给一个合理的演示取值；`contact` 用固定号段 + 主键补齐，
-- 保证唯一且一眼能看出是演示号码（不会与真实用户号码混淆）。
--
-- 【本次不清洗、但要提一句】"有效期超过 90 天"的存量数据：
--   执行时实测本机库（MySQL 8.0.12 / 10 行种子+手工造数）有两行超出需求 §2.7.3
--   「有效期不超过 90 天」的约束：
--     #9  苹果 status=1  valid_until = 2026-12-31 23:59:59（距执行日 99 天）
--     #10 大米 status=0  valid_until = 2026-12-31 23:59:59（距执行日 99 天）
--   这两行是历史手工造数写死的日期，不是本次要修的 NULL 问题，故不在本脚本范围内。
--   它们的意义在于**印证了一件事**：写在应用层的 90 天校验只管得住"通过接口发布"，
--   管不住"直接写 SQL" —— 而库里已经存在两行这样的数据，且此前没有任何机制能发现。
--   ⇒ 90 天是「发布入参」规则，不是「数据不变量」，所以存量超限行不算脏数据。
--   若后续决定把 90 天约束也落到存量数据上，可另开一条（示例，勿在本脚本中启用）：
--     UPDATE `t_demand` SET `valid_until` = DATE_ADD(NOW(), INTERVAL 89 DAY)
--     WHERE `valid_until` > DATE_ADD(NOW(), INTERVAL 90 DAY);
--   只读巡检（不做任何修改，建议随 W2「过期自动失效」一起做）：
--     SELECT `demand_id`, `category`, `valid_until`,
--            DATEDIFF(`valid_until`, NOW()) AS `距今天数`, `status`
--     FROM `t_demand`
--     WHERE `valid_until` > DATE_ADD(NOW(), INTERVAL 90 DAY)
--     ORDER BY `valid_until` DESC;
UPDATE `t_demand`
SET `region` = COALESCE(`region`, CASE `category`
                     WHEN '水果'     THEN '浙江杭州'
                     WHEN '蔬菜'     THEN '山东寿光'
                     WHEN '粮油'     THEN '黑龙江五常'
                     WHEN '畜禽产品' THEN '河南正阳'
                     WHEN '水产'     THEN '湖北潜江'
                     WHEN '干货'     THEN '福建古田'
                     ELSE                 '北京新发地'
                   END),
    `valid_until` = COALESCE(`valid_until`, DATE_ADD(NOW(), INTERVAL 89 DAY)),
    `contact`     = COALESCE(`contact`, CONCAT('13800138', LPAD(`demand_id`, 3, '0')))
WHERE `region` IS NULL
   OR `valid_until` IS NULL
   OR `contact` IS NULL;

-- 执行完步骤 1 后应先确认没有遗漏，再往下走（应返回 0）：
--   SELECT COUNT(*) FROM `t_demand`
--   WHERE `region` IS NULL OR `valid_until` IS NULL OR `contact` IS NULL;

-- ---------------------------------------------------------------------------
-- 步骤 2 · 收紧列定义：与 DemandPublishDTO 的必填校验逐字段对齐
-- ---------------------------------------------------------------------------
-- `unit` / `expect_price` / `description` 业务上非必填（DTO 无必填约束），保持可空。
--
-- 注意：修改列的可空性**不适用 ALGORITHM=INPLACE**，MySQL 会走 COPY 算法
-- （整表重建 + 逐行拷贝），期间持有表锁。演示库只有 8 行、可忽略；
-- 若在生产环境执行，需另择低峰窗口。
ALTER TABLE `t_demand`
  MODIFY COLUMN `category`    VARCHAR(30) NOT NULL COMMENT '求购品类',
  MODIFY COLUMN `quantity`    INT         NOT NULL COMMENT '求购数量',
  MODIFY COLUMN `region`      VARCHAR(50) NOT NULL COMMENT '收货地区',
  MODIFY COLUMN `valid_until` DATETIME    NOT NULL COMMENT '需求有效期',
  MODIFY COLUMN `contact`     VARCHAR(50) NOT NULL COMMENT '联系方式';

-- ---------------------------------------------------------------------------
-- 执行后自查（二选一即可，两条都应返回"无结果/0"）
-- ---------------------------------------------------------------------------
-- 1) 五列的可空性应全部为 NO：
--    SELECT COLUMN_NAME, IS_NULLABLE FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA = 'rxt' AND TABLE_NAME = 't_demand'
--      AND COLUMN_NAME IN ('category','quantity','region','valid_until','contact');
--
-- 2) 列表接口的可见行数应恢复到"进行中的全部"（而不是被 NULL 吃掉大半）：
--    SELECT COUNT(*) FROM `t_demand` WHERE `status` = 1 AND `valid_until` >= NOW();

-- ---------------------------------------------------------------------------
-- 回滚
-- ---------------------------------------------------------------------------
-- 结构可以回滚（把 5 列放宽回 NULL）：
--   ALTER TABLE `t_demand`
--     MODIFY COLUMN `category`    VARCHAR(30) NULL COMMENT '求购品类',
--     MODIFY COLUMN `quantity`    INT         NULL COMMENT '求购数量',
--     MODIFY COLUMN `region`      VARCHAR(50) NULL COMMENT '收货地区',
--     MODIFY COLUMN `valid_until` DATETIME    NULL COMMENT '需求有效期',
--     MODIFY COLUMN `contact`     VARCHAR(50) NULL COMMENT '联系方式';
--
-- 但**数据不可回滚**：步骤 1 用兜底值覆盖了原来的 NULL，原始 NULL 状态无法还原。
-- 因此本迁移是单向变更；如果需要保留回滚能力，应在执行前先备份 t_demand：
--   CREATE TABLE `t_demand_bak_20260923` AS SELECT * FROM `t_demand`;
