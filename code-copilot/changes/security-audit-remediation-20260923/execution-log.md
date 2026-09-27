# security-audit-remediation-20260923 执行记录

## 2026-09-28：扩展设计、发布快照与应用回滚可信身份收口

### 实现

- 业务扩展 CRUD 和扩展版本草稿/回滚在编辑锁、实体查询和持久化前捕获一次可信租户与操作者；扩展实体、版本审计字段和应用变更标记共用该身份，不再回退租户/用户 `1`。
- `BusinessApplicationChangeTracker` 增加显式租户入口，发布与设计写链路可沿用已经校验的租户；会话入口缺少正数租户时在 Mapper 更新前 fail-closed。
- 应用发布快照准备先解析可信租户，并将同一租户用于应用、对象、扩展、流程、版本和绑定资产查询，避免一个快照跨越不同租户上下文。
- 应用回滚与恢复入口在读取运行单和执行兼容性检查前捕获租户/操作者快照，扩展版本恢复、流程绑定恢复和应用变更标记全程复用该快照。

### 验证

- `BusinessExtensionServiceTest` 5/5、`BusinessExtensionVersionServiceTest` 3/3、`BusinessApplicationRollbackContractTest` 2/2、`BusinessApplicationPhaseFiveSecurityTest` 5/5、`BusinessExtensionExecutionServiceTest` 8/8、`BusinessApplicationChangeTrackerTest` 2/2，共 25/25 通过。
- Generator 完整依赖反应堆 33/33 模块成功，`forge-plugin-generator` 1266/1266 测试通过，0 失败、0 错误、0 跳过。

### 未覆盖

- 未连接真实 MySQL/Flowable 或启动 Admin 执行发布快照与应用回滚 HTTP 端到端；T4.5 要求的 DDL Outbox、跨数据源补偿、死信与人工重放仍未完成。

## 2026-09-28：应用与流程发布写路径可信身份收口

### 实现

- 应用发布运行单的预留、认领、恢复和进度更新不再回退租户/用户 `1`；入口捕获一次租户与操作者快照，版本号预留、运行单归属和操作者审计复用同一身份，并在更新进度前校验运行单租户。
- 应用不可变版本提交在打印模板行锁、版本查询和状态指针切换前要求可信租户与操作者；版本实体、幂等重试和应用发布指针复用同一身份，详情查询也在应用读取前 fail-closed。
- 业务流程协调发布、独立发布和历史投影恢复统一使用一次 `PublishActor` 快照，流程版本 `publishedBy/createBy/updateBy/createDept`、投影更新及未选中流程清理不再重新猜测身份。
- 业务扩展编辑锁的获取、续期、校验和释放统一复用租户/用户/用户名快照；缺失身份时不生成或持久化锁，且不再把匿名调用伪装为 `system` 或用户 `1`。
- 应用发布启用扩展的查询同步改为可信租户 fail-closed；本轮不改变既有发布步骤、状态机和跨数据源事务语义。

### 验证

- `BusinessApplicationPublishRunServiceTest` 2/2、`BusinessApplicationVersionServiceTest` 3/3、`BusinessExtensionLockServiceTest` 3/3、`BusinessProcessPublishServiceTest` 9/9、`PrintApplicationPersistenceTest` 8/8，共 25/25 通过；覆盖副作用前拒绝、真实操作者归属、流程版本审计字段和并发事务线程身份传播。
- Generator 完整依赖反应堆 33/33 模块成功，`forge-plugin-generator` 1260/1260 测试通过，0 失败、0 错误、0 跳过。

### 未覆盖

- 未连接真实 MySQL/Flowable 或启动 Admin 执行应用发布、恢复、独立流程发布和扩展锁 HTTP 端到端；T4.5 要求的 DDL Outbox、跨数据源补偿、死信与人工重放仍未完成。

## 2026-09-28：业务流程入口与动作解析身份收口

### 实现

- `BusinessFlowService` 的直接启动、触发器启动和业务流程启动入口必须取得正数租户和发起人；缺失身份、非法显式身份或租户忽略作用域均在 Flowable 调用前 fail-closed，不再把租户 `1` 或字符串 `"null"` 当作运行身份。
- 流程绑定、流程定义和业务对象查询共用入口解析后的租户；后台线程仍可使用已隔离的 `TenantContextHolder`，但禁止在忽略租户的作用域内启动业务流程。
- `BusinessObjectActionService` 的已发布动作、草稿动作和权限摘要统一要求可信租户，避免无会话请求静默读取默认租户的动作元数据。

### 验证

- 流程入口、动作解析、业务键、子表字段控制、表单资产合并、生命周期和打印身份定向测试 38/38 通过；覆盖缺失租户、线程租户传递、非法显式租户、缺失发起人及忽略租户作用域拦截。
- Generator 完整依赖反应堆 33/33 模块成功，`forge-plugin-generator` 1255/1255 测试通过，0 失败、0 错误、0 跳过。

### 未覆盖

- 未连接真实 MySQL/Flowable 或启动 Admin 做流程端到端；定时触发流程若没有明确服务身份将按新规则拒绝，需要后续通过可信调度身份显式接入。

