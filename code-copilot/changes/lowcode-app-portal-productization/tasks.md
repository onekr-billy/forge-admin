# 任务拆分 — 低代码应用门户产品化改造

> change: `lowcode-app-portal-productization`
> status: draft
> 原则：不推翻现有能力，用宜搭式产品化外壳包装 Forge 工程内核；一个 Task 一个可独立提交的原子变更；数据库变更先冻结协议再执行。

---

## 前置条件

- [ ] 用户确认 `spec.md` 中第 11 章「技术决策」和第 13 章「待澄清」的关键问题。
- [ ] 读取 `code-copilot/rules/automated-testing-standard.md`，并创建本变更的 `test-spec.md` 和 `execution-log.md`。
- [ ] 核查 `forge-server/db/migration` 当前最新版本号，确定本变更 Flyway 版本号。
- [ ] 核查 `application-runtime.[applicationCode].vue` 的页面渲染组件（`GridBlockRenderer`、对象页渲染逻辑）可被门户复用。
- [ ] 确认 `ai_business_application.options` 当前存储格式，确保新增 `portal_config`、`ai_assistant_config` 可安全合并。

---

## 阶段总览

| 阶段 | 目标 | Task | 完成标志 |
|---|---|---|---|
| P0 门户底座 | 独立运行时门户可访问、可渲染、可隔离 | 1-4 | `/app/{code}` 能打开已发布应用，独立布局，权限过滤正常 |
| P1 设置与发布 | 应用设置页、应用发布页、主题水印 slug | 5-9 | 用户可在设置页配置门户，在发布页查看状态/链接/回滚 |
| P2 创建与市场 | 创建向导、应用中心升级、模板市场 | 10-13 | 用户可通过四向导创建应用，在应用市场发现模板 |
| P3 增强分发 | AI 助理绑定、外部分发、移动端适配 | 14-16 | 应用可绑定 AI 助理，可分发到工作台/外部平台 |

---

## 功能追踪

| Spec 功能 | 主要实现 Task | 验证 Task |
|---|---|---|
| 4.1 独立运行时门户 | Task 1-4 | Task 17 |
| 4.2 应用设置页 | Task 5-6 | Task 17 |
| 4.3 应用发布页 | Task 7-8 | Task 17 |
| 4.4 创建应用向导 | Task 10-11 | Task 17 |
| 4.5 应用中心升级 | Task 12-13 | Task 17 |
| 4.6 AI 助理绑定 | Task 14 | Task 17 |
| 4.7 外部分发 | Task 15-16 | Task 17 |

---

## 建议实施顺序

```
Task 1 → Task 2 → Task 3 → Task 4 → Task 5 → Task 6 → Task 7 → Task 8
→ Task 9 → Task 10 → Task 11 → Task 12 → Task 13 → Task 14 → Task 15 → Task 16 → Task 17
```

P0 完成后再进入 P1，P1 完成后再进入 P2，以此类推。每个 Task 完成后更新本文件状态并提交。

---

## Task 1：数据库基线 — 扩展应用主表与 Flyway 迁移

> 优先级：P0
> 状态：待开始

### 目标

为 `ai_business_application` 新增门户产品化所需字段，并编写幂等 Flyway 迁移脚本，初始化存量应用数据。

### 涉及文件

- `forge-server/db/migration/V1.0.x__add_business_application_portal_config.sql`
- `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/domain/entity/AiBusinessApplication.java`
- `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/dto/businessapp/BusinessApplicationDTO.java`
- `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/vo/businessapp/BusinessApplicationVO.java`
- `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/resources/mapper/AiBusinessApplicationMapper.xml`

### 关键变更

- 新增字段 `portal_slug VARCHAR(50)`，租户内唯一。
- 新增字段 `portal_config JSON`（MySQL 用 JSON 类型，Java 用 `String` 或 `Map`）。
- 新增字段 `ai_assistant_config JSON`。
- 初始化存量应用 `portal_slug = application_code`。
- 新增唯一索引 `UNIQUE (tenant_id, portal_slug)`。

### 验收标准

