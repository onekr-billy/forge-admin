-- 数据维度与数据集字段扩展升级脚本
-- 适用于已初始化数据资产表的环境；新库请直接执行 data_tables.sql。

SET @column_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'ai_report_data_dataset_field'
    AND column_name = 'date_format'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE ai_report_data_dataset_field ADD COLUMN date_format VARCHAR(64) DEFAULT NULL COMMENT ''日期展示格式'' AFTER dict_type',
  'SELECT ''ai_report_data_dataset_field.date_format exists'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'ai_report_data_dataset_field'
    AND column_name = 'data_unit'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE ai_report_data_dataset_field ADD COLUMN data_unit VARCHAR(32) DEFAULT NULL COMMENT ''数据计量单位'' AFTER date_format',
  'SELECT ''ai_report_data_dataset_field.data_unit exists'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'ai_report_data_dataset_field'
    AND column_name = 'dimension_id'
);
SET @ddl := IF(@column_exists = 0,
  'ALTER TABLE ai_report_data_dataset_field ADD COLUMN dimension_id BIGINT DEFAULT NULL COMMENT ''绑定维度ID'' AFTER data_unit',
  'SELECT ''ai_report_data_dataset_field.dimension_id exists'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'ai_report_data_dataset_field'
    AND index_name = 'idx_data_dataset_field_dimension'
);
SET @ddl := IF(@index_exists = 0,
  'ALTER TABLE ai_report_data_dataset_field ADD INDEX idx_data_dataset_field_dimension (tenant_id, dimension_id)',
  'SELECT ''idx_data_dataset_field_dimension exists'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `ai_report_data_dimension` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `dimension_code` varchar(64) NOT NULL COMMENT '维度编码',
  `dimension_name` varchar(100) NOT NULL COMMENT '维度名称',
  `source_type` varchar(20) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：MANUAL/SQL',
  `connection_id` bigint DEFAULT NULL COMMENT 'SQL来源数据连接ID',
  `sql_text` longtext DEFAULT NULL COMMENT 'SQL来源查询语句',
  `value_column` varchar(128) DEFAULT NULL COMMENT '值字段列名',
  `label_column` varchar(128) DEFAULT NULL COMMENT '显示字段列名',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `last_sync_time` datetime DEFAULT NULL COMMENT '最近同步时间',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_data_dimension_code_tenant` (`tenant_id`, `dimension_code`),
  KEY `idx_data_dimension_source` (`tenant_id`, `source_type`),
  KEY `idx_data_dimension_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台维度';

CREATE TABLE IF NOT EXISTS `ai_report_data_dimension_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` bigint NOT NULL DEFAULT 1 COMMENT '租户ID',
  `dimension_id` bigint NOT NULL COMMENT '维度ID',
  `item_value` varchar(255) NOT NULL COMMENT '维度值',
  `item_label` varchar(255) NOT NULL COMMENT '维度显示值',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `extra_json` json DEFAULT NULL COMMENT '扩展信息JSON',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_data_dimension_item_value` (`tenant_id`, `dimension_id`, `item_value`),
  KEY `idx_data_dimension_item_dimension` (`tenant_id`, `dimension_id`),
  KEY `idx_data_dimension_item_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台维度值';