## 2026-09-28：服务端扩展执行身份收口

### 实现

- `BusinessExtensionExecutionService` 不再在缺失会话租户或执行用户时回退到 `1`；执行 Java 增强前必须取得正数可信身份，否则在处理器副作用之前拒绝。
- 单次扩展执行只捕获一份租户/用户快照，处理器上下文和执行审计共用该快照，避免中途重新解析导致归属不一致。
- `ServerBindingExecutor` 作为第二道防线，对显式扩展上下文与当前可信会话的租户、执行用户做一致性校验；缺失、非正数、跨租户或冒用其他用户均 fail-closed。

### 验证

- `BusinessExtensionExecutionServiceTest` 8/8、`ServerBindingExecutorTest` 7/7 通过；覆盖处理器不执行、无归属审计不落库、跨租户和执行用户冒用拦截。
- Generator 完整依赖反应堆 33/33 模块成功，`forge-plugin-generator` 1248/1248 测试通过，0 失败、0 错误、0 跳过。

### 未覆盖

- 未在真实 Admin/capability 网关下执行服务身份的 Java 增强端到端；本轮不改变扩展失败策略、处理器白名单和超时语义。

## 2026-09-28：T4.4 运行时流程动作可信身份收口

### 实现

- 运行时流程动作投影在入口只解析一次租户，缺失或非正数时在读取业务对象、发布版或草稿元数据前 fail-closed，不再静默落到租户 1。
- 发起流程和发送消息步骤统一从服务端投影的 `systemContext`、受控 capability 服务用户或真实会话解析执行人；三者都缺失时明确拒绝，不再伪装为平台用户 `1`。
- capability 执行上下文在无登录会话时将已校验的服务用户投影到系统上下文，确保后台调用与交互会话遵循同一身份规则。

### 验证

- 运行时动作投影、业务动作身份、动作编排和事件触发定向测试 22/22 通过；覆盖缺失租户、全链路单租户、会话用户、capability 服务用户、缺失执行人和消息副作用拦截。
- Generator 完整依赖反应堆 33/33 模块成功，`forge-plugin-generator` 1244/1244 测试通过，0 失败、0 错误、0 跳过。

### 未覆盖

- 未启动 Admin 或连接真实 MySQL/Flowable 执行 HTTP 与流程端到端验证；异步事件顺序号、Outbox 和 PENDING 崩溃恢复仍按 T4.4 未完成项继续处理。

## 2026-09-28：Generator 运行时契约与完整回归基线修复

### 实现

- 恢复业务对象流程摘要的既有协议：列表查询在 Mapper XML 内提取 `startNodeType`，不再把完整草稿 JSON 暴露给摘要 VO；运行时动作投影改用独立的租户/对象/启用状态查询批量读取草稿，消除逐流程回查。
- `LowcodeFieldSchema` 的三个派生判断方法标记为非 JSON 属性，修复 schema 序列化后因只读派生字段无法反序列化，进而导致 AI 初始化和权限配置装配失败的问题。
- 公式 LOOKUP 未命中或异常时，将配置的兜底值写入结果但不计入成功执行字段；修正 18 位身份证脱敏正则，避免合法号码未被掩码。
- 更新拆分后的前端代码生成契约路径和应用绑定/扩展版本测试夹具；数据审计捕获测试在每例开始前清理 JVM 级租户策略索引，消除测试类之间的同租户缓存污染。

### 验证

- 业务流程 Mapper/Service 定向测试 25/25，公式与脱敏 5/5，应用 AI 初始化/权限/扩展版本/绑定测试 13/13，数据审计捕获 8/8 通过。
- JDK 17 下在沙箱外执行 Generator 完整依赖反应堆：33/33 模块成功，`forge-plugin-generator` 1237/1237 测试通过，0 失败、0 错误、0 跳过；此前记录的 6 个失败和 4 个装配/协议错误全部关闭。
- 沙箱内完整测试曾仅因 `OkHttpSecureOutboundClientTest` 无权绑定本机临时端口中止；同一命令在沙箱外通过，不记录为代码失败。

### 未覆盖

- 未连接真实 MySQL 验证 `JSON_TABLE` 查询和租户拦截器组合执行计划，也未启动 Admin 做流程摘要/运行时动作的 HTTP 端到端验证；本轮以 Mapper 契约、单元测试和完整编译回归为证据。

## 2026-09-28：T4.4 低代码业务事件可信信封与幂等认领

### 实现

- 新增进程内可信事件信封，统一携带事件 ID、来源、协议版本和规范化载荷 SHA-256 摘要；动态 CRUD、流程回调和定时扫描三个生产端完成封装，发布器、触发器执行器和业务流程编排器在产生副作用前统一校验。
- 普通 CRUD 事件使用每次发生唯一的 ID；流程终态回调使用租户/流程实例/结果稳定键，定时事件使用租户/触发器/记录/日期/提醒档位稳定键，兼顾合法重复业务发生与上游重投去重。
- `ai_business_trigger_log` 新增信封字段及 `(tenant_id, trigger_id, event_id)` 唯一索引；触发器先以独立事务写 PENDING 认领记录，唯一键冲突直接跳过，执行结果再以 `id + tenantId` 显式更新。
- 业务流程事件幂等键改由 eventId 派生并写入 `source_event_id`；业务动作幂等键同步绑定 eventId。未知动作类型改为明确 FAILED，不再记录伪成功；执行状态统一使用枚举。
- `BusinessTriggerService` 缺少租户上下文时不再回退租户 1；迁移 `V1.0.191__add_business_event_envelope.sql` 先扩列、回填历史审计行，再建立唯一认领索引和非空约束。

