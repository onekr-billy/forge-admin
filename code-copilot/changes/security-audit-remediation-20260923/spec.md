# Forge 安全审计问题整改 Spec

> 变更名：`security-audit-remediation-20260923`  
> 状态：`proposed`  
> 创建日期：2026-09-23  
> 依据：2026-09-23 当前工程只读源码审计  
> 关联变更：`enterprise-framework-hardening`（本 Spec 只补充本次审计确认的具体缺陷，不覆盖其已完成内容）

## 1. 背景与结论

本变更用于处理当前工程中已经通过源码定位到的安全漏洞、明显程序 BUG 和必须补充的底层能力。审计未修改业务代码；后端 JDK 17 编译和前端生产构建已通过，但定向测试仍有一项过期 SQL 路径错误，`pnpm audit` 因工具异常未完成。

本 Spec 的优先级定义如下：

- **P0**：可能造成任意代码执行、跨租户/跨用户数据泄露或生产环境无意开放能力，必须在上线前阻断。
- **P1**：高概率造成权限绕过、重复执行、内网访问、会话失效或敏感数据泄露，应优先修复。
- **P2**：确定的业务错误、数据一致性缺陷和安全边界缺口，应在下一发布周期完成。
- **P3**：架构债务、测试和依赖治理，作为持续质量门禁。

### 1.1 已确认问题清单

| 编号 | 风险 | 结论 | 主要源码位置 |
|---|---|---|---|
| A-01 | P0 | 报表 UI 通过 `new Function`/`AsyncFunction` 执行配置脚本，黑名单可绕过 | `forge-report-ui/src/hooks/useLifeHandler.hook.ts`、`src/api/http.ts`、图表事件/过滤器组件 |
| A-02 | P0/P1 | 外部 `ScriptAdapter` 在主 JVM `engine.eval`，没有沙箱与资源限制 | `forge-plugin-external/.../ScriptAdapter.java` |
| A-03 | P1 | API 权限资源未配置时拦截器 fail-open；隐藏 API 资源查询与用户权限列表不一致 | `forge-starter-auth/.../ApiPermissionInterceptor.java`、system Mapper |
| A-04 | P1 | 外部系统/API/代理/日志 Controller 没有统一硬权限，外部 API 默认配置可能关闭权限检查 | `forge-plugin-external/.../*Controller.java`、迁移/初始化 SQL |
| A-05 | P1 | 密码修改/重置后未统一吊销旧会话；登录时改 Sa-Token 全局配置导致并发串配置 | `SystemAuthServiceImpl.java` |
| A-06 | P1 | 幂等 Token 校验与消费分离，竞态下可重复执行业务；日志记录完整 Token | `RedisTokenService.java`、`TokenRequiredStrategyHandler.java` |
| A-07 | P1 | 临时 JDBC 测试可连接任意目标；`preview-sql` 未绑定数据集 ACL | data plugin `DataConnectionController`、`DataDatasetController` |
| A-08 | P1/P2 | 分片上传未绑定用户/租户和大小策略，完成后强制变为公共文件 | `FileController`、`FileManager`、`LocalFileStorage` |
| A-09 | P1/P2 | 文件内部读取绕过权限；文件元数据 `fileId` 批量删除使用错误主键类型 | `FileManager`、`SysFileMetadataServiceImpl` |
| A-10 | P1/P2 | 数据集分页忽略 `pageNum`，`total` 只返回当前页数量 | `DataQueryExecutor.java` |
| A-11 | P1/P2 | 元数据 SQL、表名/字段名拼接；SQL 安全检查依赖脆弱正则 | `MySqlDialect.java`、`SqlSafetyValidator.java`、query builder |
| A-12 | P1/P2 | 验证码校验/删除、发送限流均非原子；缺少统一 IP/设备/账号配额 | `CaptchaServiceImpl.java` |
| A-13 | P1/P2 | 注册直接信任客户端 `tenantId`，注册字段和密码策略不完整 | `AuthController`、`RegisterRequest`、`SystemAuthServiceImpl` |
| A-14 | P2 | 找回密码查询未明显过滤禁用用户，重置更新缺少状态/租户条件 | `SysUserMapper.xml`、`SystemAuthServiceImpl` |
| A-15 | P1（配置确认） | 开放网关配置默认 `true`，`flow-actions` 可能随默认链路开启 | `forge-admin-server/src/main/resources/application.yml` |
| A-16 | P2 | 同时存在旧版 `fastjson:1.2.83` 和 `fastjson2` | Maven 依赖树、JustAuth 依赖 |
| A-17 | P3 | 多个 Vue SFC 远超 800 行，状态耦合和回归风险高 | `forge-report-ui`、`forge-admin-ui` 巨型组件 |
| A-18 | P1/P2 | 低代码与流程后端巨型类把租户、权限、事务、动态 SQL、状态流转和外部副作用混在一个类中，安全边界无法独立验证 | `BusinessFlowService`、`DynamicCrudService`、`BusinessObjectDesignerService`、`LowcodeRuntimeConfigBuilder`、`FlowTaskServiceImpl`、`FlowTaskEventListener` 等 |
| A-19 | P1 | 低代码业务事件缺少可信租户时静默落到 `tenantId=1`；未知条件操作符按匹配成功处理，可能跨租户触发或意外启动流程 | `BusinessProcessOrchestrator`、`BusinessEventPublisher`、`BusinessTriggerExecutor` |
| A-20 | P1 | 业务流程 RUNNING 状态没有执行租约，节点 attempt 完成使用“最新 attempt”而非明确 attemptId；远程 Flowable 调用与本地事务无法原子提交 | `BusinessProcessOrchestrator`、`BusinessFlowService`、`FlowClient` 调用链 |
| A-21 | P1/P2 | 低代码发布在线 DDL、配置落库、菜单/应用入口后置同步没有可靠任务和补偿，失败只记日志会产生结构与配置不一致 | `LowcodePublishService`、`LowcodeDdlService`、`LowcodePublishPostProcessor` |
| A-22 | P1/P2 | 流程监控旧接口未统一绑定租户且变量未脱敏；BPMN 关键校验依赖正则/字符串替换；事件镜像失败可被吞掉 | `FlowMonitorServiceImpl`、`FlowModelServiceImpl`、`FlowTaskEventListener`、`FlowTaskNotifyListener` |

