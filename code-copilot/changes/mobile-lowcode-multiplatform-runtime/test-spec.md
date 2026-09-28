# 移动端低代码多端运行时增量测试计划

> 变更：`mobile-lowcode-multiplatform-runtime`
> 验证范围：仅 `forge-h5-ui` 与本变更文档；后端协议和数据库未修改。

## P0 必须通过

1. 组件注册表覆盖管理端新版低代码设计器、页面画布和旧版表单设计器公开的组件键。
2. 未知组件、动态 Vue 和小程序 iframe 不得回退成可编辑输入框。
3. 审批字段权限覆盖数组、JSON 字符串、新旧权限键优先级、主子表作用域和动态数组字段。
4. H5 与微信小程序生产构建通过。
5. `git diff --check` 通过，工作分支保持 `codex/mobile-lowcode-runtime-refactor`。
6. 移动端 API 与后端 Controller 的低代码、选择器和审批路由契约保持一致，不引入 `/mobile` 或 `/h5` 专用协议。
7. 审批主子表必须展示全部可读子表，并按节点新增、修改、删除权限构造保存载荷；Long ID 保持字符串。
8. 流程写操作必须携带成对的 `idempotencyKey` 与 `requestDigest`，相同载荷摘要稳定、不同动作摘要不同。
9. 所有注册页面必须挂载统一 `AiFeedbackHost`；Toast、Notify、MessageBox、Prompt 和 ActionSheet 由 Wot Design Uni 实现，业务反馈适配器不得包含手写 DOM 或 uni 原生提示 API。
10. `FLOW_TODO` 消息必须从 `jumpUrl/taskId/task_id/bizKey` 稳定解析任务 ID；未读消息进入办理态，已读消息进入只读态，只有办理成功后才标记来源消息已读，普通消息仍可自动已读。
11. 待办页 `onShow` 必须重新请求当前作用域；办理完成后的回退使用统一兜底，不得依赖一定存在的上一页。

## P1 交互与兼容验证

1. 本地 H5 开发页可打开；Wot 输入适配层可输入、清空，按钮和表单样式无明显破坏。
2. 小程序构建产物使用 `uni.request` 适配器，并要求通过 `VITE_MP_API_BASE_URL` 提供绝对网关地址。
3. 上传、图片和签名在 H5 使用 Fetch/FormData，在小程序使用 `uni.uploadFile`。
4. 独立入口参数 `applicationId/appId/pageId/pageCode/configKey` 能透传到统一运行页。
5. 登录、首页、消息、待办、我的、审批详情和低代码运行页使用同一蓝白黑灰视觉体系，无渐变、装饰光斑、毛玻璃或持续入场动画。
6. 自定义底部导航为标准白色固定导航，具备图标、文字、选中态和底部安全区，不使用悬浮胶囊布局。
7. 所有生产页面 Vue SFC 不超过 800 行；页面滚动区与固定顶部/底部操作区边界正确。
8. 登录页账号、密码、验证码、提交按钮和工作区弹层使用 `Ai*`/Wot 组件；空表单提交能展示 Wot Toast，登录页在 390 × 844 视口下布局完整。
9. 审批意见、转办说明、低代码多行文本和数字输入使用 `Ai*`/Wot 适配组件；待办、消息和审批详情的视觉密度与底部操作区保持一致。

## 第八轮增量验证：统一反馈与登录页

1. 新增 `feedback-components.test.js`，静态锁定全部注册页面的反馈宿主、Wot 反馈组件集合、适配器禁用 API和登录页组件化结构。
2. 重新执行既有 38 项运行时测试与新增反馈测试，避免公共反馈宿主影响低代码及审批路径。
3. 重新执行 `pnpm build:h5` 与 `pnpm build:mp-weixin`，验证 Wot MessageBox/ActionSheet 在两端均可编译。
4. 使用 H5 开发页和 390 × 844 手机视口检查登录页，并触发一次空表单登录提示验证 Wot Toast 实际显示。
5. 执行反馈 API 残留扫描、生产页面行数检查和 `git diff --check`。

