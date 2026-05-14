ALTER TABLE `sys_resource`
    ADD COLUMN `sso_enabled` tinyint DEFAULT '0' COMMENT '是否启用SSO（0-否，1-是）' AFTER `is_external`,
    ADD COLUMN `sso_target_client` varchar(64) DEFAULT NULL COMMENT 'SSO目标客户端编码' AFTER `sso_enabled`;
