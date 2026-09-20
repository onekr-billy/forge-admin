# 低代码单据与审批流程生命周期同步执行记录

> 本文件按执行轮次追加命令、结果与限制，不覆盖历史记录。

## 2026-09-20 新版应用撤回与编辑读取并行（增量）

### 现场与红灯证据

- 只读检查确认用户页面不存在启用的旧单据配置，但存在应用级审批实例；原 `fillApplicationFlowRuntime` 在没有我的待办时提前返回，从未生成撤回动作。
- Admin 日志 `var/logs/forge-admin.log` 显示 `flowRedisListenerContainer` 在撤回事件中回写 `flowStatus=CANCELED` 时抛出“非 web 上下文无法获取 HttpServletRequest”，随后事务 rollback-only；引擎已 canceled，记录/关联仍为审批中。
- 原编辑入口串行请求记录详情与运行态，实测接口分别约 240–421ms 和 285–356ms（只读样本，不作为端到端性能承诺）。
- 新增按钮/失败传播回归后，首轮 25 个已执行测试中 4 个失败。发现旧 `DynamicCrudRepositoryTest` 被根 POM 排除，因此改为独立 `DynamicCrudRepositoryBackgroundAuditTest`，未调整项目测试排除规则。新测试首轮租户断言失败源于 mock 列集合漏掉 tenant_id，补全后通过。

### 实现

- 新版应用不依赖 documentEnabled 或我的待办生成撤回；运行中的发起人且具备真实撤回接口权限才有入口。复用原确认、loading、撤回接口和刷新逻辑；兼容旧配置。
- 动态仓储审计仅对 `SaTokenContextException` 做无会话处理，不虚构管理员或发起人。显式执行身份仍优先，Web 用户仍填充，其他异常继续传播；租户/数据权限不放宽。
- 状态回写不再吞异常；终态回调失败保留原始堆栈。重复撤回事件不重写关联结束时间。
- 编辑/详情加载提取为局部 composable，并行请求记录与运行态；运行态使用请求序号隔离旧响应，详情失败不打开旧数据表单。

### 验证命令与结果

后端（forge-server，JDK 17）：

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home/bin:$PATH \
mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am \
  -Dtest=DynamicCrudRepositoryBackgroundAuditTest,BusinessDocumentRuntimeServiceTest,BusinessFlowServiceLifecycleTest,DynamicCrudServiceChildListTest,DynamicCrudCommandRepositoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- 36 个测试通过（4 + 20 + 6 + 5 + 1），相关模块编译通过。包含无 Web、显式身份、非上下文异常传播、租户条件、无单据模式撤回权限反例、重复回调及子表数据回归。
- 控制台 `write failed` 堆栈来自预期失败传播测试，不是测试失败。

前端（forge-admin-ui，先 `source ~/.nvm/nvm.sh && nvm use v20.19.0`）：

```bash
pnpm --ignore-workspace exec vitest run src/components/ai-form/__tests__/use-record-form-loader.spec.js src/components/ai-form/__tests__/business-action-runtime.spec.js src/components/ai-form/__tests__/use-flow-action-feedback.spec.js
pnpm --ignore-workspace exec eslint src/components/ai-form/AiCrudPage.vue src/components/ai-form/crud/composables/useRecordFormLoader.js src/components/ai-form/__tests__/use-record-form-loader.spec.js
NODE_OPTIONS=--max-old-space-size=8192 pnpm --ignore-workspace build
```

- 28 个测试通过；ESLint 修正一处测试单行多语句后通过；生产构建通过，保留存量 CSS 注释、native config 和动态导入警告。
- Playwright headless Chromium 只读打开用户 runtime 页面及第一条记录编辑表单，两个请求都在点击后约 51ms 发出，编辑表单正常打开、子表区域存在、无 pageerror。未保存、未办理审批。截图位于临时目录 `/tmp/forge-flow-ui-check.mLnZCp/`。
- 浏览器业务接口使用正常前端请求/加密链路，未加 `X-Inner-Call`；登录使用公开 RSA 密钥的标准密码接口，Token 只传入临时浏览器会话，未输出或写入测试文件。
- `git diff --check` 通过。

### 边界与交付

- 按 Forge 流程技能保留 CANCELED 终态和幂等/权限边界；无需开启旧单据模式、重新发起或部署流程。
- 未启动/重启 Admin、Flow、MySQL、Redis；新后端类需用户重新编译并重启 ForgeAdminApplication，前端刷新即可。
- 真实撤回 E2E 未执行；测试不等于生产事务集成验证。此前回写失败的旧记录未修复，也不会因重启自动重放；需核验实例终态后按受控回调补偿，禁止直接重发流程。
- 工作区含并行审计、打印及此前子表变更，未提交或回退任何用户改动。

## 2026-09-19 实现与验证

### 红灯基线

- 前端定向测试：22 个用例中 6 个失败，覆盖 `TO_END/MANUAL` 缺少专用退回发起人回路，以及 `NEED_MODIFY/APPROVED/REJECTED/CANCELED` 未隐藏启动动作。
- 后端首次执行被终端默认 JDK 8 拦截（`无效的目标发行版: 17`）；切换本机 OpenJDK 17 后继续验证，代码本身未因此调整。

### 实现结果

- BPMN 自动路由将普通驳回与退回发起人修改改为互斥条件，并保留原手工出边。
- Flow 普通任务动作清除持久化的 `rejectToStart` 标记。
- Generator 统一同步记录状态与实例关联状态，将 `NEED_MODIFY` 纳入运行态和当前待办查询。
- 单据已有主流程实例后隐藏 `START_FLOW`；终态固定进入查看流程，撤回同步 `CANCELED`。