## 第九轮增量验证：消息与待办处理回路

1. 新增 `message-flow-navigation.test.js`，验证流程任务 ID 的多来源解析、Long ID 字符串语义、待办/只读模式和页面回退链路。
2. 验证 `FLOW_TODO` 详情打开时不会提前标记已读；普通消息仍按原逻辑自动已读，批量已读排除活动审批待办。
3. 验证待办页每次 `onShow` 刷新、审批提交后统一回退，并复用既有历史任务详情和只读表单协议。
4. 检查搜索、多行文本和数字输入均通过 `Ai*`/Wot 适配组件渲染，生产代码无原生 `input/textarea` 和 uni 原生反馈调用残留。
5. 重新执行定向单测、H5/微信小程序生产构建、390 × 844 组件交互冒烟、页面行数检查和 `git diff --check`。

## 第十轮增量验证：同步 main

1. 将最新 `origin/main` 合并到 `codex/mobile-lowcode-runtime-refactor`，确认无未解决冲突并执行暂存区空白检查。
2. 复跑 48 项 H5 运行时、审批、消息与统一反馈定向测试，确认主线更新未破坏移动端链路。
3. 重新执行 H5 与微信小程序生产构建；本轮不重复验证 main 自身已交付的管理端和后端功能。

## 第十一轮增量验证：火山方舟风格全站重构

1. 静态检查全局主色、灰阶、`6px` 圆角、`44px` 触屏高度、系统字体和无默认阴影约束。
2. 检查登录、首页、消息、待办、账户、审批详情、低代码运行页、独立入口与演示页仍挂载统一反馈宿主并复用 `Ai*` 组件。
3. 在 `390 × 844` 与 `1440 × 900` 视口验证移动单列、桌面分栏、固定操作区、滚动边界和登录页结构。
4. 复跑 53 项 H5 运行时/审批/消息/设计规范测试，执行 H5 与微信小程序生产构建及 `git diff --check`。

## 第十二轮增量验证：首页与待办工作台

1. 静态锁定 `AiField`、`AiSearchBar`、`AiSelect` 和 `AiDateTimePicker` 的垂直居中规则及 `44px` 触控高度。
2. 检查首页只有一个工作概览结构，常用应用与最新提醒层级明确，桌面端主区/侧栏和移动端单列断点保持稳定。
3. 检查待办列表不再挂载任务中转弹层；待签收任务点击调用既有签收接口后直接导航审批页，其他待办直接导航。
4. 复跑 56 项 H5 运行时、审批、消息和设计规范测试，执行 H5/微信小程序生产构建、移动视口输入控件验证及 `git diff --check`。

## 第十三轮增量验证：消息直达与审批动态表单

1. 检查 `pages.json` 无 `navigationStyle: custom`，消息页无详情弹层；列表点击根据消息类型直接导航，普通消息完整详情页具备统一反馈宿主。
2. 单测覆盖 form-create 嵌套布局、主子表数组和节点权限，并复跑既有移动运行时、审批、消息、设计规范测试。
3. 核对登录配置与租户公开 Logo 接口、用户头像与鉴权图片通道保持和 PC 相同的后端来源。
4. 在 390px 与 836px H5 视口检查搜索图标、控件 44px 高度、状态筛选和原生导航；构建 H5 与微信小程序并执行 `git diff --check`。
5. 若无真实 App/Flow 服务与可办理任务，仅记录未验证的 API/动态表单/转办弹层实际数据行为，不把静态测试当作端到端通过。

## 第十四轮增量验证：uiDocument v1 审批表单

1. 与 PC 协议测试对照，覆盖组件树优先、画布外字段不回灌、组件 props/类型、节点可见/可编辑、无字段目录的设计器新增字段、sections 排序和旧协议回退。
2. 验证 H5 页面将解析后的树交给 `LowcodeForm`，字典、表单校验和主表提交字段均来自同一解析结果；子表继续由 `childrenConfig` 渲染。
3. 复跑既有审批适配与移动端定向测试，执行 H5/微信小程序生产构建、SFC 行数和 `git diff origin/main --check`。
4. 不启动真实服务或写数据库；无真实待办和发布态表单时，记录端到端审批未验证。

