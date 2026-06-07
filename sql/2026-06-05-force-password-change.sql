USE `eval_system`;

ALTER TABLE `sys_user`
  ADD COLUMN `force_password_change` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否强制修改初始密码'
  AFTER `password_hash`;
