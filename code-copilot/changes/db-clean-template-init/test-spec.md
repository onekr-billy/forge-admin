# 干净模板库初始化 Test Spec

## 自动化（桩测试，无需数据库）

执行位置：`forge-server/scripts/db`

```bash
node --test init-db.test.mjs clean-db.test.mjs
/bin/bash -n init-db.sh && /bin/bash -n clean-db.sh
```

| 用例 | 覆盖点 |
|---|---|
| init：全量 SQL 缺失、为空 | 调用 MySQL 前失败 |
| init：非空库 | 拒绝导入，并提示 `--recreate` |
| init：`--recreate --yes` | 先 DROP 再导入 |
| init：`--clean` 与 `--with-demo` 同时使用 | 冲突报错 |
| init：`--migrate`、缺少 Maven | Flyway 执行器参数；提供替代方案提示 |
| init：`--clean` | 透传参数给 clean-db.sh |
| clean：预览 | 使用真实表清单（全量 SQL 与迁移建表）；验证 DROP、TRUNCATE、保留三类分类结果；预览不执行写入 |
| clean：SQL 计划 | 保留超级管理员与租户，清理低代码菜单，行级过滤，写入本地存储；菜单清理在 TRUNCATE 之前 |
| clean：增量未执行完、缺少超级管理员 | 拒绝执行 |
| clean：`--execute --yes` | 把计划通过 stdin 交给 mysql |
| clean：`--keep-table`、`--keep-business-tables`、`--extra-sql` | 调整清理范围 |

## 人工验收（真实 MySQL 8 + Maven）

1. 执行 `MYSQL_PWD=*** bash forge-server/scripts/db/init-db.sh --database forge_tpl_check --recreate --clean --yes`，应成功结束。
2. 执行以下查询：
   - `SELECT COUNT(*) FROM sys_user`，结果应为 1；
   - `SELECT COUNT(*) FROM sys_tenant`，结果应为 1；
   - `SELECT COUNT(*) FROM sys_role`，结果应为 1；
   - `SELECT COUNT(*) FROM ACT_RU_TASK`，结果应为 0；
   - `SELECT COUNT(*) FROM sys_job_config`，结果应大于 0。
3. `SHOW TABLES LIKE 'crm_%'` 应为空；`SHOW TABLES LIKE 'sample_purchase_order'` 应存在。
4. 将 admin 指向该库启动：
   - Flyway 应报告 "Schema is up to date"；
   - 用 admin/123456 登录后，菜单完整，不出现低代码应用菜单；
   - 上传一张图片，走本地存储应成功。
5. 再执行一次 `clean-db.sh --execute --yes`，应幂等成功。
