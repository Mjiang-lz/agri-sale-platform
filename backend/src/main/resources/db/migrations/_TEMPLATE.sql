-- ===========================================================================
-- <一句话说明这次改了什么>
-- ---------------------------------------------------------------------------
-- 日期：      YYYY-MM-DD
-- 作者：      <姓名>
-- 影响模块：  <M1 用户与权限 / M2 农产品交易 / M4 融资服务 / ...>
-- 原因：      <为什么需要这次变更>
-- 前提：      先执行过 <可选，若依赖前面的变更就写上那个文件名>
-- 执行：      mysql -u root -p rxt < backend/src/main/resources/db/migrations/<本文件名>
-- 回滚：      <写得出就写回滚语句；写不出就说明为什么无法回滚>
--
-- 注意：本文件是模板，下划线开头不会被当成真实变更。复制一份改名后使用，
--       并在提交前先在自己的本地库上执行通过。
-- ===========================================================================

-- 显式指定目标库：这样 `mysql < 文件` 和 `mysql rxt < 文件` 两种执行方式都能跑通。
-- （schema.sql / data.sql 也是这么做的，保持一致，避免踩 1046 No database selected）
USE `rxt`;

-- 示例一 · 加字段（必须给 DEFAULT 或允许 NULL，否则已有数据会导致失败）
-- ALTER TABLE `t_audit`
--   ADD COLUMN `remark` VARCHAR(255) NULL COMMENT '审核备注' AFTER `audit_time`;

-- 示例二 · 加索引（大表加索引建议带 ALGORITHM=INPLACE, LOCK=NONE）
-- ALTER TABLE `t_audit`
--   ADD INDEX `idx_audit_target` (`target_type`, `target_id`),
--   ALGORITHM=INPLACE, LOCK=NONE;

-- 示例三 · 加表（与 schema.sql 保持同一套命名规范：t_ 前缀、字段下划线、utf8mb4）
-- CREATE TABLE IF NOT EXISTS `t_example` (
--   `example_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
--   `create_time` DATETIME NOT NULL COMMENT '创建时间',
--   PRIMARY KEY (`example_id`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='示例表';

-- 示例四 · 改字段（改类型前先确认现有数据能转换过去）
-- ALTER TABLE `t_order`
--   MODIFY COLUMN `remark` VARCHAR(500) NULL COMMENT '订单备注';
