# 安全审计问题整改增量测试计划

## 1. 当前基线

- 本文件对应 `security-audit-remediation-20260923`。截至 2026-09-27，P0/P1 中能够通过源码和模块测试闭环的缺陷已进入实现与验证阶段。
- 按用户要求，T4.2 巨型前端组件拆分和 T4.3 后端巨型类拆分不属于本轮修复范围；相关任务保留但不计入本轮完成度。
- 后端统一使用 JDK 17；Admin 依赖反应堆 46 个模块编译通过，Flow Server 依赖反应堆 38 个模块编译通过。
- `forge-admin-ui` 与 `forge-report-ui` 生产构建通过。Admin 的 `pnpm --ignore-workspace build` 因 pnpm 非 TTY 依赖目录确认而中止，随后直接执行 package.json 中同一 Vite 构建命令通过；没有删除或重装现有依赖。
- 本机 Mockito inline/Byte Buddy 在部分测试中无法 self-attach；这类结果按环境阻断记录，不视为代码通过，也不覆盖此前同测试已通过的证据。
- `pnpm audit --json` 曾因工具异常 `reference.startsWith is not a function` 未完成，不能作为安全结论。
- `ClientCredentialSurfaceContractTest` 的历史失效 SQL 路径已改为仓库实际初始化脚本，显式 JDK 17 下 4/4 通过；仓库级 Maven Toolchain/CI 固定仍未完成。

## 1.1 2026-09-27 本轮实际证据

- 静态扫描通过：`forge-report-ui/src` 无 `new Function`、`AsyncFunction`、`eval(`、动态 `constructor` 和 `v-html`；Java 源码无 `engine.eval`/`ScriptEngineManager`；无 BPMN 原文 preview/substring 日志。
- JDK 17 Admin 全依赖编译通过：`mvn -DskipTests -pl forge-admin-server -am compile`，46/46 模块成功。
- JDK 17 Flow Server 全依赖编译通过；BPMN/流程监控 13 个非 Mockito 定向测试通过。
- 密码策略与客户端登录配置已通过定向与完整模块回归；密码找回测试使用显式 Byte Buddy agent 运行，当前 11/11 通过。
- 数据 SQL/预览、外部接口权限、API fail-closed、幂等、验证码、文件访问、事件租户/条件和社会化登录均已执行对应模块定向测试，详见 `execution-log.md`。
- `git diff --check` 通过；用户已有 `.DS_Store` 修改未触碰、未纳入本变更。

## 1.2 2026-09-27 A-20 租约/fencing 增量验证计划

- 范围：`ai_business_process_run` 租约字段、run claim/heartbeat/fencing CAS、节点 attempt 的租约约束、事务提交后执行和稳定副作用幂等键。
- 先执行 `git diff --check`、Flyway 版本/占位符/防重复静态检查和 Mapper XML 契约测试。
- 定向执行 `BusinessProcessOrchestratorTest` 与 `BusinessProcessRunLeaseSqlContractTest`，覆盖 run claim 失败、续租失败、节点 claim 失败和明确 attemptId 完成。
- 使用 JDK 17 编译 `forge-plugin-generator` 及其依赖；共享流程运行实体、Mapper 或装配失败即阻断本阶段提交。
- 本地未连接真实 MySQL/Flowable，不把 Flyway 实库执行、多节点故障切换和远程调用补偿记录为通过；Outbox 仍单独保留为后续任务。

### 实际结果

- `BusinessProcessOrchestratorTest` 16、`BusinessProcessRunLeaseCoordinatorTest` 2、`BusinessProcessRunLeaseSqlContractTest` 1：共 19 个通过；覆盖认领/续租失败关闭、明确 attemptId、提交后独立线程派发、心跳租户上下文恢复和 SQL fencing 条件。
- 默认 Mockito inline 在一次复跑中因 Byte Buddy 无法 self-attach 环境阻断；使用本地同版本 `byte-buddy-agent` 显式加载后，19 个用例全部通过。另以无 Mockito 方式独立执行租约协调器与 SQL 契约测试，3/3 通过。
- Generator 及 33 个依赖反应堆模块在上述测试命令中编译成功；Admin 全依赖 JDK 17 编译 46/46 成功。真实 MySQL 迁移、多 JVM 租约接管和 Flowable 远程补偿仍未执行，不据此宣称通过。

## 1.3 2026-09-28 开放网关启动门禁与分页缓存增量验证

- 风险范围：identity/open-gateway 开启时的密钥强度、默认 client/grant、SERVICE/HYBRID 身份租户/用户/组织绑定，以及数据集分页缓存串页。
- 配置门禁：空 pepper、长度不足、低字符多样性、常见占位值和三组相同 pepper 均必须阻止启动；默认关闭时不创建身份服务和数据审计 runner。
- 数据门禁：全租户活动 client/grant 中存在保留 client code、无效 auth mode、签名密钥缺失、服务身份绑定失效、能力/有效版本失效或动作字段策略缺失时必须阻止启动。
- 安全链路回归：OAuth/HMAC 验证、防重放、scope/RBAC、限流、幂等、业务/流程动作适配、高风险提交/回调及版本化 KEK 测试必须通过。
- 缓存隔离：相同 datasetId、查询参数和用户上下文下，pageNum 或 pageSize 任一变化都必须生成不同缓存项。
- 实际结果：启动门禁 10/10、platform 安全链路 45/45、replay guard 10/10、actions 12/12、高风险 15/15、数据缓存/执行器 6/6 通过；Admin 聚合编译 46/46 成功。
- 环境限制：没有真实 MySQL/Redis/开放网关，未执行生产 client/grant 实库扫描、网关压测和生产启用审批；这些结果不得替代上线人工门禁。

## 1.4 2026-09-28 外部响应字段映射增量验证

- 协议范围：仅允许版本化 `FIELD_MAP_V1` JSON 中的 `sourcePath`、`fieldMapping`、`targetPath`；路径只允许安全字段名和源数组数字下标，不提供函数、递归、通配符、网络或任意代码能力。
- 边界验证：配置最大 16 KiB、路径最大 256 字符/16 层、映射字段最大 128、集合最大 10,000 条、映射操作最大 250,000 次、输出最大 2 MiB；超限统一 fail-closed。
- 迁移验证：旧 JavaScript/未知配置在保存前和运行时均明确拒绝，不回退到主 JVM 执行；管理页只展示字段映射协议。
- 审计验证：新增/修改禁止 `OperationLog` 保存请求与响应，只记录操作、租户、操作者、API 标识、协议版本、SHA-256 摘要和结果。
- 实际结果：`JsonPathAdapterTest`、`ExternalApiControllerTransformValidationTest`、`ExternalControllerPermissionContractTest`、`ExternalProxyServiceImplTest` 共 10/10 通过；Admin 聚合编译 46/46 成功。
- 前端结果：`manage.vue` 新增部分 ESLint 通过（保留存量 template ref 规则例外），Vite 生产构建成功；只有仓库已有 CSS 注释和动态导入告警。

## 1.5 2026-09-28 数据连接与 SQL 预览审计脱敏验证

- `DataDatasetRuntimeController.query`、已保存数据集预览、临时 SQL 预览、数据连接新增/修改/测试必须显式配置 `OperationLog` 不保存请求参数和响应结果。
- 运行查询日志只记录 datasetId、类型、分页、字段数、参数键和 SQL SHA-256 摘要；预览日志只记录操作者/租户、数据集/连接标识、参数数量、行数、SQL 摘要、结果和异常类型。
- 连接与预览失败不得记录 JDBC URL、用户名、密码、完整 SQL 或 JDBC 异常消息；返回客户端的错误也使用固定诊断文案。
- 实际结果：`DataConnectionControllerSecurityTest`、`DataDatasetControllerSecurityTest`、`DataQueryExecutorTest` 共 10/10 通过，包含携带伪造密码和 SQL 的 JDBC 异常不返回原文用例；Admin 聚合编译 46/46 成功。

