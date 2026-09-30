# db-clean-template-init 执行记录

## 2026-09-30：脚本实现与桩测试

### 变更

- `forge-server/db/全量初始化SQL.sql`、`docker-forge-admin/init-sql/01-init.sql`：COS 存储配置替换为本地存储。
- 新增 `forge-server/scripts/db/flyway/pom.xml` 和 `forge-server/scripts/db/clean-db.sh`。
- 重写 `forge-server/scripts/db/init-db.sh`。
- 测试：更新 `init-db.test.mjs`，新增 `clean-db.test.mjs`。

### 验证

- `node --test init-db.test.mjs`：12 个通过，0 失败（Node v24.21.0）。
- `node --test clean-db.test.mjs`：6 个通过，0 失败。
- `/bin/bash -n init-db.sh`、`/bin/bash -n clean-db.sh`：通过（macOS bash 3.2）。

### 问题记录

- bash 3.2 在 UTF-8 locale 下会把 `$DATABASE，` 中紧跟的全角字符当作变量名的一部分，报 `DATABASE�: unbound variable`。已把所有紧跟非 ASCII 字符的变量改为 `${VAR}` 形式。
- 迁移 V1.0.114 的 `tmp_presale_process_seed` 是 `CREATE TEMPORARY TABLE`，不会落库，测试中改用虚构的 `tmp_manual_fix` 验证垃圾表规则。

### 追加：工程初始化 skill 与脚手架

- `scripts/forge-create/create-project.mjs`：
  - `ignoredTemplatePathPrefixes` 新增三项：`code-copilot/changes`、`forge-server/db/backup`（已并入全量 SQL 的旧迁移归档，Flyway 不扫描）、`forge-server/db/community-export`；
  - 新项目生成空的 `code-copilot/changes/.gitkeep`；
  - 数据库 README 和完成提示改为推荐 `init-db.sh --recreate --clean`。
- skill 改名为 `forge-project-init`，覆盖代码生成 → 本地配置 → 干净数据库 → 验收的完整流程；`AGENTS.md` 2.5 节已登记。
- 验证：用 `node scripts/forge-create/create-project.mjs .tmp-forge-create-check/demo-app --preset full --base-package com.demo.app` 生成了一个全量项目（约 23s）。
  - `code-copilot/changes` 只剩 `.gitkeep`。
  - 库名 `demo_app` 和历史表 `demo_app_schema_history` 在 `application.yml`、Flyway 执行器和 `clean-db.sh` 中一致。
  - 在生成项目内运行 `node --test init-db.test.mjs clean-db.test.mjs`：18 个全部通过。
  - 验证完成后已删除临时目录。
- 生成项目中残留 3 处 `http://forge.mdframe...` XML 命名空间 URI，只是标识符，不影响运行。
- 用 `minimal-admin` 预设生成的项目里，`clean-db.test.mjs` 失败了 2 个用例。
  - 原因：该预设裁掉了 `business-core`，`sample_purchase_order` 不再有实体，脚本 DROP 它是正确的；是测试写死了全量代码的假设。
  - 修复：测试改为和脚本同一口径判断代码引用，即 `src/main` 下的 `@TableName` 或 Mapper XML，排除 test 目录和注释。另加扫描自检（必须能找到 `sys_user`）。
- 复验：模板仓库、`minimal-admin`、`full` 三种场景下 `init-db.test.mjs` + `clean-db.test.mjs` 均为 18/18 通过。
- 生成项目里 `check-collation-consistency.test.mjs` 原先有 6 个用例失败：该检查要求存在 docker 目录，而脚手架以前不复制它。下一节补上 docker 后已修复。

## 2026-09-30：脚手架加入 H5 与 Docker

### 变更

- `module-catalog.json` 新增两个模块，并都加入 `full` 预设：
  - `h5-ui`（frontend，依赖 app-server、flow-server）；
  - `docker`（deploy，依赖 admin-server、flow-server、admin-ui）。
- `create-project.mjs`：
  - 前端和部署模块也会带上依赖的后端模块；
  - H5 复制为 `<project>-h5-ui`，docker 复制为 `docker-<project>`；不复制本机 `docker-forge-admin/.env`。
  - 替换规则：
    - `docker-forge-admin` 排在所有替换之前；
    - H5 的 `forge-h5-ui`、`/forge-h5`、`forge_h5` 排在通用 `VITE_PUBLIC_PATH=/forge` 之前。之前的顺序会把 H5 路径截成 `/-h5`。
  - docker 后处理：
    - `nginx.conf` 和 `Dockerfile.ui` 按生成的管理端公开路径和 API 前缀重写；
    - `COPY ./forge-server` 改为新后端目录；
    - mysql、redis、network、ui 的名字使用项目名；
    - `init-sql/01-init.sql` 用生成后的全量 SQL 覆盖。
  - 生成的 README 补充 H5 和 docker 启动命令，以及 docker 部署后执行清理的步骤。
- 模板仓库的 `docker-forge-admin/init-sql/01-init.sql` 同步为 `forge-server/db/全量初始化SQL.sql`。原文件与全量 SQL 相差 677 行，存在三个问题：
  - 混入了其它生成项目的包名 `com.craken`，涉及 Quartz 任务类、数据权限 Mapper 方法和流程服务类；
  - 使用小写 `qrtz_*` 表，而配置的 `tablePrefix` 是 `QRTZ_`，在 Linux 上 MySQL 表名区分大小写，会导致 Quartz 找不到表；
  - 缺少大写的 `QRTZ_*` 建表语句。

### 验证

- 用 `full` 预设生成项目：
  - docker 的 `nginx.conf` 为 `location /`、`/api/`，代理到 `demo-admin` 和 `demo-flow`；
  - `Dockerfile` 使用 `COPY ./demo-server`，jar 为 `demo-admin-server.jar`；
  - compose 的服务、网络、库名和迁移目录挂载一致；
  - `01-init.sql` 与全量 SQL 字节一致，且没有外来包名；
  - H5 生产环境变量为 `/demo-h5`、`/demo-h5-api`、`demo_h5`，`sys_client` 同步为 `demo_h5`。
- 测试结果：
  - 生成项目的 `scripts/db` 全部测试（含排序规则检查）29/29 通过；模板仓库同样 29/29。
  - H5 的 `node:test` 在模板和生成项目中都是 92/96。失败的 4 个用例完全相同，3 个因为本机未安装 `node_modules`（缺 `pinia` 等），1 个是模板中既有的品牌资源用例，均与改名无关。
- 回归：
  - `minimal-admin` 预设不生成 H5 和 docker，数据库测试 18/18 通过；
  - `--include h5-ui` 会自动带上 app-server 和 flow-server。

### 跳过项

- 本机没有 MySQL、Maven、Docker，没有在真实数据库上执行，见 tasks T7。
- 预签名 URL 中残留的 COS SecretId 只存在于测试数据表，会被清理；SQL 文件未改。
