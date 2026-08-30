# 踩坑：能力开放 / MCP / 动作发布

> 从 `code-copilot/memory/pitfalls.md` 按主题拆出。新条目追加到本文件。共 21 条。

## 低代码增强被选中不代表运行时拿到了启用版本正文


**发现日期**：2026-08-21

应用工作台若只返回增强名称、状态、草稿/启用版本号等摘要，不返回 `enabled_version` 对应的 `content / processedContent`，前端仍会筛选到 `ENABLED` 增强，但可视化规则和客户端增强实际执行为空。独立 `AiForm` 如果绕过 `AiCrudPage` 直接请求，也不会自动获得提交钩子；提交前 `BLOCK` 异常被吞掉时还会错误地继续保存。

处理原则：设计工作台预览下发增强时必须按 `enabled_version` 关联不可变版本，禁止读取草稿正文；所有表单入口统一接入 `BEFORE_SUBMIT / AFTER_SUBMIT / FORM_CHANGE`。`BEFORE_SUBMIT` 返回 `false` 或抛出 `BLOCK` 异常必须停止请求。测试通过与启用是两个状态，界面必须提供明确启用动作并说明正式应用还需重新发布。

## Capability 短期 Token 不能交给 Sa-Token 解析


**发现日期**：2026-08-02

`/oauth2/token` 与 `/openapi/v1/capabilities/**` 使用 Capability 自有的 OAuth/OpenAPI 认证，`fdu_` 不是 Forge 后台登录 Sa-Token。通用租户拦截器或操作日志切面如果在协议认证前调用 `SessionHelper/StpUtil`，会把有效 `fdu_` 误报为“token 无效”，还可能产生无意义堆栈。

处理原则：协议入口在完成自身认证前不解析 Sa-Token；OAuth 换 Token、撤销和开放网关路径不进入通用操作日志，改用 Capability 专用调用审计；开放网关完成认证后再通过 `ExecutionIdentityContextHolder` 与 `TenantContextHolder` 建立可信身份和租户上下文。

---

## 6. 关联功能使用不同条件注解会产生“Bean 存在但路由 404”


**发现日期**: 2026-08-02

**问题描述**:
能力开放网关开启、独立 Identity 开关关闭时，网关认证器依赖的 `CapabilityAccessTokenService` 已自动装配，但 `/oauth2/token` Controller 仍未注册，导致调用指南中的正确 Token 地址返回 404。

**根本原因**:
自动配置和公开 Controller 分别复制了不同的 `@ConditionalOnProperty` 条件。只修复底层 Bean 的装配条件，会形成不可用的半开启运行态。

**解决方案**:
- 对存在依赖关系的一组自动配置、Controller、Observer 和定时任务，抽取公共 `Condition` 作为唯一启用语义。
- 条件变更时同时检查公开路由、底层服务、审计、清理任务和诊断页面，不能只验证某个 Bean 存在。
- 对开放网关场景，`identity.enabled=true OR open-gateway.enabled=true` 任一满足时，Token 签发与校验链路必须整体注册。

**排查提示**:
遇到“依赖 Bean 已存在但接口 404”时，优先搜索 Controller 上的 `@ConditionalOnProperty`、`@Profile` 和组件扫描边界，而不是继续排查 Spring MVC 路径拼写。

---

## 103. AI/MCP 机器调用的数据权限必须按执行器 fail-closed


**发现日期**: 2026-07-10

**问题描述**:
规划 AI 中枢或 MCP 能力出口时，如果笼统认为所有业务查询都会经过 `DataScopeInterceptor`，会错误高估现有数据权限边界。动态 CRUD 使用 `NamedParameterJdbcTemplate`，实际通过 `DynamicDataScopeService` 构造数据范围条件；普通 MyBatis Mapper 才会进入 `DataScopeInterceptor`。后者在用户上下文获取失败、上下文为空、未知数据范围或 SQL 改写异常时存在跳过/放行分支，适合部分后台兼容场景，但不能作为外部机器调用的最终安全边界。

