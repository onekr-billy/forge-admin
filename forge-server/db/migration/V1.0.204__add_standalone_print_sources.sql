-- 平台级打印业务来源。保留既有应用来源，新增独立来源引用和绑定版本。
-- 回滚：停用独立打印中心入口并保留来源/模板/审计数据；禁止自动 DROP。

CREATE TABLE IF NOT EXISTS sys_print_business_source (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    source_code VARCHAR(80) NOT NULL COMMENT '租户内稳定业务来源编码',
    source_name VARCHAR(100) NOT NULL COMMENT '业务来源名称',
    source_type VARCHAR(20) NOT NULL COMMENT 'SERVICE/DATASET/API',
    provider_code VARCHAR(100) NULL COMMENT '服务端注册的 Provider 编码',
    dataset_id BIGINT NULL COMMENT '已发布数据集 ID',
    object_code VARCHAR(100) NOT NULL COMMENT '标准业务对象编码',
    parameter_schema_json MEDIUMTEXT NULL COMMENT '受控调用参数协议',
    mapping_json MEDIUMTEXT NULL COMMENT '数据集或接口结果到打印上下文的映射',
    catalog_json MEDIUMTEXT NULL COMMENT '发布时字段目录快照',
    catalog_hash CHAR(64) NULL COMMENT '字段目录 SHA-256',
    source_revision BIGINT NOT NULL DEFAULT 1 COMMENT '来源并发修订号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '启停',
    del_flag BIGINT NOT NULL DEFAULT 0 COMMENT '删除墓碑：有效为 0，删除写主键',
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_dept BIGINT NULL,
    update_by BIGINT NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_print_business_source_code (tenant_id, source_code, del_flag),
    KEY idx_print_business_source_type (tenant_id, source_type, status, del_flag),
    KEY idx_print_business_source_dataset (tenant_id, dataset_id, status, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @print_source_ddl := IF(EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_print_template'
      AND COLUMN_NAME = 'business_source_id'
), 'SELECT 1', 'ALTER TABLE sys_print_template ADD COLUMN business_source_id BIGINT NULL COMMENT ''独立业务来源 ID'' AFTER application_id');
PREPARE print_source_stmt FROM @print_source_ddl;
EXECUTE print_source_stmt;
DEALLOCATE PREPARE print_source_stmt;

SET @print_source_ddl := IF(EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_print_binding'
      AND COLUMN_NAME = 'business_source_id'
), 'SELECT 1', 'ALTER TABLE sys_print_binding ADD COLUMN business_source_id BIGINT NULL COMMENT ''独立业务来源 ID'' AFTER application_id');
PREPARE print_source_stmt FROM @print_source_ddl;
EXECUTE print_source_stmt;
DEALLOCATE PREPARE print_source_stmt;

SET @print_source_ddl := IF(EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_print_binding'
      AND COLUMN_NAME = 'template_version_id'
), 'SELECT 1', 'ALTER TABLE sys_print_binding ADD COLUMN template_version_id BIGINT NULL COMMENT ''独立来源固定模板版本'' AFTER template_id');
PREPARE print_source_stmt FROM @print_source_ddl;
EXECUTE print_source_stmt;
DEALLOCATE PREPARE print_source_stmt;

SET @print_source_ddl := IF(EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_print_execution'
      AND COLUMN_NAME = 'business_source_id'
), 'SELECT 1', 'ALTER TABLE sys_print_execution ADD COLUMN business_source_id BIGINT NULL COMMENT ''独立业务来源 ID'' AFTER application_id');
PREPARE print_source_stmt FROM @print_source_ddl;
EXECUTE print_source_stmt;
DEALLOCATE PREPARE print_source_stmt;

SET @print_source_ddl := IF(EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_print_execution'
      AND COLUMN_NAME = 'source_revision'
), 'SELECT 1', 'ALTER TABLE sys_print_execution ADD COLUMN source_revision BIGINT NULL COMMENT ''实际使用的来源修订号'' AFTER business_source_id');
PREPARE print_source_stmt FROM @print_source_ddl;
EXECUTE print_source_stmt;
DEALLOCATE PREPARE print_source_stmt;