## 1.6 2026-09-28 幂等 Token Redis 故障边界验证

- 确定性返回：Lua 返回 `1` 时只允许一个请求进入业务，返回 `0` 表示 Token 不存在、过期或已消费，重复请求不进入委托处理器。
- 不确定性返回：Redis 命令超时或主节点切换时，Lua 可能已消费但客户端未收到结果；应用层不得自动重试这个不可幂等的判定调用。
- 故障时必须统一 fail-closed，不执行业务委托，不记录完整 Token，并返回“重新获取 Token 后重试”的固定错误。
- 实际结果：`RedisTokenServiceTest` 12、`TokenRequiredStrategyHandlerTest` 3，共 15/15 通过；显式验证 timeout/failover 各只执行一次 Redis Lua，第二个预置“成功”结果不会被读取。

## 1.7 2026-09-28 密码历史与过期策略增量验证

- 复杂度与复用：弱密码、当前密码和 `historyCount` 窗口内的历史哈希必须拒绝；新密码通过后旧哈希与密码更新处于同一事务，历史窗口最多保留 24 条。
- 过期与首次改密：密码认证按 `expireDays` 将过期凭据标记为 `forcePasswordChange`，复用既有 API 门禁；社交登录不因不可交互随机凭据过期而被误拦截。
- 租户与状态：手机号/邮箱找回必须过滤禁用、删除账号并校验目标租户或启用成员关系；显式选择非成员租户不能泄露账号存在性。
- 数据迁移：`V1.0.190__add_user_password_history.sql` 回填并固化 `password_changed_time`，历史表仅存单向哈希且按租户/用户/时间建立索引，窗口外哈希物理清理。
- 实际结果：认证验证码 25/25、System 定向 22/22；System 依赖反应堆 26/26 模块成功且插件完整测试 154/154，Admin 聚合编译 46/46 成功。未执行真实 MySQL Flyway 与多实例并发改密。

## 1.8 2026-09-28 API 权限资源覆盖启动门禁

- 扫描范围：从 Spring MVC `RequestMappingHandlerMapping` 读取全部 Controller 映射，逐一展开一个 Handler 上的多路径与多 HTTP 方法，并按 `METHOD path` 去重；不再只检查第一个路径或第一个方法。
- 豁免范围：仅跳过 `@ApiPermissionIgnore`、`@SaIgnore`、既有认证/开放网关专用链路和显式 `apiPermissionExcludePaths`；框架 `/error` 与静态/健康检查不作为业务权限资源。
- 故障语义：资源不存在和资源查询异常都计入未覆盖；默认在 `ApplicationRunner` 阶段抛出异常，阻止应用进入 ready。`FORGE_AUTH_API_PERMISSION_COVERAGE_FAIL_ON_MISSING=false` 只用于受控灰度盘点，不改变请求期 fail-closed。
- 日志约束：报告只输出 HTTP 方法、模板路径和 Handler 名，不输出用户权限、请求参数或数据库异常详情；样本数量限制为 1～1000。
- 实际结果：`ApiPermissionCoverageVerifierTest` 3/3，认证 Starter 依赖反应堆 16/16 模块成功且 `forge-starter-auth` 58/58 测试通过；首次沙箱内全量测试仅因 MockWebServer 无权绑定本机端口失败，沙箱外同命令复跑通过。Admin 聚合编译 46/46 成功。
- 环境限制：未连接真实 MySQL 启动 Admin，尚未生成目标环境的实际缺失路由清单；默认门禁会在部署启动时阻止资源不完整或数据库查询失败的实例进入 ready。

## 1.9 2026-09-28 外部连接器租户与权限矩阵

- 租户边界：外部系统、外部 API、调用日志的详情和删除必须把可信 `tenantId` 传入 Mapper XML；按系统列 API、按编码取 API 和代理运行时读取同样不能只依赖隐式租户拦截器。
- 写入边界：外部 API 新增/修改必须验证目标系统属于当前租户，并覆盖客户端提交的 `tenantId`；修改其他租户记录必须在读取阶段返回“不存在”，不得进入更新。
- 权限矩阵：四个 Controller 的全部 20 个公开方法必须声明明确权限；普通用户只有携带目标调用权限才可执行，匿名/无上下文、缺少权限、缺少权限码或 `permissionCheckEnabled=false` 均 fail-closed。
- 平台边界：`external:proxy:debug` 与 `external:log:clear` 的资源保持 `min_user_type=1`，不得进入既有普通菜单角色的自动授权集合；常规管理资源保持租户 1、`NOT EXISTS` 和 `min_user_type=2` 契约。
- 实际结果：定向矩阵 16/16 通过；外部模块完整依赖反应堆 20/20 模块成功、`forge-plugin-external` 38/38 测试通过；Admin 聚合编译 46/46 成功。三个 Mapper XML 通过 `xmllint --noout`。
- 环境限制：沙箱内完整测试因 MockWebServer 无权绑定临时端口失败，沙箱外同命令复跑通过；未启动真实 Admin 或连接 MySQL，因此不把浏览器/HTTP 多角色权限矩阵和实库租户隔离写成通过。

## 1.10 2026-09-28 低代码业务事件可信信封与副作用认领

- 信封边界：动态 CRUD、流程回调和定时扫描事件必须携带可信租户、白名单来源、协议版本、稳定事件 ID 和规范化载荷摘要；消费者在查询触发器或启动流程前校验完整信封，载荷、租户或版本被改写时 fail-closed。
- 事件身份：普通 CRUD 每次真实发生生成新的事件 ID，避免相同载荷在不同时间被永久误去重；流程回调按租户/流程实例/结果、定时事件按租户/触发器/记录/日期/提醒档位生成稳定上游键，重投保持同一 ID。
- 副作用认领：触发器执行先以 `REQUIRES_NEW` 写入 PENDING 日志，数据库唯一键 `(tenant_id, trigger_id, event_id)` 作为执行门禁；重复事件不再进入流程、消息、写记录、字段更新、外部调用或业务动作。业务流程 run 的事件幂等键改由 eventId 派生并保留 `source_event_id`。
- 回归范围：来源/版本/摘要、载荷与租户篡改、跨租户触发器、重复投递、未知动作类型、流程 run 幂等键、Mapper/Flyway 契约及旧 WEBHOOK 保存路径。
- 实际结果：本轮相关测试 36/36 通过；Generator 完整回归当时运行 1236 个测试，剩余 6 个失败和 4 个装配/协议错误作为后续基线，现已在 1.11 阶段全部修复。另有 1 个旧触发器测试因本轮移除默认租户而失败并已修正。Admin 聚合编译 46/46 成功。
- 环境限制：未执行真实 MySQL Flyway、事务提交后崩溃恢复或多节点重复投递；PENDING 认领后的 Outbox、超时恢复/人工重放和业务事件顺序号仍属后续任务，不能据此宣称 exactly-once。

## 1.11 2026-09-28 Generator 运行时契约回归

- 流程摘要协议：列表只返回 `startNodeType` 等摘要字段，不返回草稿 JSON；运行时动作编译通过独立 Mapper 查询获取租户内启用草稿，避免 N+1 和跨租户读取。
- Schema 往返：`LowcodeFieldSchema` 序列化后必须可反序列化，派生判断方法不得形成无 setter 的协议字段；AI 初始化和应用权限装配必须通过。
- 公式边界：LOOKUP 未命中/异常返回配置兜底值，同时保留错误且不计入成功字段；合法 18 位身份证必须脱敏。
- 测试隔离：数据审计 JVM 级租户策略索引不得污染后续测试类的首个用例；每例显式建立干净租户缓存。
- 实际结果：业务流程 Mapper/Service 25/25、公式与脱敏 5/5、应用相关 13/13、数据审计捕获 8/8；Generator 完整依赖反应堆 33/33 模块成功，`forge-plugin-generator` 1237/1237，0 失败、0 错误、0 跳过。
- 环境限制：沙箱内完整运行因 MockWebServer 无权绑定临时端口中止，沙箱外同命令复跑通过；未连接真实 MySQL 或启动 Admin，不将实库查询计划和 HTTP 端到端记为通过。