- Flyway 脚本在新库、存量库、重复执行场景下均不报错。
- 脚本具备 `information_schema` 防重复保护、`tenant_id=1`、无 `${...}` 占位符。
- 后端编译通过。
- 存量应用升级后可通过 `/app/{applicationCode}` 访问。

---

## Task 2：后端协议 — 门户配置接口与 slug 解析

> 优先级：P0
> 状态：待开始

### 目标

新增后端接口支持门户配置读写、按 slug 查询应用、slug 可用性校验，并改造运行时服务支持按 slug 解析。

### 涉及文件

- `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/controller/BusinessApplicationController.java`
- `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessApplicationService.java`
- `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessApplicationRuntimeService.java`
- `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/resources/mapper/AiBusinessApplicationMapper.xml`
- `forge-admin-ui/src/api/business-application.js`

### 关键接口

```java
// 按 slug 查询应用详情
@GetMapping("/by-slug/{portalSlug}")
public RespInfo<BusinessApplicationVO> detailBySlug(@PathVariable String portalSlug);

// slug 可用性校验
@GetMapping("/slug-available")
public RespInfo<Boolean> slugAvailable(@RequestParam String portalSlug,
                                        @RequestParam(required = false) Long excludeId);

// 保存门户配置
@PutMapping("/{id}/portal-config")
public RespInfo<Void> savePortalConfig(@PathVariable Long id,
                                        @RequestBody BusinessApplicationPortalConfigDTO config);
```

### 验收标准

- 按 slug 查询应用返回与按 code 查询一致的数据。
- slug 唯一性校验正确排除自身、正确拦截保留 slug。
- 运行时服务支持按 slug 解析并返回门户配置。
- API 加密/解密注解保持与现有接口一致。

---

## Task 3：前端基座 — 应用门户布局与路由

> 优先级：P0
> 状态：待开始

### 目标

新增 `app-portal` 布局和 `/app/:applicationCodeOrSlug` 路由，使已发布应用可通过独立布局访问。

### 涉及文件

- `forge-admin-ui/src/router/index.js`
- `forge-admin-ui/src/layouts/app-portal.vue`
- `forge-admin-ui/src/views/app-center/application-portal.vue`

### 关键行为

- 新布局不含 Forge 控制台顶部/左侧菜单。
- 布局包含：应用顶栏、导航区、内容区、水印层。
- 未发布/已停用应用显示统一不可用页面。
- 用户无权限时显示统一无权限页面。

### 验收标准

- 访问 `/app/{applicationCode}` 显示独立门户布局。
- 访问 `/app-center/application/{code}/runtime` 仍保持原有设计态布局。
- 布局切换不闪烁、不依赖控制台全局 CSS。

---

## Task 4：前端渲染 — 门户页面渲染与权限过滤

> 优先级：P0
> 状态：待开始

### 目标

在门户中渲染应用页面树、首页、自定义编排页、业务对象页，并按权限过滤页面。

### 涉及文件

- `forge-admin-ui/src/views/app-center/application-portal.vue`
- `forge-admin-ui/src/views/app-center/components/portal/PortalNavigation.vue`（新建）
- `forge-admin-ui/src/views/app-center/components/portal/PortalPageRenderer.vue`（新建）
- `forge-admin-ui/src/views/app-center/components/portal/PortalEmptyState.vue`（新建）

### 关键行为

- 调用 `businessApplicationRuntimeByCode` 加载已发布版本快照。
- 根据 `portal_config` 主题色渲染导航和顶栏。
- 根据 `homePageId` 和权限自动选中首页。
- 自定义编排页复用 `GridBlockRenderer`。
- 业务对象页复用现有对象 CRUD/表单/详情渲染逻辑。

### 验收标准

- 已发布应用的多页面可在门户中正常切换。
- 无权限页面不显示，无可用页面时显示无权限提示。
- 主题色正确应用到导航和顶栏。
- 页面切换保留 query 参数不丢失。

---

## Task 5：应用设置页 — 基础属性与访问地址

> 优先级：P1
> 状态：待开始

