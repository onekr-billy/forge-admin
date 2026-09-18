# LawHub 全量系统初始化与部署

## 背景

基于当前 Forge Admin 主干初始化一个独立、可继续开发的全量系统，并部署到用户提供的 Docker 服务器。新系统中文名为“法律协同平台”，英文名为“LawHub”，数据库名为 `law_hub_admin`。

## 目标

- 使用当前仓库的全量模块，而不是最小管理端预设。
- 所有 Maven 工程、子模块、目录、包名和前端配置使用 LawHub 命名，不残留 `forge-*` 工程前缀或 `com.mdframe.forge` 包名。
- 保留完整数据库结构和系统必需数据，但不导入日志、运行记录和历史执行数据。
- 提供可复现的 Docker 构建与部署配置。
- 部署后管理端可通过服务器 80 端口访问，默认管理员能够正常登录；应用容器自动重启且健康检查通过。

## 范围

### 全量后端

- Admin、App、Report、Flow 服务和 Business 工程。
- System、Job、Message、Flow、Collaboration、Generator、AI、External、Data、MCP 插件。
- Capability Parent 以及 Core、Platform、Actions、High-Risk Approval 子模块。
- 当前仓库全部技术 Starter，包括 OpenAPI Security、Outbound、Collaboration。

### 全量前端

- 管理端前端。
- 报表端前端。

### 数据库

- 目标库：`law_hub_admin`，字符集 `utf8mb4`。
- 导入当前全量初始化 SQL 的表结构与业务必需数据。
- 对日志表、历史表、在线会话、调度运行态和 AI 执行记录仅保留表结构，不导入 `INSERT` 数据。
- 不在代码、SQL或部署文件中写入真实数据库密码。

## 命名

- 项目目录：`lawhub`
- Maven groupId / Java 基础包：`com.lawhub.platform`
- Maven artifactId 前缀：`lawhub-`
- 后端 API 前缀：`/lawhub-api`
- 管理端公开路径：`/lawhub`
- 报表端公开路径沿生成结果统一使用 LawHub 命名。

## 部署约束

- 复用服务器现有 MySQL、Redis 和 Nginx，不重复创建中间件。
- 后端通过仅主机网络访问 `127.0.0.1:3306` 和 `127.0.0.1:6379`。
- LawHub 服务使用 `restart: unless-stopped` 和 Docker 健康检查。
- 外部只通过现有 Nginx 的 80 端口提供访问。
- 密钥和密码只保存在服务器 root-only 环境文件，不提交仓库。

## 验收标准

1. 生成目录包含上述全部模块，父 POM 能解析全部子模块。
2. 生成系统中不存在 `forge-*` Maven artifact/module 引用，也不存在 `com.mdframe.forge` Java 包引用。
3. 全量 SQL 中日志/历史类表不存在初始化 `INSERT` 数据，但表结构仍然存在。
4. 后端编译和前端生产构建通过。
5. 远端所有 LawHub 容器健康并设置自动重启。
6. `http://192.168.66.158/lawhub/` 可访问，使用默认管理员账号能够登录并获取有效 Token。

## 风险与回滚

- 数据库初始化属于有状态变更，只操作新库 `law_hub_admin`；回滚时停止 LawHub 容器并删除该新库，不影响既有 `forge` 库。
- Nginx 仅新增 `/lawhub` 与 `/lawhub-api` 路由；回滚时删除对应配置并 reload。
- 服务器部署文件使用独立 `/opt/lawhub` 目录，避免覆盖 `/opt/forge-infra` 中间件配置。
