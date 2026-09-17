-- ---------------------------------------------------------------------------
-- 基于数字经济的农产品融销一体平台 · 数据库结构
-- 依据《概要设计说明书》3.4 数据设计（表 3-1 ~ 表 3-18）生成
-- 目标数据库：MySQL 8.0，字符集 utf8mb4
-- ---------------------------------------------------------------------------

CREATE DATABASE IF NOT EXISTS `rxt` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `rxt`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 表 t_user　用户
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
  `user_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键',
  `phone` VARCHAR(20) NOT NULL UNIQUE COMMENT '手机号，同时作为登录账号',
  `password` VARCHAR(100) NOT NULL COMMENT '密码，加盐哈希后存储，不可逆',
  `real_name` VARCHAR(50) NULL COMMENT '真实姓名',
  `role_id` BIGINT NOT NULL COMMENT '所属角色',
  `avatar_url` VARCHAR(255) NULL COMMENT '用户头像地址（对象存储 URL）',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '账号状态：1 正常，0 停用',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- 表 t_role　角色
DROP TABLE IF EXISTS `t_role`;
CREATE TABLE `t_role` (
  `role_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色主键',
  `role_name` VARCHAR(30) NOT NULL UNIQUE COMMENT '角色名称：农户、采购方、专家、银行用户、管理员',
  `menu_perms` VARCHAR(500) NULL COMMENT '菜单权限标识集合，供前端动态渲染菜单',
  `description` VARCHAR(200) NULL COMMENT '角色职责描述',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