另一个同源风险是部分业务服务在缺少 Session 租户时会回退默认租户 `1`，例如 `BusinessActionExecutionService#resolveTenantId`。机器身份链路如果没有先建立完整用户、租户和当前组织上下文，可能把缺失上下文误变成默认租户调用。

此外，`DynamicDataScopeService` 当前 REGION 条件仍会在运行时业务 SQL 中子查询 `sys_region_code`。租户业务数据源不包含平台控制面表时会执行失败，也与“数据权限控制面元数据固定在平台主库”的项目决策冲突。

**解决方案**:
- 外部能力统一先校验机器客户端、服务账号、租户、当前组织和授权交集，任何上下文缺失均拒绝。
- 动态低代码查询显式向 `DynamicDataScopeService.buildCondition(..., explicitContext)` 传入已验证的数据权限上下文。
- REGION 范围必须从平台主库的数据权限快照/缓存预解析成区域编码集合，业务 SQL 只使用参数化 `IN`，禁止子查询 `sys_region_code`。
- MyBatis 能力适配器启用 capability 专用 fail-closed 模式；上下文缺失、Mapper 权限配置缺失、未知范围和 SQL 改写失败都必须抛错。
- 代码业务 Provider 必须声明并执行对象级权限策略，能力层先校验、Provider 内再校验。
- 机器调用进入业务 Service 前禁止默认租户兜底，不能把 `tenantId = 1` 当作认证失败的替代值。
- 客户端参数中的 `tenantId/userId/activeOrgId` 不能覆盖认证上下文。

**影响范围**:
- AI 中枢 Capability Registry 与 MCP Server。
- `DynamicCrudService` / `DynamicDataScopeService` 动态查询。
- 通过 MyBatis Mapper 暴露的流程、消息、API 和代码业务能力。
- `BusinessActionExecutionService` 等存在默认租户兼容逻辑的业务服务。

## 107. MCP 配置声明和游标查询绑定不能替代启动硬闸门与签名


**发现日期**: 2026-07-11

**问题描述**:
仅在 YAML 中写 `spring.ai.mcp.server.protocol=STREAMABLE`，外部环境仍可覆盖为 SSE/STATELESS；仅把快照、查询指纹和排序位置编码进 Base64 游标，客户端仍可替换为另一个真实可见位置。这两种做法看似有限制，实际都缺少不可绕过的运行时边界。

**解决方案**:
- MCP enabled 时创建启动期协议 Guard，拒绝 `SSE`、`STATELESS` 和 stdio；自动配置中的 transport、认证过滤器都依赖该 Guard；
- `/mcp` 身份认证放在 SDK handler 前，失败直接返回 HTTP 401 和 requestId，不能依赖 SDK 把内部异常映射成正确状态；
- 游标必须绑定快照、查询条件和调用方，并用服务端进程密钥做 HMAC-SHA256；验证签名后再验证位置真实且调用方可见；
- 测试必须覆盖外部配置覆盖、位置篡改到另一个真实能力、跨查询/跨调用方复用和服务重启后游标失效。

**影响范围**:
- `forge-plugin-mcp` transport 与 HTTP 身份边界；
- Capability Registry 的游标分页；
- 后续机器客户端动态 `tools/list` 和能力搜索接口。

## 108. 凭据认证不能用完整实体回写，也不能依赖认证前租户上下文


**发现日期**: 2026-07-12

**问题描述**:
机器凭据认证先读取完整客户端实体，再用 `updateById` 写 `last_used_at`，会把旧快照中的 `status/key_hash/credential_version` 一并写回；并发 rotate/revoke 后，旧认证请求可能恢复已吊销凭据。另一方面，认证发生在用户登录和租户上下文建立之前，如果凭据查询或 last-used 更新依赖自动租户插件，SQL 会被追加 `tenant_id = NULL`，导致合法凭据全部失败。

