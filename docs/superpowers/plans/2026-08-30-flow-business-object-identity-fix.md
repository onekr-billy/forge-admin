# Flow 业务对象编码唯一性与待办表单解析修复计划

## 目标

消除不同业务域重复 `object_code` 导致的待办表单串单，并让业务对象编码在租户内保持唯一。

## 实施步骤

1. 在 `BusinessObjectMapper` 增加租户范围编码计数查询；`BusinessObjectService` 创建/修改时统一校验，禁止继续保存通用占位编码和跨套件重复编码。
2. 在 `BusinessFlowService` 解析任务表单运行上下文时，按流程实例关联对象 ID、配置键、套件和对象编码逐级解析；列表补充同样复用确定性上下文。
3. 增加 `V1.0.136__enforce_business_object_code_uniqueness.sql`：为已存在的重复 `business_object` 生成稳定的套件前缀编码，同步引用列和常见 JSON 配置，再创建 `(tenant_id, object_code, del_flag)` 唯一索引。
4. 增加对象服务和待办表单解析回归测试；执行 `git diff --check` 与 Generator 模块 Maven 编译。

## 风险与回滚

- 编码变更会影响旧流程业务 Key，因此迁移先同步 `ai_business_flow_instance_link`、业务绑定、应用、CRUD 配置及设计版本等引用；Flowable 原生历史表不修改。
- 若线上存在未覆盖的自定义 JSON 引用，可先回滚唯一索引迁移（删除新增索引）并根据审计日志补充引用更新，再重新执行后续版本脚本。
