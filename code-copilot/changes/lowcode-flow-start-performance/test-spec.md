# 低代码流程发起性能优化测试计划

## P0 单元回归

1. 前端运行态快照
   - 接口返回运行态能合并到行记录的 `_documentRuntime` 和 `_runtimeActions`。
   - 空运行态不覆盖已有快照。
   - 后台刷新在调度回调执行前不运行，证明主请求不等待整表刷新。
   - 后台刷新失败被独立捕获，不反向污染主操作结果。
2. Flow 发起配置
   - 未变更模型在 TTL 内连续读取只解析一次。
   - 修改首次返回对象的节点或诊断列表不影响缓存快照。
   - BPMN XML 或模型版本变化后立即重新解析。
   - 同一 modelKey 在不同 tenantId 下不共享缓存。

## P1 构建与静态检查

- `pnpm --dir forge-admin-ui --ignore-workspace exec vitest run src/components/ai-form/__tests__/business-action-runtime.spec.js`
- `pnpm --dir forge-admin-ui --ignore-workspace exec eslint src/components/ai-form/AiCrudPage.vue src/components/ai-form/business-action-runtime.js src/components/ai-form/__tests__/business-action-runtime.spec.js`
- 使用 JDK 17 执行 `mvn -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-flow -am -Dtest=FlowModelServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`
- 使用 JDK 17 执行 `mvn -pl forge-framework/forge-plugin-parent/forge-plugin-flow -am -DskipTests compile`
- `pnpm --dir forge-admin-ui --ignore-workspace run build`
- `git diff --check`

## 真实环境验收

- 浏览器 Network 确认 `/ai/business/flow/start` 返回后页面级 loading 立即结束，列表请求在后台继续执行。
- 连续发起同一模型的两条记录，对比 Flow 服务分段耗时或调试断点，确认第二次不重复解析 BPMN。
- 修改并保存流程模型后立即打开发起弹窗，确认自选审批人节点和诊断结果使用新配置。

## 验证边界

- 自动化验证不自动修改真实业务数据。
- 若 Admin、Flow、MySQL 或 Redis 未完整运行，不伪造端到端结果，在执行记录中明确标注。