## 第十五轮增量验证：轻量化商务 H5

1. 检查主题色、字号/间距、四栏 tabBar、消息角标及 320/375/430px 安全区；原生导航配置保持无 `navigationStyle: custom`。
2. 对公共输入、搜索、单选及 Tab 检查值、占位、图标和点击热区的垂直对齐；无可用服务时用组件结构及视口冒烟验证。
3. 验证待办、消息及多条件低代码列表筛选弹层的草稿/重置/确认语义；消息点击仍直达办理或详情，待办动态表单仍用 JSON 协议。
4. 复跑现有 H5 定向测试，并更新视觉契约测试；执行 H5/微信小程序生产构建和 `git diff --check`。真实后端数据和审批提交保持人工联调项。

## 执行命令

```bash
cd forge-h5-ui
node --test \
  src/api/__tests__/runtime-api-contract.test.js \
  src/store/modules/__tests__/lowcodeRuntime.test.js \
  src/components/lowcode/__tests__/mobile-component-registry.test.js \
  src/utils/__tests__/business-task-form-adapter.test.js \
  src/utils/__tests__/flow-action-idempotency.test.js \
  src/utils/__tests__/feedback-components.test.js \
  src/utils/__tests__/message-flow-navigation.test.js \
  src/utils/__tests__/console-design-system.test.js \
  src/utils/__tests__/lowcode-runtime.test.js \
  src/utils/__tests__/uni-adapter.test.js \
  src/utils/__tests__/mobile-selector-runtime.test.js \
  src/utils/__tests__/machine-code.test.js \
  src/utils/__tests__/form-create-mobile.test.js \
  src/utils/__tests__/message-html.test.js

./node_modules/.bin/uni build
./node_modules/.bin/uni build -p mp-weixin

cd ..
git diff --check
```

## 暂不执行

- 不启动 Admin、App、Flow 服务，不写数据库；后端协议未变，且用户偏好由用户完成真实服务联调。
- 不执行真实审批、上传和小程序合法域名请求；需要可用账号、已发布低代码配置、Flow 任务和 HTTPS 网关。
- 不执行审批、转办、退出登录等真实业务写操作；组件库收敛仅改变表现层和交互载体。

## 第十六轮增量验证：已登录工作台可用性

1. 已登录浏览器分别检查 375px 与当前 H5 宽度：待办筛选、分类项、详情刷新、转办人员文字及固定确认按钮；仅选择和取消，不提交真实审批或转办。
2. 核对首页可用鼠标/触摸滚动到常用应用和最新提醒，宽屏有可辨认滚动提示；四个主页面顶部在中等宽度不占掉首屏主体。
3. 结构单测锁定刷新入口归属、分类筛选不嵌套 picker、转办行高和列表紧凑结构；复跑相关既有定向测试。
4. 执行 H5 与微信小程序生产构建、`git diff --check`，记录真实服务只读浏览结果及未执行的业务写操作。

## 第十七轮增量验证：查询刷新与菜单

1. 单测授权树筛选：只接纳可见菜单、H5 已注册页面或低代码配置页；隐藏、按钮、PC 路由、演示/占位页不进入首页，不使用虚构兜底。
2. 静态检查四处查询页面下拉刷新绑定、实际请求函数与刷新状态复位；待办详情滚动轨道及快捷意见移除。
3. 已登录浏览器点击首页真实应用、待办筛选/下拉、消息查询/下拉、待办详情滚动，不执行审批写操作；没有授权低代码记录时记录空状态。
4. 复跑 H5 定向 Node 测试，执行 H5/微信小程序构建和 `git diff --check`；只记录本轮差异与真实服务异常。

## 第十八轮增量验证：审批记录本地化

