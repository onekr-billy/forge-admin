# 执行记录
## 2026-09-21
- 在独立 worktree forge-admin-main / codex/open-platform-experience 继续，保留前期开放平台改动及原有 .DS_Store，不修改 H5。
- 读取 AGENTS、DESIGN、测试标准、偏好与 capability 踩坑，使用 Forge 流程开发 Skill 及低代码/状态/验收/SQL 参考。
- 确认低代码 BusinessMessageChannelService 的第三方通道仍返回 TODO，纳入本轮实际打通；不伪装已实现待办同步。

### 实现
- Admin 编排层新增应用集成 API、DTO、Mapper XML 和 Service；插件间不新增循环依赖。新增 V1.0.183 两张引用表，所有查询限定租户/应用，配置保存带 revision，能力发布与关联同事务。
- 复用现有平台连接、消息模板、身份绑定、消息中心和能力发布器。保存应用协同绑定只维护专属通道，解绑停用；指定连接新增租户一致性防护。
- SEND_MESSAGE 节点增加站内信/本应用企业协同选择，明确收件人为流程发起人；使用 runId + nodeId 幂等键。审批回调在可信租户上下文执行，不要求伪造交互登录。入队与实际投递状态在文案中区分。
- 新增 Pinia 应用集成 store 及四页签；迟到请求隔离、防双击、空态/错误、当前能力选择、授权版本策略/失效时间、服务端分页。注册锁定已发布应用来源，不纳入历史全局能力。
- 复用 Naive 主题变量，适配窄屏、深色、表格横向滚动和模态框高度。消息配置拆分为叶子组件，原样外提旧 CSS，ActionAndApprovalNodeConfig.vue 降至 800 行。
- 企业微信入口支持显式连接、保存并恢复原应用深链；授权和回调拒绝已关闭的免登连接。

### 增量验证
- Vitest 最后执行：5 文件、62 tests passed（22:44:52）；ESLint 定向校验无输出，成功。
- Vite build 通过，28.01s；JDK17 Admin reactor 46 模块 BUILD SUCCESS，23.441s（22:45:12）。
- 最后后端测试（22:48:50）：本轮 20/20、原开放平台 37/37，均 failures=0 / errors=0。
- 定向测试首次遇到 Mockito 对 Long 未 stub 的默认值为 0；应用成员断言改为精确匹配 capabilityId，并拒绝非正 ID，重跑通过。
- 常规后端全量测试编译有既有构造器/缺少 import 问题，使用独立 verification/pom.xml 复用真实源码执行本轮定向测试，未修改无关测试。
- xmllint、迁移静态扫描与 git diff --check 通过。没有执行数据库脚本或初始化数据。
- 浏览器夹具真实组件验证连接保存、已保存/未保存提示、能力卡片、授权弹窗/列表回填、调用记录、390px 深色注册步骤及应用锁定；mock 发布后仅关闭一层注册框。390px 页面无横向整体溢出，console error=[]。
- 临时服务和测试标签已关闭，viewport override 已恢复；保留用户原有预览标签。

### 复现命令
从独立 worktree 执行，JAVA_HOME 使用 JDK17，Maven 依赖已安装到本轮临时仓库：

```bash
# forge-admin-ui 目录
./node_modules/.bin/vitest run src/views/ai/capability/__tests__ src/stores/application/__tests__ src/views/app-center/__tests__/application-integration-entry.spec.js src/components/business-process-designer/__tests__/business-process-designer.spec.js
pnpm build

# forge-server 目录
mvn -pl forge-admin-server -am install -Dmaven.test.skip=true

# 仓库根目录（使用同一 Maven 本地仓库）
mvn -f code-copilot/changes/lowcode-application-integrations/verification/pom.xml -Penable-tests test
mvn -f code-copilot/changes/open-platform-experience/verification/pom.xml -Penable-tests test
git diff --check
```

### 交付边界
- 完成代码与本地定向验证，未启动 Admin/Flow、未连接/修改数据库、未真实免登/发信/授权、未部署。
- 钉钉、飞书、企业微信待办同步、任意收件人设计器和外部主子表填报不包含在本期；UI 明示相应边界。
- 没有生成 commit 或 push；原 H5 工作树未改动，现有 .DS_Store 改动保留。
- 使用和上线说明见 usage.md；真实环境验收项留在 tasks.md。

