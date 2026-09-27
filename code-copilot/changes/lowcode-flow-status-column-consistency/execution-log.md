# 执行记录

## 2026-09-25

### 已执行并通过

- `forge-server` Generator 主模块编译：
  `mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dforge.tests.skip=true -DskipTests compile`
  - 结果：`BUILD SUCCESS`。
- 前端流程状态列回归测试（Node `v20.19.0`）：
  `pnpm --ignore-workspace exec vitest run src/components/lowcode-builder/shared/__tests__/runtime-crud-props.spec.js`
  - 结果：1 个测试文件、32 个测试通过。
- 前端定向 ESLint：
  `pnpm --ignore-workspace exec eslint src/components/lowcode-builder/shared/runtime-crud-props.js src/components/lowcode-builder/shared/__tests__/runtime-crud-props.spec.js`
  - 结果：通过。
- 前端生产构建（Node `v20.19.0`，将默认 4 GB 堆上调为 8 GB）：
  `node --max_old_space_size=8192 ./node_modules/vite/bin/vite.js build`
  - 结果：构建成功；仅有既有 Vite 动态导入、CSS 注释和 chunk 体积类 warning。
- `git diff --check`
  - 结果：通过。

### 未完成/环境限制

- 后端定向测试命令已显式关闭项目默认跳过开关，但测试编译阶段被工作区既有测试源码阻塞，尚未进入目标测试执行：
  - `FormulaExecutionEngineTest.java` 存在 UTF-8 不可映射字符。
  - `DynamicCrudRepositoryTest.java` 使用旧的一参数构造器。
  - `DbAggregateDataProviderTest.java` 使用旧的四参数构造器。
- 这些失败不来自本变更新增的生产代码；本轮未修改其他任务/用户已有测试，以避免覆盖工作区改动。
- 未启动 Admin、Flow、MySQL、Redis，未执行真实流程和数据库联调。

### 本轮覆盖的修复点

- 旧 `flowStatus/flow_status` 字段缺少托管元数据时补齐 `managedBy`、字典、状态和列表引用。
- 流程绑定、流程保存入口触发字段一致性检查；DDL/权限失败不再静默成功。
- 已发布运行快照缺少流程状态字段时，运行配置装配补齐模型字段和列表列。
- 前端兼容历史 `flow_status`，规范化列的 `key/prop/dataIndex` 为 `flowStatus`，并避免旧 `fieldRefs` 过滤掉托管列；显式隐藏仍保留。
- 重复绑定保存仅在字段/列表实际修复或草稿版本领先发布版本时标记对象为 `CHANGED`，避免无变化保存制造新的发布漂移。