1. 单测覆盖流程节点 `pending/running/completed`、审批动作 `start/approve/reject/return/delegate/claim` 及未知英文码的中文降级；时间覆盖本地 ISO、空格时间、数组时间、带时区 ISO 与空值。
2. 结构检查详情仅有“业务内容 / 流程记录”两个 Tab，节点进度和办理记录两路数据仍完整使用，审批详情不改变动作接口。
3. 已登录只读任务中检查无 `pending` 裸码和 `T` 时间，两个分区能在窄屏滚动；不点击任何审批写按钮。
4. 执行 H5 定向 Node 测试、H5/微信小程序生产构建与 `git diff --check`；记录远端表单身份校验既有异常，不误判为本轮回归。

## 第十九轮增量验证：Wot 组件与首页菜单

1. 静态检查 `main.js`、`App.vue`、`pages.json` 和依赖清单无 uView；公共弹层、选择器、开关和导航使用 Wot 组件。
2. 单测锁定选择器 44px 垂直对齐、接口菜单图标保留、常用应用前四项与“更多”全菜单结构、Logo 回退和角标边界。
3. 在 390×844 已登录视口实际检查首页品牌 Logo/角标、全部菜单弹层、待办筛选底部按钮、流程分类 Wot 单选列表及个人中心品牌 Logo。
4. 执行全部 H5 工具 Node 测试、H5 与微信小程序生产构建、uView/旧组件残留搜索和 `git diff --check`。

## 第二十轮增量验证：参考稿还原与审批表单兼容

1. 静态锁定新主题色、Slate 文字、20px 卡片、下划线 Tab、固定审批操作栏，以及首页/待办/消息/我的对真实接口数据的消费，不引入参考稿模拟数据。
2. 单测审批详情优先复用业务上下文的 `taskFormInfo`；上下文不可用时从 `/ai/business/flow/form-assets/:objectCode` 恢复字段、权限、协议版本和 JSON UI 文档。
3. 在 390×844 已登录页面实际查看首页、待办、消息、我的和可办理详情；检查详情滚动、动态表单、中文流程记录、更多操作和转办弹层底部安全区，不确认写操作。
4. 执行全部 H5 工具 Node 测试、H5/微信小程序生产构建和 `git diff --check`；仅把浏览器真实可见结果记为端到端证据。

## 第二十一轮增量验证：列表与表单密度

1. 单测通用 `AppsOutline/Grid` 图标会按印章、店铺、资产、审批等真实菜单语义映射，明确配置的业务图标保持不变，未知菜单使用稳定 ID 兜底。
2. 静态检查待办无独立统计工具行，任务卡包含类型/时间、状态、标题、节点、申请人和主操作；审批详情单行字段启用 78px 标签列且控件保持 44px。
3. 在 390×844 已登录页面对照检查首页菜单图标、待办列表、消息列表、个人中心和可办理动态表单，不执行任何业务写操作。
4. 复跑全部 H5 工具 Node 测试、H5/微信小程序生产构建和 `git diff --check`。

## 第二十二轮增量验证：动态表单全属性契约

1. 从 PC `field-components.js`、`layout-components.js` 动态读取全部属性 key，与移动端兼容清单逐项比较；PC 新增未覆盖属性时测试失败。
2. 单测 JSON 审批文档完整保留字段 `props/validation/rules/defaultValue/visibility`、布局 props 和未知未来声明式属性，字段权限仍覆盖设计器可编辑状态。
3. 单测移动字段归一、必填/长度/数值范围/正则校验，以及单选、开关、金额、文本的只读格式；复跑业务表单、form-create 和远程选择器既有测试。
4. 执行全部 H5 Node 测试、H5 与微信小程序生产构建和 `git diff --check`；在 390×844 已登录审批详情确认真实动态字段无横向溢出，不提交审批或转办。

## 第二十三轮增量验证：审批操作与主页面视觉校准

1. 静态检查审批意见无重复“处理意见”标签，多行输入正文/占位为 14px；更多操作为四列宫格且包含底部取消按钮。
2. 静态检查消息页无显式刷新按钮，使用下划线分类 Tab 和独立圆角消息卡；消息直达处理逻辑保持不变。
3. 在已登录 H5 实际检查审批意见字号/高度、更多操作宫格、消息列表和个人中心滚动条；只打开弹层，不提交审批、转办或退出。
4. 复跑全部 H5 Node 测试、H5/微信小程序生产构建与 `git diff --check`。