## 2026-09-22 本地增量收尾

### 范围与发现
- 用户要求“继续”，按本地代码完善和验收继续；未收到新的部署目标或真实环境操作授权，维持原安全边界。
- 重读流程开发 Skill、低代码/回调/验收参考，复用已有 spec/tasks/test-spec；检查了主应用扫描、发布器开关、流程验证及消息运行态，不另建流程通知链路。
- 定向测试证实：较旧的配置刷新会覆盖刚保存的 revision；刷新前或刷新中选择连接会丢失。修复为写操作使旧读取失效、写入中禁止同域刷新、刷新保留用户选择。
- 消息节点补充运行时模板启用校验、布尔开关类型和稳定节点 ID 校验；专属应用通道类型发生漂移时拒绝，避免存量动作把异常通道降级为 WEB。继续沿用现有消息中心、幂等键、租户和审批权限。

### 红/绿验证证据
- `vitest run src/stores/application/__tests__/integration-store.spec.js`：修复前 4 failed / 8 passed，明确复现旧 revision 回写及选择丢失。
- 独立后端验证 POM：修复前 24 tests / 4 failures，都是新增消息边界用例；修复后 24 tests / 0 failures / 0 errors（00:04:52）。
- 完整本轮前端定向矩阵：66 tests / 5 files passed（00:03:17）；定向 ESLint 成功且输出为空。
- Admin reactor 使用同一临时 Maven 仓库重建：46 模块 BUILD SUCCESS，22.333s（00:03:45）；之后才运行依赖新构件的测试。
- 原开放平台后端回归：37 tests / 0 failures / 0 errors，3.118s（00:06:21）。
- 前端执行 `node --max_old_space_size=4096 ./node_modules/vite/bin/vite.js build`：通过，26.75s。保留原有 CSS/chunk/timing 提示，不声称全项目零警告。
- `git diff --check` 通过；未修改本轮之外的迁移、包锁、H5 工作树或 .DS_Store。

### 环境及命令说明
- 按偏好尝试 `source /Users/mini32g/.nvm/nvm.sh && nvm use v20.19.0`，返回 N/A；未安装新 Node，使用现有 Node v24.21.0。
- `pnpm --ignore-workspace build` 在自动依赖检查阶段因 `ERR_PNPM_IGNORED_BUILDS` 停止，还未进入构建；没有批准额外依赖脚本。直接运行 package.json 原有 build 脚本正文完成验证。pnpm 自动加入的 allowBuilds 占位项仅恢复本轮产生部分，`git diff --exit-code -- forge-admin-ui/pnpm-workspace.yaml` 确认与基线相同。
- 本轮 Java 命令沿用 `JAVA_HOME=/private/tmp/forge-open-platform-java.CanL0f/amazon-corretto-17.jdk/Contents/Home`，Maven 路径为该临时目录下 `apache-maven-3.9.11/bin/mvn`，追加 `-Dmaven.repo.local=/private/tmp/forge-open-platform-java.CanL0f/repository`；测试显式 `-Penable-tests`。
- 前端定向测试命令沿用上一节的 5 路径矩阵；ESLint 本轮只校验 `integrationStore.js`、`integration-store.spec.js`、`ApplicationCollaboration.vue`。

### UI 与清理
- 只启动 `127.0.0.1:5198` 模拟 API 组件夹具；浏览器实际点击未保存选择、刷新、保存绑定，确认刷新后选择仍保留、保存后免登入口和消息通道回填。
- 控制台 error=[]；本轮没有设置 viewport override。创建的浏览器标签已关闭，临时 Vite 执行会话 30104 已发送 Ctrl-C 并确认退出 130，没有停止用户进程。
- 没有部署、真实数据库/企业微信调用、commit 或 push。真实环境验收清单仍保持未完成状态，不能用这些 mock/单元测试替代。

## 2026-09-22 控制台细节与配置闭环