**解决方案**:
- Secret 内携带全局不可猜测 keyId，只用它跨租户定位一条有效凭据，再从记录取得权威 tenantId；
- 认证专用 Mapper 明确绕过自动租户追加，但 SQL 必须使用已查记录中的 tenantId/id/keyHash/version 做约束；
- last-used 只更新 `last_used_at`，rotate/revoke 只更新目标字段，全部使用 `credential_version` CAS；
- CAS 影响行数为 0 必须失败关闭，不能把旧快照继续转换为调用主体；
- 实际人员身份不能从任意 Header 读取；机器账号与用户委托身份必须分开审计。

**影响范围**:
- MCP/API Key 等认证前跨租户凭据定位；
- 密钥轮换、吊销与最近使用时间更新；
- 多租户拦截器尚未建立上下文的 Filter 阶段；
- 机器服务账号与具体人员的审计归因。

## 111. 能力授权版本不能混用能力主表的当前 source binding


**发现日期**: 2026-07-12

**问题描述**:
能力目录按 grant 解析到 `ai_capability_version` 后，如果 Schema、policy 使用版本表，而 `source_key/source_version` 仍读取 `ai_capability` 主表当前值，能力升级或重绑定后会形成“旧版本策略 + 当前动作 binding”。PINNED grant 可能异常失效；两个业务动作发布版本号碰巧相同时，甚至可能把旧策略用于新动作。

**解决方案**:
- resolved version 的 Schema、policy、source binding、行为、风险和可见性必须全部来自同一 `ai_capability_version`；
- 主表只用于能力当前启停、发布状态和目录身份，不得替代授权版本快照；
- 调用指南、在线测试 Body 和代码示例也必须使用 grant 实际 resolved version，界面同时展示 current/resolved version，避免管理员误以为新版本未发布成功；
- grant 缩小字段后必须同步裁剪运行时输入 Schema，并在执行入口再次显式检查参数字段集合；
- 测试必须覆盖主表当前版本与 PINNED/FOLLOW_MAJOR resolved version 的 source binding 不同场景。

**影响范围**:
- Capability 版本授权和动态目录；
- 受控业务动作、后续 Flow Actions/Message Actions；
- 所有同时存在能力主表当前态与不可变版本快照的执行链。

## 112. 流程办理 DTO 的 userId 和 taskId 都不能作为授权依据


**发现日期**: 2026-07-12

**问题描述**:
流程办理 DTO 保留 userId 兼容字段时，如果 Service 用 `dto.getUserId()` 覆盖当前用户，MCP 或普通 HTTP 调用方可伪装成其他审批人。类似地，只验证 taskId 存在并不能证明调用者有办理权；候选但未签收、他人已签收、其它业务对象或其它流程模型的任务都可能被错误办理。

**解决方案**:
- `BusinessFlowService.completeBusinessTask` 始终使用 `SessionHelper` 中的可信当前用户，不读取 DTO userId；
- elicitation 前使用只读写权限校验确认任务已经由当前 A 签收，执行时再次校验，防止确认和执行之间状态漂移；
- 同时匹配 capability 发布快照、objectCode、recordId、businessKey 和 processDefKey；
- taskId 在日志和确认摘要中只保存/显示安全摘要或尾号，不记录完整值和审批意见；
- 流程幂等日志必须先预留；本地流程编排和成功日志更新尽量共用事务，独立 Flow 服务的远程副作用边界必须明确记录并保留对账证据。

**影响范围**:
- Flowable 待办办理、MCP FLOW_ACTION、低代码业务流程入口；
- 用户委托身份、审批审计、幂等和故障对账。

## 138. 管理控制面与运行时共用特性开关会产生无法解释的 404


**发现日期**: 2026-08-01

**问题描述**:
能力注册页已有菜单、权限和业务对象，但 `GET /ai/capability/flow-action/registration-source` 返回 404。原因是管理 Controller、发布 Bean 和真实执行适配器同时受 `forge.capability.flow-actions.enabled` 控制；外部执行默认失败关闭时，Spring 把管理路由也一并移除。

**解决方案**:
- 管理控制面的来源校验、发布服务和 Controller 始终装配，继续使用 Sa-Token 权限与受控发布校验保护。
- MCP/REST 真实执行的目录、Handler、工具贡献者、执行日志和流程适配器继续受特性开关控制。
- 补充开关关闭/开启两个分支的 `ApplicationContextRunner` 测试，并断言 Controller 不携带运行时条件。
- Controller 映射修改后必须重启 Admin；只重启 Vite 不会改变 Spring 路由。

