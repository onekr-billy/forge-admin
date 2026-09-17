# Forge Admin 项目长期记忆

## 0. 铁律（最先看）
1. **不编造**：所有能力点、类名、行号必须现场 `wc -l` / `grep` 核实（用户原话："你要写框架贴合实际的功能 别编造"）
2. **发前核对仓库地址**：Gitee `https://gitee.com/ForgeLab/forge-admin` / GitHub `https://github.com/yaomindong1996/forge-admin` / 文档 `http://www.dlforgelab.com:8084/forge-docs/` / 演示 `http://www.dlforgelab.com:8084/forge/login`（admin/123456）。曾凭记忆写错地址被纠正
3. **头条连续低量时禁止盲猜选题**，先看后台数据（展现量级）再动笔。0917 已验证：**同号同天同长度，钩子类型能造成 25 倍展现差距**——数据差的锅先算在钩子上，别急着怪账号

## 1. 账号与渠道现状
- **头条：账号没有权重问题**（0917 实测推翻此前"新号冷启动"的诊断）。同号同天同样 300 字微头条：数字反差型 **7981 展现 / 1330 阅读 = 点击率 16.7%**；反常识型 319 展现 / 15 阅读 = 4.7%。**差 25 倍 → 系统愿意给量，之前是钩子没打中**
- **微头条 > 长文**：300 字完读率高，长文 2000 字完读率低。当前策略：**微头条主打，长文低频**（长文仅用于验证"钩子 vs 完读率"谁是瓶颈）。每天发 2 条、间隔 4h+，发后 2h 内自己先补一条评论
- **掘金**：唯一有正反馈的渠道，有阅读但赞少。系列连载（源码拆解）是点赞主要来源
- 演示站可从外部抓取（curl 200 / agent-browser 截图成功），需要素材可直接截图

## 2. 头条写作规则
### 🔥 已验证的钩子公式（数字反差型，0917 实测 16.7% 点击率，最高优先级）
1. **第一行必须是纯中文数字，绝不能出现代码符号**（`@Async` / 英文类名一律后置或删掉）— 这是第 1 条赢、第 2 条输的**决定性差异**
2. 数字后紧跟"只干一件事 / 只有 X 行"的极端聚焦
3. 中段必须有「你以为是 A，实际是 B」的反差
4. 后果翻译成**钱 / 安全 / 时间**这类人人都懂的东西，不要停在技术层（"扣了3次款" > "接口慢"）
5. 结尾必须提问——评论数是头条推流关键指标

### 标题
- 30 字以内，**禁用圈内黑话**（DHH / Harness / Elicitation 曾致点击率仅 2%）
- 范式：新闻事件名 + 数字 + 后果；大白话 + 具体能力描述
- **不要带"开源框架"**（带项目名限流）——但 0901 用户又要求"标题必须带 ForgeAdmin"，结论：**踩坑警示型 + 项目名**是用户认可的组合，其余类型不带

### 结构公式
真实故事开头（数字反差/冲突）→ 痛点共鸣 → 拆解 → 算账对比表 → 客观说不足（5 条）→ 反思 → 互动（扣1/扣2）→ 项目地址

### 去 AI 味 8 条（最重要方法论）
开头直接进冲突 / 每段 3-4 句绝不超 5 句 / 口语词（哥们、那天、你猜怎么着）/ 具体数字代替形容词 / 真实踩坑经历开头 / 不用"首先其次最后"改"第一层第二层" / 结尾不升华用提问 / 适度自嘲。**禁用**：赋能、闭环、抓手、沉淀、致力于、旨在

### 已扑街方向（避坑）
- 故事人物叙事（"同事要离职"）→ 零流量，被判职场情感文
- 纯情怀（"做开源值不值"）/ 纯观点（低代码反常识）/ 偏商业（中小企业不外包）
- 偏源码拆解或介绍性的头条文（0826 MCP、0830 横评、0903 项目介绍）热度都不够
- **每写 5-8 篇必须切赛道**，账号层面同质化累积会触发降权

## 3. 掘金写作规则
### 结构公式
动机故事（踩坑起因）→ 设计原则（3-5 个）→ ASCII 全景图 → 核心接口（完整代码）→ 注册中心（fail-fast）→ 模型层 → 真实实现拆解（贴 60-100 行）→ 实战接入（4 步）→ 踩坑（4-5 个）→ 可带走的 N 条诀窍 → 总结 + 下一篇预告

### "有人看没人赞"对策四件套
① 开头玄学 Bug 钩子 ② 坑表四列（坑/现象/根因/解法）③ "可带走的 N 条诀窍"独立小节 ④ 结尾明确求赞 + 赌局式互动。**系列文必须兑现上一篇预告并预告下一篇**
字数 4500-5500（含代码）。标签：`#低代码` `#Spring Boot` `#Java` `#架构设计` `#企业开发`

