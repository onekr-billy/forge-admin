# MySQL 排序规则一致性修复执行记录

> status: complete
> created: 2026-08-25

## 2026-08-25 实施启动

- 新建分支 `fix/mysql-collation-consistency`。
- 工作区已有 `.DS_Store` 修改/删除，保持不动且不纳入本次提交。
- 已确认故障链路：数据库默认 `utf8mb4_unicode_ci` 与全量初始化表的
  `utf8mb4_0900_ai_ci` 不一致，Flyway 临时表继承数据库默认值后与业务表比较失败。
- 已发布 Flyway 迁移保持只读；真实 MySQL/Flyway 验收留给用户侧环境。

## 2026-08-25 失败基线

- 新增 `forge-server/scripts/db/check-collation-consistency.sh` 后，在生产文件修改前执行失败。
- 基线识别出：`init-db.sh` 创建规则错误且未校正已有数据库、Compose 服务端规则错误、
  全量/报表/消息/通知 SQL 仍存在旧显式排序规则。
- 首次基线同时发现检查脚本对以 `--` 开头的搜索模式缺少参数终止符，修正检查脚本后再实施生产修改。

## 2026-08-25 实施内容

- `init-db.sh` 的 MySQL 客户端固定为 `utf8mb4`，创建数据库后执行 `ALTER DATABASE`，
  无论数据库为新建还是预先创建，后续临时表都继承 `utf8mb4_0900_ai_ci`。
- 两份全量 SQL 在文件开头执行 `ALTER DATABASE` 和带显式 `COLLATE` 的 `SET NAMES`。
- Docker MySQL 服务端默认排序规则改为 `utf8mb4_0900_ai_ci`，移除官方镜像不识别且容易误导的
  `MYSQL_CHARSET` 环境变量。
- 统一两份全量 SQL 的 3 处遗留声明，以及报表、消息和通知插件活动 SQL 的旧声明。
- 统一项目内开发者建库示例；公开文档已由提交 `c9ba817` 统一并完成构建验证。
- 不修改 `V1.0.114` 或其他已发布迁移；历史迁移中的旧声明和描述历史故障的注释按兼容边界保留。

## 2026-08-25 自动化验证

- `bash forge-server/scripts/db/check-collation-consistency.sh`：通过。
- `bash -n forge-server/scripts/db/init-db.sh`：通过。
- `bash -n forge-server/scripts/db/check-collation-consistency.sh`：通过。
- `forge-server/scripts/db/init-db.sh --help`：通过。
- `docker compose -f docker-forge-admin/docker-compose.yml config --quiet`：通过。
- 公开文档 `CREATE DATABASE` / `DEFAULT COLLATE` / `collation-server` 旧规则命令扫描：通过。
- `git diff --quiet -- forge-server/db/migration`：通过，已发布 Flyway 文件无改动。
- `placeholder-replacement: false` 配置确认：通过，迁移内已有业务消息模板变量无需改动。
- `git diff --check`：通过。

## 跳过与环境说明

- 未启动真实 MySQL、Admin 或 Flyway，避免影响本地数据和服务。
- 本机 MySQL 客户端为 5.7；目标 `utf8mb4_0900_ai_ci` 要求 MySQL 8.0+，真实初始化留给用户环境验收。
- ShellCheck 未安装，已用 `bash -n` 覆盖语法检查。

## 2026-09-17 增量修复与验证

### 故障基线及修复

- 原校验脚本扫描已不存在的 admin `resources/sql`，rg 报 `No such file or directory (os error 2)`，脚本仍输出 `check passed` 并返回 0。
- 新增两份 Node 内置测试后，初始 16 项中 8 项失败、8 项通过，覆盖真实目录警告、扫描执行错误漏报、依赖与路径缺失、初始化 SQL 缺失/空输入以及过期帮助路径。
- 校验脚本移除废弃扫描项，预检 rg 与必需路径，明确区分 rg 的 0/1/2 退出码，并使用 `--no-config` 避免用户配置干扰。正向检查不使用 `--quiet`，避免提前返回掩盖读取错误。
- 初始化脚本在调用 MySQL 前校验必需全量 SQL 的文件类型、可读性与非空条件，保留 `--skip-admin-init`。帮助、安装指引与脚手架说明统一到实际全量 SQL，明确 baseline 只有注释、全量导入仅用于新库。
- 同步文档中的 Admin/Flow 共库、Flow 8081、开发环境覆盖文件、Maven `-Penable-tests` 和当前 Docker 目录入口。
- 首轮修复后 14 项通过、2 项失败：中文全角标点直接紧跟 `$status`，本地 Bash 在错误分支报 `unbound variable`。改为 `${status}` 后 16 项全部通过。
- 随后补强必需模式返回 2 的错误消息断言，并新增真实 rg 在预检后遭遇缺失路径的测试；最终复跑 17 项全部通过。

### 最终执行证据

以下命令均在仓库根目录执行：

| 命令 | 结果 |
|---|---|
| `node --test --test-reporter=spec forge-server/scripts/db/check-collation-consistency.test.mjs forge-server/scripts/db/init-db.test.mjs` | `tests 17`、`pass 17`、`fail 0`、`skipped 0` |
| `bash -n forge-server/scripts/db/check-collation-consistency.sh` | 退出 0 |
| `bash -n forge-server/scripts/db/init-db.sh` | 退出 0 |
| `bash forge-server/scripts/db/check-collation-consistency.sh` | `Collation consistency check passed: utf8mb4 / utf8mb4_0900_ai_ci`；真实测试同时断言 stderr 为空 |
| `node --check scripts/forge-create/create-project.mjs` | 退出 0 |
| `docker compose -f docker-forge-admin/docker-compose.yml config --quiet` | 解析通过，未启动容器 |
| `git diff --check` | 退出 0 |
| `git diff --quiet -- forge-server/db/migration` | 退出 0，迁移无未暂存改动 |
| `git diff --cached --quiet -- forge-server/db/migration` | 退出 0，迁移无暂存改动 |

活动指引的增量文本检查未发现导入 baseline 的命令、Flow 代理 8581、旧 JDBC 示例库名或单独 `cd docker`；这不是对历史档案、忽略目录和生成文档的全量清零声明。

### 边界与未覆盖项

- 初始化测试使用子 Shell 中的 MySQL 函数桩，没有真实数据库写入；空输入用 `/dev/null` 模拟，不等同于普通空文件分支的独立覆盖。
- 没有运行真实 MySQL 初始化或 Flyway，没有修改已发布迁移、AI 功能或当前暂存区，没有提交或推送。
- 本轮未启动服务，无新增 PID 需要停止；已有服务与数据保持不动。
- 未运行前后端全量构建或 E2E，本轮没有修改其运行时代码；未完成完整脚手架项目生成与编译，Node 语法通过不能代替该验收。
- IDE 问题检测工具不可用，不能计为静态检查通过；本轮依据为实际 Bash/Node 检查、测试和 Compose 解析。ShellCheck 未作为本轮完成项。
- 权限链路和巨型 SFC 保持后续独立范围；仅出现 `LambdaQueryWrapper` 或 `@Lazy` 不能作为越权或循环依赖已证实的结论。