**影响范围**:
- 所有同时包含管理配置和外部/运行时执行的插件特性开关。

## 140. Capability 开关开启但 Pepper 空值会在身份模块启动期失败


**发现日期**: 2026-08-01

**问题描述**:
`application.yml` 为 Capability 三个 Pepper 保留空占位符，开发环境未导出对应变量时，`CapabilityIdentityStartupGuard` 会报“Forge Capability Client Pepper 未配置或长度不足”。不能用每次启动生成的临时随机值绕过，否则历史客户端密钥哈希和已签发 Token 会全部失效。

**解决方案**:
- 将三个 Pepper 纳入 `CryptoSecretEnvironmentPostProcessor`，首次写入外部稳定文件并在重启后复用。
- 旧版密钥文件缺少新字段时只补齐缺失项，写入必须沿用文件锁、原子替换、符号链接拒绝和 0600 权限。
- 三个值必须满足最小长度并互不相同；环境变量/JVM 参数逐项覆盖持久化值。
- 生产集群不能让各节点使用各自本地随机文件，应由共享 Secret Manager 提供一致值。

**影响范围**:
- 所有启用 Capability Identity、MCP 或 REST Open Gateway 的 Admin 部署。

## 142. Maven Reactor 未包含已修改 Starter 时会误用本地仓库旧版类路径


**发现日期**: 2026-08-02

**问题描述**:
多模块测试只选择上层业务插件时，即使工作区中的底层 Starter 已修改，Maven 也可能从本地仓库解析旧版 Starter。Capability Identity 的 Spring 集成测试因此读取到旧版 `forge-starter-crypto` 规则，并在上下文初始化时误报新 Pepper 配置键不允许；同模块普通单测仍可通过，容易误判为业务代码或配置回归。

**解决方案**:
- 修改共享 Starter 后，运行上层插件集成测试时把该 Starter 显式加入同一 Reactor，例如同时选择 `:forge-starter-crypto,:forge-plugin-capability-platform` 并使用 `-am`。
- 先执行 Reactor `test-compile`，再运行集成测试；同时核对依赖来源、Surefire 报告中的实际用例数和失败发生阶段。
- 只有用当前 Reactor 产物复跑仍失败时，才按产品缺陷继续分析；不要把本地 Maven 仓库旧 JAR 的启动错误记为实现失败。

**影响范围**:
- 所有工作区内同时修改 Forge Starter 与依赖该 Starter 的业务插件/集成测试。
- 使用本地 Maven 仓库快照且未把底层模块纳入 Reactor 的增量构建。

## 144. 依赖型特性开关必须在自动配置中编码联动关系


**发现日期**: 2026-08-02

**问题描述**:
Open Gateway 依赖 Identity 提供 `CapabilityAccessTokenService`，但两个模块仅通过各自的 `@ConditionalOnProperty` 独立装配。环境配置出现 `open-gateway.enabled=true`、`identity.enabled=false` 时，网关认证器已经创建，依赖的 Token Service 却不存在，导致 Admin 启动失败。只在 YAML 中嵌套环境变量默认值不能覆盖 Profile、配置中心或 JVM 参数直接覆盖属性的场景。

**解决方案**:
- 依赖模块的自动配置条件必须表达真实运行依赖：Open Gateway 开启时，无论 Identity 原始开关值如何，都装配 Identity 运行底座。
- 使用 `@AutoConfiguration(after = ...)` 声明依赖装配顺序，不能依赖 imports 文件或类路径扫描的偶然顺序。
- 下游页面和诊断服务必须使用同一套“有效开关”语义，避免运行态可用但调用指南误报不可用。
- 补充两个层次的容器测试：依赖模块单独断言关键 Bean 存在，联合自动配置断言完整调用链 Bean 可创建。
- 对外入口继续安全默认关闭；只有管理员显式开启网关时才自动带起必要依赖。

