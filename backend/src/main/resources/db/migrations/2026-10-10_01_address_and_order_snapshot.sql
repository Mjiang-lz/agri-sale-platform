-- ===========================================================================
-- 新增收货地址表（t_address）与订单收货信息快照字段（t_order）
-- ---------------------------------------------------------------------------
-- 日期：      2026-10-10
-- 作者：      李官杏
-- 影响模块：  M2 农产品交易（REQ-07 下单结算 / REQ-08 我的订单）
-- 原因：      原有结算链路只调用 POST /api/trade/order { productId, quantity }，
--             生成的订单里没有任何收货信息 —— t_order 建表时就没有收货人、
--             电话、地址这三类字段，前端 checkout() 也只传了商品和数量。
--             结果是「货发到哪」在系统里无处可答，订单详情页物理上无法展示
--             收货信息，农户发货时拿不到收件人与地址。
--
--             本次做两件事：
--               ① 新建 t_address，让采购方可以维护多个收货地址；
--               ② 给 t_order 加「下单那一刻」的收货信息快照。
--
--             ② 为什么是快照而不是外键指向 t_address：
--             地址是可以被用户修改或删除的。若订单只存 address_id，
--             用户改一次地址，所有历史订单的收货信息会跟着变 —— 那是错的，
--             历史订单必须保留「当时发到哪」的事实。快照字段与
--             unit_price（下单时单价快照）是同一个口径，只是这次快照的是地址。
--             因此这里**故意不加** address_id 外键，两者是刻意的冗余关系。
-- 前提：      无（独立变更，不依赖其它迁移脚本）
-- 执行：      mysql -u root -p < backend/src/main/resources/db/migrations/2026-10-10_01_address_and_order_snapshot.sql
-- 回滚：      ALTER TABLE `t_order` DROP COLUMN `consignee`, DROP COLUMN `consignee_phone`,
--                                  DROP COLUMN `receiver_address`, DROP COLUMN `order_remark`;
--             ALTER TABLE `t_address` DROP FOREIGN KEY `fk_address_user`;
--             DROP TABLE `t_address`;
--             （回滚会丢掉已录入的地址数据与历史订单的收货信息，执行前先导出）
--
-- 可重复执行：MySQL 没有 ADD COLUMN IF NOT EXISTS，也做不到
--             ADD CONSTRAINT IF NOT EXISTS，直接重跑会报
--             1060 Duplicate column name / 1826 Duplicate foreign key constraint name。
--             本脚本对本机演示库会被重跑（README 场景二允许人工补跑），
--             所以对「加列」和「加外键」两处都先查 information_schema 再决定要不要执行，
--             整套脚本连跑两次是安全的（实测通过）。
--
-- 字段说明：
--   is_default  是否默认地址：1 是，0 否。同一用户名下允许多条 0、最多一条 1，
--               「最多一条」由应用层在设默认时先清后置来保证 ——
--               MySQL 5.7 与 8.0.12 都不真正执行 CHECK 约束（见 schema.sql 头部说明），
--               写进 DDL 也只是看着有约束。
--   consignee / consignee_phone / receiver_address
--               下单时的收货信息快照。允许 NULL —— 本变更之前产生的 10 行历史订单
--               没有这些值，加 NOT NULL 会让 ALTER 在已有数据上直接失败。
--               新订单由 Service 层强制写入（「必填」是业务规则，不是结构约束）。
--   order_remark 买家下单时填的备注（如「工作日送达」），可选。
-- ===========================================================================

-- 显式指定目标库：这样 `mysql < 文件` 和 `mysql rxt < 文件` 两种执行方式都能跑通。
-- （schema.sql / data.sql 也是这么做的，保持一致，避免踩 1046 No database selected）
USE `rxt`;