### 实现与根因
- 在 `forge-admin-main / codex/open-platform-experience` 独立工作树继续，保留已有 `.DS_Store`；原 H5 工作树仍为 `codex/mobile-lowcode-runtime-refactor`、`32915fedd4057fc035fb11925bee4f32b4788912`，工作区干净。
- 先扩展现有 spec/tasks/test-spec，读取 DESIGN、自动化测试标准、Forge 流程开发 Skill 及相关参考，按既有链路做增量修复。
- 顶部导航使用数据库菜单 `/open-platform/capability-*`，文件自动路由却为 `/ai/capability/*`，而 guard 不会注册菜单路由，导致 404。新增统一页面路由描述、兼容别名并复用到 header。导航 SQL 排除了 visible=0 的调用记录/授权工具页，补充这两页原有查询权限的精确校验，避免修复后仍 403；没有全局路由放行或后端接口权限调整。
- 企业连接和接入系统改为同页工作区，保留原列表状态；编辑使用单层局部弹窗。统一紧凑间距、主题色、表格操作列和窄屏表单，应用集成改为连接/免登/消息的配置行布局。
- 连接详情兼容扁平对象及嵌套 VO；读取/提交按连接或客户端生命周期隔离，保存防重复，失败保留编辑。客户端切换或关闭清理子弹窗和旧请求状态。
- 后端配置检查增加 Secret 与企业微信 CorpID/AgentId 条件。专属消息通道启用标记只表示当前应用是否绑定，实际投递复用现有动态校验，避免绑定时无 MESSAGE 永久锁死通道。解绑仍停用，不降级。历史旧通道提供“重新应用配置”入口，升级注意项写入 usage.md。

### 验证证据
- 新增后端用例首次 27 tests / 3 failures，复现缺凭据误报可用与消息通道状态固化；修复后 27/27 通过，最后执行 12:32:12、3.332s。
- 前端最终 11 files / 98 tests passed，12:37:50、2.53s，含最后补充的隐藏工具页权限矩阵。测试存在既有 designer component 重复注册提示，断言均通过。
- 本轮修改和新增的 20 个 JS/Vue 文件全部定向 ESLint 通过，0 errors / warnings。
- JDK17 Admin reactor 46 模块 BUILD SUCCESS，21.341s；最终 Vite 生产构建通过，29.95s。未运行存在既有测试编译问题的全仓测试；复用独立验证 POM，无无关测试源码修改。
- `git diff --check` 通过；修改 SFC 最大 778 行。没有迁移或包锁变化。

本轮前端增量矩阵在 UI 目录执行：

```bash
./node_modules/.bin/vitest run src/views/ai/capability/__tests__ src/stores/application/__tests__ src/views/app-center/__tests__/application-integration-entry.spec.js src/components/business-process-designer/__tests__/business-process-designer.spec.js src/router/guards/__tests__ src/views/system/collaboration/__tests__
node --max_old_space_size=4096 ./node_modules/vite/bin/vite.js build
```

Java 使用上一节同一 JDK17 / Maven / 临时依赖仓库。执行 Admin `-pl forge-admin-server -am install -Dmaven.test.skip=true` 后，执行本变更 `verification/pom.xml -Penable-tests test`。本轮未重复运行未修改的原开放平台 37 项后端矩阵，沿用上一节通过记录。

本地日志：`/private/tmp/forge-console-frontend-tests-final.log`、`/private/tmp/forge-console-vite-build-final.log`、`/private/tmp/forge-console-admin-build.log`、`/private/tmp/forge-console-backend-tests-final.log`。

### 浏览器与交付边界
- localhost:5199 夹具加载真实路由页面和配置组件，但使用有状态模拟 API、列表外壳及用户选择占位组件。已点击顶部三页导航、连接应用编辑/能力绑定、应用集成保存回填，并检查桌面/390px/深色样式；不冒充完整系统 E2E。
- 移动端页面无整体横向溢出；局部表格横向滚动，弹窗内容独立滚动，保存按钮可见。修复最初两个夹具 mock export 缺失后，无新增浏览器 error。
- 未连接真实数据库、企业微信或外部系统，未启动业务服务/执行迁移/部署，未 commit/push。用户原有 file:// 页面是旧静态预览，真实工程改动不会自动更新它。
- 验收结束已恢复浏览器 viewport、关闭本轮标签 6，保留用户原静态预览标签；仅停止本轮 localhost:5199 服务（执行会话 80679，退出 130）。最终 git diff --check 通过。

## 2026-09-22 注册响应与说明优化