## 1.12 2026-09-28 运行时流程动作可信身份

- 租户边界：流程动作投影必须在任何 Mapper 读取前取得正数租户，同一次投影的业务对象、发布版、草稿和应用查询必须使用同一个可信租户。
- 执行人边界：流程启动与消息默认接收人只允许服务端 `systemContext`、已校验 capability 服务用户或真实会话用户；缺失时不进入流程/消息副作用，不能回退到用户 `1`。
- 能力身份：无交互会话的 capability 请求必须将已校验的服务用户投影到系统上下文，后续步骤不再各自猜测执行人。
- 实际结果：`BusinessProcessRuntimeActionProjectionServiceTest`、`BusinessActionTrustedActorTest`、`BusinessActionExecutionServiceTest`、`BusinessTriggerExecutorEventTest` 共 22/22 通过；Generator 完整依赖反应堆 33/33 成功，`forge-plugin-generator` 1244/1244，0 失败、0 错误、0 跳过。
- 环境限制：未启动 Admin、MySQL 或 Flowable 端到端；此阶段不代表 Outbox、乱序回调或远程成功本地失败场景已闭环。

## 1.13 2026-09-28 服务端扩展可信身份

- 入口身份：执行白名单 Java 增强前必须同时取得正数租户和执行用户；缺失或异常不得回退到平台租户/用户 `1`。
- 快照一致性：处理器 `ExtensionExecutionContext` 和 `AiBusinessExtensionExecutionLog` 必须使用同一次解析的身份快照，执行期间不再重新读取会话猜测归属。
- 防御性校验：执行器必须拒绝显式上下文与可信会话租户不同，以及显式执行用户与会话用户不同的请求。
- 实际结果：`BusinessExtensionExecutionServiceTest` 8/8、`ServerBindingExecutorTest` 7/7；Generator 完整依赖反应堆 33/33 成功，`forge-plugin-generator` 1248/1248，0 失败、0 错误、0 跳过。
- 环境限制：未使用真实 SERVICE 身份经 Admin/capability 网关执行 Java 增强，不代表网关配置和生产身份数据已验收。

## 1.14 2026-09-28 业务流程入口与动作解析可信身份

- 流程租户：直接启动、触发器启动和业务流程启动必须在任何绑定/定义查询或 Flowable 调用前取得正数租户；租户忽略作用域不得启动业务流程，缺失时不得回退租户 `1`。
- 流程发起人：所有启动入口必须取得正数发起人，禁止把缺失用户转换为 `"null"` 或回退平台用户；拒绝必须发生在流程副作用前。
- 动作元数据：已发布动作、草稿动作和权限摘要必须使用可信租户，缺失租户时不得读取默认租户的对象或动作配置。
- 实际结果：相关流程入口及回归测试 38/38；Generator 完整依赖反应堆 33/33 成功，`forge-plugin-generator` 1255/1255，0 失败、0 错误、0 跳过。
- 环境限制：未连接真实 MySQL/Flowable 或启动 Admin；调度器在缺失可信执行人时将拒绝启动流程，真实服务身份接入仍需端到端验收。

## 1.15 2026-09-28 应用与流程发布写路径可信身份

- 发布运行单：预留、认领和恢复必须同时取得正数租户与操作者；一次调用内的应用行锁、幂等查询、版本号计算、运行单写入及重试审计必须使用同一身份快照。
- 应用版本：打印资产校验、不可变版本查询/插入和应用发布指针切换必须绑定同一可信租户与操作者；无身份时不得先读取应用或获取版本行锁。
- 流程版本：协调发布、独立发布和历史投影恢复必须记录真实 `publishedBy/createBy/updateBy`，并使用同一租户更新投影及清理未选中流程；缺失身份时不得读取发布对象。
- 扩展锁：获取、续期、校验和释放必须绑定当前租户与用户；匿名请求不得以租户/用户 `1` 或 `system` 获取编辑锁。
- 实际结果：上述 5 个测试类共 25/25；Generator 完整依赖反应堆 33/33 成功，`forge-plugin-generator` 1260/1260，0 失败、0 错误、0 跳过。
- 环境限制：未启动 Admin、MySQL 或 Flowable 做端到端发布/恢复；DDL Outbox、跨数据源失败补偿和人工重放仍按 T4.5 保持未完成。

## 1.16 2026-09-28 扩展设计、发布快照与应用回滚可信身份

- 扩展设计：扩展 CRUD 和版本草稿/回滚必须在锁校验、实体读取或持久化前取得同一份正数租户与操作者快照；缺失身份时不得回退租户/用户 `1`。
- 变更标记：应用和对象变更跟踪支持显式传入已经捕获的租户，避免写链路中重新读取会话导致跨线程归属漂移；会话入口缺失租户时在 Mapper 更新前 fail-closed。
- 发布快照：准备应用发布快照前先取得可信租户，应用、对象、扩展、流程、版本和绑定查询全程使用同一租户，不再静默读取默认租户资产。
- 应用回滚：回滚与恢复入口必须在运行单查询、兼容性检查和资产恢复前取得同一租户/操作者快照；扩展版本和流程绑定恢复不能跨租户读取或写入。
- 实际结果：上述 6 个测试类共 25/25；Generator 完整依赖反应堆 33/33 成功，`forge-plugin-generator` 1266/1266，0 失败、0 错误、0 跳过。
- 环境限制：未启动 Admin、MySQL 或 Flowable 执行真实发布快照和应用回滚；DDL Outbox、跨数据源失败补偿和人工重放仍按 T4.5 保持未完成。

## 1.17 2026-09-28 业务对象设计与发布租户隔离

- 对象服务：列表、详情、编码校验、运行入口、创建/修改/删除和孤儿回收必须取得正数可信租户；按 ID 读取改为显式租户 Mapper 条件，同一写调用复用捕获的租户。
- 设计上下文：加载和保存必须校验当前租户与对象实体归属一致；关系、草稿、设计版本回滚和应用变更标记使用同一租户，跨租户预加载上下文 fail-closed。
- 预览缓存：缓存键从单独 `objectId` 改为 `(tenantId, objectId)`，并在缓存读取前校验租户，避免知道对象 ID 后命中其他租户的 8 秒预览缓存。
- 版本与发布：设计版本写入和对象发布必须同时取得租户与操作者；缺失身份在对象/版本读取、状态更新和低代码发布副作用前拒绝。
- 实际结果：对象设计相关回归 76/76；新增/调整的核心身份用例 11/11；Generator 完整依赖反应堆 33/33 成功，`forge-plugin-generator` 1273/1273，0 失败、0 错误、0 跳过。
- 环境限制：未启动 Admin 或连接真实 MySQL 执行跨租户 HTTP、缓存并发和在线 DDL 发布；T4.5 的发布任务/Outbox 与补偿状态机仍未完成。

## 1.18 2026-09-28 应用运行时与发布检查租户边界

- 运行时缓存：按代码、门户地址、应用 ID 和工作台读取必须在应用查询或缓存命中前取得正数可信租户；单次调用使用同一租户构造发布版本缓存键和应用 ID 软缓存键，缺失身份不得回退租户 `1`。
- 发布资产：对象、入口、扩展和流程资产选择在任何资产读取前校验租户，四类查询复用同一租户快照，避免一次选择跨越不同身份上下文。
- 发布就绪：应用发布上下文、资产选择、流程依赖解析和业务绑定查询必须处于同一可信租户边界；缺失身份在应用读取前 fail-closed。
- 运行配置叠加：有效配置请求在入口和对象归属解析前校验租户；入口 Mapper 不得以默认租户读取，空渲染配置或空 configKey 仍保持无副作用返回。
- 实际结果：四个既有测试类和新增身份安全测试共 32/32；Generator 完整依赖反应堆 33/33 成功，`forge-plugin-generator` 1277/1277，0 失败、0 错误、0 跳过。
- 环境限制：未启动 Admin 或连接真实 MySQL 执行门户、工作台、发布检查和运行配置叠加 HTTP 跨租户矩阵；本轮以副作用前拒绝、Mapper 交互和完整模块回归作为自动化证据。

