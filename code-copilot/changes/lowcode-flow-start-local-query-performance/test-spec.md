# 低代码流程发起本地查询优化测试计划

## P0 单元回归

1. 预加载发起校验
   - 草稿状态、手工模式、已配置流程且有权限时允许发起。
   - 状态策略禁止发起时返回原有业务错误。
   - 已存在流程实例时拒绝另起主流程。
   - 手工发起缺少 `START_FLOW` 权限时拒绝；内部触发器不检查按钮权限。
   - 校验过程不读取运行配置、业务记录、应用流程运行记录、流程轮次或 Flow 待办。
2. 发起查询契约
   - 发起锁内使用 `selectLatestByBusinessKey`，不再调用 `selectRunningByBusinessKey`。
   - 预加载记录、配置视图、绑定和最新关联进入轻量校验。
   - Flow 业务 Key 与 `roundNo` 从同一个最新关联生成。
   - `ensureBusinessBinding` 使用本次已解析的运行配置和单据配置。

## P1 构建与静态检查

- 使用 JDK 17 运行 `BusinessDocumentRuntimeServiceTest,BusinessFlowPerformanceContractTest`。
- 使用 JDK 17 编译 `forge-plugin-generator` 及上游依赖。
- 执行 `git diff --check`。

## 真实环境验收

- 重启 Admin 和 Flow 后连续发起不同单据，对比 `/ai/business/flow/start` 服务端耗时。
- 开启 SQL 调试或链路观测，确认单次发起不再读取流程轮次、应用流程运行记录或当前待办。
- 验证草稿可发起、非允许状态不可发起、已有实例不可重复发起和触发器自动发起。

## 验证边界

- 自动化验证不启动或重启真实服务，不修改业务数据。
- 未由用户执行的真实链路验证不表述为已通过。

## 2026-09-20 本轮增量验证

- 重新执行新增的 3 个预加载校验测试和 `BusinessFlowPerformanceContractTest` 全部 4 个测试。
- 隔离编译 `BusinessDocumentConfigService`、`BusinessDocumentRuntimeService`、`BusinessApprovalTitleRenderer` 和 `BusinessFlowService`。
- 执行 generator 及上游 reactor 编译，确认失败仅来自本阶段范围外的工作区未完成代码。
- 执行本阶段文件及全工作区 `git diff --check`；不启动 Admin/Flow，不写业务数据。
