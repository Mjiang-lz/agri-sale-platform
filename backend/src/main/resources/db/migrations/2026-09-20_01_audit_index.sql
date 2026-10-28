-- ===========================================================================
-- 为审核中心待审队列补索引（t_product、t_comment）
-- ---------------------------------------------------------------------------
-- 日期：      2026-09-20
-- 作者：      李东阳
-- 影响模块：  M6 内容审核（REQ-15）
-- 原因：      审核中心「待审核列表」的查询形态是
--               WHERE audit_status = '待审核' ORDER BY create_time DESC LIMIT n
--             t_knowledge 已有 idx_knowledge_audit_category（audit_status 在最左），
--             可以支撑状态筛选；而 t_product 完全没有 audit_status 索引、
--             t_comment 的索引是 (target_type, target_id, audit_status)，
--             单独按 audit_status 查走不到最左前缀，因此补两个索引。
-- 前提：      无（独立变更，不依赖其它迁移脚本）
-- 执行：      mysql -u root -p rxt < backend/src/main/resources/db/migrations/2026-09-20_01_audit_index.sql
-- 回滚：      ALTER TABLE `t_product` DROP INDEX `idx_product_audit_status`;
--             ALTER TABLE `t_comment` DROP INDEX `idx_comment_audit_status`;
--
-- 同步变更：db/schema.sql 的 t_product 与 t_comment 已同步加入这两个索引，
--           新环境重跑 schema.sql 即可，无需再执行本脚本。
-- 实体类：   只加索引、不动字段，com.rxt.entity 下无需改动。
-- ===========================================================================

-- 显式指定目标库，保证 `mysql < 文件` 与 `mysql rxt < 文件` 两种方式都能跑通
USE `rxt`;

-- 1. 商品货源：待审队列按 (审核状态, 创建时间) 检索
ALTER TABLE `t_product`
  ADD INDEX `idx_product_audit_status` (`audit_status`, `create_time`),
  ALGORITHM=INPLACE, LOCK=NONE;

-- 2. 评论：待审队列按 (审核状态, 创建时间) 检索
ALTER TABLE `t_comment`
  ADD INDEX `idx_comment_audit_status` (`audit_status`, `create_time`),
  ALGORITHM=INPLACE, LOCK=NONE;