### 已写 15 篇（避免重复）
订单系统业务设计 / 零代码搭进销存 / AI能力治理规划 / 从零搭CRM / 低代码与Flowable工作流整合 / Flowable注解化接入 / MCP-Server插件源码拆解 / 协作SPI解耦设计(0906) / 数据权限拦截器SQL改写(0907) / 协议驱动vs代码生成 / crypto接口加解密全链路(0909) / 多租户tenant源码拆解(0910) / 多租户×数据权限共存-拦截器注册顺序(0911) / 幂等starter 1279行 5坑(0915) / log操作日志 1383行 5个反直觉设计(0916) / **auth认证链路 4091行 账号锁定为何完全失效(0917)**

### "框架源码拆解"系列进度
① datascope SQL 改写 → ② tenant 多租户 → ③ 共存（0911）→ ④ 幂等（0915）→ ⑤ log 操作日志（0916）→ ⑥ auth 认证链路（0917）→ ⑦ **excel 4223 行（已预告：@Async 自调用 + 异步导出）**

### 可复用硬核事实
- **MyBatis-Plus 3.5.7**；`MybatisPlusConfig` 用 `List<InnerInterceptor>` 注入
- 拦截器顺序是**隐式契约**：两个拦截器都无 `@Order`，靠 `@AutoConfigureOrder` 数字（datascope HIGHEST_PRECEDENCE 先 / tenant +10 后），项目无顺序断言测试
- `CountOnePaginationInnerInterceptor` 把 `COUNT(*)` 改 `COUNT(1)`，是为让 tenant+datascope 两个解析器都能解析深嵌套 SQL
- `_mpCount` / `_COUNT` 是分页 count 的 MappedStatement 后缀；数据权限需剥离后缀才能匹配配置
- `TenantContextHolder` = TTL + 保存恢复（可嵌套）；`DataScopeContextHolder` = 普通 ThreadLocal + clear（嵌套覆盖、线程池不传）
- crypto：FPC1 密文格式 `{算法}:{keyId}:{密文}` 五态（ACTIVE/HISTORICAL/LEGACY/UNKNOWN/CORRUPT）；`@Desensitize` 8 种预置策略；四级放行 + 算法三级优先级 + `DecryptedHttpInputMessage` 偷换 body 流
- 操作日志三快照 before/after/diff（V1.0.14 迁移）；`sys_login_log` + 账号锁定（AbstractAuthStrategy/LoginLockService）
- plugin-ai：`PermissionEngine` 63 行三态判决（工具名含 delete/submit/commit 触发人工审批）；`AiModelInvocationLog` 记 token + 调用时单价快照（按"分"存）

### starter 剩余矿脉（按行数）
tenant（已写）| idempotent 幂等（已写 0915）| log 操作日志（已写 0916）| auth 认证链路（已写 0917）| **excel 4223 行（已预告：@Async 自调用 + 异步导出）** | websocket | config 动态配置 | orm | cache

### 已核实待用的硬核事实（log / idempotent / excel / auth）
- **log**：`OperationLogAspect` 676 行，切点 `@within(@Controller)||@within(@RestController)`（`@annotation` 版被注释掉）→ 所有 Controller 方法进切面；skip 判定前已完成 4 件事含 `apiConfigManager.getApiConfig()`；线程池默认 **core=2/max=5/queue=500 + CallerRunsPolicy**（高峰期业务线程自己写日志）；QUERY 全跳过；URL 后缀自动分类（/page /list /tree /detail /getbyid /options /profile /query→QUERY）；8 个敏感凭证路径直接 exclude（/auth/login 等）；`@ApiDecrypt` 接口的 `@RequestBody` 参数替换为 `[DECRYPTED_REQUEST_BODY_OMITTED]`；`OperationAuditContext` 是**普通 ThreadLocal**（切面在主线程 fillAuditSnapshot 拷贝进 POJO 再异步提交，规避了跨线程丢失）；**OperationLogInfo 无 traceId 字段**（只进 MDC），且 finally 里先 saveLogAsync 再 MDC.remove → 异步线程无 traceId；LogProperties 默认 requestParams/responseResult 截断 2000 字符
- **idempotent**：见 2026-09-15 日志。核心：**STRICT 的 `annotation.expire()` 是死变量，锁租期固定 5s 且 Redisson 不启动 watchdog**；`extractToken()` 强转 bug（见下）
- **log（补充）**：两条独立链路（AOP 切面 + `LoginLogListener implements SaTokenListener`），共用同一个 `logTaskExecutor`；**`ILogService` 是 SPI，框架层无实现**，两个入口都 `@ConditionalOnBean(ILogService.class)`（不实现就不注册，零开销）；Sa-Token `doLogin` **只在登录成功时回调** → 失败登录不进 `sys_login_log`（失败只调 `loginLockService` 计数）；`doKickout/doReplaced/doDisable/doUntieDisable` 4 个方法漏了 `enableLoginLog` 开关检查
- **excel**：4223 行 / 30 类；`AsyncExportServiceImpl.submitExportTask` **内部自调用** `@Async protected executeExportAsync()` → 不走代理 → 异步完全失效；`taskStore` 用 ConcurrentHashMap（注释自认"生产建议用 Redis"，多实例/重启丢任务）；`downloadFile` 用 `Files.readAllBytes` 一次性读入内存（大文件 OOM）
- **auth**（4091 行 starter + 927 行 strategy，0917 全量核实）：
  - `AuthType` **7 种**：password / password_captcha / phone_captcha / wechat / email_captcha / oauth2 / social
  - 模板方法 `AbstractAuthStrategy.authenticate()` 四步：validateRequest → doAuthenticate → checkUserStatus → handleLoginSuccess
  - `AuthStrategyFactory`：精确匹配 client > 类型匹配（`supportedClient==null` 兜底），ConcurrentHashMap 缓存 `authType_client → strategy`
  - `AuthProperties` 默认：**enableLoginLock=true / maxLoginAttempts=4 / lockDuration=30min / failRecordExpire=15min**
  - 登录查询必须 `TenantContextHolder.executeIgnore(...)` 绕开租户过滤（登录时还不知用户属哪个租户）→ 跨租户查同名候选 → 逐个 matchPassword → 单工作区直接进 / 多工作区抛 `TENANT_SELECTION_REQUIRED` 让用户选；**同名多租户可有不同密码且都能登录**
  - `UsernamePasswordAuthStrategy` 仅 42 行；`SocialAuthStrategyImpl` 460 行（最重）

