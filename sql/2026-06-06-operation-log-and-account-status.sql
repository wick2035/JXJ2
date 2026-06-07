USE `eval_system`;

-- 1. 账号状态 + 登录失败计数
ALTER TABLE `sys_user`
  ADD COLUMN `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '账号状态: 1启用 0禁用' AFTER `force_password_change`,
  ADD COLUMN `failed_attempts` INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数' AFTER `status`;

-- 2. 操作记录表（软删除）
CREATE TABLE `operation_log` (
  `id`               CHAR(36)     NOT NULL,
  `operator_id`      CHAR(36)     NULL COMMENT '操作人ID(登录失败时可为空)',
  `operator_login_id` VARCHAR(50) NULL COMMENT '操作人账号',
  `operator_name`    VARCHAR(100) NULL COMMENT '操作人姓名',
  `operator_role`    VARCHAR(20)  NULL COMMENT 'admin/teacher/student',
  `module`           VARCHAR(50)  NULL COMMENT '业务模块(中文)',
  `action`           VARCHAR(100) NULL COMMENT '动作描述',
  `method`           VARCHAR(10)  NULL COMMENT 'HTTP方法',
  `uri`              VARCHAR(255) NULL,
  `params`           TEXT         NULL COMMENT '请求摘要(截断)',
  `ip`               VARCHAR(64)  NULL,
  `success`          TINYINT(1)   NOT NULL DEFAULT 1,
  `error_msg`        VARCHAR(500) NULL,
  `is_deleted`       TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_role` (`operator_role`),
  KEY `idx_module` (`module`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作记录表';

-- 3. 系统配置表（存二级密码哈希等）
CREATE TABLE `sys_config` (
  `config_key`   VARCHAR(64)  NOT NULL,
  `config_value` VARCHAR(255) NOT NULL,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置';