**影响范围**:
- 所有存在运行时依赖关系的插件开关、Starter 自动配置和管理端 readiness 诊断。
- 通过环境变量嵌套默认值联动，但又允许 Profile 或配置中心直接覆盖具体属性的部署方式。

## 149. 公开 OAuth/OpenAPI 入口跳过登录租户后必须自行建立可信租户上下文


**发现日期**: 2026-08-03

**问题描述**:
Capability OAuth/OpenAPI 入口为了不把 `fdu_` Token 交给 Sa-Token 解析，会跳过通用登录租户拦截器。如果协议完成客户端或 Token 校验后直接查询外围身份、客户端、访问令牌等租户表，MyBatis 租户处理器会报“访问租户表时缺少租户上下文”。只修复第一张报错表后，后续 Token 签发或校验仍会在下一张租户表失败。

**解决方案**:
- 凭据未验证前，只允许按全局唯一 `clientId/keyId/tokenKeyId` 做显式安全查询，不接受请求 Header、Body 自报的租户。
- 从已认证客户端或已验签 Token 得到可信 `tenantId` 后，在完整业务片段外建立租户上下文并强制 `ignoreTenant=false`。
- 认证基础设施查询不适用登录用户数据权限，应在受控边界内跳过 DataScope，避免 WARN 噪音或严格模式误拒绝；租户隔离仍保持开启。
- 上下文必须支持嵌套，并在 `finally` 中恢复原租户、租户忽略标记和 DataScope 标记，防止线程复用造成串租户。
- Token 签发、Bearer 校验、撤销、外围身份映射和 HMAC 服务身份加载必须统一检查，不能只覆盖当前异常点。

**影响范围**:
- `/oauth2/token`、`/oauth2/revoke`、`/oauth2/userinfo`、MCP Bearer 认证与 REST Open Gateway。
- 所有“公开协议先全局验凭据、再进入租户数据”的认证与网关链路。

## 150. FLOW_ACTION START 的 recordId 不是任意外部业务号


**发现日期**: 2026-08-03

**问题描述**:
流程 START 能力复用低代码业务对象流程，`recordId` 是 Forge 已保存业务记录的数据库主键，不是外围系统随手生成的业务号，也不是流程实例 ID。使用任意数字时，真实委托用户的数据权限查询返回空；如果统一映射成 `SCHEMA_INVALID`，使用者会误以为 JSON 结构有问题。

**解决方案**:
- START 只启动已有业务记录，禁止为了开放调用自动创建记录或绕过表单与数据权限；
- Schema 将 recordId 约束为正整数长整型字符串，并说明必须是已保存、当前委托用户可见的真实记录；
- 调用指南使用 `<REAL_RECORD_ID>` 占位符并在在线测试前提示替换；
- 不存在与不可见统一返回 `RESOURCE_NOT_FOUND`/404，避免记录存在性枚举；
- 在线测试直接展示网关业务错误码和文案，不能只显示 HTTP 状态。

**影响范围**:
- 低代码业务对象 FLOW_ACTION 的 START/APPROVE/REJECT；
- 调用指南、在线测试、Markdown/OpenAPI 文档和外围系统接入示例。

## 151. 跨运行数据源建单不能假装与本地幂等日志原子提交


**发现日期**: 2026-08-03

**问题描述**:
能力平台若先向低代码外部运行数据源插入业务记录，再向 Forge 主库保存 `recordId` 幂等检查点，两次写入不属于同一本地事务。主库检查点失败时外围系统重试会再次建单；只依赖请求日志、异常捕获或相同业务参数摘要都无法证明上一笔外部写入是否成功。

**解决方案**:
- 主库运行对象把业务记录创建和 `recordId` 检查点放入同一 `REQUIRES_NEW` 事务，流程启动失败后按检查点恢复；
- 外部运行数据源在没有 Outbox、事务消息或明确可查询的外部幂等键前，注册页提前禁用组合 SUBMIT，服务端发布与执行继续失败关闭；
- 不得在外部写入成功后才“尽力”补日志，也不得在检查点缺失时自动重建记录；
- 日志只用于排障，不能替代持久化幂等状态机。