### 范围及发现
- 在独立工作树 `forge-admin-main / codex/open-platform-experience` 的已推送基线 `6d6278c6` 上继续，仅保留无关 `.DS_Store`。本轮不提交/推送或部署。
- 复用现有 SDD、DESIGN、测试标准和 Forge 流程 Skill 参考，不另造消息执行链路。用户确认报错表单为自动创建；代码确认 `BusinessApplicationFormDataService.resolveRuntimeDatasource` 为自动建表选择 GenDatasource，随后按独立 JDBC 连接访问。
- 来源加载原先在 await 后才进入第二步，且每次 SYSTEM_SERVICE 都枚举全部服务。现改为同步进入步骤并显示区域 loading，来源接口新增可选 serviceCode，在执行枚举前筛选，旧无参数查询保留。未扩大查询/发布权限。
- 应用内默认填报、名称只读带入，只读发布快照直接载入页面，不再先查应用详情/全量列表；全局入口保留选择。sourceContext 初始化清除旧锁定和名称，Snowflake ID 保持字符串。
- 消息页面改为“发给谁/何时发送/发送什么”，内部通道码默认折叠，说明其为自动路由标识且不需要手工填写。
- 表单能力目前把任何受管运行数据源都拒绝，自动建表同样受影响。它并非用户配置错误；本轮只修正误导文案和空字符串编码误判，保留事务安全保护。受管数据源的完整开放填报仍是待开发项，已写入 tasks/usage，不能声称解决了用户第 2 项的实际使用阻塞。

### 验证及修正
- 复用前轮前端 11 路径矩阵，增加 application-source 组件测试；最终 12 files / 107 tests passed（15:45:11）。
- 新增测试初次失败源于 Vitest 2 不支持 `toHaveBeenCalledExactlyOnceWith`、匿名 stub 无法按名称查找，以及卸载后的 emitted 容器清空；改为该版本可用断言、命名 stub 和持久 Pinia 状态检查后通过，不改业务断言目标。
- 浏览器发现 REST 加载期间默认 kind=FLOW 导致流程模型字段闪现，改为按当前场景推断加载占位类型；复查无闪现。额外分离列表与详情请求序号，避免页面选择器发出初始 null 使 objectLoading 永久挂起，补充回归通过。
- 定向 9 文件 ESLint 最终无输出、exit 0；局部格式化最初从仓库根目录运行未找到配置，改从 UI 目录运行，未改动其他文件。
- JDK17 Admin reactor 46 模块 BUILD SUCCESS（15:32:31，24.386s）；独立开放平台 verification POM 39 tests / 0 failures / 0 errors（15:34:03，4.519s）。无全仓测试或真实 HTTP/数据库服务；原应用集成 27 项沿用前轮结果。
- 优先尝试 Node 20.19.0，当前未安装，使用已安装 Node 24.21.0；直接执行既有 build 脚本正文，未改依赖审批配置。最终 Vite 通过，28.13s；既有 CSS/chunk/plugin timing 警告保留。

### 复现与证据
前端测试路径矩阵沿用上一节，自动包含新增测试；构建为 `node --max_old_space_size=4096 ./node_modules/vite/bin/vite.js build`。Java 沿用上一节 JDK17/Maven 临时仓库，先 Admin install，再执行 `code-copilot/changes/open-platform-experience/verification/pom.xml -Penable-tests test`。

日志：`/private/tmp/forge-registration-front-tests.log`、`/private/tmp/forge-registration-backend-tests.log`、`/private/tmp/forge-registration-admin-build.log`、`/private/tmp/forge-registration-ui-build.log`。

### 浏览器与清理
- 复用 localhost:5199 真实组件夹具，为来源查询注入 6 秒模拟延迟并按 serviceCode 返回；不连接真实业务服务。实际点击 REST/低代码并观察即时 loading、当前应用/单页自动填入和发布确认；消息连接模拟保存后引导文字显示，技术编码默认折叠、可展开。
- 桌面和 390px 深色检查完成，页面宽度/滚动宽度均 390，console error=[]。保留用户原 file:// 静态预览，关闭本轮标签 7，恢复 viewport，停止本轮服务会话 18402（退出 130）。
- 未修改 H5 工作树，检查时其工作区干净，分支 `codex/mobile-lowcode-runtime-refactor`；不干预其他任务的提交更新。无数据库变更、真实发信、部署、commit 或 push。

## 2026-09-22 第二项修复：复用页面新增