### 目标

新增 `/app-center/application/:applicationCode/settings` 页面，实现基础属性、主题色、水印、访问地址配置。

### 涉及文件

- `forge-admin-ui/src/router/index.js`
- `forge-admin-ui/src/views/app-center/application-settings.[applicationCode].vue`（新建）
- `forge-admin-ui/src/views/app-center/components/settings/AppSettingsBasic.vue`（新建）
- `forge-admin-ui/src/views/app-center/components/settings/AppSettingsAccess.vue`（新建）
- `forge-admin-ui/src/api/business-application.js`

### 关键行为

- 左侧设置分组菜单，右侧配置面板。
- 主题色支持预设 + 自定义颜色选择器。
- 水印支持开关、自定义文字、用户名、当前时间占位。
- 访问地址支持自定义 slug，实时校验唯一性。
- 保存时合并到 `application.options` 并调用 `updateBusinessApplication`。

### 验收标准

- 设置页可正常加载当前应用配置。
- 修改主题色/水印/slug 后保存成功。
- slug 校验正确拦截保留词和重复值。
- 保存后刷新门户页面，新配置生效（发布后）。

---

## Task 6：应用设置页 — 导航、权限、全球化、高级

> 优先级：P1
> 状态：待开始

### 目标

完成应用设置页剩余分组：导航设置、应用权限、全球化、高级。

### 涉及文件

- `forge-admin-ui/src/views/app-center/components/settings/AppSettingsNavigation.vue`（新建）
- `forge-admin-ui/src/views/app-center/components/settings/AppSettingsPermission.vue`（新建）
- `forge-admin-ui/src/views/app-center/components/settings/AppSettingsGlobalization.vue`（新建）
- `forge-admin-ui/src/views/app-center/components/settings/AppSettingsAdvanced.vue`（新建）

### 关键行为

- 导航设置支持切换导航风格、显示/隐藏 Logo 和名称、启用收起。
- 页面排序与页面设计器中的导航树双向同步。
- 应用权限支持设置管理员、可见范围（角色/部门/用户）。
- 全球化支持多语言开关、默认语言、时区。
- 高级支持代码生成前缀、运行时缓存策略。

### 验收标准

- 各分组表单数据正确加载和保存。
- 导航风格切换后门户运行时生效。
- 可见范围配置持久化到 `portal_config`。

---

## Task 7：应用发布页 — 状态卡片与组织内访问

> 优先级：P1
> 状态：待开始

### 目标

新增 `/app-center/application/:applicationCode/publish` 页面，实现发布状态卡片、访问链接、二维码、版本历史。

### 涉及文件

- `forge-admin-ui/src/router/index.js`
- `forge-admin-ui/src/views/app-center/application-publish.[applicationCode].vue`（新建）
- `forge-admin-ui/src/views/app-center/components/publish/AppPublishStatusCard.vue`（新建）
- `forge-admin-ui/src/views/app-center/components/publish/AppPublishAccess.vue`（新建）
- `forge-admin-ui/src/views/app-center/components/publish/AppPublishVersionHistory.vue`（新建）

### 关键行为

- 状态卡片展示当前状态、版本号、最近发布时间、发布人。
- 立即发布调用现有 `publishBusinessApplication`。
- 停用/启用调用现有 `updateBusinessApplicationStatus`。
- 访问链接生成 `/app/{portalSlug}`，支持复制和二维码。
- 历史版本列表展示版本号、时间、发布人，支持查看详情和回滚。

### 验收标准

- 发布页正确显示当前应用发布状态。
- 点击发布触发版本快照生成。
- 回滚操作调用现有回滚接口。
- 二维码可扫描到正确访问地址。

---

## Task 8：应用发布页 — AI 助理与工作台分发

> 优先级：P1
> 状态：待开始

### 目标

在应用发布页增加 AI 助理绑定和分发到 Forge 工作台能力。

### 涉及文件