### ⚠️ 待修真实缺陷（累计 8 个，均未修复，按严重度排序）
1. **【高危】账号锁定在密码错误场景下完全失效**：`UsernamePasswordAuthStrategy.doAuthenticate` 里 `if (loginUser == null)` 是**死代码**（`authenticateByUsernamePassword` 失败时抛 RuntimeException，永远不返回 null）；且即便进入分支，`recordLoginFailure(null, ...)` 也会因 `loginUser == null` 在第一行直接 `throw`，不计数不锁定。**结论：暴力破解密码不会被锁定**。修复：认证方法失败时返回可区分结果（sealed interface / Optional），计数方法标 `@NonNull`，不要用异常表达失败路径
2. **死常量 key**：`LoginLockServiceImpl.LOGIN_LOCK_KEY_PREFIX = "login:lock:"` 全项目只有声明 + `clearLoginFailure` 里的 delete，**从无任何写入**；真正锁定走 `StpUtil.disable()`
3. **计数窗口 < 锁定窗口**：failRecordExpire(15min) < lockDuration(30min) → 锁定期间 failKey 已过期清零，解锁后攻击者重新获得完整 4 次机会
4. **安全开关 fail-open**：`loginLockService` 是 `@Autowired(required=false)`，Bean 缺失时 `isLoginLockEnabled()` 静默返回 false，防护消失且无任何日志
5. **提示泄露剩余次数**："还剩 N 次尝试机会" → 用户名枚举漏洞（用户不存在 vs 密码错误返回不同信息）
6. `forge-starter-idempotent/.../TokenRequiredStrategyHandler.extractToken()` 把 `getRequestAttributes()`（RequestAttributes）强转成 `RequestContextHolder` → 必然 ClassCastException 被 catch 吞 → token 恒 null → **TOKEN_REQUIRED 模式 100% 抛 TokenInvalidException**
7. `forge-starter-excel/.../AsyncExportServiceImpl` `@Async` 自调用失效 → "异步导出"实为同步
8. idempotent STRICT 的 `annotation.expire()` 是死变量，锁租期固定 5s 且 Redisson 不启动 watchdog

### 其他候选切面
能力开放网关 SPI（capability-parent，REST+MCP 双出口）/ 11 个 plugin 注册顺序 / CRUD Velocity 模板扩展点 / 部署上线踩坑 / 运维故障救回

## 4. 头条已写 26 篇长文 + 8 条微头条（角度清单）
踩坑8个 / 4框架横评 / 协议驱动vs代码生成 / 业务闭环更新 / 反常识观点 / ForgeAdmin实测能力全景(0725 唯一热过) / 搭审批系统实战(0726) / 接私活8000块2天 / 半天搞定CRM / 企业集成与开放平台(0804) / AI-Agent安全操作后台(0826) / 开源项目介绍横评(0903) / 同事离职3天重构(0906) / 一张表生成多少代码2123行实测(0907) / 企业6大真实业务场景(0907) / AI写完100万行怎么管(0908) / DHH那篇反共识(0908) / GitSpawn 7款工具中毒(0909) / DeepSeek降价算账(0909) / 等保测评师查5样(0910) / 手机号明文3行SQL(0911) / 操作日志接口慢10倍676行(0915) / 异步导出卡3分钟@Async失效(0916) / **500并发同订单扣3次款1279行幂等(0917)**
- **微头条**：0916 第1批 5 条（数字反差 7981展现✓ / 反常识 319 展现✗ / 求助提问 / 场景共鸣 / 清单盘点 未测）+ 0917 第2批 3 条（账号锁定 / 死常量 / 日志性能，全部数字反差型）

### 关键事实备查
- 实际 starter **23 个**（非 README 的 20），plugin **11 个**
- generator Velocity 模板 17 份 1527 行；purchase 样例生成 15 文件 2123 行
- 流程增强（多级退回/改派/自选审批人/驳回直送）代码在 flow-enhancement-suite（0826 提交）
