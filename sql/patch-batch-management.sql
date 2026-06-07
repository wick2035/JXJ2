USE `eval_system`;

ALTER TABLE `eval_batch`
  ADD COLUMN IF NOT EXISTS `reviewer_count` INT NOT NULL DEFAULT 1 AFTER `end_date`;

CREATE TABLE IF NOT EXISTS `batch_reviewer` (
  `id`          CHAR(36) NOT NULL,
  `batch_id`    CHAR(36) NOT NULL,
  `reviewer_id` CHAR(36) NOT NULL,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_reviewer` (`batch_id`, `reviewer_id`),
  KEY `idx_reviewer_id` (`reviewer_id`),
  CONSTRAINT `fk_br_batch` FOREIGN KEY (`batch_id`) REFERENCES `eval_batch`(`id`),
  CONSTRAINT `fk_br_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `sys_user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `audit_assignment` (
  `id`              CHAR(36)    NOT NULL,
  `declaration_id`  CHAR(36)    NOT NULL,
  `batch_id`        CHAR(36)    NOT NULL,
  `reviewer_id`     CHAR(36)    NOT NULL,
  `status`          VARCHAR(20) NOT NULL DEFAULT 'pending',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