**影响范围**:
- 跨数据库的业务申请、订单创建、流程发起等组合写能力；
- 所有需要保证“外部写入最多一次、本地状态可恢复”的开放接口。

## 152. 业务动作已启用不等于它已可执行或可开放


**发现日期**: 2026-08-03

**问题描述**:
能力注册页仅按 `status != 0` 筛选业务动作，会把页面跳转、流程入口、空动作外壳或包含未审核步骤的动作也当成可发布候选。用户只有在最后提交时才看到“缺少执行步骤”或“禁止发布动作步骤”，无法判断哪个动作真正可用。

**解决方案**:
- 候选项必须来自与发布执行一致的不可变业务对象发布快照，不能从当前草稿或纯页面动作列表推断。
- 由服务端复用真实步骤校验器输出可发布性和阻断原因，前端只消费诊断，禁止再维护一份步骤白名单。
- 不可发布动作保留在列表中但禁用，显示空步骤、不支持类型或停用原因，并给出修正入口。
- 提前诊断只改善易用性，不取代直接发布 API 和运行时的失败关闭校验。

**影响范围**:
- 能力开放平台业务动作注册、版本升级、MCP/REST 调用和业务对象动作设计。

## 153. 名为“新增”的 OPEN_PAGE 动作不是服务端创建记录能力


**发现日期**: 2026-08-03

**问题描述**:
低代码业务对象会把列表“新增/编辑/删除”和流程按钮统一保存在 `designer_options.actions`。其中“新增”通常是 `actionType=OPEN_PAGE`、`actionConfig={}`，语义只是打开新增表单。如果自动化设计器和能力注册只看动作名称或启用状态，用户会误以为它已经具备可由外围系统执行的创建记录能力。

**解决方案**:
- 页面操作、流程入口和服务端自动化不能只按同一 actions 数组展示，必须结合 `actionType` 和执行步骤分类；
- `OPEN_PAGE` 无步骤动作继续作为页面按钮生效，但不进入自动化动作列表，也不能直接发布为 BUSINESS_ACTION；
- 申请类对象需要“创建记录并发起流程”时使用 `FLOW_ACTION/SUBMIT`，不要把页面“新增”按钮改造成绕过表单和流程约束的后端接口；
- 候选诊断必须明确展示“页面操作，不能直接开放”，并提供切换到正确能力类型的入口。

**影响范围**:
- 低代码业务对象动作设计、能力注册来源、流程申请开放和接口文档语义。

## 154. 受控发布器不能生成能力内核未实现的 Schema 关键字


**发现日期**: 2026-08-03

**问题描述**:
能力目录在落库前会通过 `CapabilitySchemaValidator` 校验输入输出 Schema。若来源发布器直接使用完整 JSON Schema/OpenAPI 常见关键字，例如 `pattern`、`format`、`multipleOf`、`default`、`example`，而能力内核只实现了受控子集，发布会在执行前返回 `SCHEMA_UNSUPPORTED`。修掉第一个关键字后，日期、小数、默认值或数组字段还可能依次触发后续错误。

**解决方案**:
- 来源发布器生成 Schema 前必须对照内核 `SUPPORTED_KEYWORDS` 和类型适用范围，不能假定标准关键字已被平台实现；
- 不通过盲目扩大白名单或静默删除约束解决，只有内核真正实现定义校验、实例校验和协议投影后才能新增关键字；
- 当前子集不能表达的格式、精度、默认值等信息写入字段说明，业务适配器和低代码运行时继续执行真实校验；
- array 的 `items` 也属于 Schema 节点，必须声明受支持的明确 `type`，不能生成空对象；
- 发布器回归测试应把生成结果重新交给真实 `CapabilitySchemaValidator.validateDefinition`，避免只断言 JSON 形状而遗漏内核兼容性。

**影响范围**:
- BUSINESS_ACTION、FLOW_ACTION、SYSTEM_SERVICE 等所有动态生成能力版本 Schema 的受控发布器。

## 155. Flyway 新增字典后 SPA 全局缓存可能一直保留旧列表


**发现日期**: 2026-08-03

