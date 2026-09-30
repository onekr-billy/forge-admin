# 干净模板库初始化 Tasks

> 关联 Spec：`spec.md`

- [x] T1 移除两份初始化 SQL 中带 AK/SK 的 COS 存储配置，改为本地存储。
- [x] T2 新增 `forge-server/scripts/db/flyway/pom.xml`，其 Flyway 版本和配置与 admin 启动时一致。
- [x] T3 重写 `init-db.sh`：
  - 通过 defaults-extra-file 传密码；
  - 拒绝向非空库导入全量 SQL，并新增 `--recreate`；
  - 新增 `--migrate`、`--clean`、`--clean-arg`、`--yes`；
  - 兼容 bash 3.2。
- [x] T4 新增 `clean-db.sh`：
  - 默认预览，支持执行、备份、打印 SQL；
  - 增量完整性预检；
  - 表分类与行级清理。
- [x] T5 更新 `init-db.test.mjs`（12 个用例），新增 `clean-db.test.mjs`（6 个用例）。
- [x] T6 更新 `forge-server/db/README.md`，新增 `.agents/skills/forge-project-init/SKILL.md`（覆盖代码脚手架、配置、数据库和验收），记录踩坑。
- [x] T6.1 `forge:create` 生成新项目时：
  - 不复制 `code-copilot/changes`（包括历史和未归档的变更），只生成空目录和 `.gitkeep`；
  - 不复制 `db/backup` 和 `db/community-export`；
  - 生成的数据库 README 和完成提示改为 `init-db.sh --recreate --clean`。
- [x] T6.2 `forge:create` 支持 H5（`h5-ui`）和 Docker 部署（`docker`）模块，并加入 `full` 预设：
  - 路径、客户端 ID、nginx 公开路径和 API 前缀与改名结果保持一致；
  - docker 的初始化 SQL 与全量 SQL 同源；
  - 模板仓库的 docker 初始化 SQL 同步修复。
- [ ] T7 在真实 MySQL 8 上完整执行 `init-db.sh --recreate --clean`，然后启动 admin 并用 admin/123456 登录冒烟（本机无 MySQL/Maven，需人工执行）。
- [ ] T8 在腾讯云控制台轮换已泄露的 COS 密钥（人工）。