### 自动化证据

- `pnpm --dir forge-admin-ui --ignore-workspace exec vitest run ...`：最终通过，转换器、JSON→BPMN、业务动作共 44 个用例。
- `mvn -Penable-tests -pl ...forge-plugin-generator -am -Dtest=BusinessDocumentRuntimeServiceTest,BusinessFlowServiceLifecycleTest ... test`：最终通过，8 个用例，包含终态抵御延迟任务事件，相关模块编译成功。
- `mvn -Penable-tests -pl ...forge-plugin-flow -am -Dtest=FlowTaskServiceImplStateChangeTest ... test`：通过，4 个用例。
- `pnpm --ignore-workspace run build`：通过；仅有存量 Vite native-config、CSS `//` 注释和动态导入拆包警告。
- 变更涉及的前端 JS/Vue 文件定向 ESLint：通过。
- `git diff --check`：通过。

### 验证边界

- 未启动 Admin、Flow、MySQL、Redis，也未写入真实业务数据。
- 真实环境仍需按 `test-spec.md` 的普通驳回、退回修改、重提、完成、撤回五条链路做端到端验收。

## 2026-09-20 修改后重提入口补强

### 问题复现

- 单据与流程关联状态均已是 `NEED_MODIFY`，关联表中也已记录发起人修改任务，但 Flow 批量待办查询暂时返回空列表时，单据运行态没有 `myTask`，因此列表无法生成“修改后重提”。
- 新增回归用例后，修复前稳定报错：`runtime.getMyTask()` 为 `null`。

### 修复结果

- 单据运行态仍优先使用 Flow 返回的实时待办；查询为空时，仅在待修改状态下使用关联表中的修改任务快照兜底。
- 快照任务只向记录的任务处理人或流程发起人返回，重提仍完成原流程实例中的修改任务，不创建新实例。
- 兜底后生成 `RESUBMIT_FLOW / 修改后重提`，并继续隐藏 `START_FLOW`。

### 自动化证据

- `mvn -q -Penable-tests -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am -Dtest=BusinessDocumentRuntimeServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，共 7 个用例，包含非处理人不可见校验。
- `git diff --check`：通过。

### 验证边界

- 未办理真实任务、未修改数据库，也未重启当前已运行的 Admin、Flow 和前端服务。

## 2026-09-20 应用级流程重提入口补强

### 现场证据

- Admin 最新日志确认列表已调用运行态批量接口，流程实例关联也能正常查询。
- 当前低代码对象未启用单据模式，`ai_business_document_config` 查询结果为 0；旧逻辑因此提前返回，没有把应用级流程的修改待办转换成列表动作。
- 当前记录的 `Forge_InitiatorModify` 待办及任务快照完整，处理人为流程发起人，无需修复数据库或重新发起流程。

### 修复结果

- 未启用单据模式时不再丢弃应用级流程待办；当前用户有待办时仍生成 `HANDLE_TASK` 或 `RESUBMIT_FLOW`。
- 应用级流程处于 `NEED_MODIFY` 时，列表返回“修改后重提”，继续完成原流程实例中的修改任务。
- 无当前用户待办时保持原有“应用级业务流程”只读提示，不误加操作按钮。

### 自动化证据

- 新增“应用级流程未启用单据模式仍展示修改后重提”回归用例。
- `BusinessDocumentRuntimeServiceTest`：8 个用例全部通过。

## 2026-09-20 终态启动按钮与操作 loading

### 修复结果

- 运行态新增 `startedProcessCodes`，按业务记录批量返回已运行、已成功或已撤回的应用级流程编码；`FAILED` 仍保留重试入口。
- 静态 `START_PROCESS` 按 `processCode` 精确隐藏，不会因一条单据上的其他流程而误隐藏未发起动作。
- 撤回提示改为 `CANCELED` 终态语义；撤回后不再暗示可修改并重新发起。
- 发起应用流程、发起主流程、选定审批人后发起、修改后重提及撤回均增加页面遮罩与动态文案，行动作同步显示“发起中/提交中/撤回中”。

### 自动化证据

- `pnpm --dir forge-admin-ui --ignore-workspace exec vitest run src/components/ai-form/__tests__/business-action-runtime.spec.js`：通过，18 个用例。
- `BusinessDocumentRuntimeServiceTest`：通过，9 个用例。
- `BusinessProcessMapperContractTest#startedProcessLookupIsBatchedAndExcludesFailures`：通过，1 个用例。
- Generator 定向 Maven 命令完成相关模块编译。同类全测试首次执行时，另一条存量 `objectProcessSummaryIsTenantScoped` 断言因其目标 SQL 已不含 `JSON_TABLE` 失败，与本轮改动的 `BusinessProcessRunMapper.xml` 无关，未修改并行代码。
- 本轮 JS/Vue 定向 ESLint：通过。
- `pnpm --dir forge-admin-ui --ignore-workspace run build`：通过；仅有存量 Vite native-config、CSS `//` 注释与动态导入拆包警告。
- `git diff --check`：通过。

### 验证边界

- 遵循既有联调分工，本轮未启动 Admin、Flow、MySQL 或 Redis，未改动真实流程运行态。
- 真实页面仍需在 Admin 重启加载新后端类后，刷新列表验收完成、撤回与 loading 三类交互。
