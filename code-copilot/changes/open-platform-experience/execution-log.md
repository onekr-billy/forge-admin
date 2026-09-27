# 执行记录

## 2026-09-21
- 从独立 worktree 的 `codex/main-feature-development` 创建 `codex/open-platform-experience`，基线 c06cb0f7。
- 工作区原有 `.DS_Store` 修改保留。
- 完成目录、注册器、客户端、系统服务适配及能力安全规范梳理。
- 本机未发现 Maven/JDK，新 worktree 尚无前端依赖；验证时补充实际结果。

## 实施与复核

- 目录与接入系统重新分工；注册器三步引导，技术字段折叠、默认生成编码、发布前来源/字段确认。
- 提取注册、调用指南、在线测试、客户端工作台逻辑；Pinia 承载注册跨组件状态，SFC 均小于 800 行。
- 应用页面从发布快照解析业务对象；通用单表表单使用已发布运行配置与字段白名单，复杂来源明确拒绝。
- 新增 REST 显式纳管注解及受控定义，字典查询示例；新增低代码原子建单回执；撤回走既有业务流程服务及实例校验。
- 使用流程开发 Skill 的边界约束：保持原业务流程服务、委托用户、租户/组织、发起人检查和状态回写；不直接修改流程表。
- 测试中修复：空策略字段导致白名单误筛空、数据源快照未扁平化检查、撤回实例固定、草稿关闭重开竞态、异步校验期间双击、Naive UI 卡片内容类名错误导致底部按钮裁切。
- 对照菜单迁移核对 `/open-platform/*` 路由；“机器客户端”只重命名内置菜单，不扩大授权。

## 验证证据（2026-09-21 21:20 收尾）

- 前端依赖在独立 worktree 安装；恢复 pnpm 自动附带的 workspace 配置修改，package.json、lockfile、workspace 配置均无交付差异。
- 官方临时 JDK 17/Maven 位于 `/private/tmp/forge-open-platform-java.CanL0f`，下载校验摘要通过；使用独立 Maven repository，没有修改系统 Java/Maven 或用户全局配置。
- 定向 ESLint：退出码 0，无 errors/warnings。
- Vitest：`Test Files 2 passed (2)`，`Tests 22 passed (22)`（21:18:39）；具体命令见 test-spec。
- 生产构建：`✓ built in 28.95s`，退出码 0。保留存量 CSS/体积/构建插件计时警告，不宣称全项目零警告。日志 `/private/tmp/forge-open-platform-build-final.log`。
- 能力模块 reactor（含 system/core 依赖）40 模块 install：`BUILD SUCCESS`。日志 `/private/tmp/forge-open-platform-backend-final-install.log`。
- 常规 reactor 测试首先被存量 `DynamicCrudPrintReadTest` 构造器缺少 DataAuditCaptureService 阻断；单独模块测试又被 `SecureActionCatalogServiceTest` 缺少 SecureActionDescriptor 导入阻断。没有为此修改无关测试。
- 使用交付的 `verification/pom.xml` 编译/执行相关 9 类测试：`Tests run: 37, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`（21:15:31）。包含调整后的自动装配测试，不是只跑新类。日志 `/private/tmp/forge-open-platform-isolated-tests.log`。
- Admin 完整聚合打包 46 模块：`forge-admin SUCCESS`、`BUILD SUCCESS`（21:20:55）。中间依赖下载耗时约 4 分钟，最终通过。日志 `/private/tmp/forge-open-platform-admin-package.log`。
- `xmllint --noout --nonet`：Mapper XML、verification POM 通过。
- SQL 版本唯一、防重复保护、审计字段与幂等索引检查通过；181/182 无 `${...}` 占位符，不含 DROP/DELETE/TRUNCATE。
- `git diff --check` 通过；本 worktree 无 H5 文件差异，原 H5 工作区及分支未切换、未编辑。

## 浏览器验证及环境收尾

- 临时夹具使用真实注册器/接入工作台组件，API 全部模拟；`127.0.0.1:5197`，未访问真实业务接口。
- 验证应用选择、页面绑定、表单字段及必填锁定、确认页、REST 接口参数展示、授权工作台空态，浅色/深色和 390×844 窄屏；最终 console errors 为空。
- 预览只验证组件交互/布局，不代表整页路由或真实服务 E2E。默认视口已恢复、临时浏览器页已关闭。
- 本轮 Vite 预览（终端会话 29424）已通过 Ctrl-C 停止，退出码 130；未停止用户其他服务。Maven 聚合打包正常结束。
- 未启动 Admin/Flow、未连接数据库/Redis、未运行 Flyway、未操作真实客户端凭据或授权、未执行真实审批。SQL/权限人工审查与测试环境联调仍是上线前门槛。
- 未生成 Git commit，未 push，未部署。后续开发继续在此独立 worktree；使用与上线指南见 integration-guide.md。

## 2026-09-22 调用与测试工作台增量