“配置确认”问题必须在真实部署配置、资源表和默认 client/grant 上线前完成核验；未确认不得标记为安全。

### 1.2 后端巨型类专项扫描结论

本轮对低代码、业务流程和 Flowable 相关服务/监听器做了只读扫描。以下行数按当前工作区源码统计，说明的是复杂度和安全边界风险，不等同于“行数超标就是漏洞”：

| 类 | 当前行数 | 混合职责/审查重点 |
|---|---:|---|
| `BusinessFlowService` | 7771 | 启动、审批、回调、撤回/重提、表单上下文、字段权限、业务状态回写、流程锁和资产元数据 |
| `DynamicCrudService` | 5995 | 动态查询/写入、主子表、唯一约束、编码、公式、字段安全、审计和流程字段 |
| `BusinessObjectDesignerService` | 4256 | 设计态保存、版本回滚、Schema/关系迁移、默认页面和运行时草稿 |
| `LowcodeRuntimeConfigBuilder` | 3132 | 搜索、列、编辑、API、树表、主子表、字典、脱敏、校验和布局协议 |
| `DynamicCrudRepository` | 2195 | 动态 SQL、数据源、租户/逻辑删除条件、主子表和批量写入底层拼装 |
| `LowcodePublishService` / `LowcodeDdlService` | 999 / 938 | 发布状态、在线 DDL、版本配置和运行时结构同步 |
| `BusinessProcessOrchestrator` / `BusinessProcessService` | 1073 / 974 | 业务流程运行、节点执行、发布版本和回调编排 |
| `BusinessActionExecutionService` / `BusinessTriggerExecutor` | 999 / 939 | 业务动作副作用、事件触发、条件匹配和目标对象读取 |
| `FlowTaskServiceImpl` | 3516 | 任务动作、状态机、候选人、表单、评论、批量操作和权限校验 |
| `FlowModelServiceImpl` | 1243 | 模型 CRUD、BPMN 修复/验证、部署、版本和启动配置 |
| `FlowMonitorServiceImpl` | 1102 | 流程/任务统计、实例详情、活动节点和流程变量读取 |
| `FlowTaskEventListener` | 1069 | Flowable 事件、任务镜像、业务状态、候选人、超时、表单状态和通知事件 |
| `FlowTaskNotifyListener` | 1101 | 站内信、短信、邮件、协同卡片、抄送、Redis/Webhook 和模板解析 |