- `forge-admin-ui/src/views/app-center/components/publish/AppPublishAiAssistant.vue`（新建）
- `forge-admin-ui/src/views/app-center/components/publish/AppPublishDistribute.vue`（新建）
- `forge-admin-ui/src/api/business-application.js`
- 后端 `BusinessApplicationController`、`BusinessApplicationService`

### 关键行为

- AI 助理绑定：选择已有助理或创建新助理，选择可访问页面。
- AI 助理配置保存到 `ai_assistant_config`。
- 分发到工作台：将应用添加到当前用户「我的应用」或指定角色首页。
- 分发记录持久化到用户配置或角色配置（根据现有能力选择）。

### 验收标准

- AI 助理配置可保存并在发布页展示状态。
- 分发到工作台后，目标用户在 Forge 首页看到应用入口。
- AI 助理访问数据时经过 Forge 权限校验。

---

## Task 9：后端快照与发布校验

> 优先级：P1
> 状态：待开始

### 目标

确保所有新增配置（门户配置、AI 助理配置）纳入版本快照，并在发布时校验合法性。

### 涉及文件

- `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessApplicationSnapshotService.java`
- `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessApplicationPublishService.java`
- `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessApplicationPublishCheckService.java`（若存在）

### 关键行为

- 快照构建时包含 `portal_config` 和 `ai_assistant_config`。
- 发布前校验 `portal_slug` 是否合法、是否冲突。
- 发布前校验 AI 助理绑定的页面是否存在于当前版本中。
- 回滚时恢复完整快照，包括新增配置。

### 验收标准

- 新版本快照 JSON 中包含 `portalConfig` 和 `aiAssistantConfig`。
- 非法 slug 阻止发布并给出明确错误。
- 回滚后门户配置恢复到历史版本。

---

## Task 10：创建应用向导 — 基础框架与空白创建

> 优先级：P2
> 状态：待开始

### 目标

改造应用中心创建入口，新增全屏/大弹窗创建向导，先实现空白创建。

### 涉及文件

- `forge-admin-ui/src/views/app-center/index.vue`
- `forge-admin-ui/src/views/app-center/components/AppCreateWizard.vue`（升级或新建）
- `forge-admin-ui/src/views/app-center/components/create/AppCreateBlank.vue`（新建）

### 关键行为

- 点击「创建应用」打开向导弹窗。
- 顶部四入口 Tab：智能创建、从模板创建、从 Excel 创建、空白创建。
- 空白创建：输入应用名称、编码、图标、套件，调用现有 `createBusinessApplication`。
- 创建成功后进入应用工作台。

### 验收标准

- 创建向导弹窗可正常打开和关闭。
- 空白创建流程与现有创建一致。
- 创建成功后跳转正确。

---

## Task 11：创建应用向导 — 模板市场

> 优先级：P2
> 状态：待开始

### 目标

实现「从模板创建」和「智能创建」两个向导入口。

### 涉及文件

- `forge-admin-ui/src/views/app-center/components/create/AppCreateTemplate.vue`（新建）
- `forge-admin-ui/src/views/app-center/components/create/AppCreateAi.vue`（新建）
- 后端 `BusinessApplicationTemplateService`、`BusinessApplicationCodegenService`

### 关键行为

- 模板市场展示官方模板和组织私有模板卡片。
- 模板卡片显示：名称、图标、描述、已启用次数、立即启用/生成源码按钮。
- 智能创建：输入场景描述，调用 AI 生成方案，用户确认后初始化应用。
- 从模板创建：选择模板后调用 `initializeBusinessApplicationTemplate`。

### 验收标准

- 模板卡片可展示和搜索。
- 从模板创建后应用包含预设对象和页面。
- 智能创建可生成可初始化的应用方案。
- 「生成源码」按钮可跳转到代码生成页面。

---

## Task 12：创建应用向导 — Excel 导入

> 优先级：P2
> 状态：待开始

### 目标

实现「从 Excel 创建应用」向导，自动识别表头并生成对象和页面。

### 涉及文件

- `forge-admin-ui/src/views/app-center/components/create/AppCreateExcel.vue`（新建）
- 后端新增 Excel 解析服务：`BusinessApplicationExcelImportService.java`（新建）
- 后端 Controller 新增接口：`POST /ai/business/application/{id}/import-excel`

