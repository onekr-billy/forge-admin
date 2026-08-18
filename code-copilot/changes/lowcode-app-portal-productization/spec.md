# 低代码应用门户产品化改造

> status: implemented-with-platform-deferrals-and-environment-acceptance-pending
> created: 2026-08-17
> complexity: 🔴🔴🔴 高（跨前后端 + 数据库 + 产品化体验）
> change: `lowcode-app-portal-productization`
> parent: 参考宜搭应用发布/设置/页面管理/创建向导体验，融合 Forge 既有版本发布、代码生成、对象设计、流程编排能力。

---

## 1. 背景与目标

### 1.1 背景

Forge 的低代码应用能力已具备完整骨架：

- 后端：`ai_business_application` 聚合应用、`ai_business_application_version` 不可变版本、`ai_business_application_publish_run` 协调发布。
- 前端：`application.[applicationCode].vue` 应用工作台、`application-runtime.[applicationCode].vue` 应用级页面设计器与运行时、`app-center/index.vue` 应用中心。

但当前用户视角仍偏向「在 Forge 管理后台里配置一个应用」，而不是「创建并发布一个可独立访问的轻应用」。

### 1.2 目标

参考宜搭的产品化体验（创建向导、应用设置、应用发布、页面管理、应用中心），同时保留并放大 Forge 的独有优势（版本发布/回滚、代码生成、精细对象建模、Flowable 流程、多租户权限），将低代码应用升级为：**看起来像独立 SaaS，底层仍是工程级低代码平台**。

### 1.3 关键成功指标

- 已发布应用可通过短链 `/app/{applicationCode}` 独立访问，布局与 Forge 控制台隔离。
- 应用支持主题色、Logo、水印、自定义访问 slug、运行时导航风格配置。
- 应用发布页可视化展示状态、版本、访问链接、二维码、历史版本/回滚。
- 创建应用支持智能创建、空白创建、Excel 导入、模板市场四向导。
- 应用中心升级为「我的应用 + 应用市场」双视图。
- 所有应用级配置纳入版本快照，发布后才对正式用户生效。

---

## 2. 代码现状（Research Findings）

### 2.1 后端现状

- `BusinessApplicationController`（`/ai/business/application`）已提供应用 CRUD、对象管理、权限、发布、回滚、版本、代码生成、运行时配置接口。
- `BusinessApplicationRuntimeService.runtimeByCode(...)` 从 `ai_business_application_version` 读取不可变快照，按权限过滤页面，返回运行时配置。
- `AiBusinessApplication` 实体包含 `options` JSON 字段，已用于存储 `inAppBuilder` 应用级页面编排配置。
- 应用表 `ai_business_application` 已有 `application_code`、`application_name`、`icon`、`description`、`status`、`last_publish_version` 等字段，但没有 `portal_slug`、`theme`、`watermark` 等门户级字段。
- 权限码体系已支持页面级权限：`ai:business:application:{code}:page:{pageId}`。

### 2.2 前端现状

- `forge-admin-ui/src/router/index.js` 已注册：
  - `/app-center/application/:applicationCode` → 应用工作台
  - `/app-center/application/:applicationCode/runtime` → 应用运行时（`layout: 'empty'`）
  - `/app-center/application/:applicationCode/preview` → 应用预览
- `application-runtime.[applicationCode].vue`（6585 行）已实现：
  - 编辑态/运行态切换
  - 页面组 + 页面导航树
  - 自由拖拽组件画布
  - 表单资产、业务对象绑定
  - 左侧导航、首页、权限过滤
- `application.[applicationCode].vue` 应用工作台当前分区：`overview / objects / entries / automation / enhancements / permissions / releases`。
- `app-center/index.vue` 当前为应用列表，支持搜索、创建、卡片展示。
- `app-entry.vue` 已处理 EXTERNAL / H5 / IFRAME / ROUTE 等入口打开方式。

### 2.3 已有机遇

- 运行时页面编排器已经成熟，可直接复用作为「页面管理」核心。
- 版本发布/回滚机制已经成熟，可作为产品化卖点的底座。
- 应用级配置已可持久化到 `options` JSON，新增主题/水印/导航等字段无需改表结构。
- 自定义访问 slug 需要扩展主表字段，但扩展成本低。

### 2.4 当前缺口