### 验证

- 事件信封、来源/类型矩阵、发布、认领、重复投递、跨租户、篡改/过期版本、未知动作、WEBHOOK 和流程编排定向测试 36/36 通过；Generator 及 33 个依赖反应堆模块成功。
- Generator 完整回归当时运行 1236 个测试；本轮引起的旧 `BusinessTriggerServiceWebhookTest` 租户上下文失败已修复，其余 6 个失败、4 个既有装配/协议错误作为后续修复基线，现已在上方“Generator 运行时契约与完整回归基线修复”阶段全部关闭。
- Admin JDK 17 聚合编译 46/46 成功；`BusinessTriggerLogMapper.xml` 通过 `xmllint --noout`，Flyway 版本唯一、无 `${...}`/`tenant_id=0`，`git diff --check` 通过。

### 未覆盖

- 未连接真实 MySQL 执行 V1.0.191，也未进行多 JVM 并发投递、进程在 PENDING 认领后崩溃、Outbox 重放或业务聚合版本乱序验证。
- 当前唯一认领提供 at-most-once 执行门禁；PENDING 超时恢复、可靠 Outbox、事件顺序号、死信和人工重放仍保留在 T4.4/T4.6，不能宣称 exactly-once 或可靠投递闭环。

## 2026-09-28：T1.4 外部连接器租户边界与权限矩阵

### 实现

- 外部系统、外部 API 和调用日志的详情/删除改为显式 `id + tenantId` Mapper SQL；按系统列 API、按编码取 API 和代理运行时读取同步传入可信租户，不再只依赖 MyBatis 租户拦截器兜底。
- 外部 API 新增/修改在事务内验证所属系统属于当前租户，忽略客户端租户值并写入当前租户；跨租户记录在修改前置读取阶段统一按不存在拒绝。
- 管理 Controller 改为调用租户收口后的领域方法；系统/API/日志原有物理删除语义保持不变，仅增加显式租户条件，不扩大本轮到表结构或逻辑删除迁移。
- 外部调用权限守卫在匿名或无 Sa-Token Web 上下文时稳定 fail-closed 为 403，不再向上泄漏 `SaTokenContextException`。
- 权限契约覆盖四个 Controller 的全部公开端点，并验证高风险调试/日志清理资源仅限平台用户类型且不进入普通菜单角色自动授权集合。

### 验证

- 定向租户/权限/迁移/代理回归 16/16 通过；包含跨租户读写删除、匿名上下文、权限开关关闭、普通用户有/无权限、Controller 权限注解和 Flyway 授权边界。
- 外部插件完整依赖反应堆在沙箱外复跑成功：20/20 模块成功，`forge-plugin-external` 38/38 测试通过。首次沙箱内运行仅因 `OkHttpSecureOutboundClientTest` 的 MockWebServer 无权绑定本机临时端口而失败，未记为代码失败。
- `mvn -pl forge-admin-server -am -DskipTests compile`（JDK 17）：Admin 聚合反应堆 46/46 成功。
- `ExternalApiMapper.xml`、`ExternalSystemMapper.xml`、`ExternalApiLogMapper.xml` 均通过 `xmllint --noout`，`git diff --check` 通过。

### 未覆盖

- 未启动真实 Admin、未连接 MySQL，也未使用普通用户/租户管理员/平台管理员账号发起 HTTP 请求；T1.4 最后一项继续保持未完成，待预发执行真实角色与跨租户矩阵后关闭。

## 2026-09-28：A-03 API 权限资源覆盖启动门禁

### 实现

- 新增 `ApiPermissionCoverageVerifier`，在 `ApplicationRunner` 阶段读取 Spring MVC 全量 Controller 映射，展开一个 Handler 的所有路径和 HTTP 方法，并按 `METHOD path` 去重生成覆盖报告。
- 仅跳过显式 `@ApiPermissionIgnore`、`@SaIgnore`、既有认证/开放网关专用链路、静态/健康检查和配置白名单；受保护路由必须能匹配 `sys_resource` API 资源。
- 资源不存在和资源查询异常统一计入缺失；默认 `api-permission-coverage-fail-on-missing=true`，缺失时抛出启动异常，防止实例进入 ready。保留显式环境变量关闭阻断，仅用于受控灰度盘点，请求期仍由 `ApiPermissionInterceptor` fail-closed。
- 报告只记录方法、模板路径和 Handler 名，不记录用户权限、请求参数或数据库异常详情，并限制最多输出 100 条（可配置 1～1000）。
- 动态认证配置转换同步支持覆盖检查的启用、阻断和报告上限字段。

