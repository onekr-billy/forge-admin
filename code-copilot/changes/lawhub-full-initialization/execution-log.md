# Execution Log

> 本文件在实现和部署验证过程中持续追加命令、结果、警告、跳过项与清理情况。

## 2026-09-18 生成与静态检查

- 更新 `scripts/forge-create/module-catalog.json`，`full` 预设覆盖当前主干的 Admin、App、Report、Flow、Business、10 个业务插件、Capability 全套子模块及全部 Starter；生成器实际输出 44 个后端模块和两个前端工程。
- 执行 `node scripts/forge-create/create-project.mjs ... --preset full --java-name LawHub --exclude-log-data --force`：成功生成完整工程。
- 生成器随工程复制 `db/migration`，共 165 个 Flyway 版本脚本；全量基线和当前代码版本可以完整对齐。
- 初始化 SQL 共保留 844 条业务/系统必需 `INSERT`，移除 73 条日志或运行历史 `INSERT`；静态扫描结果 `log_runtime_inserts=0`，对应表 DDL 保留。
- `rg '<artifactId>forge-|<module>forge-|com\.mdframe\.forge'` 扫描生成项目 POM 与 Java：无匹配。
- `rg 'ForgeAdmin|企业级中后台管理系统'` 扫描运行源码与配置：无匹配；管理端和报表端显示名称分别统一为“法律协同平台”和“LawHub”。
- `node --check scripts/forge-create/create-project.mjs`：通过。
- `git diff --check`：通过。

## 2026-09-18 构建与数据库

- Docker 后端构建执行 Maven 全量 Reactor：51 个构建单元全部成功，产出 Admin、App、Flow、Report 四个运行镜像。
- 管理端执行 `pnpm --ignore-workspace build`：成功。
- 报表端执行 `pnpm --ignore-workspace build`：成功。
- 初次直接执行 `pnpm build` 受到父工作区识别影响，Dockerfile 改为显式 `--ignore-workspace` 后通过。
- 报表构建存在依赖中的 `eval`、管理端存在 CSS `//` 注释、动态导入不会拆包及大包体积警告；均为非阻断警告，没有编译错误。
- 创建新库 `law_hub_admin`，先导入去日志化的全量 SQL，再按版本顺序应用 165 个迁移脚本；全部执行成功。
- 初始基线缺少较新字段 `flow_related_visible`，应用完整迁移集后修复；因此最终工程已把迁移目录纳入全量生成和部署说明。
- 应用首次启动前抽查 `sys_job_log`、`ai_model_invocation_log`、`sys_flow_error_log`、`sys_auth_online_user`：均为 0；登录后当前在线会话属于正常运行数据。

## 2026-09-18 部署与端到端验收

- 部署目录：`/opt/lawhub`；复用服务器已有 MySQL、Redis、Nginx，不创建重复中间件。
- Compose 服务 `lawhub-admin`、`lawhub-app`、`lawhub-flow`、`lawhub-report`、`lawhub-ui`：全部 `healthy`。
- `docker inspect`：五个服务重启策略全部为 `unless-stopped`。
- Admin `18580`、Flow `18081`、Report `18581`、App `18583` 健康端口检查均返回 HTTP 200。
- 入口 Nginx 已加入 `lawhub-edge`，`/lawhub/` 与 `/lawhub-report/` 均返回 HTTP 200；报表 HTML 标题为 `LawHub`。
- 管理端 Nginx 对 HTML 使用禁止缓存策略、对带 hash 的静态资源使用长期不可变缓存，避免发布后旧 HTML 指向已删除动态模块。
- 真实浏览器访问 `http://192.168.66.158/lawhub/`，页面标题为“法律协同平台”，登录页品牌为 `LawHub`。
- 使用默认管理员凭证登录，选择“默认租户 / 法律协同平台”，成功进入 `/lawhub/home`；首页显示“超级管理员”“法律协同平台 工作台”和“LawHub 工作台”。
- 本地完整工程同步到 `/Users/mini32g/Desktop/project/lawhub`。
- 服务未清理：这是用户要求保留运行的部署交付物；最终状态为健康运行。

## 2026-09-18 初始化数据字符集修复

- 根因确认：基线后的迁移脚本曾由未显式指定 `utf8mb4` 的 MySQL 客户端逐文件导入，中文 UTF-8 字节被按 latin1 解码后再次写入，导致菜单“企业协同”“开放平台”等初始化数据出现双重编码乱码；前端渲染本身正常。
- 对数据库全部字符、文本、JSON、枚举字段执行只读审计，确认问题还涉及字典、消息模板、低代码配置、外部接口配置、任务配置等初始化数据，不局限于菜单表。
- 新增 `V1.0.166__repair_utf8_mojibake_seed_data.sql`：按乱码特征字符幂等修复受影响字段；Quartz 派生运行记录删除后由调度器重建；重复任务配置遵循逻辑删除规范保留历史。
- 修复前备份 33 张受影响表到服务器 `/opt/lawhub/backups/law_hub_admin_affected_tables_before_utf8_repair_20260918.sql`（4.6 MiB）。
- 迁移使用 `mysql --default-character-set=utf8mb4` 执行成功；再次执行全库审计无输出，即剩余乱码特征字段数为 0。
- 同一迁移重复执行成功，随后全库审计仍无输出，确认脚本具备幂等性。
- 抽查 `sys_resource`：`页面操作审计`、`企业协同`、`开放平台` 均为正常中文；任务配置中 3 条正确企业协同任务和 1 条开放网关任务保持有效。
- 修复迁移已同步到生成工程 `/Users/mini32g/Desktop/project/lawhub` 和服务器 `/opt/lawhub/lawhub-server/db/migration/`。
- 重启 Admin 后五个 LawHub 容器均为 `healthy`；真实浏览器刷新后侧栏显示“企业协同”“开放平台”，进入企业协同页面后标题、说明和六个功能页签均正常中文，开放平台子菜单“能力目录”“客户端工作台”也显示正常。
