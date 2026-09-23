-- ===========================================================================
-- 为审核工作量统计补索引（t_audit）
-- ---------------------------------------------------------------------------
-- 日期：      2026-09-22
-- 作者：      李东阳
-- 影响模块：  M6 内容审核（REQ-15）
-- 原因：      新增「审核工作量统计」接口 GET /api/audit/statistics，两条查询形态是
--               WHERE auditor_id = ? AND target_type IN (...)                    -- 我的累计已审数
--               WHERE audit_time >= ? AND audit_time < ? AND target_type IN (...) -- 近 7 日趋势
--             t_audit 现有索引只有 PRIMARY(audit_id) 与 idx_audit_target(target_type, target_id)，
--             上面两个条件一个都用不上，会退化成全表扫描。
--             该表是「全站公共审核流水」，每审一次写入一行、只增不减，
--             因此现在就补，而不是等数据量上来再回头改。
-- 前提：      无（独立变更，不依赖其它迁移脚本）
-- 执行：      mysql -u root -p rxt < backend/src/main/resources/db/migrations/2026-09-22_01_audit_statistics_index.sql
-- 回滚：      ALTER TABLE `t_audit` DROP INDEX `idx_audit_time`;
--             ALTER TABLE `t_audit` DROP INDEX `idx_audit_auditor`;
--
-- 同步变更：db/schema.sql 的 t_audit 已同步加入这两个索引，
--           新环境重跑 schema.sql 即可，无需再执行本脚本。
-- 实体类：   只加索引、不动字段，com.rxt.entity.AuditRecord 无需改动。
-- ===========================================================================

-- 显式指定目标库，保证 `mysql < 文件` 与 `mysql rxt < 文件` 两种方式都能跑通
USE `rxt`;

-- 1. 近 7 日趋势：按审核时间做范围扫描
ALTER TABLE `t_audit`
  ADD INDEX `idx_audit_time` (`audit_time`),
  ALGORITHM=INPLACE, LOCK=NONE;

-- 2. 我的累计已审数：先按审核人定位，再按内容类型过滤
ALTER TABLE `t_audit`
  ADD INDEX `idx_audit_auditor` (`auditor_id`, `target_type`),
  ALGORITHM=INPLACE, LOCK=NONE;
