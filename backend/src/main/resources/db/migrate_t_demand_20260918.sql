-- ============================================================
-- M3 求购需求表结构扩展（REQ-04 / REQ-05，用例 UC-TRD-02）
-- 适用对象：已存在 t_demand 表的实例（如本地 rxt 库）
-- 执行方式：在目标库（rxt）查询窗口中整段执行一次即可
-- 说明：schema.sql 中的 CREATE TABLE 对“已存在”的表不生效，
--       故用 ALTER 增量变更；新增字段均有 DEFAULT NULL，旧数据不受影响。
-- ============================================================

-- 1. 补 SRS 要求的三个字段：收货地区 / 需求有效期 / 联系方式
ALTER TABLE `t_demand`
  ADD COLUMN `region`      VARCHAR(50) NULL DEFAULT NULL COMMENT '收货地区'   AFTER `description`,
  ADD COLUMN `valid_until` DATETIME    NULL DEFAULT NULL COMMENT '需求有效期' AFTER `region`,
  ADD COLUMN `contact`     VARCHAR(50) NULL DEFAULT NULL COMMENT '联系方式'   AFTER `valid_until`;

-- 2. 扩展检索索引，支持“按品类 + 地区”筛选
ALTER TABLE `t_demand` DROP INDEX  `idx_demand_status_category`;
ALTER TABLE `t_demand` ADD  INDEX  `idx_demand_status_category` (`status`, `category`, `region`);