这些类目前不是单纯的可读性问题：同一方法或调用链经常同时决定“谁能读写”“属于哪个租户”“是否已删除”“是否可重试”“远程动作是否成功”和“流程是否已完成”。因此本变更把拆分、状态机、Outbox、租户上下文和输入解析作为安全整改的一部分，而不是另起一个纯重构任务。

#### A-18 具体边界缺口

- `BusinessFlowService` 在本地 `@Transactional` 方法中直接调用远程 `FlowClient`，并同时负责回调、状态写回、审计、表单/资产和恢复；远程成功而本地回滚，或本地提交而远程结果不确定时，没有统一可恢复状态。
- `DynamicCrudService` 同时拼装运行时 Schema、动态 SQL、主子表写入、字段权限、数据权限、公式和审计。子表归属检查、逻辑删除和租户条件必须由独立安全组件集中保证；当前代码中 `deleteById`/`updateById` 及一次性归属查询需要结合 `DynamicCrudRepository` 的动态条件实现做确认，不能只依赖隐式拦截器。
- `BusinessObjectDesignerService` 和 `LowcodeRuntimeConfigBuilder` 将设计态协议、历史迁移和运行态配置共置，协议字段变化可能绕过发布校验直接影响线上 CRUD 权限和字段脱敏。
- `FlowTaskServiceImpl`、`FlowTaskEventListener` 和 `FlowTaskNotifyListener` 混合流程状态、副作用、镜像和通知；事件乱序、重复投递或部分失败时，现有日志不足以恢复准确状态。

拆分不能改变现有 REST 协议、Flowable 状态码、租户条件、字段权限、逻辑删除、审计和幂等语义；每个拆分后的子服务必须有独立的权限/租户/事务测试。

#### A-19 事件租户和条件 fail-closed

`BusinessProcessOrchestrator` 在事件没有 `tenantId` 时使用 `1L`，`BusinessEventPublisher`/`BusinessTriggerExecutor` 存在同类默认行为；事件条件匹配的未知操作符使用 `default -> true`。这会把上游上下文缺失、版本不兼容或恶意条件配置转化为“可以触发”。缺失租户必须拒绝或进入隔离失败队列，未知操作符必须拒绝并记录原因；只有明确的系统初始化流程才允许默认租户，并且不能复用业务事件入口。

#### A-20 流程执行和远程一致性

当前 run 只有 `PENDING -> RUNNING` 的状态竞争，进入 `RUNNING` 后缺少租约/心跳/fencing token；节点 attempt 的 claim 返回值未形成执行门禁，完成阶段按 `selectLatestAttempt` 取最新记录，存在并发完成错误 attempt 的可能。流程启动、审批、回调、撤回和重提还依赖远程 Flowable 调用，不能由本地数据库事务提供跨系统原子性。

应将流程动作建模为显式状态机和可恢复命令：run lease、attemptId 原子 claim/complete、幂等键、Outbox、超时接管、补偿和人工恢复均必须持久化。远程成功本地失败、本地成功远程超时、重复回调和乱序回调都必须有确定终态。

