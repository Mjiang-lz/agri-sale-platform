-- ===========================================================================
-- 新增站内通知表（t_notification）
-- ---------------------------------------------------------------------------
-- 日期：      2026-10-13
-- 作者：      李东阳
-- 影响模块：  M2 农产品交易（订单状态变更）+ 跨模块公共能力（问答 / 预约 / 融资共用）
-- 原因：      《软件需求规格说明书》表 3-1「用户界面需求」最后几行明列：
--               「消息通知 | 融资申请状态变更、订单状态变更、问答与预约回复均生成站内
--                 通知，通知中心支持查看与标记已读」
--             而库中**没有承载通知的表**，后端也没有任何通知写入点 —— 这条需求从未落地。
--             schema.sql 里已有两处注释指向它（属于"设计写了、实现没跟上"）：
--               · t_answer 表头注释：「回答写入后，服务端同步将 t_question.status 更新
--                 为『已回复』并生成站内通知」——「已回复」那半句实现了，通知那半句没有；
--               · t_appointment 表头注释：「状态流转与通知规则由 M4 与 M6 共同承担」。
--             《详细设计说明书》§8.1.2 / §8.2.2 也把「消息通知通道」如实标为"未实现"，
--             并指明它是有明确挂载点、补齐时不需要改动主干消息序列的一段。
--             本次即补上这个挂载点：先建表 + 建接口，再把订单与问答两个事件接上去。
--
--             为什么是独立一张表、而不是各业务表加列：
--             ① 通知是"每个事件 × 每个接收人"一行，一单通知买卖双方就是两行，
--                与订单表是 1:N，塞进 t_order 会破坏订单行的一对一语义；
--             ② 四类事件（订单 / 问答 / 预约 / 融资）共用同一个"通知中心"入口，
--                各建一张表会让前端要拼四个列表、未读数还要四次请求求和。
--
-- 前提：      无（独立变更，不依赖其它迁移脚本）
-- 执行：      mysql -u root -p < backend/src/main/resources/db/migrations/2026-10-13_01_notification.sql
-- 回滚：      ALTER TABLE `t_notification` DROP FOREIGN KEY `fk_notification_user`;
--             DROP TABLE `t_notification`;
--             （本表只存通知，回滚不影响任何业务数据；但已产生的通知会丢失）
--
-- 可重复执行：建表用 CREATE TABLE IF NOT EXISTS；外键先查 information_schema 再决定是否执行
--             （MySQL 没有 ADD CONSTRAINT IF NOT EXISTS，直接重跑会报 1826 Duplicate
--             foreign key constraint name）。整套脚本连跑两次是安全的。
--
-- 字段说明：
--   user_id    接收人。**不是发出人** —— 通知是"推给谁"，发起方由业务事件隐式决定。
--   type       事件类型，取值 ORDER / QA / APPOINTMENT / FINANCE，与 SRS 表 3-1 列的
--              四类事件一一对应；前端按它决定图标与跳转目标，后端按它做白名单校验。
--   biz_id     关联业务主键（订单 id / 提问 id / 融资申请 id）。**故意不建外键**：
--              它指向哪张表由 type 决定，属多态关联 —— 与 t_audit.target_id、
--              t_comment.target_id 是同一处理方式（见 schema.sql 五、外键约束 的说明）。
--              只用于前端点击跳转与排查，后端不用它做 JOIN。
--   is_read    0 未读 / 1 已读。未读数是最高频查询（顶栏红点每次进页面都要），
--              因此索引把它放在 (user_id, is_read, create_time) 的第二位。
--   read_time  已读时间。NULL = 未读，与 is_read 语义重叠但保留：
--              只存布尔值无法回答"什么时候读的"，排查"用户说没看到"时这是唯一证据。
-- ===========================================================================

-- 显式指定目标库：这样 `mysql < 文件` 和 `mysql rxt < 文件` 两种执行方式都能跑通。
-- （schema.sql / data.sql 也是这么做的，保持一致，避免踩 1046 No database selected）
USE `rxt`;