| 缺口 | 说明 |
|---|---|
| 运行时 URL 仍挂在 `/app-center/` 下 | 用户感知这是 Forge 的一个功能模块，不是独立应用 |
| 缺少统一「应用设置」页 | 主题、水印、访问地址、导航风格分散或缺失 |
| 缺少独立「应用发布」页 | 发布入口隐藏在工作台/设计器里，状态不可视 |
| 缺少创建向导 | 创建应用后直接进工作台，缺少智能/Excel/模板引导 |
| 应用中心缺少市场概念 | 只有列表，没有「我的应用 + 应用市场」分层 |
| AI 助理未与应用绑定 | `forge-plugin-ai` 能力未在应用维度产品化 |
| 外部发布能力缺失 | 无法发布到工作台首页、钉钉、企业微信等 |

---

## 3. 总体设计

### 3.1 架构分层

```
┌─────────────────────────────────────────────────────────────┐
│ 产品表现层（参考宜搭，新增/升级）                              │
│  应用中心 │ 创建向导 │ 应用设置 │ 应用发布 │ 独立门户 │ 应用市场 │
├─────────────────────────────────────────────────────────────┤
│ 应用编排层（Forge 已有，强化产品化入口）                       │
│  页面编排 │ 导航编排 │ 表单资产 │ 权限编排 │ 流程编排 │ AI 助理 │
├─────────────────────────────────────────────────────────────┤
│ 领域模型层（Forge 已有，保留并作为护城河）                     │
│  业务对象 │ 字段关系 │ 数据表 │ 触发器 │ 代码生成 │ 数据权限 │
├─────────────────────────────────────────────────────────────┤
│ 发布运行层（Forge 独有优势，必须保留并放大）                   │
│  不可变版本 │ 协调发布 │ 回滚 │ 运行时快照 │ 代码导出 │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 设计原则

1. **宜搭的产品化外壳 + Forge 的工程化内核**
   - 借鉴宜搭的创建、设置、发布、访问体验。
   - 底层继续用版本快照、对象设计器、代码生成、Flowable。

2. **设计态 / 运行态 / 发布态严格分离**
   - 设计态：应用工作台 + 页面设计器。
   - 运行态：独立门户 `/app/{code}`。
   - 发布态：发布后才把设计态快照写入不可变版本，影响运行态。

3. **不废弃现有对象设计器**
   - 宜搭面向业务人员，Forge 面向实施人员 + 业务人员。
   - 复杂建模仍进入对象设计器，页面编排作为上层封装。

4. **所有应用级配置进版本快照**
   - 主题、水印、导航、页面编排、权限、AI 助理配置全部纳入 `ai_business_application_version.snapshot_json`。

5. **代码生成作为核心卖点保留**
   - 每个应用、页面、对象都应能导出源码。

---

## 4. 功能点

### 4.1 独立运行时门户（P0）

#### 4.1.1 新增应用门户路由

新增独立访问路由：

```js
{
  name: 'ApplicationPortal',
  path: '/app/:applicationCodeOrSlug',
  component: () => import('@/views/app-center/application-portal.vue'),
  meta: {
    title: '应用门户',
    layout: 'app-portal',
    skipTab: true,
    preserveOnQuery: true,
  },
}
```

- `layout: 'app-portal'` 表示使用全新独立布局，不含 Forge 控制台顶部/左侧菜单。
- 支持通过 `applicationCode` 或自定义 `portalSlug` 访问。
- 未发布或已停用应用显示统一不可用页面。

#### 4.1.2 门户布局

门户布局包含：

- 顶部栏：应用 Logo、应用名称、全局搜索（预留）、通知（预留）、用户头像/退出。
- 导航区：支持左侧垂直导航 / 顶部水平导航 / 左侧折叠导航三种风格。
- 内容区：渲染当前选中页面。
- 水印：根据配置在内容区或全屏显示水印。

#### 4.1.3 门户页面渲染

- 从 `businessApplicationRuntimeByCode` 读取已发布版本快照。
- 按当前用户权限过滤页面。
- 支持页面类型：
  - 自定义编排页（`pageType = 'content'`）→ 渲染 `GridBlockRenderer`
  - 业务对象页（`pageType = 'object'`）→ 渲染对象 CRUD/表单/详情
  - 外部页（`pageType = 'external'`）→ 渲染 IFrame 或跳转
- 页面切换通过 `pageId` query 参数或路由参数实现。

#### 4.1.4 与现有运行时页面关系

- 现有 `/app-center/application/:applicationCode/runtime` 继续保留作为**设计态预览/编辑入口**。
- 新增 `/app/:applicationCodeOrSlug` 作为**正式发布后的独立访问入口**。
- 两个入口复用同一份运行时渲染组件，但门户布局更产品化、更独立。

---

### 4.2 应用设置页（P1）

新增 `/app-center/application/:applicationCode/settings` 页面，左侧分组菜单，右侧配置面板。

#### 4.2.1 基础属性

- 应用名称（必填）
- 应用编码（只读）
- 应用图标
- 应用描述
- 应用主题色（预设 + 自定义）
- 应用水印（开关 + 自定义文字/用户名/时间）
- 应用状态（启用/停用，与现有 `updateStatus` 一致）

#### 4.2.2 访问地址

- 默认访问地址：`/app/{applicationCode}`
- 自定义 slug：`/app/{portalSlug}`
  - 全局唯一校验
  - 只允许字母、数字、中划线、下划线
  - 保留 slug 如 `admin`、`api`、`app-center`、`system` 等系统路径
- 复制链接按钮
- 二维码展示
- 新窗口打开

#### 4.2.3 导航设置

- 导航风格：左侧垂直 / 顶部水平 / 左侧折叠
- 是否显示应用名称
- 是否显示应用 Logo
- 是否允许收起
- 页面排序（与页面设计器中的导航树同步，可在此快速调整）

#### 4.2.4 应用权限

- 应用管理员设置
- 应用可见范围：全员 / 指定角色 / 指定部门 / 指定用户
- 默认角色自动赋权依赖角色成员生命周期协议，本期不提供未生效的配置入口。

#### 4.2.5 全球化

- 多语言开关
- 默认语言
- 时区/日期格式

#### 4.2.6 高级

- 代码生成前缀
- 运行时缓存策略（本期随发布快照保存；独立缓存执行器后续接入）
- 版本保留策略（本期随发布快照保存；版本清理任务后续接入）

---

### 4.3 应用发布页（P1）

新增 `/app-center/application/:applicationCode/publish` 页面。

#### 4.3.1 状态卡片

- 当前状态：已启用 / 未启用
- 当前版本号
- 最近发布时间
- 最近发布人
- 操作按钮：停用/启用、立即发布、查看历史版本

#### 4.3.2 组织内访问

- 应用访问链接（可点击复制）
- 二维码
- 访问权限提示（如「仅授权用户可访问」）
- 新窗口打开运行时

#### 4.3.3 AI 助理（Forge 独有增强）

- 创建/绑定 AI 助理
- 选择助理可访问的数据页面
- 配置自然语言指令：填单、查询、分析
- 助理状态：未创建 / 已启用 / 已停用

#### 4.3.4 分发入口（P2）

- 添加到 Forge 工作台「我的应用」
- 添加到指定角色首页
- 发布到钉钉工作台（P3，等待受管 Connector API；本期只保存待同步配置）
- 发布到企业微信（P3，等待受管 Connector API）

#### 4.3.5 历史版本

- 版本列表：版本号、发布时间、发布人、状态
- 版本详情：查看快照
- 回滚：选择历史版本回滚（复用现有 `rollbackBusinessApplication`）
- 版本对比（未来扩展）

---

### 4.4 创建应用向导（P2）

改造 `app-center/index.vue` 的创建按钮，点击后弹出全屏/大弹窗向导。

#### 4.4.1 智能创建

- 输入业务场景描述，如「做一个销售线索跟进系统」。
- 调用 AI 生成应用方案：建议对象、字段、页面、流程。
- 用户确认后一键初始化应用；流程建议只创建应用级最小设计草稿，不自动部署 Flowable/BPMN。
- 初始化后可直接进入设计态，也可选择生成源码。

#### 4.4.2 从模板创建

- 官方模板：进销存、CRM、设备管理、项目管理等。
- 组织私有模板：待应用模板快照、发布权限和租户内发现协议建立后接入；本期显示明确空态。
- 模板卡片展示：名称、图标、描述、已启用次数、立即启用按钮。
- 模板支持「在线使用」和「生成源码」两种模式（Forge 独有）。

#### 4.4.3 从 Excel 创建

- 上传 Excel 文件。
- 自动识别 Sheet/表头，推荐字段类型。
- 一键生成数据对象 + 列表页 + 表单页。
- 生成后进入对象设计器微调。

#### 4.4.4 空白创建

- 输入应用名称、编码、图标。
- 选择所属套件。
- 进入应用工作台。

---

### 4.5 应用中心升级（P2）

将 `app-center/index.vue` 从列表升级为「我的应用 + 应用市场」双视图。

#### 4.5.1 我的应用

- 我创建的
- 我有权限的
- 最近使用
- 应用卡片：图标、名称、描述、状态标签、快捷操作（设计/运行/发布）

#### 4.5.2 应用市场

- 官方模板
- 组织私有模板（本期明确空态，不伪造模板数据）
- 推荐应用
- 模板卡片展示：名称、图标、描述、已启用次数、启用按钮

#### 4.5.3 搜索与筛选

- 按名称搜索
- 按套件筛选
- 按状态筛选

---

### 4.6 页面管理强化（P1 与 P2 之间）

当前 `application-runtime.[applicationCode].vue` 的页面编排能力已经成熟，建议：

- 在应用工作台新增「页面管理」分区，作为页面编排的独立入口。
- 页面树在设置页和发布页中同步可见。
- 页面设计器默认选中第一个页面节点，避免空画布。

---

## 5. 业务规则

### 5.1 应用访问规则

- 只有通过 `/app/:applicationCodeOrSlug` 访问时才使用 `layout: 'app-portal'` 独立布局。
- 已停用或未发布的应用访问门户时，显示「应用暂不可用」页面。
- 用户无应用任何页面权限时，显示「暂无访问权限」页面。
- 用户有权限的页面不存在时，自动回退到第一个有权限的页面。

### 5.2 自定义 slug 规则

- `portal_slug` 在租户内全局唯一。
- 允许字符：`a-zA-Z0-9_-`。
- 长度限制：2-50 字符。
- 保留 slug：`admin`、`api`、`app-center`、`system`、`login`、`logout`、`auth`、`file`、`dict`、`ai`、`report`、`flow`、`h5`、`mobile`、`integration`、`preview`、`runtime`、`static`、`assets`、`favicon.ico`。
- 修改 slug 后，旧 slug 在 30 天内可 301 重定向到新 slug（可选，P2）。

### 5.3 主题与水印规则

- 主题色只影响应用门户运行时，不影响 Forge 控制台。
- 水印默认关闭；开启后默认显示当前用户名。
- 水印文字长度不超过 50 字符。
- 水印在打印/导出时应降级或禁用（P3）。

### 5.4 版本快照规则

- 所有应用级配置（基础属性、访问地址、导航、主题、水印、页面编排、权限配置、AI 助理配置）必须纳入 `ai_business_application_version.snapshot_json`。
- 设计态修改后必须发布才影响运行态。
- 回滚时恢复完整快照，包括门户配置。

### 5.5 权限规则

- 应用管理员自动拥有该应用所有页面权限。
- 应用可见范围控制用户是否能在应用中心/工作台看到该应用。
- 可见范围支持组织全员、指定角色、指定部门/组织和指定用户；应用管理员可见范围与页面权限均自动放行。
- 应用中心“我有权限的”、Forge 工作台投放查询和正式门户运行时必须复用同一应用级授权判定；`systemMenuVisible=false` 不是页面权限放行条件。
- 页面级权限继续沿用 `ai:business:application:{code}:page:{pageId}`。
- 数据范围继续由 `DataScopeInterceptor` 控制。

### 5.6 AI 助理规则

- AI 助理仅在应用启用后可用。
- AI 助理可访问的数据页面必须是已发布版本中存在的页面。
- AI 助理的自然语言指令不得绕过数据权限。

---

## 6. 数据变更

### 6.1 数据库表结构变更

| 表名 | 变更 | 字段 | 说明 |
|---|---|---|---|
| `ai_business_application` | 新增字段 | `portal_slug` VARCHAR(50) | 自定义门户访问 slug，租户内唯一 |
| `ai_business_application` | 新增字段 | `portal_config` JSON | 主题、水印、导航风格等门户配置 |
| `ai_business_application` | 新增字段 | `ai_assistant_config` JSON | AI 助理绑定配置 |
| `ai_business_application_version` | 无结构变更 | — | 快照中需包含新增字段数据 |

### 6.2 索引变更

- `ai_business_application` 新增有效行唯一索引：`UNIQUE (tenant_id, portal_slug, del_flag)`；应用表的 `del_flag` 已按项目逻辑删除规范使用 BIGINT 主键墓碑，删除后允许同 slug 重建。
- 已有 `UNIQUE (tenant_id, application_code)` 保持不变。

### 6.3 Flyway 迁移脚本

- 新增 `V1.0.x__add_business_application_portal_config.sql`：
  - 添加 `portal_slug`、`portal_config`、`ai_assistant_config` 字段（需先判断 `information_schema` 防重复）。
  - 初始化存量应用 `portal_slug = application_code`。
  - 初始化 `portal_config` 为默认空对象。
  - 新增唯一索引前需处理可能的重复 slug（理论上不会重复，因为 application_code 已唯一）。

### 6.4 系统资源（权限菜单）

- 新增隐藏路由资源 `/app/:applicationCodeOrSlug` 到 `sys_resource`。
- 权限码：`ai:businessApplication:portal`。
- 继承已绑定 `ai:businessApplication:list` 权限的角色。
- 新增应用设置页隐藏路由资源 `/app-center/application/:applicationCode/settings`。
- 新增应用发布页隐藏路由资源 `/app-center/application/:applicationCode/publish`。

---

## 7. 接口变更

### 7.1 新增后端接口

| 接口 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 按 slug 查询应用详情 | GET | `/ai/business/application/by-slug/{portalSlug}` | 门户访问时用 |
| 校验 slug 可用性 | GET | `/ai/business/application/slug-available` | 应用设置时实时校验 |
| 查询当前用户工作台应用 | GET | `/ai/business/application/workbench` | 只返回已发布、启用、已投放且当前用户有权访问的应用 |
| 保存门户配置 | PUT | `/ai/business/application/{id}/portal-config` | 保存主题/水印/导航等 |
| 保存 AI 助理配置 | PUT | `/ai/business/application/{id}/ai-assistant-config` | 绑定 AI 助理 |
| 查询 AI 助理状态 | GET | `/ai/business/application/{id}/ai-assistant-status` | 发布页展示 |
| 门户运行配置 | GET | `/ai/business/application/portal/{codeOrSlug}/runtime` | 读取当前用户过滤后的发布快照 |
| 门户 AI 对话 | POST | `/ai/business/application/portal/{codeOrSlug}/assistant/chat` | 在发布页面与能力边界内调用智能体 |
| 保存分发配置 | POST | `/ai/business/application/{id}/distribute/workbench` | 保存 Forge/钉钉受管分发配置，不代表外部已同步 |
| AI 方案初始化 | POST | `/ai/business/application/{id}/initialize-ai` | 从既有生成方案创建对象和页面草稿 |
| Excel 预览 | POST | `/ai/business/application/excel/preview` | 解析首 Sheet 表头与样例 |
| Excel 初始化 | POST | `/ai/business/application/{id}/import-excel` | 创建对象、列表页和表单页草稿 |

### 7.2 改造后端接口

| 接口 | 变更 |
|---|---|
| `BusinessApplicationService.update` | 接收并规范化 `portalSlug`、`portalConfig`、`aiAssistantConfig` |
| `BusinessApplicationRuntimeService.runtimeByCode` | 支持按 `portalSlug` 解析应用；返回门户配置 |
| `BusinessApplicationSnapshotService` | 快照构建时包含新增字段 |
| `BusinessApplicationPublishService` | 发布时校验门户配置合法性 |

### 7.3 前端接口封装

- `src/api/business-application.js` 新增：
  - `businessApplicationDetailBySlug(portalSlug)`
  - `checkBusinessApplicationSlugAvailable(slug, excludeId)`
  - `saveBusinessApplicationPortalConfig(id, config)`
  - `saveBusinessApplicationAiAssistantConfig(id, config)`
  - `businessApplicationAiAssistantStatus(id)`
  - `chatBusinessApplicationAssistant(codeOrSlug, data)`
  - `distributeBusinessApplicationToWorkbench(id, data)`
  - `initializeBusinessApplicationAi(id, plan)`
  - `previewBusinessApplicationExcel(file)`
  - `importBusinessApplicationExcel(id, formData)`

---

## 8. 影响范围

### 8.1 后端影响

- `forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/`
  - 实体 `AiBusinessApplication.java`
  - DTO `BusinessApplicationDTO.java`
  - VO `BusinessApplicationVO.java`、`BusinessApplicationRuntimeVO.java`
  - Controller `BusinessApplicationController.java`
  - Service `BusinessApplicationService.java`、`BusinessApplicationRuntimeService.java`、`BusinessApplicationSnapshotService.java`、`BusinessApplicationPublishService.java`
  - Mapper XML `AiBusinessApplicationMapper.xml`
- `forge-server/db/migration/`：新增 Flyway 脚本。

### 8.2 前端影响

- `forge-admin-ui/src/router/index.js`：新增门户路由、设置页、发布页路由。
- `forge-admin-ui/src/layouts/app-portal/`：新增独立门户布局。
- `forge-admin-ui/src/views/app-center/`：
  - 新增 `application-portal.vue`
  - 新增 `application-settings.[applicationCode].vue`
  - 新增 `application-publish.[applicationCode].vue`
  - 新增创建向导组件 `AppCreateWizard.vue`
  - 升级 `index.vue` 应用中心
  - 调整 `application.[applicationCode].vue` 增加「页面管理」入口
- `forge-admin-ui/src/api/business-application.js`：新增接口封装。
- `forge-admin-ui/src/store/modules/tenant.js` 或新增 `portal.js`：门户主题缓存。

### 8.3 数据库影响

- 新增字段、索引、Flyway 迁移。
- 存量应用数据自动初始化。

### 8.4 无影响范围

- 不修改 `ai_business_app` 旧版访问入口表。
- 不废弃业务对象设计器。
- 不改写 Flowable 引擎原生表。
- 不修改 `sys_resource` 全局菜单的现有结构，只新增隐藏资源。

---

## 9. 风险与关注点

### 9.1 高风险

- **双运行时入口并存导致维护复杂**：现有 `/app-center/application/:code/runtime` 与新 `/app/:code` 同时存在，需确保渲染逻辑复用，避免代码分叉。
- **portal_slug 唯一性与路由冲突**：需严格校验保留 slug，避免覆盖系统路由。
- **门户布局与系统布局样式隔离**：`app-portal` 布局不得依赖控制台的全局 CSS，避免样式污染。

### 9.2 中风险

- **权限过滤一致性**：门户运行时仍按 `BusinessApplicationRuntimeService.filterBuilder` 过滤页面，新增页面类型需同步更新过滤逻辑。
- **版本快照兼容性**：新增 `portal_config`、`ai_assistant_config` 字段后，旧版本快照回滚时需能兼容缺失字段。
- **AI 助理数据权限**：AI 助理访问业务数据时必须经过 Forge 的数据权限拦截。

### 9.3 低风险

- **前端路由参数变化**：需确保旧链接（`/app-center/application/:code/runtime`）继续可用。
- **应用卡片操作入口调整**：用户习惯从工作台进入设计态，新增应用市场入口后需保持操作一致性。

---

## 10. 测试策略

### 10.1 静态检查

- Flyway 脚本：版本号唯一、`NOT EXISTS` 幂等、`tenant_id=1`、无 `${...}` 占位符。
- 后端编译：相关模块 `mvn compile -DskipTests`。
- 前端构建：`pnpm build`。

### 10.2 接口验证

- 登录获取 Token。
- 创建应用，设置 `portal_slug`，校验唯一性。
- 发布应用，访问 `/app/{portal_slug}`，验证独立布局、主题、水印。
- 无权限用户访问门户，验证权限拦截。
- 停用应用后访问门户，验证不可用提示。
- 回滚版本后访问门户，验证配置恢复。

### 10.3 UI 验证

- 应用设置页：主题切换、slug 校验、二维码展示。
- 应用发布页：状态卡片、版本列表、回滚操作。
- 创建向导：四入口可用、模板卡片展示。
- 应用中心：双视图切换、搜索筛选。
- 运行时门户：导航切换、页面渲染、权限过滤。

### 10.4 兼容性验证

- 存量应用升级后，默认 `portal_slug = application_code` 仍可访问。
- 旧版 `/app-center/application/:code/runtime` 仍可打开。
- 版本快照回滚不报错。

---

## 11. 技术决策

### 11.1 门户配置存储位置

**决策**：新增 `portal_config` JSON 字段存储主题、水印、导航风格等配置，而不是平铺到多个字段。

**理由**：
- 配置项多且可能频繁扩展，JSON 更灵活。
- 版本快照天然支持整个 JSON 对象的序列化/反序列化。
- 后续若需支持更多主题配置，无需再次改表。

### 11.2 自定义 slug 是否支持中文

**决策**：不支持中文 slug，只允许 `a-zA-Z0-9_-`。

**理由**：
- 避免 URL 编码、路由匹配、唯一性校验复杂度。
- 宜搭的 slug 也是字母数字组合。

### 11.3 是否直接改造现有运行时页面

**决策**：保留现有 `/app-center/application/:code/runtime` 作为设计态预览/编辑入口，新增 `application-portal.vue` 作为发布后的独立访问入口。

**理由**：
- 设计态需要编辑按钮、保存草稿、发布等操作，运行态需要更干净的产品体验。
- 两个入口可以复用底层渲染组件，但顶层交互不同。

### 11.4 AI 助理是否必须发布后才可用

**决策**：是，AI 助理仅对已发布应用可用，且可访问页面必须来自已发布版本。

**理由**：
- 避免 AI 访问到设计态草稿数据。
- 与运行态权限保持一致。

### 11.5 代码生成能力如何融入创建向导

**决策**：模板市场在「立即启用」旁增加「生成源码」选项，作为次要入口。

**理由**：
- 大多数用户首选在线使用，但代码生成是 Forge 独有优势，需在关键路径上可见。

---

## 12. 任务拆分

详见同目录 `tasks.md`。

---

## 13. 技术决策落地

1. 本轮不支持自定义域名；正式入口为 `/app/{applicationCodeOrSlug}`。
2. AI 助理复用现有智能体编码，通过应用配置绑定，不新建重复的应用级智能体实体。
3. 官方/推荐模板使用内置目录；仓库没有组织私有模板持久化协议，市场明确展示空态，不伪造私有模板数据。
4. 移动端复用同一不可变发布快照和权限链路，通过响应式门户及 `?display=h5` 链接/二维码交付，不另建分叉运行时。
5. 新增查询和变更接口沿用权限注解；可审计的后端变更接口使用 `@OperationLog`。AI 对话正文不写入普通业务日志。
6. Excel 只解析首 Sheet，读取表头及最多 50 行样例，生成对象/页面草稿；不导入业务行、不自动执行 DDL。
7. 钉钉只保存受管连接器标识和 `PENDING_EXTERNAL_SYNC` 状态，待仓库提供真实 Connector API 后再执行外部同步。
8. 全球化配置在门户运行态用于根语言标识和水印时区/日期格式；高级缓存策略与版本保留数量先作为发布快照配置保存，待缓存/清理基础设施协议接入后再执行消费。
9. 默认角色输入项在角色成员自动赋权协议落地前移除，避免保存一个不会生效的角色配置。
10. AI 流程建议只创建应用级业务流程设计草稿；审批节点、节点表单、BPMN 绑定与发布继续由真实流程设计器维护。

---

## 14. 执行日志

完整命令、结果、警告和跳过项见 `execution-log.md`。本轮已完成：

- Java 17 下 Generator 与 Admin reactor 编译。
- 初始验证包含 92 个业务应用回归测试和前端 6 个单测；最终 Fix 增量执行 100 个业务应用测试和前端 11 个单测，均通过；另完成定向 ESLint 和 Vite 生产构建。
- Flyway 静态检查与工作区空白检查。

真实 MySQL/Flyway、服务启动、curl、Playwright/真机及受管钉钉 Connector 同步未执行。

---

## 15. 审查结论

结论：**阶段一 Spec Compliance：PASS（按第 13 章本期边界）；阶段二 Code Quality：PASS。**

Review 阻塞项已完成闭环：

- Forge 工作台现在只把设计态分发开关作为候选来源，展示名称、slug、应用可见范围、页面 RBAC 和首页均复用不可变发布快照；设计态修改不会提前影响正式入口。
- 角色 ID 以前后端字符串/Long 传递，后端校验租户、启用状态、门户基础权限和当前用户可管理角色范围。
- `/by-slug/{portalSlug}` 与正式门户统一按当前发布快照解析，不再返回可变设计态元数据。
- AI 方案新增流程建议模型，确认初始化后只创建可编辑的应用级最小流程草稿；未自动部署 Flowable 或写入不完整 BPMN。
- 未有真实协议的组织私有模板、AI 业务数据读写、钉钉/企微 Connector、缓存执行器和版本清理任务已明确延期；默认角色未生效输入项已移除，不再把配置态描述为完成能力。

代码级验证均通过，证据见 `execution-log.md`。真实 MySQL/Flyway、服务 API、Playwright/移动真机和外部 Connector 仍属于环境/平台验收项，不计入本次代码级 PASS。