#### A-21 低代码发布一致性

`LowcodePublishService.publish()` 在本地事务中可能执行运行时数据源 DDL；DDL 成功后配置落库失败时，数据库结构不能随本地事务回滚。`LowcodePublishPostProcessor` 使用异步事务事件，失败只记录 `warn`，没有持久化重试、死信、对账或人工重放。

发布必须拆为预检、DDL 命令、发布状态、配置版本和后置同步几个可恢复阶段。每次任务记录 `requestId/schemaHash/configId/versionId/tenantId/dataSource/operator`，菜单和应用入口同步按幂等键执行，失败进入重试/死信，不得以日志代替可靠状态。

#### A-22 流程监控、BPMN 和事件镜像

- `FlowMonitorServiceImpl` 的旧统计、实例详情、活动节点和变量接口直接使用 Flowable Query，未统一绑定租户；即使当前 Controller 对部分新接口做了租户断言，服务层仍不是自洽安全边界。流程变量读取还需要白名单和敏感字段脱敏。
- `FlowModelServiceImpl` 的 process key、sequenceFlow 引用和结构校验依赖正则/字符串处理；应复用关闭 DTD/外部实体的安全 DOM/StAX 解析，避免单引号、CDATA、命名空间、属性重排、多 process 或嵌套节点导致误判/漏判。
- `validateNoProcessData()` 使用 Service 层 `LambdaQueryWrapper` 查询，需迁移到 Mapper XML，并显式带租户和逻辑删除条件。
- `FlowTaskEventListener` 的镜像/业务状态/候选人同步失败可能只记录日志而不阻止 Flowable 主流程；`FlowTaskNotifyListener` 使用 `fallbackExecution=true` 且通知失败无可靠 Outbox。事件必须具备唯一 id、顺序/版本、幂等副作用和补偿任务。
- BPMN 原文、流程变量和通知 URL 不得进入普通日志；URL 的 scheme 检查不能替代可信域名/协议策略，Webhook 出站仍需沿用 `SecureOutboundClient` 的场景 allowlist/private-network policy。

## 2. 目标

1. 消除主 JVM 和浏览器主页面对不可信脚本的直接执行能力。
2. 将认证、动态 API 权限、外部连接器、文件、数据集和开放网关统一改为默认拒绝、显式授权。
3. 建立会话吊销、原子幂等、验证码原子消费、密码策略和租户注册边界。
4. 修复文件、数据集分页、SQL 构造和元数据权限等确定性程序 BUG。
5. 建立临时 JDBC、SQL 预览、文件上传和动态网关的资源、网络和数据隔离边界。
6. 补齐回归测试、权限资源迁移、依赖治理和 CI 质量门禁，使同类问题不会重新出现。

## 3. 非目标

- 不在本变更中修改低代码业务功能、代码生成器行为或用户已有未提交改动。
- 不直接执行生产数据库迁移、凭据轮换、开放网关启用或外部系统调用；这些动作必须由部署负责人按人工审查和 Runbook 执行。
- 不以关键字黑名单继续扩展脚本能力；需要脚本时必须采用隔离执行或受限 DSL。
- 不把 `setReadOnly(true)`、前端隐藏按钮或菜单可见性当作权限边界。

## 4. 功能与安全需求

### S1 动态脚本与 HTML 安全（P0）

- 报表过滤器、事件处理器、URL 模板和数据转换不得在主页面通过 `new Function`、`Function` 或 `AsyncFunction` 执行。
- 首选方案是受限表达式/JSONPath/字段映射 DSL；若确需 JavaScript，必须运行在独立 Worker 或 sandbox iframe，使用最小 API 白名单、执行超时、脚本大小上限和调用次数上限。
- 报表脚本必须绑定租户、项目和操作者权限，脚本版本可审计，不能访问 Token、Cookie、DOM、网络、存储和宿主原型链。
- `v-html` 只允许经过严格 allowlist 清洗的内容；无法证明安全的内容按纯文本显示。
- 服务端 `ScriptAdapter` 不得在主 JVM 执行任意脚本。应改为白名单转换 DSL；过渡期若保留脚本，必须独立进程/容器隔离并设置 CPU、内存、时间和网络限制。