-- ---------------------------------------------------------------------------
-- 1. 收货地址表。与 schema.sql 保持同一套命名规范：t_ 前缀、字段下划线、utf8mb4
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_address` (
  `address_id`  BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '地址主键',
  `user_id`     BIGINT       NOT NULL                            COMMENT '所属用户',
  `consignee`   VARCHAR(50)  NOT NULL                            COMMENT '收货人姓名',
  `phone`       VARCHAR(20)  NOT NULL                            COMMENT '收货人手机号',
  `province`    VARCHAR(50)  NOT NULL                            COMMENT '省 / 直辖市',
  `city`        VARCHAR(50)  NOT NULL                            COMMENT '市',
  `district`    VARCHAR(50)      NULL                            COMMENT '区 / 县',
  `detail`      VARCHAR(200) NOT NULL                            COMMENT '详细地址（街道门牌）',
  `is_default`  TINYINT      NOT NULL DEFAULT 0                  COMMENT '是否默认地址：1 是，0 否',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`address_id`),
  -- 我的地址列表（按用户取全部，默认地址由应用层单独筛）
  KEY `idx_address_user` (`user_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '收货地址';

-- ---------------------------------------------------------------------------
-- 2. t_address 的外键。与 schema.sql 末尾「统一在建表完成后添加」的写法保持一致。
-- ---------------------------------------------------------------------------
SET @fk_exists = (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
   WHERE CONSTRAINT_SCHEMA = 'rxt'
     AND TABLE_NAME        = 't_address'
     AND CONSTRAINT_NAME   = 'fk_address_user'
     AND CONSTRAINT_TYPE   = 'FOREIGN KEY'
);
SET @sql = IF(@fk_exists = 0,
  'ALTER TABLE `t_address` ADD CONSTRAINT `fk_address_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`user_id`)',
  'SELECT ''fk_address_user 已存在，跳过'' AS note');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 3. 订单收货信息快照字段。
--    只探 consignee 一列：这四列是同一个 ALTER 加的，要么都在、要么都不在，
--    逐列判四次除了让脚本更长之外不会更安全。
-- ---------------------------------------------------------------------------
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = 'rxt'
     AND TABLE_NAME   = 't_order'
     AND COLUMN_NAME  = 'consignee'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE `t_order`
     ADD COLUMN `consignee`        VARCHAR(50)  NULL COMMENT ''收货人姓名（下单时快照）''   AFTER `order_amount`,
     ADD COLUMN `consignee_phone`  VARCHAR(20)  NULL COMMENT ''收货人手机号（下单时快照）'' AFTER `consignee`,
     ADD COLUMN `receiver_address` VARCHAR(300) NULL COMMENT ''收货地址全文（下单时快照）'' AFTER `consignee_phone`,
     ADD COLUMN `order_remark`     VARCHAR(200) NULL COMMENT ''买家备注''                   AFTER `receiver_address`',
  'SELECT ''t_order 收货信息字段已存在，跳过'' AS note');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 4. 本次不加索引。
--    t_order 的既有查询口径是「按 buyer_id / seller_id + order_status」，
--    收货信息只跟随订单行一起返回、不作为过滤条件，加索引只增加写开销。
--    等真出现「按收货地区统计发货量」这类需求时再按需补。
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 5. 演示数据：给现有测试账号补一条默认地址。
--    否则采购方（13800000005 陈志远）第一次进结算页会看到空地址列表，
--    演示时容易被误判成「新功能坏了」。
--    用 INSERT ... SELECT 而不是写死主键，避免与手工造的数撞主键；
--    LEFT JOIN ... IS NULL 保证重复执行时不会插出第二条。
-- ---------------------------------------------------------------------------
INSERT INTO `t_address` (`user_id`, `consignee`, `phone`, `province`, `city`, `district`, `detail`, `is_default`)
SELECT u.`user_id`, '陈志远', u.`phone`, '江西省', '赣州市', '章贡区', '红旗大道 118 号 融销通大厦 3 楼', 1
  FROM `t_user` u
  LEFT JOIN `t_address` a ON a.`user_id` = u.`user_id`
 WHERE u.`phone` = '13800000005'
   AND a.`address_id` IS NULL;
