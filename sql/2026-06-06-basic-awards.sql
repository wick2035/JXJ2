ALTER TABLE `award`
  ADD COLUMN `award_type` VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT 'normal=普通奖项,basic=基础奖项' AFTER `name`;

CREATE TABLE IF NOT EXISTS `batch_basic_award` (
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

CREATE TABLE IF NOT EXISTS `batch_basic_score` (
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
