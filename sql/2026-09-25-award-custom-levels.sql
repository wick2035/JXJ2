-- Allow award-specific level definitions while retaining the five shared levels.
-- Apply once after backing up eval_system, before deploying the updated backend.
USE `eval_system`;

ALTER TABLE `award_level_def`
  ADD COLUMN `award_id` CHAR(36) NULL COMMENT 'NULL=shared level, otherwise owner award' AFTER `name`,
  DROP INDEX `uk_sort_order`,
  ADD KEY `idx_award_level_order` (`award_id`, `sort_order`),
  ADD CONSTRAINT `fk_ald_award` FOREIGN KEY (`award_id`) REFERENCES `award`(`id`);