### 验证

- 测试先以缺失实现产生预期编译失败，完成实现后 `ApiPermissionCoverageVerifierTest` 3/3 通过，覆盖多路径/多方法、显式匿名、配置白名单、资源缺失、查询异常和 enforce/report 两种模式。
- 认证 Starter 全量依赖反应堆在沙箱外复跑成功：16/16 模块成功，`forge-starter-auth` 58/58 测试通过；首次沙箱内运行仅因 `OkHttpSecureOutboundClientTest` 的 MockWebServer 无权绑定临时端口而失败，未记为代码失败。
- `mvn -pl forge-admin-server -am -DskipTests compile`（JDK 17）：Admin 聚合反应堆 46/46 成功。

### 未覆盖

- 本地未连接真实 MySQL 启动 Admin，因此没有把某一目标环境的实际路由覆盖率或缺失资源清单记录为通过；部署时默认门禁会针对该环境数据库生成报告并阻止未覆盖实例进入 ready。

## 2026-09-28：A-13 密码历史、过期与认证边界闭环

### 实现

- `PasswordPolicyService` 在统一复杂度校验之外，新增当前密码与历史密码复用检查；历史窗口由 `historyCount` 控制并限制最大 24 条，改密、找回和管理员重置均在同一事务中留存被替换的单向哈希并裁剪窗口。
- 新增 `sys_user.password_changed_time` 和内部安全表 `sys_user_password_history`，通过 `V1.0.190__add_user_password_history.sql` 完成存量时间回填、非空约束、租户/用户/时间索引及防重复建表。超出策略窗口的哈希物理清理，以最小化凭据材料；表不提供业务查询或恢复接口。
- 密码登录与密码+验证码登录按 `expireDays` 判断凭据年龄，过期后复用既有 `forcePasswordChange` 门禁，只允许进入改密路径；第三方社交登录不套用密码过期策略。
- 新用户注册、后台创建和第三方自动建号写入初始密码变更时间；密码修改 SQL 同时更新版本、变更时间和强制改密状态。
- 找回查询与更新继续强制启用状态、未删除和目标租户/成员关系条件；显式选择非成员租户时统一返回用户名或密码错误，不泄露租户归属。

### 验证

- 定向测试：`CaptchaServiceImplTest` 25/25；密码策略、SQL/迁移契约、跨租户登录、找回及管理员重置 22/22，覆盖弱密码、当前/历史密码、90 天过期、禁用/删除/跨租户条件和验证码二次消费失败。
- System 依赖反应堆完整测试在沙箱外复跑成功：26/26 模块成功，`forge-plugin-system` 154/154 测试通过；首次沙箱内运行因 MockWebServer 无权绑定本机临时端口而失败，属于环境限制且未记录为代码通过。
- `mvn -pl forge-admin-server -am -DskipTests compile`（JDK 17）：Admin 聚合反应堆 46/46 成功。
- 两个 Mapper XML 均通过 `xmllint --noout`，`git diff --check` 通过。

### 未覆盖

- 未连接真实 MySQL 执行 Flyway，也未进行多实例并发改密或生产密码策略灰度；上线前需在预发备份后执行迁移，并验证存量账号首次登录/过期改密流程。

## 2026-09-28：T0.2 安全契约测试过期路径修复

### 实现与验证

- `ClientCredentialSurfaceContractTest` 不再读取已删除的 `forge-admin-server/sql/初始化脚本.sql`，只校验仓库实际维护的 `forge-server/db/全量初始化SQL.sql`。
- 使用显式 JDK 17 和本地 Byte Buddy agent 运行定向测试，`ClientCredentialSurfaceContractTest` 4/4 通过，26 个依赖反应堆模块全部成功。
- 仓库级 Maven Toolchain 与 CI JDK 固定尚未落地，因此 T0.2 继续保持部分完成状态，不把本机显式 `JAVA_HOME` 误记为全局构建门禁。

## 2026-09-28：A-06 幂等 Token Redis 故障 fail-closed

### 实现

- 保持 Redis Lua 的单次原子消费语义；对命令超时或主节点切换这类“服务端可能已消费、客户端未收到结果”的不确定状态，明确禁止应用层自动重试。
- `TokenRequiredStrategyHandler` 捕获 Redis `DataAccessException` 后统一 fail-closed，不调用业务委托，返回固定错误并要求客户端重新获取 Token。
- 故障日志仅记录 prefix 和异常类型，移除 Token 提取异常原文，不记录 Token 或 Redis 驱动错误详情。

### 验证

- `RedisTokenServiceTest` 12/12 通过：覆盖成功消费、已消费、空 Token、Redis 超时和节点切换；超时/切换用例均断言 Redis Lua 只调用一次。
- `TokenRequiredStrategyHandlerTest` 3/3 通过：覆盖原子消费成功、竞争失败和 Redis failover 结果不确定，后两者都不进入业务处理器。
- idempotent 模块及 5 个依赖模块编译成功；`git diff --check` 通过。

### 未覆盖

