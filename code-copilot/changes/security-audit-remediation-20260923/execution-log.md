# security-audit-remediation-20260923 执行记录

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
- 密码历史/过期策略、流程事件 event-id/Outbox 与补偿、低代码 DDL 发布 Outbox、CI SCA/SAST/SBOM 和 Playwright 恶意输入仍是后续任务；`tasks.md` 中保持未完成状态。
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