### 路线与实现
- 用户确认“表单填报就是复用表单新增”后，撤回本任务未完成的 LowcodeFormStorage、V1.0.184 运行库初始化脚本和 RuntimeJdbcTemplateProvider 连接固定改造；这些均未提交或执行，没有删除用户数据。迁移目录、运行 JDBC provider 最终与基线无差异。
- 按 Forge 低代码流程 Skill 的既有入口/事件约定，将 `POST /ai/crud/{configKey}` 和开放适配器接入 `DynamicCrudCreateManager`，继续调用原 `DynamicCrudService.insert`。不通过 HTTP 自调用、不复制字段转换或建单逻辑、不要求另建业务动作。
- 来源使用原运行数据源解析器检查可写与发布一致性，不再拒绝自动建表的 datasourceId；保留租户、用户委托、具体对象权限、发布版本和字段白名单。新增 storageKey 固定注册时存储路由，兼容旧主库能力快照。
- 平台既有回执由 LowcodeFormInvocationGuard 管理：主库仍同事务；独立运行库先持久化候选主键占位，再调用普通新增。已有空占位视为处理中/未知，不能重放；成功回执返回原 recordId，摘要冲突拒绝。失败只记录能力 ID、键哈希及异常类型，不记录表单正文。
- 共同新增入口保留原创建事件；外层事务时延后到提交成功后，再挂起已提交事务上下文派发，防止事件写入加入已完成连接。消息/流程不是持久 Outbox，不承诺 exactly-once。
- 新增使用说明明确 FORM_SUBMISSION_UNCERTAIN 的人工核对边界，不宣称跨库自动恢复。未放开建单送审组合 SUBMIT 的既有限制。

### 验证与修正
- 最终 Admin reactor 46 模块 BUILD SUCCESS，20.898s，16:34:53；未运行全仓已知有测试编译问题的测试集。
- 最终后端定向 12 类、58 tests / 0 failures / 0 errors，4.202s，16:40:05。verification POM 仅扩展明确测试文件白名单，覆盖本轮公共 Controller 及新增入口；H2 依赖仅 test scope。
- 初次隔离事务测试因 H2 DATABASE_TO_LOWER 把 SQL 列别名 requestDigest 强制小写而失败；改为保留别名大小写后 53 项通过。收尾新增来源列表测试的重载参数/VO 夹具类型，以及权限测试误修改 ExecutionIdentity 返回的副本，均按实际契约修正，最终 58 项全部执行通过，未放宽生产权限判断或删除断言。
- H2 测试使用真实 Mapper XML、Spring 事务和两个独立内存库；覆盖并发与跨库未知结果，但业务写入 Supplier 为测试夹具，不等价于真实 MySQL/动态 CRUD E2E。每个测试完成关闭自己创建的内存库。
- 前端原 12 文件矩阵 107/107 通过（16:30:11，2.91s）；CapabilitySystemSource.vue 定向 ESLint exit 0；生产构建 27.46s、exit 0，既有 CSS/chunk/plugin timing 提示保留。Node 20.19.0 仍不可用，使用已安装 Node 24.21.0，不改依赖审批配置。
- xmllint、git diff --check 通过；jar 清单核验无撤回的初始化脚本/类。前端仅支持文案增量，未重复浏览器模拟验收。

复跑 Java 仍使用 `/private/tmp/forge-open-platform-java.CanL0f` 下 JDK17、Maven 与隔离 repository；先 `-pl forge-admin-server -am install -Dmaven.test.skip=true`，再运行 `code-copilot/changes/open-platform-experience/verification/pom.xml -Penable-tests test`。前端 Vitest 路径矩阵同上一节，构建执行原脚本正文。

日志：`/private/tmp/forge-form-create-admin-build-final.log`、`/private/tmp/forge-form-create-backend-tests-final.log`、`/private/tmp/forge-form-create-front-tests.log`、`/private/tmp/forge-form-create-ui-build.log`。

### 交付边界
- 只修改 `forge-admin-main / codex/open-platform-experience`。H5 工作树仍在自己的分支且有其他任务进行中的改动，仅只读确认，不触碰。保留独立工作树已有无关 `.DS_Store`。
- 未启动业务服务、操作线上数据库、执行迁移、真实发信、部署、提交或推送。真实网关/数据库验收仍由用户执行；当前旧 file:// 静态预览不会自动展示工程改动。

## 2026-09-22 应用内表单能力来源误判修复