### 实现范围
- 在独立 forge-admin-main / codex/open-platform-experience 上继续，保留前两轮尚未提交的表单来源/应用业务流程修复与无关 .DS_Store；不修改 H5 工作树。
- 先补当前 spec/tasks/test-spec，读取 DESIGN、用户偏好与自动化测试标准。首屏改为接入系统选择和阻断项优先，提供明确的“开始测试”入口；技术地址、参数契约、Curl/Java 与导出单独收纳。
- 在线测试拆成身份、请求、结果三个面板；普通标量/嵌套标量字段直接填写，复杂/未知结构保留 JSON，双向共用同一请求体。字符串长 ID 不转 Number，JSON 未知字段不因编辑一个字段而丢失，必填/类型错误不会发起请求。
- 两个 Pinia store 保存工作台/测试会话状态，不做持久化。初始 show=true 正常加载；列表/指南分别隔离过期请求。换接入系统或关闭清除输入，查看文档则保留填写。
- USER 委托、OAuth/HMAC、原授权升级权限/二次确认、副作用确认保持。确认后立即关闭提示框，在原工作区显示进度，防双击；每条异步链绑定本次运行，关闭/换源后旧令牌不会继续触发业务请求，不混入新的选择。
- 结果默认展示业务返回、耗时与 requestId，Token/完整报文折叠；认证字段脱敏且不记录日志。网络失败不自动重试、不宣称业务未执行。没有修改后端网关、数据库、权限资源或流程状态机。

### 测试与构建
- 首轮新增测试 23/23 通过；随后增加身份方式切换保留密钥、HMAC 签名及过期确认解锁，共新增 26 项。
- 最终复用前端原矩阵：16 文件，141 tests passed，18:59:08，2.56s。包含上一轮注册器/流程设计器/应用集成/路由/协同测试，没有跳过失败断言。
- 14 个任务 JS/Vue 文件定向 ESLint 通过，0 errors/warnings。首轮修正格式、子组件直接修改 props、布尔 Select 值与输入类型兼容；收尾改动的指南 hook/测试再次定向 lint 通过。
- 最终 Vite production build：built in 27.13s，exit 0。Node 20.19.0 未安装，使用现有 24.21.0；直接执行原 build 脚本正文，不修改 pnpm 依赖审批配置。保留既有 CSS/chunk/plugin timing 与测试 transfer 重复注册提示。
- git diff --check 通过，修改 SFC 最大 356 行。本轮后端无新增差异，不重复 Java 构建；之前后端修复验证沿用 lowcode-application-integrations/execution-log.md。

### 可复跑命令
从独立工作树 forge-admin-ui 执行：

```bash
export PATH=/Users/mini32g/.nvm/versions/node/v24.21.0/bin:$PATH
./node_modules/.bin/vitest run src/views/ai/capability/__tests__ src/stores/application/__tests__ src/views/app-center/__tests__/application-integration-entry.spec.js src/components/business-process-designer/__tests__/business-process-designer.spec.js src/router/guards/__tests__ src/views/system/collaboration/__tests__
./node_modules/.bin/eslint src/stores/capability/callGuideStore.js src/stores/capability/onlineTestStore.js src/views/ai/capability/components/CapabilityCallGuideModal.vue src/views/ai/capability/components/CapabilityGuideDocumentation.vue src/views/ai/capability/components/CapabilityOnlineTestPanel.vue src/views/ai/capability/components/CapabilityTestIdentity.vue src/views/ai/capability/components/CapabilityTestRequest.vue src/views/ai/capability/components/CapabilityTestResult.vue src/views/ai/capability/components/capabilityTestRequest.js src/views/ai/capability/components/useCapabilityCallGuide.js src/views/ai/capability/components/useCapabilityOnlineTest.js src/views/ai/capability/__tests__/call-guide-workspace.spec.js src/views/ai/capability/__tests__/test-request-fields.spec.js src/views/ai/capability/__tests__/online-test-workspace.spec.js
node --max_old_space_size=4096 ./node_modules/vite/bin/vite.js build
```

仓库根目录执行 git diff --check。最终日志：/private/tmp/forge-call-workspace-regression-final.log、forge-call-workspace-eslint-final.log、forge-call-workspace-build-final.log。指南确认框收尾后的两个文件 lint 输出为空且命令 exit 0。

### 浏览器证据与清理
- /private/tmp/forge-call-workspace.lfFBuU 使用真实组件、全模拟 API/网关，监听 127.0.0.1:5200。先普通沙箱监听报 EPERM，获批后正常启动；没有尝试连接真实业务环境。
- 实际点击：多系统选择 → 未授权系统显示优先阻断及配置入口 → 已授权系统 → 身份缺失时阻止下一步 → 填模拟凭据 → 字段输入 → JSON 中同一个字符串长 ID → 取消写操作确认无残留遮罩 → 再次确认并模拟调用 → 业务返回和请求编号可见。
- 浏览器发现旧 n-card__content 选择器不匹配实际 n-card-content，长结果导致 footer 越界，按真实 DOM 修正；最终内容 overflowY=auto，390px 下页面 scrollWidth/clientWidth=390，footerBottom=814.09 < 844。深色单列、文档切换保留填写及代码示例均检查通过。
- 浏览器 console error=[]；开发服务曾在 18:50:44 记录一次 ResizeObserver loop 通知未交付，未据此宣称全程零警告。后续最终布局交互未再出现。
- 已关闭本轮标签 9、恢复 viewport、停止仅本轮创建的会话 31910（exit 130）；保留用户原 file:// 标签和所有用户服务。
- 未使用真实凭据、执行真实审批/写入、启动 Admin/Flow、操作数据库或迁移；未部署、commit 或 push。真实身份/网关联调仍由用户环境验收，不把 mock 成功说成已上线。