**问题描述**:
`useDict` 使用模块级 Map 缓存成功结果，但没有 TTL。用户在旧字典已加载的页面中继续操作时，即使 Flyway 已向数据库新增字典项、后端已经重启，当前 SPA 仍可能一直复用旧数组，表现为“数据库和字典管理中存在，业务下拉框里没有”。这与后端 Redis 缓存不是同一问题；`/system/dict/data/list` 本身直接查询数据库。

**解决方案**:
- 普通展示可以复用全局缓存，发布、授权等关键配置弹窗打开时应调用 `reload(dictType)` 强制重读关键字典；
- 初始化默认值必须发生在强制刷新完成之后，避免先按旧列表选中错误操作；
- 跨能力类型自动切换前也要刷新目标字典，不能在 SUBMIT 缺失时静默回退 START；
- 刷新失败或关键项仍缺失时提供可见错误和重新加载入口，不要求用户猜测清浏览器缓存；
- 选项内容仍由系统字典维护，禁止为了绕过缓存直接在页面硬编码完整 options。

**影响范围**:
- Flyway 新增或调整字典后的长生命周期管理后台页面，尤其是能力发布、权限授权和状态流转配置。

## 157. 桌面常驻属性面板不能用移动端抽屉显隐状态判断是否保存


**发现日期**: 2026-08-03

**问题描述**:
响应式设计器可能在桌面端常驻渲染属性面板，在紧凑端改用抽屉展示。如果统一顶部保存仍只检查抽屉的 `propertyVisible`，桌面用户已经修改字段并点击保存时不会调用更新接口，页面本地看起来是新类型，但草稿、发布快照和外部能力契约仍保留旧类型。能力发布器随后忠实读取旧快照，容易被误判为“类型映射错误”。

**解决方案**:
- 保存分支先判断当前是否为紧凑布局；只有紧凑布局且抽屉关闭时才要求先打开属性面板；
- 桌面布局应直接从已挂载的属性面板读取 payload 并调用真实字段更新接口，面板未加载时给出明确错误；
- 字段类型变化后提示完整生效链路：保存草稿、同步物理表、重新发布业务对象、发布能力新版本；
- 能力类型映射先归一化 SQL 声明，并复用统一解析器识别语义类型和组件类型；不得按字段名称猜类型或原地修改不可变发布版本。

**影响范围**:
- 所有桌面常驻/移动抽屉复用同一属性组件的低代码设计器；
- 从低代码发布快照生成 JSON Schema、OpenAPI 或外部调用示例的能力发布链路。

## 158. 低代码业务字段编码不能被当作同名物理列


**发现日期**: 2026-08-03

**问题描述**:
低代码设计器允许稳定业务字段编码与数据库列名分离，例如外部契约字段 `dpe` 映射到设计器生成列 `field_input4`。如果动态 CRUD 的字段白名单读取模型、写入映射却只读取数据库元数据并做 camelCase/snake_case 推导，就会出现“模型和能力文档有字段、物理表也有目标列，但执行仍提示字段不存在”的断链。普通页面新增、自动化 `CREATE_RECORD` 和流程 `SUBMIT` 都可能受影响。

**解决方案**:
- 把 `LowcodeFieldSchema.field -> columnName` 视为发布态运行契约，数据库元数据映射只作为物理列和传统同名字段兼容基础；
- 查询、写入、内部动作、唯一校验、单号、加解密/脱敏和导出使用同一主模型字段映射，禁止每条链路各自猜列名；
- 读取物理列后补充业务字段编码别名，使动作后续步骤、表单回显和外部投影使用稳定字段名；
- 仍必须校验目标列真实存在。缺失时同时报告业务字段、目标列、配置键和表名，引导同步表结构，不能绕过列校验或静默丢字段；
- 排查时分别核对发布模型 `field/columnName`、运行配置版本和 `information_schema.columns`，不要仅凭“页面能看到字段”或“表里有相似列”下结论。

**影响范围**:
- 所有业务字段编码与物理列名分离的低代码查询、表单写入、自动化动作、流程提交和能力开放执行链路。