### 关键行为

- 上传 Excel 文件，解析 Sheet 和表头。
- 推荐字段类型（字符串、数字、日期、下拉选项）。
- 用户确认后创建数据对象、列表页、表单页。
- 生成后进入对象设计器微调。

### 验收标准

- Excel 表头可正确解析为字段。
- 生成的对象包含正确字段类型和列表/表单页。
- 复杂 Excel（多 Sheet）至少处理第一个 Sheet。

---

## Task 13：应用中心升级 — 我的应用 + 应用市场

> 优先级：P2
> 状态：待开始

### 目标

将 `app-center/index.vue` 从列表升级为「我的应用 + 应用市场」双视图。

### 涉及文件

- `forge-admin-ui/src/views/app-center/index.vue`
- `forge-admin-ui/src/views/app-center/components/AppMarketPanel.vue`（新建）
- `forge-admin-ui/src/views/app-center/components/MyApplicationsPanel.vue`（新建）
- `forge-admin-ui/src/views/app-center/components/AppCard.vue`（升级）

### 关键行为

- 顶部 Tab 切换「我的应用」和「应用市场」。
- 我的应用分组：我创建的、我有权限的、最近使用。
- 应用市场分组：官方模板、组织私有模板、推荐应用。
- 应用卡片快捷操作：设计、运行、发布、代码生成、删除。

### 验收标准

- 双视图可正常切换。
- 搜索和筛选生效。
- 应用卡片操作跳转正确。

---

## Task 14：AI 助理与应用绑定

> 优先级：P3
> 状态：待开始

### 目标

将 `forge-plugin-ai` 的助理能力与应用绑定，使应用可拥有专属 AI 助理。

### 涉及文件

- `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessApplicationAiAssistantService.java`（新建）
- `forge-admin-ui/src/views/app-center/components/publish/AppPublishAiAssistant.vue`
- AI 插件相关接口对接

### 关键行为

- 应用可创建或关联一个 AI 助理。
- 配置助理可执行的操作：查询数据、填写表单、分析统计。
- 助理访问业务数据时复用应用运行时权限。
- 助理对话入口可嵌入到门户顶栏或首页。

### 验收标准

- 应用可成功绑定 AI 助理。
- 助理可查询应用内授权数据。
- 助理不能访问未授权页面数据。

---

## Task 15：外部分发到工作台

> 优先级：P3
> 状态：待开始

### 目标

实现将应用分发到钉钉工作台、企业微信等外部平台（先实现钉钉）。

### 涉及文件

- 后端 `BusinessApplicationDistributeService.java`（新建）
- 钉钉/企微 SDK 接入配置
- `forge-admin-ui/src/views/app-center/components/publish/AppPublishDistribute.vue`

### 关键行为

- 在应用发布页配置钉钉工作台应用凭证。
- 调用钉钉开放 API 注册应用/更新应用。
- 生成钉钉工作台访问地址。

### 验收标准

- 配置凭证可保存并脱敏展示。
- 分发到钉钉后，组织成员可在钉钉工作台看到应用入口。
- 分发失败给出明确错误提示。

---

## Task 16：门户移动端适配与 H5 入口

> 优先级：P3
> 状态：待开始

### 目标

使应用门户在移动端有基本可用体验，并支持生成 H5 访问入口。

### 涉及文件

- `forge-admin-ui/src/views/app-center/application-portal.vue`
- `forge-admin-ui/src/layouts/app-portal.vue`
- `forge-h5-ui` 相关运行时渲染（可选）

### 关键行为

- 门户布局响应式：移动端导航折叠为底部 Tab 或顶部抽屉。
- CRUD 页面在移动端适配为卡片列表或简化表格。
- 生成 H5 访问二维码。

### 验收标准

- 门户在移动浏览器中可正常访问和切换页面。
- H5 二维码可扫描打开。
- 复杂页面至少保证可读和基础操作。

---

## Task 17：集成测试与验收