### 根因与处理
- 从已推送的 7d80e9d5 基线继续，在 forge-admin-main / codex/open-platform-experience 工作；不修改 H5 工作树，保留无关 .DS_Store。
- 用户提供 ApplicationIntegrationController.system → requireSource 异常。真实 objectSnapshot 漏存 suiteCode，来源校验却以 suiteCode + objectCode 强等匹配；之前测试手工补齐了运行 VO，掩盖实际发布路径。
- 按低代码流程 Skill 的来源身份与既有链路规范，新快照保存对象自己的 suiteCode；归属检查复用 BusinessObjectMapper.selectByObjectCode 的可信租户、套件、编码、逻辑删除条件，按稳定 objectId 和 objectCode 对照已发布成员，有 suiteCode 时也校验。不以应用套件或当前草稿成员列表兜底，不取消权限校验。
- 存量缺字段快照无需修改/重发；缺 ID、空来源、跨套件同编码、删除重建或草稿新增对象失败关闭。不改表结构、迁移、前端或表单新增/幂等逻辑。

### 红绿验证
- 修复前本变更 verification/pom.xml：42 tests / 4 failures / 0 errors（17:30:19）；复现缺字段、旧版本注册被拒、缺少租户身份查询以及 null suiteCode 与缺字段匹配而误放行。
- 修复后 JDK17 Admin reactor：46 模块 BUILD SUCCESS，22.379s（17:31:39）。
- 本变更定向测试：42 tests / 0 failures / 0 errors / 0 skipped，4.451s（17:32:12）；新增 ApplicationIntegrationSourceTest 15 项真实快照生成和读取回归。
- 原开放平台回归：58 tests / 0 failures / 0 errors / 0 skipped，5.479s（17:32:14），覆盖此前共用新增入口及回执安全行为。
- git diff --check 通过。未跑已知存在无关测试编译问题的全仓测试；模拟 Mapper 结果不等同真实数据库、HTTP 或生产环境验收。

### 复现命令与交付边界
沿用 /private/tmp/forge-open-platform-java.CanL0f 下 Corretto17、Maven3.9.11、repository；从独立工作树运行：

```bash
mvn -f forge-server/pom.xml -pl forge-admin-server -am install -Dmaven.test.skip=true
mvn -f code-copilot/changes/lowcode-application-integrations/verification/pom.xml -Penable-tests test
mvn -f code-copilot/changes/open-platform-experience/verification/pom.xml -Penable-tests test
git diff --check
```

实际命令使用上述 Java/Maven 绝对路径并加 -Dmaven.repo.local=/private/tmp/forge-open-platform-java.CanL0f/repository。日志均在 /private/tmp：forge-published-source-red.log、forge-published-source-admin-build.log、forge-published-source-green.log、forge-published-source-platform-tests.log。本轮未启动业务服务、操作数据库、部署、commit/push；升级 Admin 后重试注册仍待用户环境验收。

## 2026-09-22 应用业务流程开放：替换旧主流程口径

### 根因与实现
- 用户指出应用已发布、业务流程内已有审批节点，注册仍报旧主流程错误。确认注册器原 FLOW_ACTION 分支由 FlowActionSourceService 读取 ai_business_binding，与应用业务流程编排不是同一来源。
- 按 forge-business-flow-development Skill 读取应用快照/业务流程版本并复用既有编排器，未新增另一套审批引擎。新增受控 SYSTEM_SERVICE `lowcode.business-process.start`；来源按应用发布版本、页面主对象身份和业务流程版本匹配，不查询旧主流程绑定。
- 仅提供手动发起，输入只有已保存 recordId；使用用户委托、原动态 CRUD 数据范围及节点权限。固定应用版本、流程版本和 hash，编排器启动前再次匹配版本；旧失败运行不会被外部重复调用自动重试。原页面启动/重试保持原语义。
- 注册弹窗默认“应用业务流程”，原对象审批标注旧版；共享来源状态放 Pinia，新增独立流程选择组件/组合函数。当前应用自动填入；单可用流程自动选，多流程显式选；立即 loading、失败重试、切换/关闭迟到结果隔离；升级重试不自动替换原来源。
- 应用内 system 发布接口核验来源 applicationId 与路径一致；上一轮发布快照 suiteCode 修复继续保留。无数据库脚本、线上数据或既有能力版本修改。

