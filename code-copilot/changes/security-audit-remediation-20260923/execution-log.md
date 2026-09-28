# security-audit-remediation-20260923 执行记录

## 2026-09-23：创建整改规格

### 范围

- 根据当前工程只读源码审计，新增 `spec.md`、`tasks.md`、`test-spec.md`。
- 未修改 Java、Vue、SQL、配置或测试业务代码。
- 保留工作区既有未提交改动。

### 已有验证证据（复用基线，非本变更完成证据）

- 后端 JDK 17 编译通过。
- 前端生产构建通过：`pnpm --ignore-workspace build`。
- `forge-plugin-system` 定向测试 115 个中 114 个通过，1 个错误，原因为测试引用过期路径 `forge-admin-server/sql/初始化脚本.sql`，实际初始化文件为 `forge-server/db/全量初始化SQL.sql`。
- 使用默认 Java 8 执行 Java 17 测试会因 class 文件版本不兼容失败。
- Flyway 版本未发现重复，当前最高版本为 `V1.0.184`。
- `pnpm audit --json` 因工具异常 `reference.startsWith is not a function` 未完成。

### 本轮结果

- 规格文件写入成功，待用户后续按 Phase 和 Task 实施。
- 未启动常驻服务、未连接真实 MySQL/Redis、未执行 Flyway、未执行生产部署或凭据轮换。

## 2026-09-23：低代码/流程后端巨型类专项扫描

### 范围

- 只读扫描低代码、业务流程和 Flowable 服务/监听器，重点关注类规模、职责混合、租户边界、动态 SQL、事务/远程调用、流程状态、事件副作用和 BPMN 输入解析。
- 记录的代表性类规模：`BusinessFlowService` 7771 行、`DynamicCrudService` 5995 行、`BusinessObjectDesignerService` 4256 行、`LowcodeRuntimeConfigBuilder` 3132 行、`FlowTaskServiceImpl` 3516 行、`FlowModelServiceImpl` 1243 行、`FlowMonitorServiceImpl` 1102 行、`FlowTaskEventListener` 1069 行、`FlowTaskNotifyListener` 1101 行。

### 新增结论

- 新增 A-18 至 A-22：巨型类安全边界、事件默认租户和条件 fail-open、流程执行租约/远程一致性、低代码发布 DDL/后置同步、流程监控/BPMN/事件镜像安全。
- 新增 S8/S9、T4.3-T4.6 和低代码/流程专项测试项；要求按领域拆分并保持原有 API、权限、租户、状态、逻辑删除、审计和幂等语义。
- `DynamicCrudRepository` 已存在动态租户条件实现线索，因此子表更新/删除的具体漏洞状态仍需结合完整 SQL、运行时数据源和并发测试确认；当前 Spec 将其列为必须显式收敛和验证的安全边界，不提前断言为已确认跨租户漏洞。
- `FlowTaskNotifyListener` 的 Webhook 已发现使用 `SecureOutboundClient` 场景策略，当前结论是补充重定向、私网、可信域名和幂等测试，不将其简单定性为已确认 SSRF。

### 本轮验证

- 未修改 Java、Vue、SQL、配置或测试业务代码，仅更新 `spec.md`、`tasks.md`、`test-spec.md`、`execution-log.md`。
- 尚未运行后端构建、定向测试、真实 Flowable/MySQL/Redis、发布 DDL 或生产配置验证；复杂度工具（PMD/CPD/ArchUnit/Sonar）尚未执行。

### 阻断与后续

- P0/P1 任务开始前必须先完成 T0.1/T0.2 的路由、资源、网关配置和测试运行时基线。
- 任何“配置待确认”项需结合真实环境和数据库资源表复核后才能关闭。