- 未连接真实 Redis Cluster/Sentinel 触发故障转移；本轮在服务边界模拟 `QueryTimeoutException` 与 `RedisConnectionFailureException`，并验证安全的单次调用和 fail-closed 语义。

## 2026-09-28：A-07 数据连接与 SQL 预览审计脱敏

### 实现

- 为数据集运行查询、已保存数据集预览、临时 SQL 预览、数据连接新增/修改/已保存测试/临时测试显式配置元数据审计，禁止 `OperationLog` 持久化请求体和响应数据。
- 数据连接测试日志移除 JDBC URL、用户名、密码和异常消息/堆栈，只保留租户、操作者、保存/临时范围、连接标识、结果和异常类型。
- 数据集预览日志只保留数据集/连接标识、类型、参数数量、返回行数、SQL SHA-256 摘要、结果和异常类型；不记录 SQL、参数值或预览数据。
- JDBC 异常返回改为固定诊断文案，避免驱动消息将密码、连接串或 SQL 反射到 API 响应及操作日志错误字段。

### 验证

- `DataConnectionControllerSecurityTest` 2、`DataDatasetControllerSecurityTest` 4、`DataQueryExecutorTest` 4：共 10/10 通过。
- 测试验证敏感端点的请求/响应不进入操作日志，且包含 `password=super-secret` 和完整 SQL 的伪造 JDBC 错误不返回原文。
- data 模块及 19 个依赖模块编译成功；Admin 聚合反应堆 46/46 编译成功；`git diff --check` 通过。

### 未覆盖

- 未连接真实 MySQL/外部数据源，未验证各 JDBC 驱动的所有错误文案；代码不再记录或返回驱动异常原文，因此默认 fail-closed。

## 2026-09-28：A-02 外部响应转换收敛为白名单字段映射

### 实现

- 修复管理页仍引导填写 JavaScript、运行时却必然被 `ScriptAdapter` 拒绝的前后端协议错配；界面、保存校验和运行时统一为 `FIELD_MAP_V1` 字段映射 JSON。
- `JsonPathAdapter` 改为版本化白名单解释器，仅支持受限源路径、目标字段映射和目标包装路径；禁止未知键、函数、通配符、括号语法和任意代码。
- 对配置长度、路径长度/深度、字段数、集合记录数、映射操作数和结果大小设置硬上限；旧脚本在保存和运行阶段均返回明确迁移错误。
- 外部 API 新增/修改审计禁止保存请求参数和响应原文；另记录租户、操作者、API 标识、协议版本、配置 SHA-256 摘要与结果，不记录映射原文。
- 保留数据库字段 `response_transform_script` 用于兼容存量表结构，其中仅允许存储新协议 JSON，无需修改表结构或执行数据迁移。

### 验证

- 外部模块定向测试 10/10 通过：覆盖允许字段映射、敏感字段不复制、可执行/未知/超长配置拒绝、操作数/记录数/结果大小上限、旧脚本保存和运行时拒绝、权限/审计注解契约。
- `forge-admin-ui` Vite 生产构建成功；`manage.vue` 新增部分 ESLint 通过，仅对存量 template ref 规则做定向禁用。
- `mvn -pl forge-admin-server -am -DskipTests compile`（JDK 17）：Admin 聚合反应堆 46/46 成功。
- `git diff --check` 通过。

### 未覆盖

- 未连接真实外部服务，未使用生产租户数据回放存量映射；上线前需将旧 JavaScript 配置人工改写为 `FIELD_MAP_V1`，否则设计为 fail-closed。
- `pnpm` 在非 TTY 环境提示清理依赖目录后中止，本轮未重装依赖；改为直接运行本地 Vite/ESLint 可执行文件完成同等验证。

## 2026-09-28：A-15 开放网关启动门禁与 A-10 数据集缓存隔离

### 实现

- capability identity/open-gateway 开启时增加双层启动门禁：配置层拒绝空值、长度不足、低熵、占位词和三组重复 pepper；数据层全局审计所有租户的活动 client/grant，发现保留 client code、无效认证模式、缺失签名密钥、SERVICE/HYBRID 身份未绑定有效用户/组织/组织角色、失效能力/版本或动作字段策略缺失时直接阻止启动。
- 启动数据检查通过独立 Mapper XML 完成，并显式忽略租户拦截器以覆盖全量租户；只在 identity 或 open-gateway 开启时装配，默认关闭部署不访问这些表。
- 保留既有 OAuth、HMAC、防重放、scope/RBAC、限流、幂等和高风险审批实现，并补齐开启模式的自动配置、动作适配、审批回调和版本化 KEK 回归。
- 为数据集运行时缓存增加 pageNum/pageSize 隔离用例，确认相同数据集与查询参数的不同页不会复用缓存项；生产缓存键实现无需修改。
- 修复 `SecureActionCatalogServiceTest` 缺失 `SecureActionDescriptor` import 导致的既有测试编译阻断，不改变生产行为。

### 验证

