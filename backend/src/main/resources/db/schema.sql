-- ===========================================================================
-- 基于数字经济的农产品融销一体平台 · 数据库结构（schema.sql）
-- ---------------------------------------------------------------------------
-- 目标数据库：MySQL 8.0（utf8mb4 / InnoDB）
-- 设计依据：《概要设计说明书》3.1 设计原则、3.2 实体关系总览、
--           3.3 数据表设计（表 3-1 ~ 表 3-18）、3.4 数据字典约定、
--           3.5 查询与索引设计
-- 一致性来源：与 backend/src/main/java/com/rxt/entity 下 18 个实体一一对应
--
-- 执行方式：
--   mysql -uroot -p < backend/src/main/resources/db/schema.sql
--   mysql -uroot -p < backend/src/main/resources/db/data.sql      -- 初始数据
--
-- 本脚本仅建表、建索引、建外键，不含任何业务数据。
-- ===========================================================================

CREATE DATABASE IF NOT EXISTS `rxt`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `rxt`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ===========================================================================
-- 一、用户与治理域（对应功能模块 M1 用户与认证、M6 个人信息）
-- ===========================================================================

-- 表 3-2　t_role 角色表（RBAC 基础数据，对应 REQ-14）
DROP TABLE IF EXISTS `t_role`;
CREATE TABLE `t_role` (
  `role_id`     BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '角色主键',
  `role_name`   VARCHAR(30)  NOT NULL                            COMMENT '角色名称：农户、采购方、专家、银行用户、管理员',
  `menu_perms`  VARCHAR(500)     NULL                            COMMENT '菜单权限标识集合，供前端动态渲染菜单',
  `description` VARCHAR(200)     NULL                            COMMENT '角色职责描述',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`role_id`),
  -- 角色名称唯一
  UNIQUE KEY `uk_role_name` (`role_name`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '角色';

-- 表 3-1　t_user 用户表
-- 说明：实名认证按"不采集真实身份信息"的限制，仅保留姓名等最少必要字段，证件号码不入库。
--       password 为加盐哈希后的不可逆密文，VARCHAR(100) 可容纳 BCrypt(60) 或"盐$摘要"格式。
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
  `user_id`     BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '用户主键',
  `phone`       VARCHAR(20)  NOT NULL                            COMMENT '手机号，同时作为登录账号',
  `password`    VARCHAR(100) NOT NULL                            COMMENT '密码，加盐哈希后存储，不可逆',
  `real_name`   VARCHAR(50)      NULL                            COMMENT '真实姓名',
  `role_id`     BIGINT       NOT NULL                            COMMENT '所属角色',
  `avatar_url`  VARCHAR(255)     NULL                            COMMENT '用户头像地址（对象存储 URL）',
  `status`      TINYINT      NOT NULL DEFAULT 1                  COMMENT '账号状态：1 正常，0 停用',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`),
  -- 手机号唯一，作为登录账号
  UNIQUE KEY `uk_user_phone` (`phone`),
  -- 按角色检索用户
  KEY `idx_user_role_id` (`role_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '用户';

-- 表 3-3　t_farmer_profile 农户档案表
-- 说明：credit_record 与交易行为数据共同构成 S1 AI 智能服务的特征输入；
--       交易完成后由 M5 回写行为数据。
DROP TABLE IF EXISTS `t_farmer_profile`;
CREATE TABLE `t_farmer_profile` (
  `profile_id`    BIGINT      NOT NULL AUTO_INCREMENT             COMMENT '档案主键',
  `user_id`       BIGINT      NOT NULL                            COMMENT '关联农户用户',
  `main_product`  VARCHAR(50)     NULL                            COMMENT '主营农产品',
  `scale_desc`    VARCHAR(50)     NULL                            COMMENT '经营规模描述',
  `credit_record` TEXT            NULL                            COMMENT '信用与经营特征记录，作为智能匹配的输入特征',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`profile_id`),
  -- 一个农户用户仅一条档案（一对一）
  UNIQUE KEY `uk_farmer_profile_user_id` (`user_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '农户档案';

-- 表 3-4　t_audit 审核记录表（统一审核记录表，对应 REQ-15）
-- 说明：采用统一审核记录表而非为每类对象建表，避免审核逻辑重复实现。
--       target_id 为多态关联字段（由 target_type 决定指向的表），故不建数据库外键。
DROP TABLE IF EXISTS `t_audit`;
CREATE TABLE `t_audit` (
  `audit_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '审核记录主键',
  `target_type`  VARCHAR(30)  NOT NULL                COMMENT '审核对象类型：USER、PRODUCT、KNOWLEDGE、EXPERT、COMMENT',
  `target_id`    BIGINT       NOT NULL                COMMENT '审核对象主键，与 target_type 联合定位',
  `audit_result` VARCHAR(20)  NOT NULL                COMMENT '审核结果：通过、不通过',
  `audit_remark` VARCHAR(200)     NULL                COMMENT '审核意见',
  `auditor_id`   BIGINT           NULL                COMMENT '审核人（管理员）',
  `audit_time`   DATETIME     NOT NULL                COMMENT '审核时间',
  PRIMARY KEY (`audit_id`),
  -- 审核记录回溯
  KEY `idx_audit_target` (`target_type`, `target_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '审核记录';

-- ===========================================================================
-- 二、交易域（对应功能模块 M5 农产品交易）
-- ===========================================================================

-- 表 3-5　t_product 商品货源表（对应 REQ-02）
DROP TABLE IF EXISTS `t_product`;
CREATE TABLE `t_product` (
  `product_id`   BIGINT        NOT NULL AUTO_INCREMENT             COMMENT '商品主键',
  `product_name` VARCHAR(100)  NOT NULL                            COMMENT '商品名称',
  `category`     VARCHAR(30)       NULL                            COMMENT '商品品类',
  `price`        DECIMAL(12,2) NOT NULL                            COMMENT '单价，下单时作为价格快照写入订单',
  `stock`        INT           NOT NULL DEFAULT 0                  COMMENT '可售库存',
  `unit`         VARCHAR(10)       NULL                            COMMENT '计量单位（斤、公斤、箱等）',
  `image_url`    VARCHAR(255)      NULL                            COMMENT '商品图片地址（对象存储 URL）',
  `description`  TEXT              NULL                            COMMENT '商品描述',
  `seller_id`    BIGINT        NOT NULL                            COMMENT '发布农户',
  `audit_status` VARCHAR(20)   NOT NULL                            COMMENT '审核状态：待审核、已通过、未通过',
  `status`       TINYINT       NOT NULL DEFAULT 1                  COMMENT '上下架状态：1 上架，0 下架',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`product_id`),
  -- 商品列表与品类筛选
  KEY `idx_product_status_category` (`status`, `category`),
  -- 我发布的商品
  KEY `idx_product_seller_id` (`seller_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '商品货源';

-- 表 3-6　t_cart 购物车表（对应 REQ-03）
-- 说明：一期购物车采用"一行一商品"的扁平结构，由 cart_id 承担条目主键；
--       按 (buyer_id, product_id) 唯一约束避免同一商品重复入车。
DROP TABLE IF EXISTS `t_cart`;
CREATE TABLE `t_cart` (
  `cart_id`      BIGINT        NOT NULL AUTO_INCREMENT             COMMENT '购物车条目主键',
  `buyer_id`     BIGINT        NOT NULL                            COMMENT '所属采购方',
  `product_id`   BIGINT        NOT NULL                            COMMENT '商品',
  `quantity`     INT           NOT NULL DEFAULT 1                  COMMENT '数量',
  `total_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00               COMMENT '条目金额合计 = 商品单价 × 数量',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '加入时间',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`cart_id`),
  -- 同一采购方对同一商品仅一行
  UNIQUE KEY `uk_cart_buyer_product` (`buyer_id`, `product_id`),
  -- 商品被加车情况统计
  KEY `idx_cart_product_id` (`product_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '购物车';

-- 表 3-7　t_order 订单表（对应 REQ-03）
-- 说明：unit_price 采用下单时快照而非实时读取商品表，保证历史订单金额不受调价影响；
--       order_amount 由服务端计算，不接受前端传入，从设计上杜绝金额篡改。
DROP TABLE IF EXISTS `t_order`;
CREATE TABLE `t_order` (
  `order_id`     BIGINT        NOT NULL AUTO_INCREMENT             COMMENT '订单主键',
  `order_no`     VARCHAR(32)   NOT NULL                            COMMENT '订单编号，对外展示',
  `buyer_id`     BIGINT        NOT NULL                            COMMENT '采购方',
  `seller_id`    BIGINT        NOT NULL                            COMMENT '农户（卖家）',
  `product_id`   BIGINT        NOT NULL                            COMMENT '商品',
  `quantity`     INT           NOT NULL                            COMMENT '购买数量',
  `unit_price`   DECIMAL(12,2) NOT NULL                            COMMENT '下单时单价快照',
  `order_amount` DECIMAL(12,2) NOT NULL                            COMMENT '订单金额 = unit_price × quantity，与订单明细完全一致',
  `order_status` VARCHAR(20)   NOT NULL                            COMMENT '订单状态：待支付、待发货、待收货、已完成、已取消',
  `pay_time`     DATETIME          NULL                            COMMENT '支付成功时间',
  `deliver_time` DATETIME          NULL                            COMMENT '发货时间',
  `receive_time` DATETIME          NULL                            COMMENT '收货时间',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '下单时间',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`order_id`),
  -- 订单号精确查询
  UNIQUE KEY `uk_order_no` (`order_no`),
  -- 我买到的
  KEY `idx_order_buyer_status` (`buyer_id`, `order_status`),
  -- 我卖出的
  KEY `idx_order_seller_status` (`seller_id`, `order_status`),
  -- 商品成交统计
  KEY `idx_order_product_id` (`product_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '订单';

-- 表 3-8　t_demand 求购需求表（对应 REQ-04）
DROP TABLE IF EXISTS `t_demand`;
CREATE TABLE `t_demand` (
  `demand_id`    BIGINT        NOT NULL AUTO_INCREMENT             COMMENT '求购需求主键',
  `publisher_id` BIGINT        NOT NULL                            COMMENT '发布人（采购方或农户）',
  `category`     VARCHAR(30)       NULL                            COMMENT '求购品类',
  `quantity`     INT               NULL                            COMMENT '求购数量',
  `unit`         VARCHAR(10)       NULL                            COMMENT '计量单位',
  `expect_price` DECIMAL(12,2)     NULL                            COMMENT '期望单价',
  `description`  TEXT              NULL                            COMMENT '需求补充说明',
  `region`       VARCHAR(50)       NULL                            COMMENT '收货地区',
  `valid_until`  DATETIME          NULL                            COMMENT '需求有效期',
  `contact`      VARCHAR(50)       NULL                            COMMENT '联系方式',
  `status`       TINYINT       NOT NULL DEFAULT 1                  COMMENT '状态：1 有效，0 已关闭',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '发布时间',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`demand_id`),
  -- 求购需求检索（品类 + 地区）
  KEY `idx_demand_status_category` (`status`, `category`, `region`),
  -- 我发布的求购
  KEY `idx_demand_publisher_id` (`publisher_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '求购需求';

-- 表 3-9　t_comment 评论表
-- 说明：商品评论与知识评论共用本表，通过 target_type 区分，避免同类结构重复建表。
--       target_id 为多态关联字段，故不建数据库外键。
DROP TABLE IF EXISTS `t_comment`;
CREATE TABLE `t_comment` (
  `comment_id`   BIGINT      NOT NULL AUTO_INCREMENT             COMMENT '评论主键',
  `user_id`      BIGINT      NOT NULL                            COMMENT '评论人',
  `target_type`  VARCHAR(30) NOT NULL                            COMMENT '评论对象类型：PRODUCT、KNOWLEDGE',
  `target_id`    BIGINT      NOT NULL                            COMMENT '评论对象主键',
  `content`      TEXT        NOT NULL                            COMMENT '评论内容',
  `audit_status` VARCHAR(20) NOT NULL                            COMMENT '审核状态：待审核、已通过、未通过',
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '评论时间',
  PRIMARY KEY (`comment_id`),
  -- 对象下的已通过评论
  KEY `idx_comment_target` (`target_type`, `target_id`, `audit_status`),
  -- 我的评论
  KEY `idx_comment_user_id` (`user_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '评论';

-- ===========================================================================
-- 三、融资域（对应功能模块 M3 融资服务、M7 融资管理、S1 AI 智能服务）
-- ===========================================================================

-- 表 3-10　t_loan_product 贷款产品表（对应 REQ-05）
-- 说明：产品由平台预置，一期不提供产品维护界面。
DROP TABLE IF EXISTS `t_loan_product`;
CREATE TABLE `t_loan_product` (
  `loan_product_id` BIGINT        NOT NULL AUTO_INCREMENT             COMMENT '贷款产品主键',
  `product_name`    VARCHAR(100)  NOT NULL                            COMMENT '产品名称',
  `max_amount`      DECIMAL(12,2) NOT NULL                            COMMENT '最高可贷额度',
  `annual_rate`     DECIMAL(6,4)  NOT NULL                            COMMENT '年利率，如 0.0450 表示 4.5%',
  `term_months`     INT               NULL                            COMMENT '贷款期限（月）',
  `min_group_size`  INT               NULL                            COMMENT '起贷所需联合人数下限',
  `description`     TEXT              NULL                            COMMENT '产品说明与申请条件',
  `status`          TINYINT       NOT NULL DEFAULT 1                  COMMENT '状态：1 在售，0 下架',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`loan_product_id`),
  -- 在售产品一览
  KEY `idx_loan_product_status` (`status`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '贷款产品';

-- 表 3-11　t_finance_apply 融资申请表（对应 REQ-06）
-- 说明：本表是整个融资主流程的载体，apply_status 驱动各环节流转，取值见 3.4 数据字典。
DROP TABLE IF EXISTS `t_finance_apply`;
CREATE TABLE `t_finance_apply` (
  `apply_id`        BIGINT        NOT NULL AUTO_INCREMENT             COMMENT '融资申请主键',
  `apply_no`        VARCHAR(32)   NOT NULL                            COMMENT '申请编号，对外展示与银行侧引用',
  `farmer_id`       BIGINT        NOT NULL                            COMMENT '申请农户',
  `loan_product_id` BIGINT        NOT NULL                            COMMENT '申请的贷款产品',
  `apply_amount`    DECIMAL(12,2) NOT NULL                            COMMENT '申请额度',
  `apply_term`      INT           NOT NULL                            COMMENT '申请期限（月）',
  `apply_purpose`   VARCHAR(200)      NULL                            COMMENT '资金用途说明',
  `group_id`        BIGINT            NULL                            COMMENT '所属联合贷款小组，匹配成功后回填',
  `apply_status`    VARCHAR(20)   NOT NULL                            COMMENT '申请状态：待匹配、已组建小组、已推送银行、审批通过、审批未通过、已放款',
  `push_time`       DATETIME          NULL                            COMMENT '推送至银行侧的时间',
  `apply_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '提交申请时间',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`apply_id`),
  -- 申请编号对外唯一
  UNIQUE KEY `uk_finance_apply_no` (`apply_no`),
  -- 我的融资（按农户与状态）
  KEY `idx_finance_apply_farmer` (`farmer_id`, `apply_status`),
  -- 银行侧待办申请
  KEY `idx_finance_apply_bank` (`apply_status`, `push_time`),
  -- 按产品统计
  KEY `idx_finance_apply_product` (`loan_product_id`),
  -- 小组内成员申请
  KEY `idx_finance_apply_group` (`group_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '融资申请';

-- 表 3-12　t_loan_group 联合贷款小组表（对应 REQ-08）
-- 说明：小组成员关系由成员的 t_finance_apply.group_id 反向表达，
--       一期不单独建成员关联表，避免为固定小规模场景引入额外表结构。
DROP TABLE IF EXISTS `t_loan_group`;
CREATE TABLE `t_loan_group` (
  `group_id`      BIGINT        NOT NULL AUTO_INCREMENT             COMMENT '联合贷款小组主键',
  `group_name`    VARCHAR(50)       NULL                            COMMENT '小组名称，可自动生成',
  `leader_id`     BIGINT        NOT NULL                            COMMENT '组长，即发起融资申请的农户',
  `target_amount` DECIMAL(12,2) NOT NULL                            COMMENT '目标融资金额合计',
  `member_count`  INT           NOT NULL DEFAULT 1                  COMMENT '当前成员数',
  `group_status`  VARCHAR(20)   NOT NULL                            COMMENT '小组状态：组建中、已提交、已受理、已放款、未通过',
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`group_id`),
  -- 我组建的小组
  KEY `idx_loan_group_leader_id` (`leader_id`),
  -- 按小组状态检索
  KEY `idx_loan_group_status` (`group_status`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '联合贷款小组';

-- 表 3-13　t_approval 审批记录表（对应 REQ-13）
-- 说明：approval_opinion 在不通过时具有业务必填语义，由服务端校验而非数据库约束，
--       以便区分"未填写"与"不适用"。
DROP TABLE IF EXISTS `t_approval`;
CREATE TABLE `t_approval` (
  `approval_id`      BIGINT        NOT NULL AUTO_INCREMENT             COMMENT '审批记录主键',
  `apply_id`         BIGINT        NOT NULL                            COMMENT '被审批的融资申请',
  `bank_user_id`     BIGINT        NOT NULL                            COMMENT '审批人（银行用户）',
  `approval_result`  VARCHAR(20)   NOT NULL                            COMMENT '审批结果：通过、不通过',
  `loan_amount`      DECIMAL(12,2)     NULL                            COMMENT '核准放款金额，审批通过时必填',
  `approval_opinion` VARCHAR(200)      NULL                            COMMENT '审批意见；不通过时须填写原因',
  `approval_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '审批时间',
  `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  PRIMARY KEY (`approval_id`),
  -- 一笔申请对应一条审批记录
  KEY `idx_approval_apply_id` (`apply_id`),
  -- 银行侧我的审批
  KEY `idx_approval_bank_user_id` (`bank_user_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '审批记录';

-- ===========================================================================
-- 四、专家服务域（对应功能模块 M4 专家助力）
-- ===========================================================================

-- 表 3-14　t_expert 专家信息表
-- 说明：专家信息经管理员审核通过后才在前台"专家一览"中展示，审核过程记录在 t_audit 中。
DROP TABLE IF EXISTS `t_expert`;
CREATE TABLE `t_expert` (
  `expert_id`     BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '专家主键',
  `user_id`       BIGINT       NOT NULL                            COMMENT '关联用户账号',
  `expert_domain` VARCHAR(50)      NULL                            COMMENT '专业领域（种植、养殖、农技、金融等）',
  `introduction`  TEXT             NULL                            COMMENT '专家介绍与服务范围',
  `workload_stat` VARCHAR(200)     NULL                            COMMENT '服务工作量统计（问答数、预约数等）',
  `audit_status`  VARCHAR(20)  NOT NULL                            COMMENT '审核状态：待审核、已通过、未通过',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`expert_id`),
  -- 一个用户仅一份专家信息（一对一）
  UNIQUE KEY `uk_expert_user_id` (`user_id`),
  -- 已通过专家一览
  KEY `idx_expert_audit_status` (`audit_status`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '专家信息';

-- 表 3-15　t_knowledge 农业知识表（对应 REQ-09）
-- 说明：仅 audit_status = 已通过 的条目对外检索。
DROP TABLE IF EXISTS `t_knowledge`;
CREATE TABLE `t_knowledge` (
  `knowledge_id` BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '知识条目主键',
  `title`        VARCHAR(100) NOT NULL                            COMMENT '标题',
  `category`     VARCHAR(30)      NULL                            COMMENT '分类',
  `content`      TEXT         NOT NULL                            COMMENT '正文内容',
  `image_url`    VARCHAR(255)     NULL                            COMMENT '配图地址（对象存储 URL）',
  `author_id`    BIGINT       NOT NULL                            COMMENT '发布专家',
  `audit_status` VARCHAR(20)  NOT NULL                            COMMENT '审核状态：待审核、已通过、未通过',
  `view_count`   INT          NOT NULL DEFAULT 0                  COMMENT '浏览量',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`knowledge_id`),
  -- 知识条目检索
  KEY `idx_knowledge_audit_category` (`audit_status`, `category`),
  -- 我发布的知识
  KEY `idx_knowledge_author_id` (`author_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '农业知识';

-- 表 3-16　t_question 提问表（对应 REQ-10）
-- 说明：expert_id 允许为空是一期简化设计，保留"公开提问 + 专家认领"的扩展空间。
DROP TABLE IF EXISTS `t_question`;
CREATE TABLE `t_question` (
  `question_id` BIGINT      NOT NULL AUTO_INCREMENT             COMMENT '提问主键',
  `asker_id`    BIGINT      NOT NULL                            COMMENT '提问农户',
  `expert_id`   BIGINT          NULL                            COMMENT '指定专家；为空表示公开提问，任何专家可认领',
  `content`     TEXT        NOT NULL                            COMMENT '提问内容',
  `status`      VARCHAR(20) NOT NULL                            COMMENT '状态：待回复、已回复、已关闭',
  `ask_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '提问时间',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  PRIMARY KEY (`question_id`),
  -- 问答会话查询
  KEY `idx_question_status` (`status`),
  -- 专家侧待回复提问
  KEY `idx_question_expert_id` (`expert_id`),
  -- 农户侧我的提问
  KEY `idx_question_asker_id` (`asker_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '提问';

-- 表 3-17　t_answer 回答表
-- 说明：提问与回答分离为两张表，支持一个问题多条回答及后续追问场景。
--       回答写入后，服务端同步将 t_question.status 更新为"已回复"并生成站内通知。
DROP TABLE IF EXISTS `t_answer`;
CREATE TABLE `t_answer` (
  `answer_id`   BIGINT   NOT NULL AUTO_INCREMENT             COMMENT '回答主键',
  `question_id` BIGINT   NOT NULL                            COMMENT '关联提问',
  `expert_id`   BIGINT   NOT NULL                            COMMENT '回复专家',
  `content`     TEXT     NOT NULL                            COMMENT '回复内容',
  `answer_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '回复时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  PRIMARY KEY (`answer_id`),
  -- 一个提问多条回答
  KEY `idx_answer_question_id` (`question_id`),
  -- 专家回答过的提问
  KEY `idx_answer_expert_id` (`expert_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '回答';

-- 表 3-18　t_appointment 专家预约表（对应 REQ-11）
-- 说明：状态流转与通知规则由 M4 与 M6 共同承担。
DROP TABLE IF EXISTS `t_appointment`;
CREATE TABLE `t_appointment` (
  `appt_id`     BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '预约主键',
  `farmer_id`   BIGINT       NOT NULL                            COMMENT '预约农户',
  `expert_id`   BIGINT       NOT NULL                            COMMENT '被预约专家',
  `appt_time`   DATETIME     NOT NULL                            COMMENT '预约时间',
  `appt_topic`  VARCHAR(200)     NULL                            COMMENT '咨询主题',
  `appt_status` VARCHAR(20)  NOT NULL                            COMMENT '状态：待处理、已接受、已拒绝、已完成、已取消',
  `remark`      VARCHAR(200)     NULL                            COMMENT '备注（专家处理意见或取消原因）',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`appt_id`),
  -- 专家侧预约查询
  KEY `idx_appointment_expert_id` (`expert_id`),
  -- 农户侧我的预约
  KEY `idx_appointment_farmer_id` (`farmer_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '专家预约';

-- ===========================================================================
-- 五、外键约束
-- 说明：统一在建表完成后添加，避免建表顺序依赖；不引入级联删除（见 3.1 第 8 条）。
--       多态关联字段（t_audit.target_id、t_comment.target_id）按设计不建外键。
-- ===========================================================================

-- 用户与治理域
ALTER TABLE `t_user`
    ADD CONSTRAINT `fk_user_role_id` FOREIGN KEY (`role_id`) REFERENCES `t_role` (`role_id`);
ALTER TABLE `t_farmer_profile`
    ADD CONSTRAINT `fk_farmer_profile_user_id` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`user_id`);

-- 交易域
ALTER TABLE `t_product`
    ADD CONSTRAINT `fk_product_seller_id` FOREIGN KEY (`seller_id`) REFERENCES `t_user` (`user_id`);
ALTER TABLE `t_cart`
    ADD CONSTRAINT `fk_cart_buyer_id` FOREIGN KEY (`buyer_id`) REFERENCES `t_user` (`user_id`),
    ADD CONSTRAINT `fk_cart_product_id` FOREIGN KEY (`product_id`) REFERENCES `t_product` (`product_id`);
ALTER TABLE `t_order`
    ADD CONSTRAINT `fk_order_buyer_id` FOREIGN KEY (`buyer_id`) REFERENCES `t_user` (`user_id`),
    ADD CONSTRAINT `fk_order_seller_id` FOREIGN KEY (`seller_id`) REFERENCES `t_user` (`user_id`),
    ADD CONSTRAINT `fk_order_product_id` FOREIGN KEY (`product_id`) REFERENCES `t_product` (`product_id`);
ALTER TABLE `t_demand`
    ADD CONSTRAINT `fk_demand_publisher_id` FOREIGN KEY (`publisher_id`) REFERENCES `t_user` (`user_id`);
ALTER TABLE `t_comment`
    ADD CONSTRAINT `fk_comment_user_id` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`user_id`);

-- 融资域
ALTER TABLE `t_loan_group`
    ADD CONSTRAINT `fk_loan_group_leader_id` FOREIGN KEY (`leader_id`) REFERENCES `t_user` (`user_id`);
ALTER TABLE `t_finance_apply`
    ADD CONSTRAINT `fk_finance_apply_farmer_id` FOREIGN KEY (`farmer_id`) REFERENCES `t_user` (`user_id`),
    ADD CONSTRAINT `fk_finance_apply_loan_product_id` FOREIGN KEY (`loan_product_id`) REFERENCES `t_loan_product` (`loan_product_id`),
    ADD CONSTRAINT `fk_finance_apply_group_id` FOREIGN KEY (`group_id`) REFERENCES `t_loan_group` (`group_id`);
ALTER TABLE `t_approval`
    ADD CONSTRAINT `fk_approval_apply_id` FOREIGN KEY (`apply_id`) REFERENCES `t_finance_apply` (`apply_id`),
    ADD CONSTRAINT `fk_approval_bank_user_id` FOREIGN KEY (`bank_user_id`) REFERENCES `t_user` (`user_id`);

-- 专家服务域
ALTER TABLE `t_expert`
    ADD CONSTRAINT `fk_expert_user_id` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`user_id`);
ALTER TABLE `t_knowledge`
    ADD CONSTRAINT `fk_knowledge_author_id` FOREIGN KEY (`author_id`) REFERENCES `t_expert` (`expert_id`);
ALTER TABLE `t_question`
    ADD CONSTRAINT `fk_question_asker_id` FOREIGN KEY (`asker_id`) REFERENCES `t_user` (`user_id`),
    ADD CONSTRAINT `fk_question_expert_id` FOREIGN KEY (`expert_id`) REFERENCES `t_expert` (`expert_id`);
ALTER TABLE `t_answer`
    ADD CONSTRAINT `fk_answer_question_id` FOREIGN KEY (`question_id`) REFERENCES `t_question` (`question_id`),
    ADD CONSTRAINT `fk_answer_expert_id` FOREIGN KEY (`expert_id`) REFERENCES `t_expert` (`expert_id`);
ALTER TABLE `t_appointment`
    ADD CONSTRAINT `fk_appointment_farmer_id` FOREIGN KEY (`farmer_id`) REFERENCES `t_user` (`user_id`),
    ADD CONSTRAINT `fk_appointment_expert_id` FOREIGN KEY (`expert_id`) REFERENCES `t_expert` (`expert_id`);

SET FOREIGN_KEY_CHECKS = 1;

-- ===========================================================================
-- 建库完成：18 张业务表 + 24 条外键 + 索引（见《概要设计说明书》3.5）
-- 后续：执行 data.sql 写入角色、账号、贷款产品等初始数据
-- ===========================================================================