## 1.19 2026-09-28 应用目录与对象关系租户边界

- 应用与入口：业务应用创建、入口详情/打开、应用入口发布和快照恢复必须在依赖读取前取得正数可信租户；同一次调用的应用校验、入口查询和实体写入复用同一租户，不得回退租户 `1`。
- 应用对象：对象编排列表、配置键检查、替换、孤儿解绑和反向应用查询必须在应用/对象读取前校验租户；关联实体和逻辑删除使用入口捕获的租户。
- 权限摘要：权限概览必须在数据范围和绑定查询前校验租户；按对象 ID 生成文档动作摘要时必须使用显式租户 Mapper 条件，批量权限查询复用同一租户。
- 对象关系：关系列表、保存和删除必须在解析业务对象前取得可信租户，并将该租户贯穿关系查重、实体归属、缺失项删除和按 ID 查询。
- 自动化证据：应用目录与相邻应用发布/权限定向回归 57/57 通过；Generator 完整测试 1283/1283 通过，依赖反应堆 33/33 模块成功。
- 环境限制：未启动 Admin 或连接真实 MySQL 执行应用目录、入口、权限摘要和对象关系 HTTP 跨租户矩阵；本轮以 Mapper 副作用前拒绝、显式租户条件和完整模块回归作为自动化证据。

## 1.20 2026-09-28 应用入口、能力挂接与业务套件租户边界

- 入口打开：按入口 ID 打开和运行态打开信息构建必须先取得正数可信租户；入口与运行配置查询复用同一租户，缺失身份不得读取默认租户入口或配置。
- 能力挂接：列表、新增、修改、删除和批量保存必须在目标服务或 Mapper 访问前校验租户；查重、实体归属、按 ID 查询及批量删除使用入口捕获的同一租户。
- 业务套件：分页、列表、详情、汇总、创建、更新、状态修改、删除和树查询均要求可信租户；套件按 ID 读取必须走包含 `tenant_id`、`del_flag` 和 `id` 条件的显式 XML SQL。
- 套件验收：在套件、对象和就绪度读取前校验租户，验收链路中的套件与对象查询使用同一租户快照。
- 自动化证据：入口、绑定、套件及相邻应用目录定向回归 48/48 通过；Generator 完整测试 1289/1289 通过，依赖反应堆 33/33 模块成功。
- 环境限制：未启动 Admin 或连接真实 MySQL 执行入口、能力挂接、套件管理和套件验收 HTTP 跨租户矩阵；本轮以副作用前拒绝、显式租户 SQL 契约和完整模块回归作为自动化证据。

## 1.21 2026-09-28 运行态业务数据租户边界

- 记录选择与元数据：记录查询必须在对象和动态数据访问前取得正数可信租户；字段标签与字段类型解析校验外部传入对象的租户归属，跨租户对象在运行配置读取前拒绝。
- 数量台账：余额、流水和锁定记录查询，以及入库、锁定、释放、提交和调拨写操作均要求可信租户，并在一次调用内复用捕获的租户，不得回退租户 `1`。
- 对象就绪度：就绪度检查在依赖读取前校验租户，对象按 ID 读取必须使用显式租户 Mapper 条件，并将同一租户传给后续检查。
- 引擎汇总：业务引擎统计在绑定数据读取前 fail-closed，缺失租户上下文不得查询默认租户数据。
- 自动化证据：首轮运行态数据定向回归 28/28 通过；补充元数据跨租户保护后聚焦回归 17/17 通过；Generator 完整测试 1296/1296 通过，依赖反应堆 33/33 模块成功。
- 环境限制：未启动 Admin 或连接真实 MySQL 执行记录选择、数量台账、对象就绪度和引擎汇总 HTTP 跨租户矩阵，也未执行真实并发锁竞争；本轮以副作用前拒绝、显式租户 Mapper 交互和完整模块回归作为自动化证据。

## 1.22 2026-09-28 业务单据、流程变量与关联运行时租户边界

- 单据配置与编号：配置读取、保存、字段校验、主流程绑定和历史绑定同步必须复用一次捕获的可信租户；编号序列键缺少正数租户时必须在申请序列前拒绝，不得落入租户 `1` 的共享序列。
- 单据运行时：单条和批量运行态查询必须在配置、记录、流程链接及流程运行单读取前取得可信租户，后续配置键、对象和流程查询沿用该租户。
- 流程变量：候选变量解析必须在流程模型、业务对象和运行配置读取前校验租户；对象字段目录查询使用入口捕获的租户，异常处理不得吞掉缺失租户错误。
- 对象关联：源对象按 ID 读取必须使用显式租户 Mapper；启用且已发布的目标运行配置必须保持可打开，只有停用状态才能返回“目标运行配置已停用”。
- 自动化证据：新增身份与状态回归 6/6，连同单据编号和单据运行时定向测试共 31/31；Generator 完整测试 1302/1302 通过，依赖反应堆 33/33 模块成功。
- 环境限制：未启动 Admin 或连接真实 MySQL/Flowable 执行单据配置、单号并发、流程变量和对象关联 HTTP 跨租户矩阵；本轮以副作用前拒绝、显式租户 Mapper 交互、状态分支和完整模块回归作为自动化证据。

## 1.23 2026-09-28 业务元数据与发布校验租户边界

- 元数据服务：业务字段模板、流程应用配置和应用初始化必须在对象、流程、模板及模型访问前取得正数可信租户；同一次同步或保存复用该租户，不得回退租户 `1`。
- 表映射与数据库同步：映射详情、差异预览及托管数据库同步必须先校验当前租户，并验证发布上下文中的业务对象归属当前租户；统计查询和后续 DDL 编排使用入口捕获的租户。
- 发布校验：设计态与部署态校验在应用入口、数据源、关系、触发器和单据配置读取前要求可信租户；外部传入发布上下文的对象租户不一致时必须在 Mapper 或 DDL 交互前拒绝。
- 自动化证据：新增 `BusinessMetadataIdentitySecurityTest` 7/7，连同表映射和数据库同步测试定向回归 26/26；Generator 完整测试 1309/1309 通过，依赖反应堆 33/33 模块成功。
- 环境限制：未启动 Admin 或连接真实 MySQL 执行字段模板、流程应用配置、初始化同步、表映射及发布校验 HTTP 跨租户矩阵，也未执行真实在线 DDL；本轮以副作用前拒绝、上下文租户一致性和完整模块回归作为自动化证据。

## 1.24 2026-09-28 查询方案、消息收件人与动态导入导出租户边界

- 查询方案：列表、详情、新增、修改和删除必须同时取得正数可信租户与用户，并在单次操作内复用身份快照；默认方案清理、实体归属和用户范围查询不得回退租户 `1`。
- 消息收件人：非内置消息通道查询和角色/组织收件人解析必须使用可信租户；调用方显式传入的租户必须与当前可信上下文一致，跨租户请求在 Mapper 访问前拒绝。
- 动态导入导出：运行配置、系统参数、模板字典、导出任务和导入写入访问前必须验证租户；导出任务复用租户/用户快照，异步任务读取必须同时匹配租户、用户和任务 ID，禁止仅按主键加载。
- 自动化证据：新增 `LowcodeServiceIdentitySecurityTest` 5/5，覆盖缺失身份副作用前拒绝、显式跨租户消息收件人拒绝和异步导出归属查询；Generator 完整测试 1314/1314 通过，依赖反应堆 33/33 模块成功。
- 环境限制：未启动 Admin 或连接真实 MySQL/文件存储执行查询方案、消息通道、Excel 同步/异步导出及批量导入 HTTP 跨租户矩阵；本轮以 Mapper 交互、异步任务归属条件和完整模块回归作为自动化证据。