SET @print_source_ddl := IF(EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_print_template'
      AND COLUMN_NAME = 'application_id' AND IS_NULLABLE = 'NO'
), 'ALTER TABLE sys_print_template MODIFY COLUMN application_id BIGINT NULL COMMENT ''低代码应用 ID；独立来源为空''', 'SELECT 1');
PREPARE print_source_stmt FROM @print_source_ddl;
EXECUTE print_source_stmt;
DEALLOCATE PREPARE print_source_stmt;

SET @print_source_ddl := IF(EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_print_binding'
      AND COLUMN_NAME = 'application_id' AND IS_NULLABLE = 'NO'
), 'ALTER TABLE sys_print_binding MODIFY COLUMN application_id BIGINT NULL COMMENT ''低代码应用 ID；独立来源为空''', 'SELECT 1');
PREPARE print_source_stmt FROM @print_source_ddl;
EXECUTE print_source_stmt;
DEALLOCATE PREPARE print_source_stmt;

SET @print_source_ddl := IF(EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_print_execution'
      AND COLUMN_NAME = 'application_id' AND IS_NULLABLE = 'NO'
), 'ALTER TABLE sys_print_execution MODIFY COLUMN application_id BIGINT NULL COMMENT ''低代码应用 ID；独立来源为空''', 'SELECT 1');
PREPARE print_source_stmt FROM @print_source_ddl;
EXECUTE print_source_stmt;
DEALLOCATE PREPARE print_source_stmt;

SET @print_source_ddl := IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_print_template'
      AND INDEX_NAME = 'uk_print_template_code'
), 'ALTER TABLE sys_print_template DROP INDEX uk_print_template_code', 'SELECT 1');
PREPARE print_source_stmt FROM @print_source_ddl;
EXECUTE print_source_stmt;
DEALLOCATE PREPARE print_source_stmt;

SET @print_source_ddl := IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_print_template'
      AND INDEX_NAME = 'uk_print_template_source_code'
), 'SELECT 1', 'ALTER TABLE sys_print_template ADD UNIQUE KEY uk_print_template_source_code (tenant_id, source_key, template_code, del_flag)');
PREPARE print_source_stmt FROM @print_source_ddl;
EXECUTE print_source_stmt;
DEALLOCATE PREPARE print_source_stmt;

SET @print_source_ddl := IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_print_binding'
      AND INDEX_NAME = 'uk_print_binding_source'
), 'ALTER TABLE sys_print_binding DROP INDEX uk_print_binding_source', 'SELECT 1');
PREPARE print_source_stmt FROM @print_source_ddl;
EXECUTE print_source_stmt;
DEALLOCATE PREPARE print_source_stmt;

SET @print_source_ddl := IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_print_binding'
      AND INDEX_NAME = 'uk_print_binding_source_template'
), 'SELECT 1', 'ALTER TABLE sys_print_binding ADD UNIQUE KEY uk_print_binding_source_template (tenant_id, source_key, template_id, scene, del_flag)');
PREPARE print_source_stmt FROM @print_source_ddl;
EXECUTE print_source_stmt;
DEALLOCATE PREPARE print_source_stmt;

SET @print_source_ddl := IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_print_template'
      AND INDEX_NAME = 'idx_print_template_business_source'
), 'SELECT 1', 'ALTER TABLE sys_print_template ADD KEY idx_print_template_business_source (tenant_id, business_source_id, status, del_flag)');
PREPARE print_source_stmt FROM @print_source_ddl;
EXECUTE print_source_stmt;
DEALLOCATE PREPARE print_source_stmt;

SET @print_source_ddl := IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_print_binding'
      AND INDEX_NAME = 'idx_print_binding_business_source'
), 'SELECT 1', 'ALTER TABLE sys_print_binding ADD KEY idx_print_binding_business_source (tenant_id, business_source_id, scene, status, del_flag)');
PREPARE print_source_stmt FROM @print_source_ddl;
EXECUTE print_source_stmt;
DEALLOCATE PREPARE print_source_stmt;