- identity 启动门禁、Mapper SQL 契约及 identity/open-gateway 自动配置测试：10/10 通过。
- capability platform 选定安全链路测试：45/45 通过；openapi replay guard：10/10 通过。
- capability actions 自动配置与网关适配测试：12/12 通过；高风险审批提交、回调和 KEK 加密测试：15/15 通过。
- 数据缓存与执行器测试：6/6 通过，包含不同 pageNum/pageSize 的显式隔离断言。
- Mapper XML 通过 `xmllint --noout`；`git diff --check` 通过。
- `mvn -pl forge-admin-server -am -DskipTests compile`（JDK 17）：Admin 聚合反应堆 46/46 成功。

### 未覆盖

- 未连接真实 MySQL/Redis，也未实际打开生产网关；数据库启动审计以 Mapper XML 契约测试和 Spring 自动配置测试验证，不宣称实库执行、网关压测或生产灰度通过。
- 生产开启仍必须提供正式环境配置、审计快照和人工审批；该任务保持未完成，回滚策略仍为关闭 open-gateway/identity/flow-actions。
- 一次从 capability 子模块直接执行 Maven 因沙箱禁止写入用户目录 `.m2/*.lastUpdated` 失败；改由仓库根反应堆运行后全部通过，此项记录为环境限制而非代码失败。

## 2026-09-23：创建整改规格

### 范围

- 根据当前工程只读源码审计，新增 `spec.md`、`tasks.md`、`test-spec.md`。
- 未修改 Java、Vue、SQL、配置或测试业务代码。
- 保留工作区既有未提交改动。

### 已有验证证据（复用基线，非本变更完成证据）

- 后端 JDK 17 编译通过。
- 前端生产构建通过：`pnpm --ignore-workspace build`。
- `forge-plugin-system` 当时定向测试 115 个中 114 个通过，1 个错误，原因为测试引用过期路径 `forge-admin-server/sql/初始化脚本.sql`；该引用已在 2026-09-28 修复并经完整 System 测试验证。
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
- 幂等 Token 使用 Redis Lua 原子消费并仅记录摘要；验证码原子消费，发送间隔原子占位且发送失败回滚；短信/邮件增加目标、来源 IP、设备和租户四维自然日配额、跨挑战失败窗口与短时锁定，Redis 异常时不调用发送器或继续校验。
- 分片上传补充绑定主体、数量/大小/TTL 上限；共享缓存保存会话和逐片状态，对外 uploadId 不暴露对象存储信息，完成阶段核对连续分片、ETag、总大小、MIME/扩展名；完成后默认私有并继承私有属性。下载/URL/Base64/bytes/字典转换统一走授权入口；`removeBatch` 按字符串 fileId 删除。
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
- `CaptchaServiceImplTest`：25 个通过，包含原子消费、发送间隔、日配额、失败阈值短锁和 Redis fail-closed 用例；`RedissonCacheServiceImplTest` 2 个通过，覆盖计数与首建 TTL 的单次 Lua 执行。
- `SystemAuthServiceImplClientCredentialTest` 7、`PasswordPolicyServiceTest` 1：共 8 个通过。
- 社会化登录相关定向测试：29 个通过；依赖树验证通过。
- 文件权限/分片相关定向测试：新增阶段测试 35 个通过；历史文件测试基线保持通过。
- 数据 SQL/预览相关定向测试：17 个通过。
- `forge-report-ui` 生产构建：通过；保留既有 Rollup 循环 chunk、CSS `:deep()` 和第三方 `lottie-web` eval 警告。
- `forge-admin-ui` 直接执行 package.json 对应 Vite 生产构建：通过；保留既有 Vite 配置、CSS 注释和动态导入提示。

### 环境阻断与未宣称完成项

- `SystemAuthServiceImplPasswordRecoveryTest` 最新 11 个用例在显式加载本地 Byte Buddy agent 后全部通过；未加载 agent 时 Mockito inline 仍会因当前 macOS/JDK 无法 self-attach，后续复跑必须保留相同 JVM 参数。
- `FlowModelServiceImplTest` 中 9 个 Mockito 用例同样因 Byte Buddy attach 失败；流程解析的 13 个非 Mockito 测试和完整编译均通过。
- 未连接真实 MySQL/Redis/对象存储/Flowable 服务，未执行 Flyway 实库迁移、Redis 故障切换、集群分片续传、真实跨租户接口矩阵或生产灰度。
- Flowable 镜像事件 event-id/Outbox 与补偿、低代码 DDL 发布 Outbox、CI SCA/SAST/SBOM 和 Playwright 恶意输入仍是后续任务；`tasks.md` 中保持未完成状态。
- T4.2/T4.3 巨型组件/巨型类改造按用户要求不处理，不作为本轮遗留缺陷。

## 2026-09-27：A-20 业务流程运行租约与 fencing

### 实现

