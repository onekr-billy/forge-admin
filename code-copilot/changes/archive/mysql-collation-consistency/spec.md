# MySQL 排序规则一致性修复

> status: complete
> created: 2026-08-25
> complexity: 🟡中等

## 1. 背景

Forge Admin 新环境同时存在 `utf8mb4_0900_ai_ci`、`utf8mb4_unicode_ci` 和
`utf8mb4_general_ci`。文档按旧排序规则创建数据库时，全量初始化表使用 MySQL 8 默认排序
规则，后续 Flyway 临时表又继承数据库默认值，字符串列比较可能在
`V1.0.114__seed_presale_business_process.sql` 报 `Illegal mix of collations`。

## 2. 目标

- [x] 文档、`init-db.sh`、Docker Compose 和全量初始化 SQL 统一使用
  `utf8mb4` + `utf8mb4_0900_ai_ci`。
- [x] 报表、消息、通知等仍用于新环境初始化的独立 SQL 不再显式声明旧排序规则。
- [x] 初始化脚本在数据库已存在时也校正数据库默认排序规则，避免临时表继续继承旧值。
- [x] 提供可重复执行的静态一致性检查，阻止旧排序规则重新进入活动初始化入口。

## 3. 兼容边界

- 运行环境为 MySQL 8.0+；`utf8mb4_0900_ai_ci` 不兼容 MySQL 5.7。
- 不修改任何已发布的 `db/migration/V*.sql`，避免已执行环境发生 Flyway checksum mismatch。
- 不批量转换已有业务库的表或列；有数据的历史库需先备份，再按文档排查并由 DBA 制定转换方案。
- `db/backup`、历史变更 Spec 和描述历史故障原因的注释不纳入活动初始化门禁。

## 4. 验收标准

- `init-db.sh` 创建并校正数据库默认排序规则为 `utf8mb4_0900_ai_ci`。
- Docker MySQL 服务端默认排序规则为 `utf8mb4_0900_ai_ci`。
- 两份全量初始化 SQL 及活动独立 SQL 中不存在显式
  `utf8mb4_unicode_ci` / `utf8mb4_general_ci` 声明。
- Forge 项目内面向开发者的建库示例与公开文档保持一致。
- Shell 语法、Docker Compose 解析、静态一致性脚本和差异检查通过。
- 不启动真实 MySQL、Admin 或 Flyway；真实初始化由用户侧环境验收。

## 5. 实施结果

- 手工初始化、Docker 服务端、两份全量 SQL、活动独立 SQL 和项目内建库说明已统一到
  `utf8mb4_0900_ai_ci`。
- 全量 SQL 会先校正当前数据库默认值，再设置连接排序规则；Flyway 临时表因此继承相同默认值。
- 已发布 Flyway 文件无工作区改动，避免 checksum 回归。
- 静态一致性、Shell 语法、Compose 配置解析、文档命令扫描和补丁检查通过。

## 2026-09-17 增量边界

- 已复现扫描路径不存在但脚本仍返回 0；本轮要求扫描执行失败、依赖缺失、必需路径缺失均阻断通过。
- `V1.0.0__baseline.sql` 仅为迁移基线注释，不是初始化入口。首次安装指引与初始化脚本统一指向 `forge-server/db/全量初始化SQL.sql`；仅用于新库，禁止在已有业务库重跑。
- 初始化所需全量 SQL 缺失或为空时，在调用 MySQL 前失败；用户显式传入 `--skip-admin-init` 时保持原行为，可选 seed/module 目录仍可缺省。
- 文档继续以当前配置、脚本和 POM 为准；修正 Flow 代理端口、共库说明、测试 Profile 和 Docker 目录入口，不复制本地配置中的凭据。
- 不修改 AI 功能、运行中服务、数据库内容、历史迁移或当前 Git 暂存区；权限链路与巨型 SFC 不纳入本轮。

### 本轮验收结论

- 校验脚本失败分支、初始化 SQL 预检、安装指引和脚手架说明已修复；17 项回归测试全部通过。
- Bash/Node 语法、真实排序规则扫描、Compose 解析和差异检查通过；工作区与暂存区均未修改历史迁移。
- 未运行真实数据库/Flyway、完整脚手架生成编译或前后端 E2E，未提交代码。完整证据与覆盖边界见同目录 `test-spec.md`、`execution-log.md`。