## 1.25 2026-09-28 低代码设计、生成与发布租户边界

- 元数据边界：业务领域、数据模型和应用草稿在查询、校验或写入前必须获取正数可信租户；按 ID 读取的实体必须再次校验归属，不得从实体或默认租户 `1` 推导执行身份。
- 代码生成：预览、下载、历史版本和生成选项必须使用当前租户，显式配置的 `tenantId` 与当前上下文不一致时在生成器和 Mapper 交互前 fail-closed。
- 发布链路：发布、回滚、版本号分配和版本快照共用入口捕获的租户；事务后异步事件必须显式携带租户，校验与配置归属一致后再恢复 `TenantContextHolder` 作用域。
- 实际结果：新增 `LowcodeMetadataIdentitySecurityTest` 8/8；Generator 完整依赖反应堆 33/33 模块成功，`forge-plugin-generator` 1322/1322，0 失败、0 错误、0 跳过。生成器低代码服务中本轮扫描模式命中的默认租户回退已清零。
- 环境限制：未启动 Admin 或连接真实 MySQL/业务数据源执行领域、模型、代码生成和发布/回滚 HTTP 跨租户验收；Flow Server 业务对象运行适配器的默认租户回退保留为下一批修复。

## 1.26 2026-09-28 Flow 运行入口可信身份与显式租户 SQL

- 身份边界：流程运行入口和业务对象落表在元数据读取或业务写入前必须取得正数会话租户与用户；线程租户与会话租户同时存在时必须一致。
- 防伪造：`startUserId` 和 `startDeptId` 只能作为对可信会话身份的一致性断言，不再作为缺失会话时的执行人回退；跨用户、跨组织或跨租户入口在查询和落表前拒绝。
- 显式 SQL：流程入口、字段映射、表单版本和批次明细在 `@IgnoreTenant` Controller 调用链下使用显式 `tenant_id = #{tenantId}` 条件，不假设 MyBatis-Plus 自动租户拦截仍生效。
- 实际结果：`FlowRuntimeIdentitySecurityTest` 6/6、`FlowBusinessObjectRuntimeAdapterIdentityTest` 3/3，四个 Mapper XML 结构校验通过；Flow Server 主代码依赖反应堆 38/38 模块编译成功。
- 回归基线：`forge-plugin-flow` 完整运行 185 个测试，其中 2 个与本批修改文件无关的基线失败位于用户组源码契约与动态数组必填校验；`forge-flow-server` 完整运行 46 个测试，其中 2 个与本批修改文件无关的基线失败位于流程监控源码契约。四个失败均未命中本批修改类，不记为完整模块通过。
- 后续范围：`FlowFormServiceImpl`、`FlowFillBatchServiceImpl`、`FlowInstanceServiceImpl` 和 `FlowRecordParticipantServiceImpl` 的默认租户 `1` 回退已在 1.27 批次收口。

## 1.27 2026-09-28 Flow 表单、填报、实例与参与人租户边界

- 表单管理：分页、启用列表、详情、编码查重、复制、发布、版本和字段目录读取必须先取得正数会话租户；发布还必须取得正数操作者，客户端实体租户不得覆盖可信租户。
- 填报批次：批次分页、更新、发布、删除与明细查询必须使用同一可信租户；`@IgnoreTenant` 控制器下的表单、版本、批次和明细 Mapper 必须显式包含 `tenant_id = #{tenantId}`。
- 实例启动：会话租户与线程租户同时存在时必须一致；无会话后台任务只能使用非忽略作用域中显式建立的正数租户，缺失租户或发起人必须在 Flowable 查询/启动前拒绝。
- 参与人索引：监听器和服务调用必须显式传递业务租户，空租户不得回退平台租户；填报明细行锁只能使用带租户条件的查询。
- 实际结果：新增 `FlowMetadataTenantBoundaryTest` 11/11；连同相关身份、实例、参与人和行锁测试共 24/24 通过，四个 Mapper XML 校验通过，Flow Server 主代码依赖反应堆 38/38 模块编译成功。
- 回归基线：`forge-plugin-flow` 完整运行 196 个测试，剩余 2 个与本批修改文件无关的失败位于用户组运行时解析源码契约和动态数组新增行必填校验；`forge-flow-server` 完整运行 46 个测试，剩余 2 个与本批修改文件无关的失败位于流程监控源码契约。不记为完整模块通过。
- 环境限制：未连接真实 MySQL/Flowable 或启动 Flow Server 执行表单、批次、实例与参与人路径的 HTTP 跨租户矩阵；四个回归基线失败留待后续批次修复。

## 1.28 2026-09-28 Flow 动态数组必填与拆分职责契约回归

- 动态数组必填：Schema 声明的必填字段是基础约束，字段权限只能追加必填要求，不能用缺省或显式 `required=false` 削弱 Schema；新增行缺少基础必填字段必须拒绝。
- 用户组可见性：`FlowAccessGuard` 通过 `FlowCandidateMembershipResolver` 统一取得角色、组织和自定义用户组；契约测试同时验证 Guard 委托与 Resolver 调用 `FlowUserGroupService` 的完整链路，避免要求 Guard 重复实现解析逻辑。
- 流程监控：实例分页继续委托 `FlowMonitorViewAssembler` 完成可选用户显示名解析和查询异常降级；源码契约跟随职责组件验证 `ObjectProvider` 可选桥接、稳定日志与插件隔离。
- 实际结果：定向测试分别为插件 9/9、Flow Server 28/28；`forge-plugin-flow` 完整测试 196/196，`forge-flow-server` 完整测试 46/46，均为 0 失败、0 错误、0 跳过；此前四个回归基线失败全部清零。
- 环境限制：本轮未连接真实 MySQL/Flowable 或启动 Flow Server 执行动态表单任务提交与监控分页 HTTP 验收；以校验器行为测试、委托链源码契约和两个模块完整测试作为自动化证据。

## 1.29 2026-09-28 Flow 通知提交边界与 Webhook 日志脱敏

- 提交边界：`FlowTaskNotifyListener` 只允许在 `AFTER_COMMIT` 阶段消费，`fallbackExecution` 必须保持 false；无事务事件不能直接触发站内信、协同卡片、Redis 或 Webhook 外部副作用。
- 日志边界：Webhook 日志目标只保留小写的 scheme、host 和显式 port，不得包含 userinfo、path、query 或 fragment；失败日志只记录异常类型，不记录可能携带完整 URL 的异常消息与堆栈。
- 出站边界：实际请求仍必须使用 `SecureOutboundClient` 的 `FLOW_API` 场景，继续执行协议、域名/IP、私网、重定向、超时、响应大小、并发隔离和熔断策略。
- 实际结果：`FlowNotificationContentRendererTest` 与 `FlowWebhookNotifierTest` 定向回归 7/7；`forge-plugin-flow` 完整测试 197/197，0 失败、0 错误、0 跳过；源码扫描无 `fallbackExecution=true` 和完整 Webhook URL 日志。
- 环境限制：本轮未实现通知 Outbox、唯一事件 ID、顺序版本、失败补偿与人工重放；也未连接真实 Webhook 目标执行 DNS 重绑定和网络策略集成验证。

## 1.30 2026-09-28 BPMN 嵌套执行容器绕过修复