- `ai_business_process_run` 新增单调 `execution_token`、lease owner/expiry 和 heartbeat 字段，并以 `V1.0.187__add_business_process_run_lease.sql` 提供防重复迁移和恢复扫描索引。
- `BusinessProcessOrchestrator` 只在创建/回调事务提交后认领 run；认领、续租、检查点推进、节点 attempt claim/complete 均校验租户、token、owner 和未过期租约。旧 worker 丢失租约后 fail-closed，不能覆盖接管者状态。
- 增加 Lease Guard 协调器：90 秒租约、20 秒心跳、双 daemon 心跳 worker 和 4 线程/256 队列的限界提交后执行池；后台执行与续租显式建立并恢复可信租户上下文，线程生命周期清理继承上下文。
- 节点副作用幂等键固定为 `runId:nodeId`，重试不再因 attemptNo 变化生成新副作用键；审批回调继续按明确 WAITING attemptId 原子完成。
- MySQL 更新语句的 `SET` 左值不使用表别名，避免不同 MySQL 版本对 `SET alias.column` 的兼容问题。

### 验证

- `git diff --check`：通过。
- 无 Mockito 定向测试：`BusinessProcessRunLeaseCoordinatorTest` 与 `BusinessProcessRunLeaseSqlContractTest` 共 3/3 通过。
- 显式加载本地 Byte Buddy agent 后执行 `BusinessProcessOrchestratorTest`、租约协调器测试和 SQL 契约测试：19/19 通过；33 个 Generator 依赖反应堆模块全部成功。
- `mvn -DskipTests -pl forge-admin-server -am compile`（JDK 17）：Admin 全依赖反应堆 46/46 成功。
- 一次默认 Mockito inline 复跑因当前 macOS/JDK 无法 self-attach 导致 16 个构造期错误；同一源码在显式 agent 模式全部通过，该失败记录为运行环境问题而非代码通过证据。

### 未覆盖

- 未连接真实 MySQL/Flowable，未执行 Flyway 实库迁移、双 JVM 崩溃接管、网络分区或远程 FlowClient 补偿。A-20 的 Outbox、重试/补偿和人工恢复记录仍保留为后续任务。
- T4.2/T4.3 巨型组件/巨型类改造继续按用户要求排除。

## 2026-09-27：A-12 验证码失败窗口与 Redis fail-closed

### 实现

- 新增独立 `CaptchaAttemptGuard`，按短信/邮箱目标摘要维护失败窗口和短时锁；默认 10 分钟内 5 次错误触发 10 分钟锁定，配置支持环境变量覆盖。
- 正确验证码清理历史失败状态；错误答案仍先消费当前挑战再累计失败，锁定期间发码和校验均直接拒绝，日志及缓存键不保存联系方式原文。
- 缓存层新增 `incrementWithExpiry`，以单次 Redis Lua 原子执行 `INCRBY` 和首建 `PEXPIRE`；自然日发送配额同步复用该原语，避免计数与 TTL 分步执行留下永久键。
- 发码间隔、配额或风控缓存异常统一返回失败且不调用短信/邮件发送器；校验缓存异常统一返回 false。

### 验证

- `CaptchaServiceImplTest`：25/25 通过，新增短信/邮箱阈值锁定、成功后清理失败状态、Redis 发码占位异常和锁查询异常 fail-closed 用例。
- `RedissonCacheServiceImplTest`：2/2 通过，验证失败计数使用单次 Lua 并拒绝非正 TTL。
- 命令：`mvn -pl forge-framework/forge-starter-parent/forge-starter-auth -am -Penable-tests -DskipTests=false -Dforge.tests.skip=false -Dsurefire.failIfNoSpecifiedTests=false -Djava.awt.headless=true -Dtest=CaptchaServiceImplTest test`（测试 JVM 显式加载本地 Byte Buddy agent）。
- 命令：`mvn -pl forge-framework/forge-starter-parent/forge-starter-cache -am -Penable-tests -DskipTests=false -Dforge.tests.skip=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=RedissonCacheServiceImplTest test`（测试 JVM 显式加载本地 Byte Buddy agent）。
- 命令：`mvn -pl forge-admin-server -am -DskipTests compile`，Admin 聚合反应堆 46/46 成功。
- `git diff --check`：通过；`CaptchaServiceImpl` 984 行，未突破 Java 单类 1000 行约束。
- 首次显式 Java agent 全量运行未设置 headless，图形验证码加载 AWT 时测试 JVM 退出码 134；增加 `-Djava.awt.headless=true` 后同一全量测试 25/25 通过。

### 未覆盖

- 未连接真实 Redis 集群，未执行节点切换、网络分区及 Lua 实库并发压测；本轮以 Lua 调用契约、服务行为单测和 Admin 聚合编译作为自动化证据。

## 2026-09-27：A-05 密码凭证版本与跨实例会话失效

### 实现