## 第二十四轮增量验证：消息筛选与审批详情加载态

1. 静态检查消息查询行具备可收缩搜索容器、固定筛选触控区和 359px 以下图标模式，页面不得产生横向滚动。
2. 单测锁定详情整页和表单局部均使用 `TodoDetailSkeleton`，且旧列表骨架仅保留给转办人员列表。
3. 在 320px 已登录消息页检查筛选按钮边界；在 390×844 审批详情捕获加载态并检查任务摘要、表单、流程及意见标题图标。
4. 复跑全部 H5 Node 测试、H5/微信小程序生产构建与 `git diff --check`，不触发审批、转办或消息写操作。

## 第二十五轮增量验证：审批 JSON 分区与完整操作

1. 用用户粘贴响应的关键结构覆盖回归：主表字段权限全部可写而 `uiDocument` 两字段 `editable=false` 时，最终三个字段仍按节点权限可编辑；显式 `props.readonly` 继续只读。
2. 单测 `section_default + child_business_object_hl92 + child_business_object_0eq3` 保持原顺序，并分别关联 `business_object_hl92/business_object_0eq3` 与 `inline_grid` 子表展示模式，不重复追加模块。
3. 静态检查主表组件树挂载到真实 card section，多子表启用 Tab；审批意见使用常用意见接口；`allowRejectToStart` 进入更多操作并调用 `/api/flow/task/reject-to-start` 或业务任务原子动作接口。
4. 在已登录 H5 只读点验字段可编辑、快捷意见填入、意见管理弹层和更多操作授权按钮；不保存意见、不删除意见、不提交同意/驳回/退回/转办。
5. 复跑全部 H5 Node 测试、H5 与微信小程序生产构建和 `git diff --check`；若远端任务上下文仍被身份校验拒绝，子表只以真实响应契约测试为证据并明确未完成在线数据验收。

## 第二十六轮增量验证：鉴权图片请求循环

1. 静态回归锁定 `AiAuthImage` 按原始文件 ID 记录重试、排除兜底图片错误，并禁止恢复按临时签名 URL 判断重试的旧逻辑。
2. 静态回归锁定 `resolveFileAccessUrl` 对同一文件 ID 复用进行中的 Promise，并在完成后安全清理 pending 状态。
3. 在已登录 H5 审批详情刷新页面，观察管理员头像文件 ID `b5d279d093c746c0bcccb49bbfb26678` 的 `/api/file/url/` 请求：页面稳定后不得持续增长；不触发审批、转办或其它业务写操作。
4. 复跑全部 H5 Node 测试、H5 与微信小程序生产构建及 `git diff --check`；记录既有构建警告和用户已有前端服务状态。

## 第二十七轮增量验证：审批上下文真实性与 Flow 自调用

1. Flow 客户端单测锁定未显式配置时的服务地址为 `http://localhost:8081`；Flow 服务部署后确认其内部 `/api/flow/task/form/{taskId}` 请求携带当前用户 Token，不降低租户和任务参与人校验。
2. H5 结构回归锁定审批字段只来自 `businessContext`，源码和 API 层不再包含 `hydrateBusinessContextFromAssets/getBusinessFlowFormAssets`，也不以 `formInfo.variables` 初始化业务表单。
3. 模拟 `task-form-context` 失败时，页面记录可读错误、隐藏动态表单并禁用审批动作；同一次进入详情不二次调用上下文接口。接口返回成功时继续完整渲染 `uiDocument.sections`、主子表字段、权限与真实记录。
4. 执行全部 H5 Node 测试、H5/微信小程序构建、Flow client 目标单测或模块编译及 `git diff --check`。若本机缺少 Java/Maven 或登录态，只记录限制，不把远端接口和真实表单宣称为已通过。
