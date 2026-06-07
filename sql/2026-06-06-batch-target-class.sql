USE `eval_system`;

-- 批次发布范围：全部学生 或 指定班级（按 年级+班级 组合匹配）
-- 注意：MySQL 8 不支持 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`，
-- 这里用 information_schema 做幂等判断，可安全重复执行。
SET @add_target_type := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `eval_batch` ADD COLUMN `target_type` ENUM(''all'',''specified'') NOT NULL DEFAULT ''all'' AFTER `status`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'eval_batch' AND COLUMN_NAME = 'target_type'
);
PREPARE stmt FROM @add_target_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `batch_class` (
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
