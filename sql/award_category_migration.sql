-- Dynamic award category migration for existing eval_system databases.
-- Run this after backing up the database. This script also applies the
-- force_password_change prerequisite because authenticated category requests
-- pass through ForcePasswordChangeFilter before reaching /api/categories.

USE `eval_system`;

SET @has_force_password_change := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_user'
    AND COLUMN_NAME = 'force_password_change'
);

SET @add_force_password_change := IF(
  @has_force_password_change = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `force_password_change` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''force initial password change'' AFTER `password_hash`',
  'SELECT 1'
);

PREPARE add_force_password_change_stmt FROM @add_force_password_change;
EXECUTE add_force_password_change_stmt;
DEALLOCATE PREPARE add_force_password_change_stmt;

CREATE TABLE IF NOT EXISTS `award_category` (
  `id`          CHAR(36)     NOT NULL,
  `code`        VARCHAR(50)  NOT NULL COMMENT 'stable category code',
  `name`        VARCHAR(100) NOT NULL COMMENT 'category display name',
  `color`       VARCHAR(20)  NOT NULL DEFAULT '#1677FF',
  `sort_order`  INT          NOT NULL DEFAULT 0,
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_award_category_code` (`code`),
  KEY `idx_award_category_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='award category definitions';

ALTER TABLE `eval_batch_category`
  MODIFY COLUMN `category` VARCHAR(50) NOT NULL;

ALTER TABLE `award`
  MODIFY COLUMN `category` VARCHAR(50) NOT NULL;

ALTER TABLE `declaration_item`
  MODIFY COLUMN `category` VARCHAR(50) NOT NULL;

INSERT IGNORE INTO `award_category` (`id`, `code`, `name`, `color`, `sort_order`) VALUES
  (UUID(), 'morality', '品德表现', '#1677FF', 1),
  (UUID(), 'ability',  '能力发展', '#52C41A', 2),
  (UUID(), 'sports',   '体育健康', '#FAAD14', 3);