### S2 权限默认拒绝与外部连接器保护（P1）

- `ApiPermissionInterceptor` 在资源未配置、缓存异常、匹配失败时默认拒绝；匿名接口只能通过显式白名单放行。
- 启动或 CI 阶段扫描 Controller 路由与 `sys_resource`/API 配置的覆盖关系，缺失资源必须报警或阻止发布。
- 隐藏 API 也必须参与受控接口匹配；资源可见性不能改变权限校验语义。
- `ExternalSystemController`、`ExternalApiController`、`ExternalProxyController`、`ExternalApiLogController` 增加硬权限和平台管理员边界；`permissionCheckEnabled` 只能作为额外策略，不能替代 Controller 权限。
- 补充 `/external/api/**`、`/external/proxy/**`、`/external/api/log/**` 的 Flyway 资源和角色授权策略，默认不自动授予普通角色。
- 外部 API 的权限检查默认开启；外部配置、凭据、脚本和调试代理均记录审计，不返回密钥原文。

### S3 会话、密码、幂等和验证码（P1）

- 修改密码、找回密码、管理员重置和安全策略触发时，统一吊销旧 Token/会话；支持密码版本号作为多实例兜底。
- 登录使用 `SaLoginModel` 的单次配置，不修改 `SaManager` 全局配置；不同 client 的 Token TTL、并发和共享策略互不串扰。
- 幂等 Token 使用 Redis Lua 或等价原子操作完成校验和消费；失败日志只记录摘要，禁止输出完整 Token。
- 图形、短信、邮箱验证码验证和删除必须原子完成；发送间隔使用 `SET NX`/Lua；增加 IP、设备、账号、租户和日配额限流及失败次数锁定。
- 注册必须校验确认密码、账号格式、联系方式、租户存在且启用；不得直接信任任意客户端 `tenantId`，公开注册须采用默认租户或邀请码/注册码策略。
- 统一 `PasswordPolicyService` 覆盖注册、修改、找回、管理员重置和第三方自动建用户；落地复杂度、历史密码、过期和首次登录改密。
- 找回密码只允许启用且未删除用户，并在更新语句中带租户、状态和删除条件。

### S4 文件安全与元数据一致性（P1/P2）

- 分片上传会话绑定 `tenantId/userId/businessType/businessId`，签发总大小、分片数、TTL 和存储类型；Redis/对象存储保存会话，支持集群续传。
- 每片及合并阶段校验大小、数量、连续性、ETag、真实 MIME、扩展名和病毒/压缩包风险；超额上传立即终止并清理临时数据。
- 分片完成必须继承初始化时的 `isPrivate`，默认私有；不能因存储实现固定写入 `false`。
- `getFileBytes`、Base64、下载、访问 URL、导出和 AI 消费统一经过同一个读取授权函数；私有文件必须检查租户、上传者/业务主体和过期时间。
- `fileId` 接口和业务列表必须过滤租户、状态和私有文件权限；批量删除按字符串 `fileId` 查询，不得调用 Long 主键 `getById`。
- 明确 `sys_file_metadata` 的逻辑删除字段语义；实体、Mapper、查询和 Flyway 必须一致，避免仅依赖未使用的 `@TableLogic` import。

### S5 数据连接、SQL 和数据集运行时（P1/P2）

