-- Allow correction audit actions such as correction_approve.
ALTER TABLE `audit_record`
  MODIFY COLUMN `action` VARCHAR(32) NOT NULL COMMENT 'approve/reject/return/correction_*';
