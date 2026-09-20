-- ===========================================================================
-- 商品表新增「产地」与「多图」字段（t_product）
-- ---------------------------------------------------------------------------
-- 日期：      2026-09-20
-- 作者：      李东阳
-- 影响模块：  M2 农产品交易（REQ-05 商品货源检索 / REQ-06 商品详情）
-- 原因：      子任务「商品与检索接口」要求
--               ① 商品分页检索支持「关键词 + 分类 + 价格区间 + 产地」组合筛选；
--               ② 商品支持多图（返回可访问地址）。
--             而 t_product 原本既没有产地字段，image_url 也只能存一张图，
--             两项功能都缺基础字段，因此本次一并补上。
-- 前提：      无（独立变更，不依赖其它迁移脚本）
-- 执行：      mysql -u root -p rxt < backend/src/main/resources/db/migrations/2026-09-20_02_product_origin_images.sql
-- 回滚：      ALTER TABLE `t_product` DROP COLUMN `origin`, DROP COLUMN `images`;
--
-- 字段说明：
--   origin  产地。粒度到「市」，写法与 t_farmer_profile.credit_record 里
--           已有的地区保持一致（如「江西赣州」「黑龙江五常」），
--           后续按产地做智能匹配时可直接比对，不需要再解析自由文本。
--           允许 NULL —— 历史商品没有这个信息，新发布商品由农户填写。
--   images  多图地址数组，存 JSON 数组（如 ["/uploads/a.jpg","/uploads/b.jpg"]）。
--           保留原有 image_url 作为「主图」语义不动：商品列表接口正在用它，
--           直接改成数组会破坏现有接口与前端渲染，因此采取「主图 + 多图」并存。
--
-- 索引：      本次不加索引。列表检索的固定过滤条件是
--             audit_status='已通过' AND status=1，已能命中现有
--             idx_product_audit_status 的最左前缀；产地/分类等属于其后的
--             过滤条件，且当前表仅 26 行，新增索引的写开销大于收益。
--             待数据量上量、出现真实慢查询后再按需补。
--
-- 同步变更：  db/schema.sql 的 t_product 已同步加入这两个字段，
--             新环境重跑 schema.sql 即可；db/data.sql 同步为 26 条演示商品
--             补上产地，保证「产地筛选」在新建库上也有数据可验证。
-- ===========================================================================

-- 显式指定目标库，保证 `mysql < 文件` 与 `mysql rxt < 文件` 两种方式都能跑通
USE `rxt`;

-- 1. 产地：放在商品描述之后，与描述相邻便于阅读
ALTER TABLE `t_product`
  ADD COLUMN `origin` VARCHAR(50) NULL COMMENT '产地（到市，如 江西赣州）' AFTER `description`;

-- 2. 多图：放在主图 image_url 之后，两者语义相邻
ALTER TABLE `t_product`
  ADD COLUMN `images` JSON NULL COMMENT '多图地址数组（JSON），列表主图仍用 image_url' AFTER `image_url`;

-- 3. 为已有演示商品补产地数据
--    依据：商品的 seller_id 对应农户档案 t_farmer_profile.credit_record 里记录的地区
--      · 农户 2（王建国）  地区：江西赣州
--      · 农户 3（李秀兰）  地区：黑龙江五常
--      · 农户 4（赵大勇）  地区：湖北荆州
--    加 `origin IS NULL` 是为了让脚本只填历史空值，不覆盖农户已自行填写的内容。
UPDATE `t_product` SET `origin` = '江西赣州'   WHERE `seller_id` = 2 AND `origin` IS NULL;
UPDATE `t_product` SET `origin` = '黑龙江五常' WHERE `seller_id` = 3 AND `origin` IS NULL;
UPDATE `t_product` SET `origin` = '湖北荆州'   WHERE `seller_id` = 4 AND `origin` IS NULL;
