# 干净模板库初始化 Spec

> 变更名：`db-clean-template-init`  
> 状态：`implemented`（脚本与桩测试完成，待真实 MySQL 验收）  
> 创建日期：2026-09-30  
> 涉及：数据库物理删除、权限数据清理，需人工审查

## 1. 背景

把当前工程作为模板初始化新项目时，数据库带着大量开发期数据：日志、低代码应用及其自动建表、测试用户/租户/组织/角色、流程实例与模型、报表大屏、AI 供应商配置、文件记录等。旧 `init-db.sh` 只导入全量 SQL 和 required seed，存在以下问题：

- 非空库重跑直接报 `Table already exists`，没有重建选项。
- 不执行 Flyway 增量，必须再启动一次后端。
- 密码走命令行参数，`ps` 可见。
- 没有任何清理能力。

另外，全量 SQL 和 `docker-forge-admin/init-sql/01-init.sql` 的 `sys_file_storage_config` 提交了一条带真实 AK/SK 的腾讯 COS 配置。

## 2. 目标

一条命令得到干净系统：**全量 SQL → required seed → Flyway 增量 → 清理**。

保留：

- 默认租户（`--tenant-id`，默认 1）。
- 超级管理员（`--admin-username`，默认 admin），及其角色、角色菜单授权、一个根组织。
- 菜单与权限 `sys_resource`、字典、系统参数、行政区划、客户端、消息模板。
- 定时任务配置 `sys_job_config` 与 Quartz 定义表（日志清空）。
- 内置模板：页面/提示词/供应商模板、系统流程模板与 SpEL 模板、系统常用语、通用字段模板、内置公式与编码规则、`general` 业务域、`dashboard_generator` Agent。
- `forge_schema_history`，确保后续新增迁移能继续增量执行。

清空或删除：

- 日志、历史、outbox/inbox、幂等记录、在线用户。
- 消息公告、文件元数据和分组、存储配置（清空后重建一条本地存储）。
- 全部流程数据：`sys_flow_*` 运行与设计数据、Flowable `ACT_*`/`FLW_*`（保留 `ACT_GE_PROPERTY`、`ACT_ID_PROPERTY` 与 liquibase 变更表）。
- 低代码：应用、对象、页面配置、发布记录、能力开放，以及它们生成的菜单和角色授权。
- 报表大屏与数据集、AI 供应商/模型/路由/知识库/会话、代码生成记录、打印模板、外部系统、社交登录绑定、岗位。
- 其它租户的数据、逻辑删除残留。
- 低代码自动建的业务表：DROP。

## 3. 设计

### 3.1 顺序约束

清理必须在 Flyway 增量之后执行。多个迁移脚本会写演示数据（例如 V1.0.105 预售应用和 `ps_presale_*` 表、V1.0.114 预售流程、V1.0.158 Gitee 租户、V1.0.107 外部接口示例），V1.0.166 还会更新 `sys_resource_0607`。先清理再迁移，数据会被重新写回来。

`clean-db.sh` 在预检时比对 `db/migration` 的版本和 `forge_schema_history` 中 `success = 1` 的版本。有缺失就拒绝执行，除非显式传 `--allow-pending-migrations`。

### 3.2 表分类

依据 `information_schema.tables` 的实时表清单，每张表按以下顺序判定：

1. `--keep-table` 列出的表，或精确保留表：`forge_schema_history`、`qrtz_locks`、`act_ge_property`、`act_id_property`、流程模板类表、Flowable liquibase 表。
2. 框架前缀 `sys_|ai_|gen_|qrtz_|act_|flw_|forge_`，以及 `worker_node`、`config_properties`：
   - 命中备份、历史副本命名（`_bak|_backup|_old|_copy|_tmp`、数字日期后缀、`tmp_` 前缀）的：DROP。
   - 命中清空规则的：TRUNCATE。
   - 其余：保留，只做后面的行级过滤。
3. 其它表视为业务表：
   - 有 `@TableName` 实体或 Mapper XML 引用（如 `sample_purchase_order`、`biz_leave_request`）的：TRUNCATE，避免代码启动后缺表。
   - 其余：DROP；传 `--keep-business-tables` 时只 TRUNCATE。

### 3.3 行级清理

- 低代码菜单：以 `path LIKE '/ai/crud-page/%'`、`ai:business:application:%` 权限和 `ai_crud_config.menu_resource_id` 为种子，用递归 CTE 收集子孙，写入临时表后删除，并同步删除对应 `sys_role_resource`。**必须在 TRUNCATE `ai_crud_config` 之前执行。**
  - 不从 `ai_lowcode_domain.menu_parent_id` 推导种子：`cust_manage` 域指向系统目录 39。
- 身份数据：
  - 删除其它租户和用户。
  - 重置超级管理员的头像和登录统计。
  - 只保留超级管理员角色，并重建用户-角色、用户-租户、用户-组织关系。
  - 组织只保留一个根节点。
  - 清空角色-组织关系，过滤数据权限表。
- 全部带 `tenant_id` 列的表：`DELETE WHERE tenant_id NOT IN (0, @tenant_id)`。`0` 保留给历史平台级数据。
- 全部带 `del_flag` 或 `deleted` 列的表：删除非 0 的行。
- 删除孤儿 `sys_role_resource`。
- 没有存储配置时插入默认本地存储 `local`（`tenant_id = 1`）。

### 3.4 物理删除说明（AGENTS.md 5.12）

- **原因**：该脚本的定位就是“生成模板库”，被清理的都是开发期数据，不是可恢复的业务数据。
- **影响范围**：只作用于显式指定的 `--database`。预检阶段拒绝缺少 `sys_user`、`sys_tenant`、`sys_role`、`sys_resource` 的库，以及增量未执行完的库。
- **执行保护**：
  - 默认只预览，需要 `--execute` 才执行。
  - 执行前要求输入库名确认（`--yes` 跳过）。
  - 支持 `--backup-file` 先做 mysqldump。
- **回滚**：
  - 用备份恢复：`mysql <db> < backup.sql`。
  - 模板库可以直接 `init-db.sh --recreate --clean` 重新生成。
- 禁止对生产业务库执行。

### 3.5 init-db.sh 调整

- 密码只通过临时 `--defaults-extra-file`（权限 600）传给 mysql，不出现在进程参数里。
- 非空库直接失败，并提示使用 `--recreate`（需确认或 `--yes`）。
- `--migrate` 使用独立的 `scripts/db/flyway/pom.xml`。它的 Flyway 版本和配置与 admin 启动时一致：表 `forge_schema_history`、baseline 1.0.0、关闭占位符替换。
- `--clean` 隐含 `--migrate`，与 `--with-demo` 互斥；参数通过 `--clean-arg` 透传给 `clean-db.sh`。
- 兼容 macOS 自带的 bash 3.2。

### 3.6 敏感数据

两份初始化 SQL 的 COS 配置已替换为本地存储。密钥仍留在 git 历史中，**必须在腾讯云控制台轮换**。测试数据中的预签名 URL 还带着 SecretId，会被清理脚本清空，SQL 文件本身未改动。

## 4. 不做

- 不重写全量 SQL 本身，不删除迁移脚本中的演示数据（迁移已执行过的库不能改脚本）。
- 不处理 `forge-report-server` 的独立库。
