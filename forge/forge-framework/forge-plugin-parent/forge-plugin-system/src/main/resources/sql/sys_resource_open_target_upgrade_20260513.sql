ALTER TABLE `sys_resource`
    ADD COLUMN `open_target` varchar(20) DEFAULT '_self' COMMENT '打开方式：_self-当前页/_blank-新窗口' AFTER `sso_target_client`;