- 顶层边界：流程结构校验只能把 `process` 的直属 `startEvent`/`endEvent` 视为流程边界；嵌套容器内部的开始/结束节点不能替代顶层节点。
- 执行白名单：`transaction` 与 `adHocSubProcess` 必须进入可执行节点识别集合并按不支持类型拒绝，不能因未被解析器识别而绕过 `subProcess`/`callActivity`/`scriptTask` 同级限制。
- 表达式兼容：合法 `conditionExpression` 的 CDATA 写法必须继续通过；结构加固不能退化单引号、命名空间前缀或属性顺序兼容性。
- 实际结果：`FlowModelBpmnPreflightTest` 与 `BpmnXmlUtilsTest` 定向回归 10/10；`forge-plugin-flow` 完整测试 198/198，0 失败、0 错误、0 跳过。
- 环境限制：未部署到真实 Flowable 引擎执行恶意 transaction/adHocSubProcess 模型；事件乱序仍属于通知 Outbox 与顺序版本任务，不计入本批完成项。

## 1.31 2026-09-28 Flow 通知事务 Outbox 与有序补偿

- 事务边界：通知事件先在发布方事务内写入 `sys_flow_notify_outbox`；Outbox 写入失败必须越过旧监听器降级边界并回滚 Flowable 事务，提交后才异步派发；无事务发布也必须先持久化，不能直接执行站内信、协同卡片、Redis 或 Webhook 副作用。
- 信封与幂等：每个通知具有唯一 `eventId`、协议 `eventVersion`、数据库单调 `eventSequence` 和 `payloadHash`；相同事件 ID 仅允许完全一致的载荷幂等复用，摘要冲突必须拒绝。
- 有序补偿：按租户进行原子 CAS 认领，处理中租约超时后可接管；同一聚合只有最早未送达事件可被认领，失败按指数退避重试，超过阈值进入死信并阻断同聚合后续事件越序发送。
- 失败可见性：通知通道异常不再被吞掉，统一回写重试/死信状态；Webhook 使用稳定事件请求头，日志仅记录安全目标、业务键和异常类型，站内信/H5/协同卡片完整 URL 不进入日志。
- 实际结果：Outbox/监听器/Webhook/内容渲染定向回归 20/20；`forge-plugin-flow` 完整测试 210/210；Flow Server 38 模块主链编译全部成功，Flow Server 自身测试 46/46，均为 0 失败、0 错误、0 跳过。
- 既有限制：38 模块全测试在无关的 `forge-plugin-ai` 测试夹具编译处阻断（`RecordingAdapter` 未实现新增的 `createEmbeddingModel`），但生产主链编译、Flow 插件全量测试和 Flow Server 自身测试均独立通过。
- 环境限制：未连接真实 MySQL 执行 V1.0.192/Flyway，未进行双节点并发认领、进程崩溃和真实下游故障注入；本节记录的死信人工重放缺口已在 1.33 关闭，Flowable 镜像、候选人与业务状态同步仍需独立补偿闭环。

## 1.32 2026-09-28 业务触发器命令租约与崩溃恢复

- 持久化信封：触发器认领日志必须保存规范化的完整业务事件与触发器不可变快照，并对二者联合计算 SHA-256 摘要；恢复前必须复验租户、触发器、事件、动作身份及摘要，篡改或缺失快照一律转人工处理。
- 租约恢复：新事件先插入 PENDING 再以数据库 CAS 认领；定时恢复器只认领本租户下超时 PENDING 或到期 FAILED，执行结果必须按 `tenant_id + id + PENDING + lock_owner` 回写，旧 worker 不得覆盖接管者结果。
- 重试边界：失败按指数退避重试，超过上限进入 DEAD；START_FLOW、SEND_MESSAGE、UPDATE_FIELD 和带稳定幂等键的业务动作允许自动恢复，结果不明确的 CREATE_RECORD、WEBHOOK 不得盲目重放，必须转 TODO 人工处理。
- 幂等契约：消息副作用使用由触发器 ID 与事件 ID 派生的稳定幂等键；重复事件必须验证摘要一致后跳过，摘要冲突必须拒绝。
- 实际结果：恢复信封、认领/CAS、重试/DEAD、消息幂等、危险动作人工分流、Mapper 与迁移契约定向测试 24/24；Generator 模块完整测试基线 1332/1332；Admin 聚合编译 46/46，均为 0 失败、0 错误。
- 环境限制：未连接真实 MySQL 执行 V1.0.193/Flyway，未执行双 JVM 并发认领、kill -9 崩溃接管或真实消息/Flowable 故障注入；全依赖测试在无关 `forge-starter-outbound` MockWebServer 绑定本机端口处被沙箱限制阻断，Generator 自身完整测试与 Admin 聚合编译独立通过。

## 1.33 2026-09-28 Flow 通知死信人工重放与审计

- 可见性与租户隔离：监控接口只分页返回当前租户的 DEAD 通知摘要，不返回载荷、载荷摘要或下游敏感参数；匿名、无查看权限和跨租户记录均不得暴露死信存在性。
- 重放权限与协议：重放接口必须具备独立管理权限，使用带 Bean Validation 的明确 DTO 接收必填原因；操作者取自可信登录会话，不能由客户端覆盖。
- 状态机与审计：只有当前租户下仍为 DEAD 的记录可通过单条 CAS 重置为 PENDING；同时清理锁、错误和重试计数，并原子记录重放次数、操作者、原因和时间。不存在与跨租户统一返回 404，非 DEAD 状态返回 409。
- 顺序保证：人工重放只重新入队，不直接调用通知通道；既有定时派发器仍按聚合最早未完成事件认领，重放成功前后续事件继续被阻断。
- 实际结果：Flow 插件定向测试 13/13、完整测试 215/215；Flow Server 控制器/边界测试 29/29、完整测试 47/47；为适配现有接口补齐的 AI 测试夹具 4/4；Admin 聚合编译 46/46，均为 0 失败、0 错误。
- 环境限制：未连接真实 MySQL 执行 V1.0.194/Flyway，未启动 Flow Server 执行真实 HTTP 角色矩阵，也未对真实失败通知通道执行人工重放；这些结果不能替代生产审批与操作审计验收。

## 1.34 2026-09-28 Flowable 本地镜像投影 Outbox 与顺序 fencing

- 事务恢复点：任务创建、完成、分配、取消及流程完成、驳回、取消必须先在 Flowable 当前事务写入投影 Outbox；Outbox 持久化失败向引擎传播并回滚，投影执行失败则保留 PENDING 记录，由定时任务恢复。
- 不可变信封：每个投影事件包含唯一 event ID、协议版本、数据库全局顺序号、可信租户、聚合类型/ID、任务或业务状态快照及 SHA-256 摘要；恢复前必须复验摘要，篡改载荷不得执行。
- 幂等与乱序：任务、业务、表单和候选关系写入均显式绑定租户；任务/业务/表单保存最近投影事件与顺序号，只有不小于当前顺序的事件可更新。候选关系按租户、任务、类型和值幂等 upsert，迟到事件不能覆盖新状态。
- 补偿状态机：认领使用租户 + 记录 ID + 租约 CAS，同一聚合存在更早未完成事件时禁止认领；处理中租约可超时接管，失败按指数退避，超过上限进入 DEAD，错误只保存异常类型。
- 兼容行为：创建时直接指定处理人仍自动签收；TASK_ASSIGNED 先于 TASK_CREATED 时可先建立镜像，后续创建投影以更高顺序号收敛。通知继续走独立通知 Outbox，不与镜像重试重复发送。
- 实际结果：投影 Outbox/处理器/调度器/Mapper/迁移及原监听器回归定向测试 19/19；`forge-plugin-flow` 完整测试 226/226，0 失败、0 错误、0 跳过；Admin JDK 17 聚合编译 46/46 模块成功。
- 环境限制：未连接真实 MySQL 执行 V1.0.195/Flyway，未执行两个 Flow 节点竞争认领、kill -9 接管、数据库瞬断或真实 Flowable 乱序注入；投影 DEAD 的监控与人工重放仍可作为后续运维增强，不将契约测试当作生产故障演练。

