-- Non-destructive patch for notification announcement tables.
-- Keeps existing data intact and only creates the tables when missing.

CREATE TABLE IF NOT EXISTS `notice` (
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

CREATE TABLE IF NOT EXISTS `notice_recipient` (
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
