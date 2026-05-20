-- =============================================
-- 智能工单处理Agent - 数据库初始化脚本
-- =============================================

CREATE DATABASE IF NOT EXISTS `work_order_agent` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `work_order_agent`;

-- =============================================
-- 1. 工单主表
-- =============================================
DROP TABLE IF EXISTS `work_order`;
CREATE TABLE `work_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_no` VARCHAR(64) NOT NULL COMMENT '工单编号（唯一）',
    `title` VARCHAR(500) NOT NULL COMMENT '工单标题',
    `content` TEXT NOT NULL COMMENT '工单详细内容',
    `work_type` VARCHAR(32) DEFAULT NULL COMMENT '工单类型（AI自动识别：consult/咨询 fault/故障 appeal/申诉 suggest/建议 ops_error/运维报错 func_error/功能异常）',
    `priority` TINYINT DEFAULT 4 COMMENT '优先级 1紧急/2高/3中/4低',
    `module` VARCHAR(64) DEFAULT NULL COMMENT '所属业务模块（AI抽取）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0待处理/1处理中/2已办结/3已关闭',
    `handler_user_id` BIGINT DEFAULT NULL COMMENT '处理人ID',
    `handler_user_name` VARCHAR(64) DEFAULT NULL COMMENT '处理人姓名',
    `dept_name` VARCHAR(64) DEFAULT NULL COMMENT '责任部门（AI分派）',
    `ai_answer` LONGTEXT DEFAULT NULL COMMENT 'AI自动答复内容',
    `ai_parse_result` JSON DEFAULT NULL COMMENT 'AI解析完整结果（JSON）',
    `is_auto_finish` TINYINT DEFAULT 0 COMMENT '是否AI自动办结 0否 1是',
    `is_duplicate` TINYINT DEFAULT 0 COMMENT '是否重复工单 0否 1是',
    `duplicate_order_id` BIGINT DEFAULT NULL COMMENT '关联的重复工单ID',
    `submit_user_name` VARCHAR(64) DEFAULT NULL COMMENT '提交人姓名',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `finish_time` DATETIME DEFAULT NULL COMMENT '办结时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_work_type` (`work_type`),
    KEY `idx_priority` (`priority`),
    KEY `idx_status` (`status`),
    KEY `idx_module` (`module`),
    KEY `idx_handler` (`handler_user_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_dept_name` (`dept_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单主表';

-- =============================================
-- 2. 知识库文档表
-- =============================================
DROP TABLE IF EXISTS `work_knowledge`;
CREATE TABLE `work_knowledge` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title` VARCHAR(500) NOT NULL COMMENT '知识点标题',
    `content` LONGTEXT NOT NULL COMMENT '知识点内容',
    `module` VARCHAR(64) DEFAULT NULL COMMENT '所属业务模块',
    `keywords` VARCHAR(500) DEFAULT NULL COMMENT '关键词（逗号分隔，辅助检索）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_module` (`module`),
    KEY `idx_status` (`status`),
    FULLTEXT KEY `ft_content` (`title`, `content`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';

-- =============================================
-- 3. 工单复盘报告表
-- =============================================
DROP TABLE IF EXISTS `work_order_report`;
CREATE TABLE `work_order_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `report_no` VARCHAR(64) NOT NULL COMMENT '报告编号',
    `report_type` VARCHAR(16) NOT NULL COMMENT '报告类型 day/week/month',
    `report_period` VARCHAR(32) NOT NULL COMMENT '报告周期（如 2026-05-20 / 2026-W21 / 2026-05）',
    `total_count` INT DEFAULT 0 COMMENT '工单总数',
    `finish_count` INT DEFAULT 0 COMMENT '办结工单数',
    `ai_auto_count` INT DEFAULT 0 COMMENT 'AI自动办结数',
    `timeout_count` INT DEFAULT 0 COMMENT '超时工单数',
    `duplicate_count` INT DEFAULT 0 COMMENT '重复工单数',
    `report_content` LONGTEXT DEFAULT NULL COMMENT 'AI复盘报告内容（Markdown）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_report_no` (`report_no`),
    KEY `idx_report_type` (`report_type`),
    KEY `idx_report_period` (`report_period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单复盘报告表';

-- =============================================
-- 4. 工单流转日志表
-- =============================================
DROP TABLE IF EXISTS `work_order_flow_log`;
CREATE TABLE `work_order_flow_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id` BIGINT NOT NULL COMMENT '关联工单ID',
    `order_no` VARCHAR(64) NOT NULL COMMENT '关联工单编号',
    `action` VARCHAR(32) NOT NULL COMMENT '操作类型 submit/parse/assign/auto_finish/manual_finish/close/timeout_warn/review',
    `operator` VARCHAR(64) DEFAULT 'SYSTEM' COMMENT '操作人',
    `content` TEXT DEFAULT NULL COMMENT '操作内容说明',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_action` (`action`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单流转日志表';

-- =============================================
-- 5. 部门责任人配置表
-- =============================================
DROP TABLE IF EXISTS `work_dept_config`;
CREATE TABLE `work_dept_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dept_name` VARCHAR(64) NOT NULL COMMENT '部门名称',
    `module` VARCHAR(64) DEFAULT NULL COMMENT '负责的业务模块（NULL表示默认部门）',
    `handler_user_id` BIGINT NOT NULL COMMENT '默认处理人ID',
    `handler_user_name` VARCHAR(64) NOT NULL COMMENT '默认处理人姓名',
    `priority` TINYINT DEFAULT 0 COMMENT '部门处理优先级 数字越小越优先',
    `status` TINYINT DEFAULT 1 COMMENT '状态 0禁用 1启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_module` (`module`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门责任人配置表';

-- =============================================
-- 6. 插入初始测试数据
-- =============================================

-- 部门配置
INSERT INTO `work_dept_config` (`dept_name`, `module`, `handler_user_id`, `handler_user_name`, `priority`, `status`) VALUES
('技术运维部', 'ops_error', 1001, '张三', 1, 1),
('技术运维部', 'fault', 1001, '张三', 1, 1),
('客服部', 'consult', 1002, '李四', 1, 1),
('客服部', 'appeal', 1002, '李四', 1, 1),
('产品部', 'suggest', 1003, '王五', 1, 1),
('产品部', 'func_error', 1003, '王五', 1, 1),
('技术运维部', NULL, 1001, '张三', 10, 1);

-- 知识库初始数据
INSERT INTO `work_knowledge` (`title`, `content`, `module`, `keywords`, `status`) VALUES
('如何重置登录密码', '请前往系统登录页面，点击"忘记密码"链接，按照提示输入注册邮箱或手机号，系统将发送验证码，验证通过后即可设置新密码。如仍无法解决，请联系IT支持。', 'consult', '密码,登录,重置,忘记密码', 1),
('系统访问缓慢怎么办', '1. 检查本地网络连接是否正常；2. 清除浏览器缓存和Cookie；3. 尝试使用无痕模式访问；4. 确认是否在VPN环境下（如需要）；5. 如以上均无效，请提供您的IP地址和tracert结果提交工单。', 'fault', '缓慢,卡顿,访问慢,速度慢', 1),
('订单退款申请流程', '用户申请退款需在订单完成后7个自然日内提交。客服审核通过后，退款将在3-5个工作日内原路返回。超过7天的订单需走特殊审批流程，请联系上级主管审批。', 'consult', '退款,订单,退货,申请退款', 1),
('数据库连接超时报错处理', '错误信息：Connection timeout / Too many connections。处理步骤：1. 检查数据库服务是否正常运行；2. 查看当前连接数 show processlist；3. 检查连接池配置 max_connections；4. 分析慢查询日志；5. 如连接数已满，临时调大最大连接数或重启服务。', 'ops_error', '数据库,连接超时,timeout,connection', 1),
('API接口返回500错误排查', '1. 查看应用日志定位具体异常堆栈；2. 检查最近是否有代码上线或配置变更；3. 验证下游服务是否正常；4. 检查服务器资源使用情况（CPU/内存/磁盘）；5. 回滚最近变更，优先恢复服务再排查根因。', 'ops_error', '500,接口报错,服务器错误,API', 1),
('新功能建议提交规范', '提交功能建议时请包含以下信息：1. 功能描述和使用场景；2. 期望的交互方式和流程；3. 业务价值和优先级判断；4. 是否有竞品参考。产品部将在5个工作日内评估并反馈。', 'suggest', '功能建议,需求,新功能,建议', 1);
