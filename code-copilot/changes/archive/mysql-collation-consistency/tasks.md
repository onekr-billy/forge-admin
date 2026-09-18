# MySQL 排序规则一致性修复 Implementation Plan

> status: complete

**Goal:** 让 Forge Admin 所有新环境初始化入口稳定采用
`utf8mb4_0900_ai_ci`，消除由数据库默认值和表/列显式值不同导致的比较错误。

## Task 1: Spec 与失败基线

- [x] 固化活动初始化范围、Flyway checksum 边界和 MySQL 8.0+ 前提。
- [x] 新增静态一致性检查脚本。
- [x] 在修改生产文件前执行检查并记录失败基线。

## Task 2: 统一初始化入口

- [x] 修改 `forge-server/scripts/db/init-db.sh` 的建库与默认值校正逻辑。
- [x] 修改 `docker-forge-admin/docker-compose.yml` 的 MySQL 服务端排序规则。
- [x] 统一服务端与 Docker 两份全量初始化 SQL。

## Task 3: 统一活动独立 SQL 与项目说明

- [x] 统一报表、消息、通知插件独立初始化 SQL。
- [x] 统一 `AGENTS.md` 与 `code-copilot/rules/project-context.md` 建库示例。
- [x] 确认公开文档仍为 `utf8mb4_0900_ai_ci`。

## Task 4: 验证与交付

- [x] 执行静态一致性、Shell 语法、Compose 解析和 Flyway 保护检查。
- [x] 执行 `git diff --check` 并复核未改写已发布 Flyway 脚本。
- [x] 回填执行日志并提交，不自动 Push。

## 2026-09-17 增量修复计划：校验漏报与初始化指引

保留以上历史验收记录。本轮增量已完成：17 项测试通过，语法、真实扫描、Compose 与迁移保护检查通过；证据见同目录 `execution-log.md`。本轮未提交、未启动数据库、未修改已发布迁移。

- [x] 在 `forge-server/scripts/db/check-collation-consistency.test.mjs` 使用 Node 内置测试运行器覆盖真实仓库扫描、两类扫描返回 0/1/2、缺少 rg、缺少扫描路径及必需模式检查失败；先运行并记录失败基线。
- [x] `check-collation-consistency.sh` 移除已废弃的 admin resources/sql 扫描项；检查 rg 依赖与其余必需路径；负向扫描返回 1 才代表无匹配，返回 2 等执行错误必须失败。保留历史迁移排除范围。
- [x] `init-db.sh` 在连接 MySQL 前检查必需全量 SQL，缺失或为空立即失败；保留 `--skip-admin-init` 显式跳过语义，并更正帮助中的 SQL 路径。新增同目录 `init-db.test.mjs` 以 MySQL 桩验证，不连接真实数据库。
- [x] `AGENTS.md`、`project-context.md` 的首次安装命令改为实际全量 SQL，并说明只适用于新库；配置示例引用服务模板，Flow 与 Admin 共库。恢复 `-Penable-tests` 命令与实际前端默认端口。
- [x] 同步 `create-project.mjs` 的数据库说明模板，以及 `docker-forge-admin/docker-compose.yml` 中遗留的 `cd docker`。
- [x] 执行 `node --test forge-server/scripts/db/*.test.mjs`、两个 Shell 的 `bash -n`、真实静态扫描、Compose 解析、`git diff --check`；记录失败、跳过项与 Flyway 未改动证明。
