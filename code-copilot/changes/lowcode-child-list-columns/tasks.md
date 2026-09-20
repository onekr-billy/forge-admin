# 低代码子表列表字段发布修复 Tasks

- [x] 新增发布编译回归用例，覆盖已选子表字段 `listVisible=false` 的场景。
- [x] 修复显式列选择被子表默认可见性二次过滤的问题。
- [x] 去除字段抽屉、设计预览与正式运行列标题的子表名前缀。
- [x] 修复 form-first 视图清洗只识别主表字段、覆盖 table zone 子表引用的问题。
- [x] 发布编译优先读取列表 grid 显式选列，兼容已经产生 grid/zone 分叉的历史草稿。
- [x] 执行增量测试、编译/构建、ESLint 和 `git diff --check`。

## 增量：预览列表取数

- [x] 在 `DynamicCrudServiceChildListTest.java` 新增回归：真实编译器 + Repository，模拟 JDBC 边界；旧列配置配合新 grid 选列必须生成子表 JOIN，返回别名与表头一致。修复前 2 个用例因仅生成主表 SELECT 失败。
- [x] `AiCrudConfigService` 提取 `resolveDraftRuntimeConfig`，用配置副本承载草稿编译结果；表头和 `DynamicCrudService.getConfig` 共用入口，正式请求仍走已发布快照。
- [x] 验证聚合、拆行、无子行、无设计权限、正式快照隔离及不修改原配置；Generator 编译和 38 个定向测试通过。
- [x] 只读复核真实数据/配置，记录证据及仍需用户执行的部署动作。不发布或写入业务数据，不提交工作区中交叠的未提交改动。
