# 增量验证计划

## 本轮 P0

1. `git diff --check`。
2. `mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -DskipTests compile`。
3. `BusinessFlowStartLockManagerTest`：事务完成前互斥、异常释放、Redisson 分布式锁优先。
4. `RuntimeFieldMetadataCompilerTest`：字典去重与顺序、脱敏和加密规则。

## 本轮 P1

运行 `BusinessFlowServiceLifecycleTest`、`BusinessFlowServiceBusinessKeyTest`、`LowcodeRuntimeConfigBuilderTest` 和 `GeneratedLowcodeRuntimeConfigBuilderTest`，检查入口行为没有因委托改变。

不启动 Admin/Flow 服务或改动数据库；本轮没有 API/SQL 变更，也未具备本机 MySQL/Redis 端到端环境。后续状态回调和发布阶段必须升级到服务级验证。

## 第二轮增量验证：树与主子表编译

- P0：`git diff --check`；generator Reactor 编译；`RuntimeTreeConfigBuilderTest`、`RuntimePageRelationResolverTest`、`RuntimeChildTableCompilerTest`，分别覆盖外部树来源优先级、字段名归一化、关系推断、树 API 协议和子表保存模式。
- P1：重跑 `LowcodeRuntimeConfigBuilderTest`、`GeneratedLowcodeRuntimeConfigBuilderTest`、`RuntimeFieldMetadataCompilerTest`，确认整体序列化配置及上一轮字段元数据保持兼容。
- 本轮不修改 Flowable 回调、数据库或 HTTP 协议；因此不启动 Admin/Flow 服务。后续流程状态修复阶段再做数据库和真实流程联调。

## 第三轮增量验证：流程状态写入与 1000 行规范

- P0：`git diff --check`、generator Reactor 编译；新增状态修复组件单测覆盖发布版/草稿版配置选择、状态字段白名单和写入失败传播。
- P1：重跑 `BusinessFlowServiceLifecycleTest`、`BusinessFlowStatusFieldServiceTest`、`BusinessFlowServiceBusinessKeyTest`，确认任务创建/完成、终态回调、重复回调和字段回写仍按原路径执行。
- 仅在当前变更的编译及单测范围验证；真实 MySQL/Redis/Flowable 端到端验收按已有用户偏好由用户执行，不将未执行的服务级测试写成通过。
