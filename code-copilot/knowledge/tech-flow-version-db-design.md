# 流程版本管理数据库设计模板

> 来源：变更 flow-model-version-management  
> 时间：2026-05-09

## 问题描述
流程模型需要支持多版本管理，每个版本需要记录部署时间、状态、部署人等关键信息，并能与 Flowable 流程定义关联。

## 解决方案

### 表结构设计：sys_flow_model_version

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(64) | 主键 |
| model_id | varchar(64) | 关联模型 ID |
| version | int | 版本号（业务版本） |
| flowable_version | int | Flowable 流程定义版本 |
| status | int | 状态：0=待部署, 1=已部署, 2=已发布, 3=已归档 |
| is_primary | int | 是否主版本：0=否, 1=是 |
| bpmn_xml | longtext | BPMN XML 内容 |
| deploy_time | datetime | 部署时间 |
| deploy_by | varchar(64) | 部署人 |
| create_time | datetime | 创建时间 |
| tenant_id | varchar(64) | 租户 ID |

### 核心约束
1. 同一 model_id 下 version 必须唯一
2. 每个 model_id 只能有一个 is_primary=1 的版本
3. 状态流转：待部署 → 已部署 → 已发布 → 已归档（单向）
4. 删除模型时，所有版本记录一并删除（外键约束）

### SQL 示例
```sql
CREATE TABLE sys_flow_model_version (
  id VARCHAR(64) PRIMARY KEY,
  model_id VARCHAR(64) NOT NULL COMMENT '关联模型ID',
  version INT NOT NULL COMMENT '业务版本号',
  flowable_version INT COMMENT 'Flowable流程定义版本',
  status INT DEFAULT 0 COMMENT '状态:0待部署,1已部署,2已发布,3已归档',
  is_primary INT DEFAULT 0 COMMENT '是否主版本:0否,1是',
  bpmn_xml LONGTEXT COMMENT 'BPMN XML内容',
  deploy_time DATETIME COMMENT '部署时间',
  deploy_by VARCHAR(64) COMMENT '部署人',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by VARCHAR(64),
  update_by VARCHAR(64),
  tenant_id VARCHAR(64) DEFAULT '1',
  UNIQUE KEY uk_model_version (model_id, version),
  KEY idx_model_id (model_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程模型版本表';
```

## 相关文件
- forge/forge-framework/forge-plugin-parent/forge-plugin-flow/src/main/java/com/mdframe/forge/starter/flow/entity/FlowModelVersion.java
- forge/forge-framework/forge-plugin-parent/forge-plugin-flow/src/main/resources/sql/flow_version_init.sql