- 临时 JDBC 仅限平台管理员；驱动类、协议、主机、端口和数据库目标采用白名单，默认禁止环回、私网、云元数据地址和文件协议；固定测试 SQL 为 `SELECT 1`。
- 临时连接设置连接/读取/执行超时、最大连接数、响应大小和并发限制，使用后立即关闭数据源。
- `preview-sql` 必须绑定已保存数据集和当前用户的管理/查询权限；禁止只凭 connectionId 执行任意 SQL。预览仍执行行权限、列权限和脱敏。
- 元数据查询全部参数化；schema、table、column 和 field name 使用严格标识符校验及正确转义。
- SQL 安全检查使用 AST 或数据库只读账号，只允许单条 SELECT；禁止多语句、写操作、锁、延时函数、文件函数、用户变量、系统敏感表和未授权 `UNION`。
- 数据集分页必须生成正确 offset，`total` 使用独立 count 或明确标记为当前页数量；页码、页大小有上限。

### S6 开放网关默认关闭和身份边界（P1，需线上确认）

- `open-gateway`、`flow-actions`、`identity` 默认关闭，生产必须通过环境变量显式开启。
- 启动时拒绝默认 client/grant、空 token pepper、弱密钥和无组织/租户绑定的 SERVICE 身份。
- 开放网关必须继续校验 OAuth/HMAC、scope、RBAC、租户、组织、幂等、防重放、限流和高风险确认；不得因关闭普通 API 权限而绕过业务权限。
- 变更前后记录 capability、client、grant 和高风险动作的审计快照，支持回滚到关闭状态。

### S7 依赖、测试和架构治理（P2/P3）

- 升级或排除 `fastjson:1.2.83`，确认 JustAuth 兼容版本；CI 运行 SCA 并阻止高危依赖。
- 修复定向测试引用的过期 SQL 路径，统一 JDK 17 Maven Toolchain，禁止 JDK 8 误运行 Java 17 测试。
- 为本 Spec 的权限、租户、并发、SQL、文件和默认配置增加回归测试；安全测试失败不得通过静态扫描绕过。
- 将超过 800 行的 Vue SFC 按面板/业务域拆分，跨层状态迁移到 Pinia；拆分期间保持路由、权限和数据协议兼容。
- 前端构建警告、无效动态 import、超大 chunk 和 CSS 非法注释纳入 CI warning budget。

### S8 后端巨型类拆分与安全边界保持（P2/P3）

- 低代码/流程 Service、Listener 和 Builder 按单一业务子域拆分，目标是每个类不超过 800 行；禁止通过无意义的转发类规避复杂度门禁。
- 优先形成 `BusinessFlowStartService`、`BusinessFlowCallbackService`、`BusinessFlowStatusSyncService`、`DynamicCrudQueryService`、`DynamicCrudWriteService`、`DynamicMasterDetailService`、`DynamicFieldSecurityService`、`Lowcode*SchemaBuilder`、`FlowTaskMirrorSyncHandler`、`FlowTaskNotificationService` 等边界；具体命名可按现有包结构调整。
- 拆分前建立 REST/API、权限、租户、逻辑删除、字段权限、状态码、审计、幂等和事件行为快照；拆分后逐项回归，不能以“重构”名义改变安全语义。
- 动态字段和标识符必须从已发布运行时 Schema 白名单解析；主子表读写必须同时满足租户、逻辑删除、主表归属和字段权限；远程调用不能把本地 `@Transactional` 当作跨系统一致性保证。
- 增加 ArchUnit/PMD/CPD 或等价复杂度门禁，同时保留人工评审，避免把核心权限逻辑散落到不可追踪的微小类中。

### S9 流程和低代码运行态一致性（P1/P2）

- 流程启动、审批、回调、撤回、重提、业务状态回写和通知采用显式状态机；同一动作在重复请求下最多成功一次。
- 事件必须携带可信租户、来源、版本和幂等键；缺失租户拒绝或隔离，未知条件操作符 fail-closed，不能默认使用租户 1 或匹配成功。
- run 和 node attempt 使用租约/fencing token；claim、complete、retry 和 timeout recovery 均按明确 attemptId 原子更新，重复/乱序回调不能改变已确定终态。
- Flowable 镜像、表单实例、业务单据、菜单/入口和运行配置支持可靠事件、Outbox、重试、死信、对账和人工重放；远程成功本地失败、本地成功远程超时必须可恢复。
- 流程监控和变量读取始终绑定租户与资源授权，变量按白名单和敏感等级脱敏；BPMN 使用安全 XML 解析，禁止通过日志输出原文。