-- ---------------------------------------------------------------------------
-- 1. 站内通知表。与 schema.sql 保持同一套命名规范：t_ 前缀、字段下划线、utf8mb4
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_notification` (
  `notification_id` BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '通知主键',
  `user_id`         BIGINT       NOT NULL                            COMMENT '接收人（t_user.user_id）',
  `type`            VARCHAR(20)  NOT NULL                            COMMENT '事件类型：ORDER 订单 / QA 问答 / APPOINTMENT 预约 / FINANCE 融资',
  `title`           VARCHAR(50)  NOT NULL                            COMMENT '标题',
  `content`         VARCHAR(200) NOT NULL                            COMMENT '正文摘要',
  `biz_id`          BIGINT           NULL                            COMMENT '关联业务主键（订单 / 提问 / 申请 id），多态字段不建外键',
  `is_read`         TINYINT      NOT NULL DEFAULT 0                  COMMENT '是否已读：0 未读，1 已读',
  `read_time`       DATETIME         NULL                            COMMENT '已读时间，NULL 表示未读',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '产生时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`notification_id`),
  -- 我的通知列表 + 顶栏未读数（两者都按 user_id 取，未读另有 is_read 过滤）
  KEY `idx_notification_user_read` (`user_id`, `is_read`, `create_time`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '站内通知';

-- ---------------------------------------------------------------------------
-- 2. 外键。与 schema.sql 末尾「统一在建表完成后添加」的写法保持一致。
--    只对 user_id 建外键：它是单一指向、且删除用户时通知必须一起清理。
--    biz_id 是多态字段，按本项目约定不建外键（见文件头字段说明）。
-- ---------------------------------------------------------------------------
SET @fk_exists = (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
   WHERE CONSTRAINT_SCHEMA = 'rxt'
     AND TABLE_NAME        = 't_notification'
     AND CONSTRAINT_NAME   = 'fk_notification_user'
     AND CONSTRAINT_TYPE   = 'FOREIGN KEY'
);
SET @sql = IF(@fk_exists = 0,
  'ALTER TABLE `t_notification` ADD CONSTRAINT `fk_notification_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`user_id`)',
  'SELECT ''fk_notification_user 已存在，跳过'' AS note');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 3. 只为 type 建一个"不建索引"的说明位（本次不加索引）。
--    现有索引 (user_id, is_read, create_time) 已覆盖全部查询口径：
--      · 我的通知列表 → user_id + 按 create_time 倒序分页；
--      · 只看未读     → user_id + is_read + create_time；
--      · 未读数       → user_id + is_read 计数。
--    没有"按 type 或 biz_id 反查通知"的场景（业务表自己有记录，通知只是提醒），
--    加这两个索引只会增加写开销。等真出现"按类型筛选通知"再补。
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 4. 演示数据：给测试账号补几条通知，覆盖「未读 / 已读」与「订单 / 问答」两类事件，
--    否则新用户第一次点开通知中心是空的，演示时容易被误判成"功能坏了"。
--    用 INSERT ... SELECT 而不是写死主键；NOT EXISTS 保证重复执行不会插出第二批。
--    收件人取真实存在的账号（user 5/6 采购方、7/8 专家、2 农户），
--    biz_id 指向 data.sql 里真实存在的订单与提问，点进链接不会落到 404。
-- ---------------------------------------------------------------------------
INSERT INTO `t_notification` (`user_id`, `type`, `title`, `content`, `biz_id`, `is_read`, `read_time`, `create_time`)
SELECT 6, 'ORDER', '订单已发货', '你的订单 RX20260906001 已发货，请留意查收。', 4, 0, NULL, '2026-09-07 08:30:00'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `t_notification` n WHERE n.`user_id` = 6 AND n.`type` = 'ORDER' AND n.`biz_id` = 4);

INSERT INTO `t_notification` (`user_id`, `type`, `title`, `content`, `biz_id`, `is_read`, `read_time`, `create_time`)
SELECT 6, 'QA', '专家已回复你的提问', '赵国强 专家回复了你的提问：控水之外还要补钙……', 1, 0, NULL, '2026-09-01 15:20:00'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `t_notification` n WHERE n.`user_id` = 6 AND n.`type` = 'QA' AND n.`biz_id` = 1);

INSERT INTO `t_notification` (`user_id`, `type`, `title`, `content`, `biz_id`, `is_read`, `read_time`, `create_time`)
SELECT 7, 'QA', '有新的提问待回复', '农户提交了新提问：小龙虾塘水草长得太密，需要打捞吗？', 6, 0, NULL, '2026-09-12 10:30:00'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `t_notification` n WHERE n.`user_id` = 7 AND n.`type` = 'QA' AND n.`biz_id` = 6);

INSERT INTO `t_notification` (`user_id`, `type`, `title`, `content`, `biz_id`, `is_read`, `read_time`, `create_time`)
SELECT 8, 'QA', '有新的提问待回复', '农户提交了新提问：小龙虾塘水草长得太密，需要打捞吗？', 6, 0, NULL, '2026-09-12 10:30:00'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `t_notification` n WHERE n.`user_id` = 8 AND n.`type` = 'QA' AND n.`biz_id` = 6);

-- 已读样例：证明 is_read / read_time 两个字段真的会被用起来，也让"只看未读"的筛选项有对照
INSERT INTO `t_notification` (`user_id`, `type`, `title`, `content`, `biz_id`, `is_read`, `read_time`, `create_time`)
SELECT 2, 'ORDER', '有新的订单', '你的商品「赣南脐橙」有新订单 RX20260906001，请及时发货。', 4, 1, '2026-09-06 12:00:00', '2026-09-06 10:55:00'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `t_notification` n WHERE n.`user_id` = 2 AND n.`type` = 'ORDER' AND n.`biz_id` = 4);
