-- ============================================================
-- 综合测评系统 (Student Comprehensive Evaluation System)
-- MySQL Database Schema
-- ============================================================

-- 1. 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS `eval_system`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `eval_system`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. USER & AUTH
-- ============================================================

CREATE TABLE `sys_user` (
  `id`            CHAR(36)     NOT NULL COMMENT 'UUID主键',
  `login_id`      VARCHAR(50)  NOT NULL COMMENT '登录账号: 学号或工号',
  `password_hash` VARCHAR(255) NOT NULL COMMENT 'bcrypt密码哈希',
  `force_password_change` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否强制修改初始密码',
  `status`        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '账号状态: 1启用 0禁用',
  `failed_attempts` INT        NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
  `name`          VARCHAR(100) NOT NULL COMMENT '姓名',
  `role`          ENUM('admin','teacher','student') NOT NULL COMMENT '角色',
  `email`         VARCHAR(255) NULL     COMMENT '邮箱',
  `phone`         VARCHAR(20)  NULL     COMMENT '手机号',
  `college`       VARCHAR(100) NULL     COMMENT '学院',
  `major`         VARCHAR(100) NULL     COMMENT '专业',
  `class_name`    VARCHAR(100) NULL     COMMENT '班级',
  `grade`         VARCHAR(20)  NULL     COMMENT '年级',
  `is_deleted`    TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_login_id` (`login_id`),
  KEY `idx_role` (`role`),
  KEY `idx_college_major` (`college`, `major`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- ============================================================
-- 2. BATCH (批次)
-- ============================================================

CREATE TABLE `eval_batch` (
  `id`          CHAR(36)     NOT NULL,
  `name`        VARCHAR(200) NOT NULL COMMENT '批次名称',
  `status`      ENUM('draft','open','closed') NOT NULL DEFAULT 'draft',
  `target_type` ENUM('all','specified') NOT NULL DEFAULT 'all' COMMENT '发布范围: all=全部学生, specified=指定班级',
  `start_date`  DATE         NOT NULL,
  `end_date`    DATE         NOT NULL,
  `reviewer_count` INT       NOT NULL DEFAULT 1 COMMENT '每份申报需要审核人数',
  `description` TEXT         NULL,
  `created_by`  CHAR(36)     NOT NULL,
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_batch_created_by` FOREIGN KEY (`created_by`) REFERENCES `sys_user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评测批次表';

CREATE TABLE `eval_batch_category` (
  `id`              CHAR(36)      NOT NULL,
  `batch_id`        CHAR(36)      NOT NULL,
  `category`        VARCHAR(50)   NOT NULL,
  `weight_percent`  DECIMAL(5,2)  NOT NULL COMMENT '权重百分比',
  `max_score_cap`   DECIMAL(8,2)  NULL     COMMENT '封顶分数,NULL=不封顶',
  `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_category` (`batch_id`, `category`),
  CONSTRAINT `fk_bc_batch` FOREIGN KEY (`batch_id`) REFERENCES `eval_batch`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次类别权重配置';

CREATE TABLE `batch_reviewer` (
  `id`          CHAR(36) NOT NULL,
  `batch_id`    CHAR(36) NOT NULL,
  `reviewer_id` CHAR(36) NOT NULL,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_reviewer` (`batch_id`, `reviewer_id`),
  KEY `idx_reviewer_id` (`reviewer_id`),
  CONSTRAINT `fk_br_batch` FOREIGN KEY (`batch_id`) REFERENCES `eval_batch`(`id`),
  CONSTRAINT `fk_br_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `sys_user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次审核人配置';

CREATE TABLE `batch_class` (
  `id`         CHAR(36)     NOT NULL,
  `batch_id`   CHAR(36)     NOT NULL,
  `grade`      VARCHAR(20)  NULL     COMMENT '年级,NULL=未填',
  `class_name` VARCHAR(100) NOT NULL COMMENT '班级名称',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_class` (`batch_id`, `grade`, `class_name`),
  KEY `idx_bcl_batch` (`batch_id`),
  CONSTRAINT `fk_bcl_batch` FOREIGN KEY (`batch_id`) REFERENCES `eval_batch`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次发布目标班级';

-- ============================================================
-- 3. AWARD LIBRARY (奖项库)
-- ============================================================

CREATE TABLE `award_category` (
  `id`          CHAR(36)     NOT NULL,
  `code`        VARCHAR(50)  NOT NULL COMMENT '稳定类别编码',
  `name`        VARCHAR(100) NOT NULL COMMENT '类别显示名称',
  `color`       VARCHAR(20)  NOT NULL DEFAULT '#1677FF',
  `sort_order`  INT          NOT NULL DEFAULT 0,
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_award_category_code` (`code`),
  KEY `idx_award_category_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖项类别定义表';

CREATE TABLE `award_level_def` (
  `id`          CHAR(36)     NOT NULL,
  `code`        VARCHAR(50)  NOT NULL COMMENT 'national/provincial/municipal/school/college',
  `name`        VARCHAR(100) NOT NULL COMMENT '国家级/省级/市级/校级/院级',
  `sort_order`  INT          NOT NULL COMMENT '层级排序:1=最高',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  UNIQUE KEY `uk_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖项级别定义表';

CREATE TABLE `award` (
  `id`          CHAR(36)     NOT NULL,
  `category`    VARCHAR(50)  NOT NULL,
  `name`        VARCHAR(200) NOT NULL,
  `award_type`  VARCHAR(20)  NOT NULL DEFAULT 'normal' COMMENT 'normal=普通奖项,basic=基础奖项',
  `description` TEXT         NULL,
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖项主表';

CREATE TABLE `award_level_score` (
  `id`          CHAR(36)    NOT NULL,
  `award_id`    CHAR(36)    NOT NULL,
  `level_id`    CHAR(36)    NOT NULL,
  `base_score`  DECIMAL(8,2) NOT NULL,
  `is_deleted`  TINYINT(1)  NOT NULL DEFAULT 0,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_award_level` (`award_id`, `level_id`),
  CONSTRAINT `fk_als_award` FOREIGN KEY (`award_id`) REFERENCES `award`(`id`),
  CONSTRAINT `fk_als_level` FOREIGN KEY (`level_id`) REFERENCES `award_level_def`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖项各级别基础分数';

CREATE TABLE `batch_award` (
  `id`                  CHAR(36)     NOT NULL,
  `batch_id`            CHAR(36)     NOT NULL,
  `award_id`            CHAR(36)     NOT NULL,
  `level_id`            CHAR(36)     NOT NULL,
  `override_base_score` DECIMAL(8,2) NULL COMMENT '批次覆盖分数,NULL=沿用默认',
  `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_award_level` (`batch_id`, `award_id`, `level_id`),
  CONSTRAINT `fk_ba_batch` FOREIGN KEY (`batch_id`) REFERENCES `eval_batch`(`id`),
  CONSTRAINT `fk_ba_award` FOREIGN KEY (`award_id`) REFERENCES `award`(`id`),
  CONSTRAINT `fk_ba_level` FOREIGN KEY (`level_id`) REFERENCES `award_level_def`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次-奖项-级别关联';

-- ============================================================
-- 4. DECLARATION (申报)
-- ============================================================

CREATE TABLE `declaration` (
  `id`              CHAR(36)    NOT NULL,
  `batch_id`        CHAR(36)    NOT NULL,
  `student_id`      CHAR(36)    NOT NULL,
  `status`          ENUM('draft','submitted','approved','rejected','returned') NOT NULL DEFAULT 'draft',
  `total_score`     DECIMAL(10,2) NULL,
  `morality_score`  DECIMAL(10,2) NULL,
  `ability_score`   DECIMAL(10,2) NULL,
  `sports_score`    DECIMAL(10,2) NULL,
  `submitted_at`    DATETIME    NULL,
  `is_deleted`      TINYINT(1)  NOT NULL DEFAULT 0,
  `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_student` (`batch_id`, `student_id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_decl_batch` FOREIGN KEY (`batch_id`) REFERENCES `eval_batch`(`id`),
  CONSTRAINT `fk_decl_student` FOREIGN KEY (`student_id`) REFERENCES `sys_user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生申报主表';

CREATE TABLE `declaration_item` (
  `id`                  CHAR(36)     NOT NULL,
  `declaration_id`      CHAR(36)     NOT NULL,
  `category`            VARCHAR(50)  NOT NULL,
  `award_id`            CHAR(36)     NULL     COMMENT '预设奖项ID,NULL=自定义',
  `level_id`            CHAR(36)     NULL,
  `custom_award_name`   VARCHAR(200) NULL,
  `custom_level_name`   VARCHAR(100) NULL,
  `custom_base_score`   DECIMAL(8,2) NULL,
  `use_downgrade`       TINYINT(1)   NOT NULL DEFAULT 0,
  `computed_score`      DECIMAL(8,2) NULL,
  `final_score`         DECIMAL(8,2) NULL,
  `description`         TEXT         NULL,
  `sort_order`          INT          NOT NULL DEFAULT 0,
  `is_deleted`          TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_declaration_id` (`declaration_id`),
  KEY `idx_category` (`category`),
  CONSTRAINT `fk_di_declaration` FOREIGN KEY (`declaration_id`) REFERENCES `declaration`(`id`),
  CONSTRAINT `fk_di_award` FOREIGN KEY (`award_id`) REFERENCES `award`(`id`),
  CONSTRAINT `fk_di_level` FOREIGN KEY (`level_id`) REFERENCES `award_level_def`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='申报明细项';

CREATE TABLE `declaration_attachment` (
  `id`                  CHAR(36)     NOT NULL,
  `declaration_item_id` CHAR(36)     NOT NULL,
  `file_name`           VARCHAR(255) NOT NULL,
  `file_path`           VARCHAR(500) NOT NULL,
  `file_size`           BIGINT       NULL,
  `mime_type`           VARCHAR(100) NULL,
  `is_deleted`          TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_item_id` (`declaration_item_id`),
  CONSTRAINT `fk_att_item` FOREIGN KEY (`declaration_item_id`) REFERENCES `declaration_item`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='申报附件表';

-- ============================================================
-- 5. AUDIT (审核记录)
-- ============================================================

CREATE TABLE `audit_record` (
  `id`              CHAR(36)    NOT NULL,
  `declaration_id`  CHAR(36)    NOT NULL,
  `reviewer_id`     CHAR(36)    NOT NULL,
  `action`          VARCHAR(32) NOT NULL COMMENT 'approve/reject/return/correction_*',
  `comment`         TEXT        NULL,
  `snapshot_scores` JSON        NULL COMMENT '审核时刻分数快照',
  `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_declaration_id` (`declaration_id`),
  KEY `idx_reviewer_id` (`reviewer_id`),
  CONSTRAINT `fk_ar_declaration` FOREIGN KEY (`declaration_id`) REFERENCES `declaration`(`id`),
  CONSTRAINT `fk_ar_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `sys_user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核记录表';

CREATE TABLE `audit_assignment` (
  `id`              CHAR(36)    NOT NULL,
  `declaration_id`  CHAR(36)    NOT NULL,
  `batch_id`        CHAR(36)    NOT NULL,
  `reviewer_id`     CHAR(36)    NOT NULL,
  `status`          VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected/returned/cancelled',
  `action`          VARCHAR(20) NULL,
  `comment`         TEXT        NULL,
  `reviewed_at`     DATETIME    NULL,
  `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_assignment` (`declaration_id`, `reviewer_id`),
  KEY `idx_batch_status` (`batch_id`, `status`),
  KEY `idx_reviewer_status` (`reviewer_id`, `status`),
  CONSTRAINT `fk_aa_declaration` FOREIGN KEY (`declaration_id`) REFERENCES `declaration`(`id`),
  CONSTRAINT `fk_aa_batch` FOREIGN KEY (`batch_id`) REFERENCES `eval_batch`(`id`),
  CONSTRAINT `fk_aa_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `sys_user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核任务分配表';

-- ============================================================
-- 6. SCORE RECALCULATION LOG
-- ============================================================

CREATE TABLE `score_recalc_log` (
  `id`            CHAR(36)  NOT NULL,
  `batch_id`      CHAR(36)  NOT NULL,
  `triggered_by`  CHAR(36)  NOT NULL,
  `reason`        TEXT      NULL,
  `affected_count` INT      NOT NULL DEFAULT 0,
  `started_at`    DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at`   DATETIME  NULL,
  `status`        ENUM('running','completed','failed') NOT NULL DEFAULT 'running',
  PRIMARY KEY (`id`),
  KEY `idx_batch_id` (`batch_id`),
  CONSTRAINT `fk_srl_batch` FOREIGN KEY (`batch_id`) REFERENCES `eval_batch`(`id`),
  CONSTRAINT `fk_srl_triggered_by` FOREIGN KEY (`triggered_by`) REFERENCES `sys_user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分数重算日志';

-- ============================================================
-- 6.1 OPERATION LOG (操作记录) + SYS CONFIG (系统配置)
-- ============================================================

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

CREATE TABLE `sys_config` (
  `config_key`   VARCHAR(64)  NOT NULL,
  `config_value` VARCHAR(255) NOT NULL,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置';

-- ============================================================
-- 7. NOTICE
-- ============================================================

CREATE TABLE `notice` (
  `id`           CHAR(36)      NOT NULL,
  `title`        VARCHAR(200)  NOT NULL COMMENT '公告标题',
  `content`      TEXT          NOT NULL COMMENT '公告内容',
  `target_type`  ENUM('all','specified') NOT NULL COMMENT '接收范围',
  `status`       ENUM('published','withdrawn') NOT NULL DEFAULT 'published',
  `created_by`   CHAR(36)      NOT NULL,
  `withdrawn_at` DATETIME      NULL,
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_by` (`created_by`),
  CONSTRAINT `fk_notice_created_by` FOREIGN KEY (`created_by`) REFERENCES `sys_user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知公告表';

CREATE TABLE `notice_recipient` (
  `id`           CHAR(36)    NOT NULL,
  `notice_id`    CHAR(36)    NOT NULL,
  `user_id`      CHAR(36)    NOT NULL,
  `confirmed`    TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否确认阅读',
  `confirmed_at` DATETIME    NULL,
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_user` (`notice_id`, `user_id`),
  KEY `idx_user_confirmed` (`user_id`, `confirmed`),
  CONSTRAINT `fk_nr_notice` FOREIGN KEY (`notice_id`) REFERENCES `notice`(`id`),
  CONSTRAINT `fk_nr_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知公告接收确认表';

-- ============================================================
-- 8. SEED DATA
-- ============================================================

INSERT INTO `award_level_def` (`id`, `code`, `name`, `sort_order`) VALUES
  (UUID(), 'national',    '国家级', 1),
  (UUID(), 'provincial',  '省级',   2),
  (UUID(), 'municipal',   '市级',   3),
  (UUID(), 'school',      '校级',   4),
  (UUID(), 'college',     '院级',   5);

INSERT INTO `award_category` (`id`, `code`, `name`, `color`, `sort_order`) VALUES
  (UUID(), 'morality', '品德表现', '#1677FF', 1),
  (UUID(), 'ability',  '能力发展', '#52C41A', 2),
  (UUID(), 'sports',   '体育健康', '#FAAD14', 3);

-- ============================================================
-- 默认账号 (密码均为明文对应的 bcrypt hash)
-- ============================================================

-- 管理员: admin / admin123
INSERT INTO `sys_user` (`id`, `login_id`, `password_hash`, `name`, `role`) VALUES
  (UUID(), 'admin', '$2a$10$4u8kCHr8.p.0HXvh0z5rzeorozCJK4ygaP8V.G2ULIqfq0dq/kUdm', '系统管理员', 'admin');

-- 示例教师: T001 / 123456
INSERT INTO `sys_user` (`id`, `login_id`, `password_hash`, `name`, `role`, `college`) VALUES
  (UUID(), 'T001', '$2a$10$/FerWcgm7SGP5bkozoha3uFQRaqttonwRQ4zchBoPBsrTluZkYbGS', '张老师', 'teacher', '计算机学院');

-- 示例学生: S2024001 / 123456, S2024002 / 123456
INSERT INTO `sys_user` (`id`, `login_id`, `password_hash`, `name`, `role`, `college`, `major`, `class_name`, `grade`) VALUES
  (UUID(), 'S2024001', '$2a$10$/FerWcgm7SGP5bkozoha3uFQRaqttonwRQ4zchBoPBsrTluZkYbGS', '李同学', 'student', '计算机学院', '计算机科学与技术', '计科2班', '2024'),
  (UUID(), 'S2024002', '$2a$10$/FerWcgm7SGP5bkozoha3uFQRaqttonwRQ4zchBoPBsrTluZkYbGS', '王同学', 'student', '计算机学院', '软件工程', '软工1班', '2024');

CREATE TABLE `batch_basic_award` (
  `id`             CHAR(36)     NOT NULL,
  `batch_id`       CHAR(36)     NOT NULL,
  `award_id`       CHAR(36)     NOT NULL,
  `category`       VARCHAR(50)  NOT NULL,
  `imported_by`    CHAR(36)     NULL,
  `imported_count` INT          NOT NULL DEFAULT 0,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_basic_award` (`batch_id`, `award_id`),
  KEY `idx_bba_category` (`batch_id`, `category`),
  CONSTRAINT `fk_bba_batch` FOREIGN KEY (`batch_id`) REFERENCES `eval_batch`(`id`),
  CONSTRAINT `fk_bba_award` FOREIGN KEY (`award_id`) REFERENCES `award`(`id`),
  CONSTRAINT `fk_bba_imported_by` FOREIGN KEY (`imported_by`) REFERENCES `sys_user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次基础奖项导入记录';

CREATE TABLE `batch_basic_score` (
  `id`         CHAR(36)     NOT NULL,
  `batch_id`   CHAR(36)     NOT NULL,
  `award_id`   CHAR(36)     NOT NULL,
  `student_id` CHAR(36)     NOT NULL,
  `score`      DECIMAL(8,2) NOT NULL DEFAULT 0,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_basic_score` (`batch_id`, `award_id`, `student_id`),
  KEY `idx_bbs_student` (`batch_id`, `student_id`),
  CONSTRAINT `fk_bbs_batch` FOREIGN KEY (`batch_id`) REFERENCES `eval_batch`(`id`),
  CONSTRAINT `fk_bbs_award` FOREIGN KEY (`award_id`) REFERENCES `award`(`id`),
  CONSTRAINT `fk_bbs_student` FOREIGN KEY (`student_id`) REFERENCES `sys_user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次学生基础奖项分数';

SET FOREIGN_KEY_CHECKS = 1;