> 优先级：P0-P3 通用
> 状态：待开始

### 目标

完成全链路验证，包括后端编译、前端构建、接口测试、UI 验证、数据库迁移验证。

### 涉及文件

- `code-copilot/changes/lowcode-app-portal-productization/test-spec.md`
- `code-copilot/changes/lowcode-app-portal-productization/execution-log.md`

### 验证矩阵

| 验证项 | 命令/方法 | 通过标准 |
|---|---|---|
| 后端编译 | `mvn -pl forge-admin-server -am package -DskipTests` | 无编译错误 |
| 前端构建 | `pnpm build` | 无构建错误 |
| Flyway 静态检查 | `rg -n '\$\{[^}]+\}' forge-server/db/migration` | 无输出 |
| 数据库迁移 | 启动 `forge-admin-server` | `forge_schema_history` 成功 |
| 接口验证 | curl 测试新增接口 | 返回符合预期 |
| UI 验证 | 浏览器/Playwright | 门户、设置、发布、创建向导可用 |
| 权限验证 | 无权限用户访问门户 | 正确拦截 |
| 版本验证 | 发布/回滚后访问门户 | 配置随版本生效/恢复 |

### 验收标准

- P0 功能全部验证通过。
- 至少 P1 功能核心路径验证通过。
- 所有失败项有根因和下一步。
- `execution-log.md` 记录完整证据。

---

## 任务状态总览

| Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|
| 1 数据库基线 | P0 | 待开始 | 无 |
| 2 后端协议 | P0 | 待开始 | Task 1 |
| 3 前端基座 | P0 | 待开始 | Task 2 |
| 4 前端渲染 | P0 | 待开始 | Task 3 |
| 5 设置页基础 | P1 | 待开始 | Task 2 |
| 6 设置页进阶 | P1 | 待开始 | Task 5 |
| 7 发布页状态 | P1 | 待开始 | Task 2 |
| 8 发布页增强 | P1 | 待开始 | Task 7 |
| 9 快照与发布校验 | P1 | 待开始 | Task 1、Task 2 |
| 10 创建向导基础 | P2 | 待开始 | 无 |
| 11 创建向导模板/AI | P2 | 待开始 | Task 10 |
| 12 创建向导 Excel | P2 | 待开始 | Task 10 |
| 13 应用中心升级 | P2 | 待开始 | Task 10 |
| 14 AI 助理绑定 | P3 | 待开始 | Task 8、Task 9 |
| 15 外部分发 | P3 | 待开始 | Task 7 |
| 16 移动端适配 | P3 | 待开始 | Task 4 |
| 17 集成测试 | 通用 | 待开始 | 所有 Task |

---

## 验收标准汇总

### P0 完成标准

- [ ] `/app/{applicationCode}` 可访问已发布应用。
- [ ] 门户使用独立布局，不含控制台菜单。
- [ ] 已停用/未发布应用显示不可用页面。
- [ ] 无权限用户被正确拦截。
- [ ] 多页面应用可在门户中切换。

### P1 完成标准

- [ ] 应用设置页可配置主题、水印、slug、导航、权限。
- [ ] 应用发布页可查看状态、访问链接、二维码、历史版本、回滚。
- [ ] 配置发布后门户实时生效。
- [ ] 回滚后门户配置恢复。

### P2 完成标准

- [ ] 创建向导包含智能/模板/Excel/空白四入口。
- [ ] 应用中心支持「我的应用 + 应用市场」双视图。
- [ ] 从模板创建可生成应用。
- [ ] 从 Excel 创建可生成对象和页面。

### P3 完成标准

- [ ] 应用可绑定 AI 助理。
- [ ] 应用可分发到钉钉工作台。
- [ ] 门户在移动端基本可用。

---

## 备注

- 所有 Task 完成后需同步更新 `spec.md` 中的「执行日志」和「审查结论」。
- 实施过程中若发现 Spec 需要调整，先更新 Spec 再执行代码，保持文档领先代码。
- 每个 Task 提交信息格式：`[lowcode-app-portal-productization] <中文简述>`。