## 5. 数据库与迁移要求

1. 外部 API、代理、日志权限资源使用新的 Flyway 版本脚本，所有 `INSERT` 明确列名并 `NOT EXISTS` 防重复，`tenant_id=1`。
2. 若引入上传会话、验证码风控、密码历史、密码版本、幂等状态或审计表，必须提供逻辑删除/TTL/索引设计、回滚说明和租户索引。
3. 禁止修改已执行的 Flyway 版本；当前最高版本为 `V1.0.184`，新脚本版本必须递增。
4. 任何迁移先做 `information_schema` 防重复检查，先扩展结构再切换代码；回滚不删除审计证据。

## 6. 验收标准

### P0/P1 阻断条件

- 不能通过报表配置执行 `window`、网络、存储、DOM 或原型链访问；主 JVM 不执行外部配置脚本。
- 未配置 API 权限、权限缓存异常、外部资源缺失时请求被拒绝；外部系统/API/代理/日志普通用户无法调用。
- 密码修改/重置后旧 Token 立即失效；并发登录不会改变其他用户/client 的会话策略。
- 同一幂等 Token、验证码和发送间隔在并发请求下最多成功一次。
- 临时 JDBC 无法访问未允许的主机/端口，`preview-sql` 无法绕过数据集 ACL。
- 私有文件不会被跨用户/跨租户读取，分片完成保持私有属性；批量删除能正确按 `fileId` 工作。
- 开放网关默认关闭，线上开启必须有显式配置和人工审查记录。

### P2/P3 验收

- 第二页数据与第一页不同，`total` 与 count 一致；非法标识符和危险 SQL 均拒绝。
- 禁用用户不能找回密码，注册不能伪造租户，密码策略覆盖所有写入入口。
- 无旧版高危 fastjson 运行时依赖；JDK 17 测试路径和 SQL 初始化路径一致。
- 目标模块测试、前端构建、权限矩阵、租户隔离、并发和回滚验证均有命令及结果记录。

## 7. 风险、灰度与回滚

- 脚本禁用可能影响既有报表：先盘点脚本，提供 DSL 迁移工具和按租户灰度开关；禁止回滚到无沙箱执行。
- 默认拒绝可能暴露遗漏资源：先生成路由覆盖报告，补齐资源后切 enforce；生产不允许长期 observe。
- 文件会话从内存迁移到 Redis/对象存储时保留旧上传 TTL，未完成上下文按过期清理，不恢复已删除文件。
- SQL AST/只读账号可能拒绝历史 SQL：提供诊断错误和迁移清单，不能通过放开写权限回滚。
- 密码、会话、网关和权限属于人工审查项；真实环境上线前必须完成灰度、回滚演练和安全负责人签字。

## 8. 实施顺序

1. P0：动态脚本隔离、API 权限 fail-closed、外部接口硬权限、开放网关默认关闭。
2. P1：密码会话、幂等、验证码、临时 JDBC、`preview-sql`、文件私有权限。
3. P2：数据集分页、SQL AST/标识符、注册/密码策略、文件元数据逻辑删除、依赖清理。
4. P3：测试门禁、SCA、JDK/SQL 路径统一、巨型前端组件拆分。

## 9. 明确未完成项

本文件只创建整改规格，不代表任何问题已修复。现有 `enterprise-framework-hardening` 中已经完成的请求体限制、认证响应缓存控制、部分验证码挑战、私有下载缓存头和普通 API 限流等能力，应在执行本 Spec 时复用并按本次审计要求补充缺口，不得重复实现或把旧验证记录当成本 Spec 的完成证据。