-- 表 t_farmer_profile　农户档案
DROP TABLE IF EXISTS `t_farmer_profile`;
CREATE TABLE `t_farmer_profile` (
  `profile_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '档案主键',
  `user_id` BIGINT NOT NULL UNIQUE COMMENT '关联农户用户',
  `main_product` VARCHAR(50) NULL COMMENT '主营农产品',
  `scale_desc` VARCHAR(50) NULL COMMENT '经营规模描述',
  `credit_record` TEXT NULL COMMENT '信用与经营特征记录，作为智能匹配的输入特征',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='农户档案';

-- 表 t_audit　审核记录
DROP TABLE IF EXISTS `t_audit`;
CREATE TABLE `t_audit` (
  `audit_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审核记录主键',
  `target_type` VARCHAR(30) NOT NULL COMMENT '审核对象类型：USER、PRODUCT、KNOWLEDGE、EXPERT、COMMENT',
  `target_id` BIGINT NOT NULL COMMENT '审核对象主键，与 target_type 联合定位',
  `audit_result` VARCHAR(20) NOT NULL COMMENT '审核结果：通过、不通过',
  `audit_remark` VARCHAR(200) NULL COMMENT '审核意见',
  `auditor_id` BIGINT NULL COMMENT '审核人（管理员）',
  `audit_time` DATETIME NOT NULL COMMENT '审核时间',
  PRIMARY KEY (`audit_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核记录';

-- 表 t_product　商品货源
DROP TABLE IF EXISTS `t_product`;
CREATE TABLE `t_product` (
  `product_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品主键',
  `product_name` VARCHAR(100) NOT NULL COMMENT '商品名称',
  `category` VARCHAR(30) NULL COMMENT '商品品类',
  `price` DECIMAL(12,2) NOT NULL COMMENT '单价，下单时作为价格快照写入订单',
  `stock` INT NOT NULL DEFAULT 0 COMMENT '可售库存',
  `unit` VARCHAR(10) NULL COMMENT '计量单位（斤、公斤、箱等）',
  `image_url` VARCHAR(255) NULL COMMENT '商品图片地址（对象存储 URL）',
  `description` TEXT NULL COMMENT '商品描述',
  `seller_id` BIGINT NOT NULL COMMENT '发布农户',
  `audit_status` VARCHAR(20) NOT NULL COMMENT '审核状态：待审核、已通过、未通过',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '上下架状态：1 上架，0 下架',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品货源';

-- 表 t_cart　购物车
DROP TABLE IF EXISTS `t_cart`;
CREATE TABLE `t_cart` (
  `cart_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '购物车条目主键',
  `buyer_id` BIGINT NOT NULL COMMENT '所属采购方',
  `product_id` BIGINT NOT NULL COMMENT '商品',
  `quantity` INT NOT NULL DEFAULT 1 COMMENT '数量',
  `total_amount` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '条目金额合计',
  `create_time` DATETIME NOT NULL COMMENT '加入时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`cart_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车';

-- 表 t_order　订单
DROP TABLE IF EXISTS `t_order`;
CREATE TABLE `t_order` (
  `order_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单主键',
  `order_no` VARCHAR(32) NOT NULL UNIQUE COMMENT '订单编号，对外展示',
  `buyer_id` BIGINT NOT NULL COMMENT '采购方',
  `seller_id` BIGINT NOT NULL COMMENT '农户（卖家）',
  `product_id` BIGINT NOT NULL COMMENT '商品',
  `quantity` INT NOT NULL COMMENT '购买数量',
  `unit_price` DECIMAL(12,2) NOT NULL COMMENT '下单时单价快照',
  `order_amount` DECIMAL(12,2) NOT NULL COMMENT '订单金额',
  `order_status` VARCHAR(20) NOT NULL COMMENT '订单状态：待支付、待发货、待收货、已完成、已取消',
  `pay_time` DATETIME NULL COMMENT '支付成功时间',
  `deliver_time` DATETIME NULL COMMENT '发货时间',
  `receive_time` DATETIME NULL COMMENT '收货时间',
  `create_time` DATETIME NOT NULL COMMENT '下单时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单';

-- 表 t_demand　求购需求
DROP TABLE IF EXISTS `t_demand`;
CREATE TABLE `t_demand` (
  `demand_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '求购需求主键',
  `publisher_id` BIGINT NOT NULL COMMENT '发布人（采购方或农户）',
  `category` VARCHAR(30) NULL COMMENT '求购品类',
  `quantity` INT NULL COMMENT '求购数量',
  `unit` VARCHAR(10) NULL COMMENT '计量单位',
  `expect_price` DECIMAL(12,2) NULL COMMENT '期望单价',
  `description` TEXT NULL COMMENT '需求补充说明',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 有效，0 已关闭',
  `create_time` DATETIME NOT NULL COMMENT '发布时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`demand_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='求购需求';

-- 表 t_comment　评论
DROP TABLE IF EXISTS `t_comment`;
CREATE TABLE `t_comment` (
  `comment_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评论主键',
  `user_id` BIGINT NOT NULL COMMENT '评论人',
  `target_type` VARCHAR(30) NOT NULL COMMENT '评论对象类型：PRODUCT、KNOWLEDGE',
  `target_id` BIGINT NOT NULL COMMENT '评论对象主键',
  `content` TEXT NOT NULL COMMENT '评论内容',
  `audit_status` VARCHAR(20) NOT NULL COMMENT '审核状态：待审核、已通过、未通过',
  `create_time` DATETIME NOT NULL COMMENT '评论时间',
  PRIMARY KEY (`comment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论';

-- 表 t_loan_product　贷款产品
DROP TABLE IF EXISTS `t_loan_product`;
CREATE TABLE `t_loan_product` (
  `loan_product_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '贷款产品主键',
  `product_name` VARCHAR(100) NOT NULL COMMENT '产品名称',
  `max_amount` DECIMAL(12,2) NOT NULL COMMENT '最高可贷额度',
  `annual_rate` DECIMAL(6,4) NOT NULL COMMENT '年利率，如 0.0450 表示 4.5%',
  `term_months` INT NULL COMMENT '贷款期限（月）',
  `min_group_size` INT NULL COMMENT '起贷所需联合人数下限',
  `description` TEXT NULL COMMENT '产品说明与申请条件',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 在售，0 下架',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`loan_product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='贷款产品';

-- 表 t_finance_apply　融资申请
DROP TABLE IF EXISTS `t_finance_apply`;
CREATE TABLE `t_finance_apply` (
  `apply_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '融资申请主键',
  `apply_no` VARCHAR(32) NOT NULL UNIQUE COMMENT '申请编号，对外展示与银行侧引用',
  `farmer_id` BIGINT NOT NULL COMMENT '申请农户',
  `loan_product_id` BIGINT NOT NULL COMMENT '申请的贷款产品',
  `apply_amount` DECIMAL(12,2) NOT NULL COMMENT '申请额度',
  `apply_term` INT NOT NULL COMMENT '申请期限（月）',
  `apply_purpose` VARCHAR(200) NULL COMMENT '资金用途说明',
  `group_id` BIGINT NULL COMMENT '所属联合贷款小组，匹配成功后回填',
  `apply_status` VARCHAR(20) NOT NULL COMMENT '申请状态',
  `push_time` DATETIME NULL COMMENT '推送至银行侧的时间',
  `apply_time` DATETIME NOT NULL COMMENT '提交申请时间',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`apply_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='融资申请';

-- 表 t_loan_group　联合贷款小组
DROP TABLE IF EXISTS `t_loan_group`;
CREATE TABLE `t_loan_group` (
  `group_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '联合贷款小组主键',
  `group_name` VARCHAR(50) NULL COMMENT '小组名称，可自动生成',
  `leader_id` BIGINT NOT NULL COMMENT '组长，即发起融资申请的农户',
  `target_amount` DECIMAL(12,2) NOT NULL COMMENT '目标融资金额合计',
  `member_count` INT NOT NULL DEFAULT 1 COMMENT '当前成员数',
  `group_status` VARCHAR(20) NOT NULL COMMENT '小组状态：组建中、已提交、已受理、已放款、未通过',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联合贷款小组';

-- 表 t_approval　审批记录
DROP TABLE IF EXISTS `t_approval`;
CREATE TABLE `t_approval` (
  `approval_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批记录主键',
  `apply_id` BIGINT NOT NULL COMMENT '被审批的融资申请',
  `bank_user_id` BIGINT NOT NULL COMMENT '审批人（银行用户）',
  `approval_result` VARCHAR(20) NOT NULL COMMENT '审批结果：通过、不通过',
  `loan_amount` DECIMAL(12,2) NULL COMMENT '核准放款金额，审批通过时必填',
  `approval_opinion` VARCHAR(200) NULL COMMENT '审批意见；不通过时须填写原因',
  `approval_time` DATETIME NOT NULL COMMENT '审批时间',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`approval_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录';

-- 表 t_expert　专家信息
DROP TABLE IF EXISTS `t_expert`;
CREATE TABLE `t_expert` (
  `expert_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '专家主键',
  `user_id` BIGINT NOT NULL UNIQUE COMMENT '关联用户账号',
  `expert_domain` VARCHAR(50) NULL COMMENT '专业领域（种植、养殖、农技、金融等）',
  `introduction` TEXT NULL COMMENT '专家介绍与服务范围',
  `workload_stat` VARCHAR(200) NULL COMMENT '服务工作量统计（问答数、预约数等）',
  `audit_status` VARCHAR(20) NOT NULL COMMENT '审核状态：待审核、已通过、未通过',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`expert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家信息';

-- 表 t_knowledge　农业知识
DROP TABLE IF EXISTS `t_knowledge`;
CREATE TABLE `t_knowledge` (
  `knowledge_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '知识条目主键',
  `title` VARCHAR(100) NOT NULL COMMENT '标题',
  `category` VARCHAR(30) NULL COMMENT '分类',
  `content` TEXT NOT NULL COMMENT '正文内容',
  `image_url` VARCHAR(255) NULL COMMENT '配图地址（对象存储 URL）',
  `author_id` BIGINT NOT NULL COMMENT '发布专家',
  `audit_status` VARCHAR(20) NOT NULL COMMENT '审核状态：待审核、已通过、未通过',
  `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览量',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`knowledge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='农业知识';

-- 表 t_question　提问
DROP TABLE IF EXISTS `t_question`;
CREATE TABLE `t_question` (
  `question_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '提问主键',
  `asker_id` BIGINT NOT NULL COMMENT '提问农户',
  `expert_id` BIGINT NULL COMMENT '指定专家；为空表示公开提问，任何专家可认领',
  `content` TEXT NOT NULL COMMENT '提问内容',
  `status` VARCHAR(20) NOT NULL COMMENT '状态：待回复、已回复、已关闭',
  `ask_time` DATETIME NOT NULL COMMENT '提问时间',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提问';

-- 表 t_answer　回答
DROP TABLE IF EXISTS `t_answer`;
CREATE TABLE `t_answer` (
  `answer_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '回答主键',
  `question_id` BIGINT NOT NULL COMMENT '关联提问',
  `expert_id` BIGINT NOT NULL COMMENT '回复专家',
  `content` TEXT NOT NULL COMMENT '回复内容',
  `answer_time` DATETIME NOT NULL COMMENT '回复时间',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`answer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回答';

-- 表 t_appointment　专家预约
DROP TABLE IF EXISTS `t_appointment`;
CREATE TABLE `t_appointment` (
  `appt_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '预约主键',
  `farmer_id` BIGINT NOT NULL COMMENT '预约农户',
  `expert_id` BIGINT NOT NULL COMMENT '被预约专家',
  `appt_time` DATETIME NOT NULL COMMENT '预约时间',
  `appt_topic` VARCHAR(200) NULL COMMENT '咨询主题',
  `appt_status` VARCHAR(20) NOT NULL COMMENT '状态：待处理、已接受、已拒绝、已完成、已取消',
  `remark` VARCHAR(200) NULL COMMENT '备注（专家处理意见或取消原因）',
  `create_time` DATETIME NOT NULL COMMENT '创建时间',
  `update_time` DATETIME NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`appt_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家预约';

-- ---------------------------------------------------------------------------
-- 外键约束（统一在建表完成后添加，避免建表顺序依赖）
-- 注意：被引用列必须写各表 **真实的主键列名**。
--   这批表的主键命名并不完全统一（t_finance_apply→apply_id、t_loan_group→group_id 等为简写），
--   不能按「t_表名_id」推导，否则会引用到不存在的列。
-- ---------------------------------------------------------------------------
ALTER TABLE `t_user` ADD CONSTRAINT `fk_t_user_role_id` FOREIGN KEY (`role_id`) REFERENCES `t_role` (`role_id`);
ALTER TABLE `t_farmer_profile` ADD CONSTRAINT `fk_t_farmer_profile_user_id` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`user_id`);
ALTER TABLE `t_product` ADD CONSTRAINT `fk_t_product_seller_id` FOREIGN KEY (`seller_id`) REFERENCES `t_user` (`user_id`);
ALTER TABLE `t_cart` ADD CONSTRAINT `fk_t_cart_buyer_id` FOREIGN KEY (`buyer_id`) REFERENCES `t_user` (`user_id`);
ALTER TABLE `t_cart` ADD CONSTRAINT `fk_t_cart_product_id` FOREIGN KEY (`product_id`) REFERENCES `t_product` (`product_id`);
ALTER TABLE `t_order` ADD CONSTRAINT `fk_t_order_buyer_id` FOREIGN KEY (`buyer_id`) REFERENCES `t_user` (`user_id`);
ALTER TABLE `t_order` ADD CONSTRAINT `fk_t_order_seller_id` FOREIGN KEY (`seller_id`) REFERENCES `t_user` (`user_id`);
ALTER TABLE `t_order` ADD CONSTRAINT `fk_t_order_product_id` FOREIGN KEY (`product_id`) REFERENCES `t_product` (`product_id`);
ALTER TABLE `t_demand` ADD CONSTRAINT `fk_t_demand_publisher_id` FOREIGN KEY (`publisher_id`) REFERENCES `t_user` (`user_id`);
ALTER TABLE `t_comment` ADD CONSTRAINT `fk_t_comment_user_id` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`user_id`);
ALTER TABLE `t_finance_apply` ADD CONSTRAINT `fk_t_finance_apply_farmer_id` FOREIGN KEY (`farmer_id`) REFERENCES `t_user` (`user_id`);
ALTER TABLE `t_finance_apply` ADD CONSTRAINT `fk_t_finance_apply_loan_product_id` FOREIGN KEY (`loan_product_id`) REFERENCES `t_loan_product` (`loan_product_id`);
ALTER TABLE `t_finance_apply` ADD CONSTRAINT `fk_t_finance_apply_group_id` FOREIGN KEY (`group_id`) REFERENCES `t_loan_group` (`group_id`);
ALTER TABLE `t_loan_group` ADD CONSTRAINT `fk_t_loan_group_leader_id` FOREIGN KEY (`leader_id`) REFERENCES `t_user` (`user_id`);
ALTER TABLE `t_approval` ADD CONSTRAINT `fk_t_approval_apply_id` FOREIGN KEY (`apply_id`) REFERENCES `t_finance_apply` (`apply_id`);
ALTER TABLE `t_approval` ADD CONSTRAINT `fk_t_approval_bank_user_id` FOREIGN KEY (`bank_user_id`) REFERENCES `t_user` (`user_id`);
ALTER TABLE `t_expert` ADD CONSTRAINT `fk_t_expert_user_id` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`user_id`);
ALTER TABLE `t_knowledge` ADD CONSTRAINT `fk_t_knowledge_author_id` FOREIGN KEY (`author_id`) REFERENCES `t_expert` (`expert_id`);
ALTER TABLE `t_question` ADD CONSTRAINT `fk_t_question_asker_id` FOREIGN KEY (`asker_id`) REFERENCES `t_user` (`user_id`);
ALTER TABLE `t_question` ADD CONSTRAINT `fk_t_question_expert_id` FOREIGN KEY (`expert_id`) REFERENCES `t_expert` (`expert_id`);
ALTER TABLE `t_answer` ADD CONSTRAINT `fk_t_answer_question_id` FOREIGN KEY (`question_id`) REFERENCES `t_question` (`question_id`);
ALTER TABLE `t_answer` ADD CONSTRAINT `fk_t_answer_expert_id` FOREIGN KEY (`expert_id`) REFERENCES `t_expert` (`expert_id`);
ALTER TABLE `t_appointment` ADD CONSTRAINT `fk_t_appointment_farmer_id` FOREIGN KEY (`farmer_id`) REFERENCES `t_user` (`user_id`);
ALTER TABLE `t_appointment` ADD CONSTRAINT `fk_t_appointment_expert_id` FOREIGN KEY (`expert_id`) REFERENCES `t_expert` (`expert_id`);
SET FOREIGN_KEY_CHECKS = 1;