### 最终验证
- JDK17 Admin reactor 46 模块 BUILD SUCCESS（18:02:36，24.570s），先 install 更新本轮依赖，再执行独立 verification POM。
- 开放平台 14 测试类、86 tests / 0 failures / 0 errors / 0 skipped（18:03:33，5.333s）。新增应用业务流程服务 17 项，复用真实编排器测试共 11 项，覆盖版本漂移、正常启动/重复复用、失败不自动重试；保留表单共用新增入口等原回归。
- 应用集成 4 类、43 tests / 0 failures / 0 errors / 0 skipped（18:03:28，4.038s），包括真实快照生成/读取及应用内跨应用发布拦截。
- 前端原矩阵加新来源测试，共 13 文件、115/115 通过（18:07:09，2.82s）；7 个任务 JS/Vue 文件最终 ESLint 通过，无 errors/warnings。Vitest 保留现有 designer-core transfer 重复注册提示。
- Vite 生产构建通过（33.17s）；Node 20.19.0 未安装，使用现有 Node 24.21.0，直接执行原 build 脚本正文，未改依赖审批设置。已有 CSS/chunk/plugin timing 警告保留。
- 首轮 Java 编译发现身份上下文没有 require API，改用已有 current().orElseThrow；首轮测试修正缺失 verifyNoInteractions import、Mockito Spring context 所需 BusinessApplicationVersionMapper，以及 SERVICE 身份测试构造不符合契约的问题。修复后重新编译/运行上述全部断言，没有删除失败断言或放宽权限判断。定向 ESLint 首轮仅格式问题，针对任务文件修复后重新测试。

### 可复跑命令与证据

从 forge-admin-main 根目录，沿用已存在的 JDK/Maven 临时工具链：

```bash
export JAVA_HOME=/private/tmp/forge-open-platform-java.CanL0f/amazon-corretto-17.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:/private/tmp/forge-open-platform-java.CanL0f/apache-maven-3.9.11/bin:$PATH"
mvn -B -ntp -Dmaven.repo.local=/private/tmp/forge-open-platform-java.CanL0f/repository -f forge-server/pom.xml -pl forge-admin-server -am install -Dmaven.test.skip=true
mvn -B -ntp -Dmaven.repo.local=/private/tmp/forge-open-platform-java.CanL0f/repository -f code-copilot/changes/open-platform-experience/verification/pom.xml -Penable-tests test
mvn -B -ntp -Dmaven.repo.local=/private/tmp/forge-open-platform-java.CanL0f/repository -f code-copilot/changes/lowcode-application-integrations/verification/pom.xml -Penable-tests test
git diff --check
```

从 forge-admin-main/forge-admin-ui：

```bash
export PATH=/Users/mini32g/.nvm/versions/node/v24.21.0/bin:$PATH
./node_modules/.bin/vitest run src/views/ai/capability/__tests__ src/stores/application/__tests__ src/views/app-center/__tests__/application-integration-entry.spec.js src/components/business-process-designer/__tests__/business-process-designer.spec.js src/router/guards/__tests__ src/views/system/collaboration/__tests__
./node_modules/.bin/eslint src/stores/capability/registrationStore.js src/views/ai/capability/components/CapabilityRegisterModal.vue src/views/ai/capability/components/CapabilityProcessSource.vue src/views/ai/capability/components/useApplicationProcessRegistration.js src/views/ai/capability/components/useCapabilityRegistration.js src/views/ai/capability/__tests__/registration-workflow.spec.js src/views/ai/capability/__tests__/application-process-source.spec.js
node --max_old_space_size=4096 ./node_modules/vite/bin/vite.js build
```

日志位于 /private/tmp：forge-process-capability-build-final.log、forge-process-back-tests-final.log、forge-process-integration-tests-final.log、forge-process-front-tests.log、forge-process-ui-build.log。最终前端 18:07 复跑和 ESLint 结果见当前任务命令输出，早先 front-tests.log 为首轮 115 项通过记录。

