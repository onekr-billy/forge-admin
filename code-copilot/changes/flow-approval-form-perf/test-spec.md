# 审批表单与待办性能优化 Test Spec

## 1. 待办列表

- SQL 合同：租户、状态和用户边界保持不变。
- SQL 合同：候选组主路径使用 `sys_flow_task_candidate` 的 GROUP IN 查询。
- SQL 合同：标准化关系存在时不进入 CSV 兜底；不再逐行关联用户角色/组织表。
- SQL 合同：迁移增加覆盖 `task_id` 的候选查询索引且具备 `information_schema` 防重复保护。
- 服务测试：会话角色、当前组织和自定义用户组只解析一次后传给 Mapper。

## 2. 表单加载

- Flow 测试：`TaskFormInfo` 返回已通过可见性校验的任务状态、签收人和候选镜像。
- Generator 测试：一次业务表单上下文调用只读取一次 Flow 表单信息，不再读取 Flow 任务详情。
- 前端测试：业务表单上下文返回 `taskFormInfo` 时不再调用 `flowApi.getTaskFormInfo`；旧后端缺失快照时仍可回退。
- 回归：审批历史仍与表单并行加载，失败互不阻塞。

## 3. 提交审批

- 前端测试：业务表单审批只调用一次 `task-action`，payload 含 `{ main, children }`；手动暂存仍调用保存接口。
- 后端测试：动作入口按当前节点权限过滤和校验 data，保存成功后才调用 Flow 动作。
- 后端测试：无可写字段、越权字段、未授权子表增删仍被拒绝或过滤，不能绕过路线 A/B 已有安全校验。
- 回归：非业务表单、动态 AiForm、快速审批和代码表单行为不变。

## 4. 执行命令

执行阶段先读取 `code-copilot/rules/automated-testing-standard.md`，按本轮差异执行：

```bash
cd forge-admin-ui
pnpm exec vitest run <本轮定向测试>
pnpm exec eslint <本轮前端文件与测试>
pnpm build

cd forge-server
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-flow -am test \
  -Dforge.tests.skip=false -DskipTests=false -Dtest=<本轮定向测试> -Dsurefire.failIfNoSpecifiedTests=false
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am compile -DskipTests
```

## 5. 环境验收

- 记录 `/api/flow/task/todo` 的响应时间、SQL EXPLAIN 和查询行数。
- 记录打开同一待办时浏览器请求数、`task-form-context` 总耗时及 Admin→Flow 调用次数。
- 记录业务表单同意动作的浏览器请求数、`task-action` 总耗时和数据保存结果。
- 验证同意、驳回、驳回至发起人、退回、已办只读以及主子表编辑无回归。

## 6. 本轮结果

- Vitest：11 个文件、70 个测试通过。
- ESLint：第三轮前端文件与新增测试通过，0 errors、0 warnings。
- Vite 生产构建：通过。
- Mapper XML、Flyway 占位符、`git diff --check` 静态检查：通过。
- Java 定向测试/编译：当前主机无 Java Runtime、无 Maven，未执行。
- 真实接口耗时、SQL EXPLAIN、Admin/Flow/MySQL E2E：未启动服务，保留为部署验收项。