## 1.35 2026-09-28 低代码 CRUD 事务事件 Outbox 与聚合顺序

- 事务原子性：新增、修改、单条删除、审计删除和批量删除必须通过统一事务编排器执行，主数据源业务记录与事件 Outbox 同事务提交或回滚；普通 CRUD 与低代码表单填报必须复用同一入口。
- 外部数据源边界：已绑定业务对象但运行在外部数据源的写入，在没有 XA、CDC 或远端 Outbox 协议时必须在数据变更前失败关闭；不能先写远端数据再把主库 Outbox 失败降级为日志。
- 不可变信封：Outbox 必须持久化可信来源、协议版本、租户、业务对象/记录聚合键、稳定逻辑摘要、数据库聚合顺序号、完整载荷摘要和不可变载荷；重复逻辑事件只允许摘要一致的幂等复用，篡改、跨租户或身份不一致不得派发。
- 顺序与恢复：认领使用租户 + 记录 ID + 状态/租约 CAS；同一聚合只允许最早未完成事件被认领。处理中租约可超时接管，失败指数退避，达到上限进入 DEAD，恢复前必须复验载荷摘要与聚合身份。
- 交付边界：Outbox 只有在触发器命令同步持久化和可选流程编排完成后才能标记成功；消费端继续使用稳定幂等键。验证目标是可靠投递和幂等消费，不把未做真实崩溃演练的结果表述为 exactly-once。
- 实际结果：Generator 定向测试 24/24、完整测试 1350/1350；capability-actions 下游适配测试 19/19；Admin JDK 17 聚合编译 46/46，均为 0 失败、0 错误。Mapper XML、迁移静态扫描和 `git diff --check` 通过。
- 环境限制：未连接真实 MySQL 执行 V1.0.196/Flyway，未执行双 JVM/双节点竞争、进程崩溃窗口、数据库瞬断或真实外部回调乱序；DEAD 事件监控和人工审计重放仍待补齐。

## 1.36 2026-09-28 FlowClient 启动命令持久化与补偿

- 远端调用前持久化 START 命令：稳定 command key、可信租户/业务/流程身份、完整请求快照和 SHA-256 摘要必须在独立事务落库；相同幂等键但不同摘要失败关闭。
- 远端幂等恢复：认领后先按稳定流程业务键查询远端状态；已有进程实例或命令已保存 processInstanceId 时不得再次调用启动接口。调用异常后再次对账，能确认远端成功则先以独立事务记录 `REMOTE_SUCCEEDED`。
- 本地补偿：远端成功后本地流程关联和业务状态仍在原事务完成；本地失败将命令转为 RETRY，扫描器按可信请求快照恢复。租约超时可接管，失败指数退避，耗尽进入 DEAD，错误字段只记录异常类型。
- 租户与篡改边界：所有单行读写均显式携带 tenantId；跨租户扫描只在系统调度器中执行，进入恢复前重建租户上下文并复验命令身份、快照和摘要。
- 实际结果：启动命令服务、Mapper 契约、Coordinator 及性能契约定向测试 15/15；`forge-plugin-generator` 完整测试 1356/1356，0 失败、0 错误、0 跳过；Admin JDK 17 聚合编译 46/46 模块成功。Mapper XML、迁移静态扫描和 `git diff --check` 通过。
- 环境限制：未连接真实 MySQL 执行 V1.0.197/Flyway，未执行双 JVM 租约竞争、FlowClient 网络分区、远端成功后进程崩溃或真实 Flowable 对账；审批、回调和状态同步命令仍属于 T4.4 未完成项。

## 1.37 2026-09-28 Flow 回调可靠 Inbox 与顺序 fencing

- 可靠身份透传：Flow 通知 Outbox 的事件 ID、协议版本和数据库顺序号必须完整反序列化到 `FlowEventContext`；可靠消费者必须是唯一可被 `FlowEventSubscriber` 扫描的回调入口，旧业务服务不得重复订阅。
- 入箱与事务边界：带可靠身份的事件必须先以独立事务持久化可信租户、流程/业务聚合键、不可变快照和 SHA-256 摘要，再调用原业务回调事务；只有业务事务提交后才能将 Inbox 标记 COMPLETED。重复事件 ID 仅在摘要一致时幂等复用。
- 顺序与恢复：同一聚合存在已知更早未完成事件或其他 PROCESSING 事件时不得认领；已完成的更高顺序号必须 fencing 迟到事件。FAILED 和过期 PROCESSING 支持退避重试、租约接管和 DEAD，状态更新使用租户、记录 ID、PROCESSING 状态与 lock owner CAS。
- 租户与篡改边界：跨租户扫描仅返回恢复候选，实际处理前必须复验事件身份、聚合键、快照摘要并重建原租户上下文；错误信息只保存异常类型。
- 实际结果：Inbox/消费者/Mapper/既有生命周期定向测试 18/18；`forge-plugin-generator` 完整依赖测试 1365/1365，0 失败、0 错误、0 跳过；Admin JDK 17 聚合编译 46/46。Mapper XML、迁移静态扫描和 `git diff --check` 通过。
- 环境限制：未连接真实 MySQL 执行 V1.0.198/Flyway，未做双 JVM、崩溃和真实乱序故障注入；Redis Pub/Sub 在事件成功入箱前仍无消费端 ACK，旧 `/callback` DTO 和无可靠身份发布方继续走兼容路径。本阶段不能表述为端到端 exactly-once。

## 1.38 2026-09-28 审批动作稳定幂等凭证

- 稳定身份：相同动作、任务 ID 和规范化载荷的用户重试必须得到相同 `idempotencyKey` 与 `requestDigest`；仅对象键顺序变化视为相同请求，数组顺序、动作或有效载荷变化必须生成不同身份。
- 摘要边界：凭证必须使用 Web Crypto SHA-256，并输出 64 位小写十六进制摘要；缺少 Web Crypto 或 `TextEncoder` 时失败关闭，禁止降级到非加密散列。
- 实际结果：凭证单测 4/4、目标 ESLint 和前端生产构建通过。前端完整基线 1956 项中 1947 通过、9 项既有失败；新增测试全部通过，既有失败位于用户明确排除的巨型组件/设计器及相关应用、打印工作区范围。
- 环境限制：未连接真实 Flowable 注入“远端成功、本地响应/状态同步失败”；本批只验证稳定凭证契约，不能替代本地审批命令持久化、Outbox、租约恢复、服务端重放授权和 `return`/撤回/重提的完整幂等链路。

## 2. P0 必跑验证

### 动态脚本与 HTML

- 主线程代码扫描：`rg -n "new Function|AsyncFunction|\bFunction\(" forge-report-ui/src`，目标是仅允许隔离执行器或明确测试夹具。
- 恶意脚本用例：读取 `window`、`document.cookie`、`localStorage`、`fetch`、原型链、构造器和无限循环均被拒绝或超时终止。
- HTML 用例：事件属性、`javascript:` URL、SVG 外链和未允许标签均被清洗；纯文本保留原文显示。

### 外部脚本适配器

- `engine.eval` 主 JVM 静态扫描和单元测试必须无可执行路径。
- 允许 DSL 输入通过；未知函数、超长输入、超时、异常和结果超限必须失败关闭。

## 3. P1 必跑验证

### 权限与网关

- 未配置 API 资源、缓存异常、隐藏 API、普通用户访问外部配置和代理接口均返回 401/403，不得返回业务数据。
- 资源迁移重复执行不产生重复行；Controller 路由覆盖报告无遗漏。
- open-gateway/flow-actions/identity 无环境变量时均为关闭；开启时缺少 pepper、默认 client/grant 或组织绑定失败启动/调用。

### 认证、幂等与验证码

