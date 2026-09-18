# MySQL 排序规则一致性修复测试计划

> status: complete
> created: 2026-08-25

## 1. 自动化检查

| 编号 | 场景 | 预期 |
|---|---|---|
| P0-01 | 手工初始化脚本 | 同时包含 `CREATE DATABASE` 和 `ALTER DATABASE`，目标均为 `utf8mb4_0900_ai_ci` |
| P0-02 | Docker 初始化 | MySQL 服务端参数为 `utf8mb4` / `utf8mb4_0900_ai_ci` |
| P0-03 | 全量 SQL | 服务端与 Docker 副本不含旧排序规则声明 |
| P0-04 | 活动独立 SQL | 报表和插件初始化 SQL 不含旧排序规则声明 |
| P0-05 | Flyway 兼容 | 已发布迁移文件没有工作区改动，迁移目录不存在占位符 |
| P1-01 | Shell 语法 | `bash -n` 通过 |
| P1-02 | Compose 配置 | `docker compose config` 可解析；未安装 Docker 时明确记录跳过 |
| P1-03 | 补丁质量 | `git diff --check` 通过，用户已有 `.DS_Store` 改动未被纳入 |

## 2. 计划命令

```bash
bash forge-server/scripts/db/check-collation-consistency.sh
bash -n forge-server/scripts/db/init-db.sh
bash -n forge-server/scripts/db/check-collation-consistency.sh
docker compose -f docker-forge-admin/docker-compose.yml config
rg -n '\$\{[^}]+\}' forge-server/db/migration
git diff --check
```

## 3. 人工环境验收

- 在全新 MySQL 8.0 实例运行 `init-db.sh` 后确认数据库默认值、字符列和临时表比较正常。
- 使用全新 Docker volume 启动 Compose，确认 MySQL 默认排序规则和 Admin Flyway 启动正常。
- 本轮不自动启动真实数据库、Admin 或 Flyway，避免影响本地已有数据和服务。

## 4. 执行结果

| 验证项 | 结果 |
|---|---|
| 静态排序规则一致性 | 通过 |
| `init-db.sh` / 检查脚本 Bash 语法 | 通过 |
| `init-db.sh --help` | 通过 |
| Docker Compose 配置解析 | 通过 |
| 公开文档建库/服务端参数命令扫描 | 通过 |
| 已发布 Flyway 文件无改动 | 通过 |
| Flyway placeholder 配置 | `placeholder-replacement: false`，现有业务模板变量按原样保留 |
| `git diff --check` | 通过 |
| ShellCheck | 跳过，本机未安装 |
| 真实 MySQL 8 / Flyway | 按计划留给用户侧环境验收 |

## 2026-09-17 增量验证：脚本失败分支与安装指引

保留以上历史结果。本轮仅验证校验脚本、初始化脚本和关联文档，不重复执行数据库初始化。

| 级别 | 增量场景 | 验收条件 |
|---|---|---|
| P0 | 真实仓库扫描 | 在脚本目录调用也成功，stderr 为空 |
| P0 | guide / SQL 负向扫描 | rg 返回 0 表示违规、1 表示无匹配、2 表示执行错误；只有 1 可继续通过 |
| P0 | 必需模式、依赖及路径 | 必需模式缺失、rg 不可用、扫描路径缺失均返回 1，不能输出通过 |
| P0 | 预检后的扫描错误 | 内存中给真实 rg 增加缺失路径，必须报告退出码 2 并阻断通过 |
| P0 | 必需全量 SQL 输入 | 缺失路径或 `/dev/null` 空输入桩在任何 MySQL 调用前被拒绝；后者不是普通空文件的独立测试 |
| P0 | 初始化执行与跳过 | 正常输入交给 MySQL 桩；显式跳过保持原语义；MySQL 桩失败不得输出完成 |
| P1 | 安装与配置说明 | 使用实际全量 SQL、新库限制、Admin/Flow 共库、8081 代理和 `-Penable-tests`；开发覆盖使用 `.env.development.local` |
| P1 | 语法与差异边界 | Bash/Node 语法、Compose 解析、差异检查通过；工作区和暂存区均未改历史迁移 |

从仓库根目录复跑：

```bash
node --test --test-reporter=spec forge-server/scripts/db/check-collation-consistency.test.mjs forge-server/scripts/db/init-db.test.mjs
bash -n forge-server/scripts/db/check-collation-consistency.sh
bash -n forge-server/scripts/db/init-db.sh
bash forge-server/scripts/db/check-collation-consistency.sh
node --check scripts/forge-create/create-project.mjs
docker compose -f docker-forge-admin/docker-compose.yml config --quiet
git diff --check
git diff --quiet -- forge-server/db/migration
git diff --cached --quiet -- forge-server/db/migration
```

结果：17 项测试通过，0 失败；上述语法、静态扫描、Compose 解析与迁移保护检查均通过。测试只在子 Shell 中模拟 MySQL、在内存中替换脚本参数，不连接真实数据库。

未覆盖：真实 MySQL/Flyway 初始化、完整脚手架生成与编译、前后端全量构建及 E2E。本轮没有修改前后端运行时代码；脚手架只校验了语法与说明模板，不能据此认定生成工程已完成端到端验收。