- `sys_user` 新增 `password_version BIGINT NOT NULL DEFAULT 0`，以 `V1.0.188__add_user_password_version.sql` 提供 `information_schema` 防重复迁移；用户改密、找回密码和管理员重置均在密码更新 SQL 内原子递增版本。
- 登录时把密码版本写入 `LoginUser` Token Session；Sa-Token 登录拦截器通过可扩展的 `LoginSessionValidator` 链调用系统插件校验器，逐请求读取数据库权威版本。该路径不依赖在线用户镜像或 Redis 版本缓存，跨实例更新和缓存丢失不会放行旧 Token。
- 版本不一致、用户禁用/删除、租户成员关系撤销或数据库读取异常均 fail-closed，返回登录凭证失效；迁移前无版本字段的 Session 按 0 兼容，首次改密后自然失效。
- 新增 `FORGE_AUTH_KEEP_CURRENT_SESSION_AFTER_PASSWORD_CHANGE`，Admin/App 显式配置且默认 false。开启时仅在数据库事务提交后更新当前 Session 的版本并排除当前 Token，其余会话仍吊销；读取或刷新新版本失败时退化为吊销全部会话。找回密码和管理员重置始终吊销全部会话。
- 改密和管理员重置日志记录目标用户、配置选择及实际是否保留当前会话，不记录密码或完整 Token。

### 验证

- `LoginSessionValidationServiceTest`：3/3 通过，覆盖缺失登录用户、责任链短路及 auth starter 独立运行。
- `PasswordVersionLoginSessionValidatorTest`：5/5 通过，覆盖版本一致、旧 Session 版本 0、多实例版本变化、用户不可用和数据库异常 fail-closed。
- `SysUserPasswordVersionContractTest`：1/1 通过，确认改密与管理员重置两条 SQL 均原子递增凭证版本。
- `SystemAuthServiceImplPasswordRecoveryTest`：11/11 通过，包含默认吊销当前会话、显式保留当前会话以及提交前不提前刷新 Session 的事务边界。
- `SysUserServiceImplPasswordResetTest`：1/1 通过，确认管理员重置走版本递增 Mapper 并踢出目标用户全部会话。
- 上述 System 插件定向测试合并运行 18/18 通过；`LoginSessionValidationServiceTest` 单独 3/3 通过。测试 JVM 显式加载 Byte Buddy agent 并启用 headless。
- `mvn -pl forge-admin-server -am -DskipTests compile`：Admin 全依赖反应堆 46/46 成功。
- `git diff --check`：通过。

### 未覆盖

- 未连接真实 MySQL/Redis，未执行 Flyway 实库迁移、两个真实应用实例间的旧 Token 回放或数据库故障注入；本轮以数据库权威版本设计、SQL 契约和服务级自动化测试作为证据。

## 2026-09-27：A-08 分片上传会话、校验与文件状态

### 实现

- 分片初始化现在强制签发总大小、总分片数、存储类型、MIME、用户、租户、业务主体、私有属性和 30 分钟 TTL；客户端只获得随机会话号，对象存储 provider uploadId、bucket 与 key 留在服务端。
- 新增 `MultipartUploadSessionStore` 策略：Admin 存在 Redis/Redisson 时使用共享缓存、独立分片键和分布式锁，缓存异常 fail-closed；无缓存的独立部署保留单节点兜底。RustFS/COS 支持主动 abort，本地存储将上下文持久化到临时目录，在共享挂载下可跨节点恢复。
- 每片上传校验序号、服务端收到的大小、累计总量和内容类型，并保存服务端返回的 ETag；完成时逐号核对连续性、客户端 ETag、逐片总大小、存储端最终大小、MIME 和扩展名。超时定时清理，合并异常终止 provider 上传并删除会话状态。
- 分片完成回填初始化时的文件名、业务主体、上传者和私有属性；字典文件 URL/名称转换改走 `FileManager` 授权入口，不再直接读取元数据表绕过私有文件权限。
- `sys_file_metadata.status` 明确为 `1=正常/0=已删除`：实体增加 `@TableLogic(value="1", delval="0")`，自定义查询/计数/重命名/软删除迁移到 Mapper XML 并显式过滤活动状态，新增 `V1.0.189__normalize_file_metadata_status.sql` 将空值/异常值 fail-closed 为已删除并固定非空默认值。

### 验证

- `FileManagerTest`、`LocalFileStorageTest`、`FileControllerPermissionContractTest`、`RedisMultipartUploadSessionStoreTest`：29/29 通过，覆盖主体绑定、服务端 ETag、缺片、累计配额、MIME 变化、过期 abort、共享会话跨节点续传、共享目录本地续传、Redis TTL/分布式锁及缓存异常 fail-closed。
- `SysFileMetadataStatusContractTest`：3/3 通过，验证实体逻辑删除注解、Mapper 活动态过滤/软删除和 Flyway 非空迁移。
- `SytemDictValueProviderTest`、`SytemDictValueProviderLegacyCacheTest`：3/3 通过，其中新增用例确认文件 URL/名称通过授权文件管理入口读取。
- `mvn -pl forge-framework/forge-plugin-parent/forge-plugin-system -am -DskipTests compile`：26/26 反应堆模块成功；`mvn -pl forge-admin-server -am -DskipTests compile`：46/46 反应堆模块成功。
- `FileManager` 862 行、`LocalFileStorage` 633 行，均未超过 Java 单类 1000 行规范。

### 未覆盖

- 未连接真实 Redis、RustFS、腾讯 COS 或 MySQL，未执行真实双 JVM 节点切换、对象存储 abort、Flyway 实库迁移及网络分区注入；本轮以共享 Store/共享目录双实例行为测试、Mapper/Flyway 契约和聚合编译作为自动化证据。