- 密码修改、找回和管理员重置后旧 Token 请求失败。
- 两个并发请求使用同一幂等 Token，业务方法只执行一次。
- 两个并发验证码校验最多一次成功；发送间隔和日配额不能被并发绕过。
- 同一短信/邮箱目标在失败窗口达到阈值后必须短时锁定，锁定期间不得继续校验或调用发送器；Redis 查询、计数或发送占位异常时统一 fail-closed。
- 不同 client 并发登录不互相修改 Token TTL、并发和共享策略。

### 数据连接和 SQL 预览

- 环回、私网、云元数据、未允许协议/驱动、非 `SELECT 1` 测试 SQL、超时和超大响应均拒绝。
- `preview-sql` 无数据集 ACL、跨租户、未发布或无查询权限均拒绝；可用数据集仍执行行/列权限和脱敏。

### 文件

- uploadId 跨用户、跨租户、过期、超分片、超大小和错误存储类型均拒绝。
- 对外 uploadId 不包含对象存储 bucket/key/provider uploadId；Redis 会话和逐片状态允许另一节点续传，本地存储在共享挂载目录下可恢复磁盘会话。
- 完成请求必须与服务端记录的连续分片、ETag、逐片大小、总大小、MIME 和扩展名一致；过期或合并异常会终止存储端上传并清理会话。
- 分片完成保留私有属性；私有文件的 download/url/Base64/bytes/导出均要求授权。
- 批量删除按字符串 fileId 生效，不能删除其他租户或其他上传者文件。
- `sys_file_metadata.status` 固定为 `1=正常/0=已删除`，实体 `@TableLogic`、自定义 Mapper SQL 和 Flyway 迁移保持一致。

## 4. P2 正确性验证

- 数据集 page 1/page 2 返回不同记录，`total` 与 count 一致，缓存不串页。
- SQL parser 拒绝注释绕过、大小写绕过、换行绕过、多语句、锁、延时函数、文件函数、系统表和危险 UNION。
- 注册确认密码、格式、租户、禁用用户找回密码和统一密码策略测试通过。
- `mvn dependency:tree` 不再将 `fastjson:1.2.83` 带入运行时，或有经批准的兼容例外。

## 5. 低代码/流程专项验证

### 巨型类拆分与行为保持（本轮排除）

以下用例属于后续架构改造验收基线，本轮按用户要求不执行、不计入安全缺陷修复完成度。

- 生成目标类的行数、方法数、依赖数、圈复杂度、事务方法和远程调用基线；拆分后单类不超过 800 行，且不存在仅转发调用的规避类。
- 对 `BusinessFlowService`、`DynamicCrudService`、`BusinessObjectDesignerService`、`LowcodeRuntimeConfigBuilder`、`FlowTaskServiceImpl` 和监听器执行 API、权限、租户、逻辑删除、状态、幂等和审计行为快照对比。
- 子表跨租户、跨主记录、逻辑删除记录和并发更新/删除均被拒绝；动态字段、表名和列名不接受运行时 Schema 白名单之外的输入。

### 事件租户与流程并发

- 缺失 `tenantId` 的事件被拒绝或进入隔离失败队列，不能静默落到租户 1；事件 DTO 伪造其他租户、来源或签名失败。
- 未知事件条件操作符、未知字段类型和格式错误表达式均 fail-closed；重复事件只产生一个 run。
- 同一 run 并发执行时只有一个 worker 获得租约；同一节点只有一个 attempt claim 成功，complete 必须按该 attemptId 原子更新。
- 过期租约只能被一个恢复 worker 接管；重复审批回调、乱序回调、跨租户回调和远程超时不能错误改变终态。
- Flowable 远程成功后本地写回失败可通过 Outbox/补偿恢复；本地事务回滚但远程已成功不会再次无条件启动流程。

### 流程监控和变量安全

- `FlowMonitorServiceImpl` 的旧统计、实例、活动节点和变量接口均无法跨租户读取；不存在未绑定 tenant context 的公共服务路径。
- 流程变量按白名单和敏感等级过滤/脱敏；读取行为记录操作者、租户、实例和字段摘要，不记录变量原文。

### BPMN 输入安全

- XML 外部实体、DOCTYPE、外部 schema 和危险解析配置被拒绝；单引号属性、命名空间前缀、属性顺序、CDATA 和换行不影响合法文档校验。
- 非法 sequenceFlow 引用、不支持的 scriptTask/callActivity/subProcess/serviceTask 执行委托和恶意表达式不能绕过白名单。
- process id 替换只修改目标 XML 节点，不误改文本、注释、CDATA 或其他 process；多 process 文档按明确策略拒绝或只允许指定主 process。
- BPMN 日志只记录模型 id、版本、hash、长度和错误定位，不输出原始 XML。

### 低代码发布一致性

- DDL 成功而配置失败、菜单成功而入口失败、重复 post processor、重试耗尽和死信场景均可对账并人工重放。
- 发布任务包含 requestId/schemaHash/tenantId/versionId/dataSource/operator，重复执行不产生重复菜单、入口或版本。
- 发布状态在预检、DDL、配置、后置同步和补偿之间单调可追踪，不把本地 `@Transactional` 视为 DDL 回滚保证。

## 6. 命令基线

```bash
cd forge-server
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -Penable-tests -Dforge.compiler.skip=false -Dforge.tests.skip=false test
```

```bash
cd forge-admin-ui
pnpm --ignore-workspace lint
pnpm --ignore-workspace build
```

```bash
git diff --check
rg -n "new Function|AsyncFunction|engine\.eval|StrictHostKeyChecking=no|fastjson:1\.2\.83" forge-report-ui forge-server
```

仅文档阶段应执行 `git diff --check` 和关键路径静态检查；代码实现阶段按本文件增量追加实际命令、输出、接口返回和数据库结果。

## 7. 跳过项和环境限制

- 未配置本地 MySQL/Redis 时，不将真实接口、Flyway、并发 Lua 和 JDBC 网络边界写成通过；改用 Testcontainers 或明确记录跳过原因。
- 未启动服务时，不进行真实浏览器、网关、租户隔离和文件存储结论。
- 不停止工作区其他任务启动的进程，不删除现有数据库、缓存和测试数据。

## 8. 2026-09-27 验证码风控增量验证

- 风险范围：共享 Redis 计数原语、短信/邮箱验证码校验与发送、Admin/App 配置绑定。
- 定向行为：执行 `CaptchaServiceImplTest` 全量 25 个用例，覆盖失败阈值、短时锁、成功清理和 Redis fail-closed。
- 缓存契约：执行 `RedissonCacheServiceImplTest`，确认 `INCRBY + 首建 PEXPIRE` 使用单次 Lua，非正 TTL 拒绝。
- 聚合兼容：执行 `mvn -pl forge-admin-server -am -DskipTests compile`，验证共享 `ICacheService` 接口变化的 46 模块编译。
- 环境限制：无真实 Redis 集群，本轮不声明节点切换、网络分区或 Lua 实库并发压测通过。

## 9. 2026-09-27 密码凭证版本增量验证

- 风险范围：用户改密、找回密码、管理员重置、Sa-Token 受保护请求和多实例旧会话吊销。
- 数据契约：两条密码更新 SQL 必须在同一语句中执行 `password_version + 1`；Flyway 为存量用户补齐非空默认版本 0。
- 请求期校验：Token Session 版本与数据库权威版本一致才允许继续；用户禁用、删除、租户成员关系撤销、版本不一致或数据库读取异常均 fail-closed。
- 滚动兼容：迁移前创建且不含版本字段的会话按版本 0 处理；首次改密后数据库版本递增，所有旧会话立即失效。
- 产品配置：`FORGE_AUTH_KEEP_CURRENT_SESSION_AFTER_PASSWORD_CHANGE` 默认 false；显式开启时只刷新发起改密的当前会话版本，其余会话仍失效。找回密码和管理员重置不受该开关影响，始终吊销全部会话。
- 环境限制：本轮不连接真实 MySQL/Redis，不声明 Flyway 实库迁移、双 JVM 或网络分区演练通过。