### 浏览器和交付边界
- 复用仅监听 127.0.0.1:5199 的真实 Vue 组件夹具，以模拟 API 延迟 2 秒返回两个业务流程。实际点击当前应用 → 注册能力 → 流程操作 → 应用业务流程 → 选择审批流程 v3 → 发布确认，确认只输入 recordId，模拟发布正常返回列表；页面来源加载时立即反馈并禁止继续。
- 桌面与 390px 深色检查通过，width/scrollWidth 均为 390，控制台 error=[]。这不是实际审批或开放 HTTP 联调；夹具替换了 API 与 AiCrudPage 列表外壳。
- 已恢复 viewport、关闭本轮标签 8，保留用户原 file:// 页面；仅停止本轮 Vite 会话 75789（退出 130）。没有启动或停止用户 Admin/Flow/MySQL 服务。
- 仅修改 forge-admin-main / codex/open-platform-experience，保留已有 .DS_Store，不操作 H5 工作树。未执行全仓已知失败的测试编译、真实数据库/网关/审批/消息 E2E、迁移、部署、commit 或 push。部署前人工权限/状态流转审查与真实环境验收仍待用户执行。

## 2026-09-22 应用业务流程发布风险等级修复

- 现场异常发生在 `CapabilityCatalogService.publishSystemService`：应用业务流程注册来源返回 `HIGH`，而受控系统服务目录契约和当前客户端授权只接受 `SYSTEM_SERVICE / ACTION / MEDIUM`，因此在应用、页面和流程来源均已校验通过后仍被拒绝。
- 将 `lowcode.business-process.start` 定义为受控中风险系统服务。它仍固定应用/对象/流程版本，只接受已保存 `recordId`，并保留用户委托、启动权限、节点权限、数据范围、幂等与编排器校验；没有放宽高风险能力发布或授权。
- 新增来源风险等级回归断言，并在系统服务发布器测试中断言最终目录命令为 `ACTION / MEDIUM`。修复前定向测试 17 项中 1 项按预期失败（`expected MEDIUM but was HIGH`）；修复后开放平台 86/86 通过。
- JDK 17 Admin 46 模块聚合安装 `BUILD SUCCESS`，总耗时 24.786s；补齐最终发布器断言后开放平台 verification 总耗时 5.873s。未改前端/数据库/Flyway，未启动服务或执行真实网关、审批、消息 E2E。

## 2026-09-22 注册、归属与授权闭环

### 实现
- 表单来源由“进入场景即全量扫描业务对象”改为“先读应用发布页面，选中后按 `applicationId + objectId` 读取单一来源”；高级直接选对象入口保留按需全量加载。后端上下文请求只调用 `BusinessObjectService.detail`，不再调用列表扫描。
- 新建流程能力仅保留应用业务流程；旧 `FLOW_ACTION` 数据和升级兼容继续存在，但不再向新建用户展示“旧版对象审批”。`lowcode.business-process.start` 的发布元数据保持 `SYSTEM_SERVICE / ACTION / MEDIUM`。
- 目录选择应用来源时统一经应用发布接口写入关联；应用能力页首次进入和手动刷新调用显式同步接口，从当前不可变发布快照幂等认领业务动作、存量流程、表单和应用流程能力。任意 REST、跨应用来源和坏 JSON 不自动归属；同一来源换编码重复注册由服务端拒绝。
- 系统服务授权把 Java null、Jackson `NullNode` 和空对象统一视为无字段策略并落库 NULL；数组、标量及非空对象仍失败关闭。前端应用授权请求同步发送 null。
- 在线测试身份来源从分段 RadioButton 改为普通 Radio，去除蓝色内部竖分隔，不改变身份语义。

### 验证
- 前端定向 Vitest 3 文件、41/41 通过；定向 ESLint 0 errors / 0 warnings；Vite build 26.79s 通过，仅保留既有 CSS/chunk/plugin timing 提示。
- JDK17 Admin reactor 46 模块最终 `BUILD SUCCESS`（20.795s）。开放平台 verification 96/96、应用集成 verification 47/47，均 0 failures / errors / skipped。应用集成首轮有 1 个 Mockito 调用次数夹具与新同步顺序不一致；补存量同源候选只认领一次后，首次编译又发现局部变量与 lambda 参数同名，改名后重新完成全量构建和 47 项回归，未放宽生产校验。
- `xmllint` 与 `git diff --check` 通过；构建产生的 pnpm workspace 审批占位变更已恢复。保留工作树原有无关 `.DS_Store`。

### 边界
- 未启动 Admin/Flow、连接 MySQL、调用开放网关、执行真实审批或部署；没有数据库迁移。同步、唯一来源及授权策略由单元/Mapper 契约测试覆盖，不等价于线上 E2E。
- 本轮在 `forge-admin-main / codex/open-platform-experience` 独立 worktree 完成，未 commit、未 push，未触碰 H5 工作树。
