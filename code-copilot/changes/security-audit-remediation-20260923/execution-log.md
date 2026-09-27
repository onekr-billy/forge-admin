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

## 2026-09-27：安全与正确性缺陷修复

### 范围与排除项

- 本轮按用户要求修复 `security-audit-remediation-20260923` 中的实际安全/正确性缺陷。
- T4.2 巨型前端组件拆分、T4.3 后端巨型类拆分明确排除；未为了完成度修改这些大型类的结构。
- 使用项目级 `forge-business-flow-development` Skill 约束流程/BPMN 修改，保持 Flowable 租户、发布、状态和表单协议边界。
- 保留用户工作区已有 `.DS_Store` 修改，未覆盖或纳入本轮变更。

### 已完成修复

- 报表前端移除主线程动态 JavaScript 与动态 HTML：数据表达式、URL 模板和事件动作改为受限表达式/结构化动作，旧脚本只读展示并可清除。
- 后端 `ScriptAdapter` 移除主 JVM 脚本引擎执行，旧脚本配置显式拒绝迁移；API 权限改为 fail-closed，隐藏资源参与鉴权。
- 外部系统/API/代理/日志接口增加明确权限与 fail-closed 守卫；新增 `V1.0.185__secure_external_api_permissions.sql`，外部 API 默认启用权限检查。
- open-gateway、flow-actions、identity 未配置时默认关闭；补充配置绑定验证。
- 临时 JDBC 增加管理员边界、协议/驱动/主机/端口策略、私网/元数据地址阻断、超时、只读与资源上限；`preview-sql` 绑定已保存数据集、ACL、只读和最大行数，新增 `V1.0.186__secure_dataset_sql_preview.sql`。
- 数据集分页改为 offset/limit 并执行独立 count；元数据查询改为参数绑定；SQL 预览改用 AST 单 SELECT 校验，标识符严格白名单。
- 登录不再修改 Sa-Token 全局配置；改密、找回和管理员重置后吊销旧会话。注册/改密/找回/管理员重置/第三方建用户统一使用 `PasswordPolicyService`，注册租户不再信任客户端 tenantId。
- 幂等 Token 使用 Redis Lua 原子消费并仅记录摘要；验证码原子消费，发送间隔原子占位且发送失败回滚；短信/邮件增加目标、来源 IP、设备和租户四维自然日配额，Redis 异常时不调用发送器。
- 分片上传补充绑定主体、数量/大小/TTL 上限，完成后默认私有并继承私有属性；下载/URL/Base64/bytes 统一授权；`removeBatch` 按字符串 fileId 删除。
- 低代码事件缺少可信租户时 fail-closed，未知操作符/格式错误条件不再命中。
- 业务流程节点执行改为携带创建时的明确 attemptId；claim 失败时禁止进入节点副作用，完成阶段只允许原子更新同一个 RUNNING attempt，不再查询并误写“最新 attempt”。
- Flow 监控服务统一 tenant filter 和业务实例归属检查；变量返回使用 fail-closed 白名单并只审计字段名。
- BPMN 解析使用关闭 DTD/外部实体的 DOM，process id 语义替换，不再依赖正则；预检覆盖单 process、引用完整性、开始/结束节点、执行节点白名单、网关默认分支与条件；原始 BPMN 不再写日志。
- `validateNoProcessData()` 迁移到 Mapper XML 并显式加入租户条件。
- JustAuth 升级到 1.16.7，fastjson 升级到 1.2.84；依赖树确认不再使用 1.2.83。

### 验证结果

- `git diff --check`：通过。
- 静态扫描：`forge-report-ui/src` 无 `new Function`/`AsyncFunction`/`eval(`/动态 `constructor`/`v-html`；Java 源码无 `engine.eval`/`ScriptEngineManager`；未发现 BPMN 原文截断日志。
- `mvn -DskipTests -pl forge-admin-server -am compile`（JDK 17）：46 个反应堆模块全部成功。
- Flow Server 反应堆编译（JDK 17）：38 个模块全部成功。
- `BpmnXmlUtilsTest` 3、`FlowModelBpmnPreflightTest` 6、`FlowMonitorVariableSanitizerTest` 2、`FlowBusinessStatsMapperSqlContractTest` 2：共 13 个通过。
- generator 事件租户/条件定向测试：20 个通过。
- `BusinessProcessOrchestratorTest`：14 个通过，包含 attempt claim 失败不执行副作用与禁止 latest-attempt 回写用例。
- `CaptchaServiceImplTest`：20 个通过，包含原子消费、发送间隔和日配额 fail-closed 用例。
- `SystemAuthServiceImplClientCredentialTest` 7、`PasswordPolicyServiceTest` 1：共 8 个通过。
- 社会化登录相关定向测试：29 个通过；依赖树验证通过。
- 文件权限/分片相关定向测试：27 个通过。
- 数据 SQL/预览相关定向测试：17 个通过。
- `forge-report-ui` 生产构建：通过；保留既有 Rollup 循环 chunk、CSS `:deep()` 和第三方 `lottie-web` eval 警告。
- `forge-admin-ui` 直接执行 package.json 对应 Vite 生产构建：通过；保留既有 Vite 配置、CSS 注释和动态导入提示。

### 环境阻断与未宣称完成项

- `SystemAuthServiceImplPasswordRecoveryTest` 本轮 9 个用例均因 Mockito inline/Byte Buddy 无法 self-attach 报错；这是测试运行时限制，不记录为代码通过。相同用例在本次会话早期曾全部通过，当前仍以最新运行的环境阻断为准。
- `FlowModelServiceImplTest` 中 9 个 Mockito 用例同样因 Byte Buddy attach 失败；流程解析的 13 个非 Mockito 测试和完整编译均通过。
- 未连接真实 MySQL/Redis/对象存储/Flowable 服务，未执行 Flyway 实库迁移、Redis 故障切换、集群分片续传、真实跨租户接口矩阵或生产灰度。
- 密码历史/过期策略、验证码失败次数锁定、流程事件 event-id/Outbox/租约 fencing、低代码 DDL 发布 Outbox、CI SCA/SAST/SBOM 和 Playwright 恶意输入仍是后续任务；`tasks.md` 中保持未完成状态。
- T4.2/T4.3 巨型组件/巨型类改造按用户要求不处理，不作为本轮遗留缺陷。
