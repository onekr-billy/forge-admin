# Forge Admin 前端开发与组件规范

本规范适用于 `forge-admin-ui` 的 Vue 3 页面、组件和接口代码。项目级约束以仓库根目录 `AGENTS.md` 为准；本文件补充前端实现细则。

## 1. 技术与基本约定

- 使用 Vue 3 Composition API 与 `<script setup>`，不新增 Options API 页面。
- UI 组件优先使用 Naive UI；原子样式使用 UnoCSS；请求统一经过项目请求工具。
- 使用 `pnpm`。前端校验优先执行：

```bash
source ~/.nvm/nvm.sh && nvm use v20.19.0
pnpm exec eslint <changed-files>
pnpm build
```

- 页面是操作控制台，不添加营销式大标题、无业务价值的统计卡或持续动画。
- 新功能优先复用现有组件、组合式函数和字典能力，不复制粘贴同类实现。

## 2. 目录职责

```text
src/
├── api/              # 按业务域定义接口请求
├── components/       # 可跨页面复用的展示与交互组件
│   ├── ai-form/      # AiCrudPage、AiForm、AiTable 等配置化能力
│   ├── common/       # 通用工作台、树、选择器、鉴权图片等
│   └── lowcode-builder/ # 低代码设计、预览和协议解释器
├── composables/      # useXxx 组合式状态与行为
├── config/           # 应用级静态配置
├── layouts/          # 应用导航、页签和整体壳层
├── router/           # 路由与动态路由装配
├── stores/           # Pinia 状态；跨页面且有明确生命周期的数据才放这里
├── styles/           # 全局变量、重置和主题样式
├── utils/            # 无 UI 的纯工具、请求、加密、文件和校验能力
└── views/            # 路由页面，按业务域组织，例如 system/、flow/、ai/
```

文件应放在最小且正确的作用域：只服务一个页面的子组件放在该页面同级 `components/`；可被两个以上业务域使用的组件放入 `src/components/`。

## 3. 页面布局与工作台

### 系统页面

`/system/**` 路由会由应用级 `SystemPageLayout` 提供统一的满高和滚动边界。页面工作区外边距由各 Layout 统一提供 8px；组件内部不负责页面级外边距。

普通 CRUD 页面根节点保持最小：

```vue
<template>
  <div class="system-config-page">
    <AiCrudPage ... />
  </div>
</template>
```

### 左侧对象 + 右侧工作区

当左侧树、角色、分类等对象决定右侧数据时，使用 `MasterDetailWorkspace`，不再分别制作两个独立卡片。默认使用连体模式：一条外框、内部细分隔线，视觉上是同一个工作台。

```vue
<MasterDetailWorkspace
  :collapsed="collapsed"
  :aside-width="220"
  :collapsed-aside-width="72"
>
  <template #aside>
    <OrgTreePanel />
  </template>

  <AiCrudPage ... />
</MasterDetailWorkspace>
```

- `aside` 插槽放树、对象列表或筛选导航；默认插槽放表格、详情或页签工作区。
- 仅当两块内容确实相互独立时才设置 `:attached="false"`。
- 组件已经处理窄屏上下堆叠；页面不要复制响应式列宽逻辑。
- 左右区域必须各自 `min-height: 0` 并在内容区滚动，避免整页和表格双重滚动。

## 4. 组件选择与使用规范

| 场景                                 | 首选组件/能力                        | 约定                                                                                       |
| ------------------------------------ | ------------------------------------ | ------------------------------------------------------------------------------------------ |
| 标准列表、新增、编辑、删除、导入导出 | `AiCrudPage`                         | API 占位符使用 `:id`，分页使用 `pageNum`、`pageSize`                                       |
| 配置化表单                           | `AiForm`                             | Schema 只描述字段与行为，不在页面重复实现控件渲染                                          |
| 表格                                 | `AiTable` / `AiCrudPage` 内置表格    | 默认密度为 `medium`；需要切换时使用紧凑/默认/宽松语义                                      |
| 字典下拉与回显                       | `DictSelect`、`DictTag`、`useDict()` | 禁止硬编码业务枚举和状态标签                                                               |
| 行政区划                             | `RegionTreeSelect`                   | 不自行复制区划树转换逻辑                                                                   |
| 鉴权图片                             | `AuthImage`                          | 文件字段保存的是 `fileId`，不是可直接使用的 URL                                            |
| 左树右表、主从页                     | `MasterDetailWorkspace`              | 左侧和右侧通过插槽组织                                                                     |
| 页面级统一边界                       | `SystemPageLayout`                   | `/system/**` 已自动接入，无需手工包裹                                                      |
| 用户、组织、租户等实体单元格         | `SystemTableCell`                    | 实体使用主标题 + 换行辅助标识；用户可提供详情入口和首字标识，多值归属显示主值与可展开 `+N` |

新公共组件应明确：输入 Props、输出事件、插槽职责、空态/加载态和键盘可访问性。图标按钮必须有 `title` 或 `aria-label`。

`AiCrudPage` 只负责搜索、工具栏、表格和表单业务区域；不要在组件内加入页面级 margin、padding 或外框。外边距统一由当前 Layout 提供，主从工作台的外框由 `MasterDetailWorkspace` 提供。

## 5. 命名规范

| 对象                 | 规则                                                    | 示例                                   |
| -------------------- | ------------------------------------------------------- | -------------------------------------- |
| Vue 组件文件与组件名 | `PascalCase.vue`                                        | `MasterDetailWorkspace.vue`            |
| 页面文件             | 新页面用 `kebab-case.vue`；历史文件不为统一命名而改路由 | `storage-config.vue`                   |
| 组合式函数           | `use` + `PascalCase`，文件同名                          | `useDict.js`、`usePermission.js`       |
| Pinia Store          | `use` + 领域 + `Store`                                  | `useUserStore`                         |
| 普通函数/变量        | `camelCase`                                             | `loadUserList`、`selectedOrgNode`      |
| 常量                 | `UPPER_SNAKE_CASE`                                      | `USER_STATUS_DICT`                     |
| CSS 类               | 业务/组件前缀 + `kebab-case`                            | `master-detail-workspace__aside`       |
| 事件                 | 动词开头的 kebab-case                                   | `@selection-change`、`@submit-success` |

避免含义宽泛的 `data`、`list`、`handleClick`。使用能表达领域和动作的名字，例如 `tenantOptions`、`handleOrgNodeSelect`。

## 6. 接口、状态与字典

- API 定义放在 `src/api/<domain>.js`，页面只调用领域接口或 `AiCrudPage` 配置，不直接散落 Axios 配置。
- 普通请求使用 `request`；与后端 `@ApiDecrypt` 对应的敏感提交使用 `postEncrypt`。
- REST CRUD 保持 `GET /page`、`GET /:id`、`POST /`、`PUT /`、`DELETE /:id` 语义；配置化 API 路径占位符用 `:id`，不用 `{id}`。
- 远端字典通过 `useDict('<dict_type>')` 获取。Schema 中的 options 用 `computed` 派生，确保异步加载后可回显。
- 仅当前页面使用的交互状态放 `ref`；需要跨路由共享、可恢复或全局可见时才放 Pinia。

## 7. 样式与交互

- 优先使用主题变量：`--primary-color`、`--bg-primary`、`--border-light`、`--text-primary`、`--text-tertiary`。
- 操作链接遵循语义色：编辑/查看 `text-primary`，详情 `text-info`，警告 `text-warning`，删除 `text-error`，成功操作 `text-success`。
- 企业后台以边框、层级和留白区分内容；避免渐变、重阴影、无意义的圆角卡片嵌套。
- 通常使用 6px 面板圆角、8px Layout 工作区边距、6–8px 控件间距。不要在页面根节点叠加大于 12px 的无业务留白，也不要对 `AiCrudPage` 再套大圆角白色卡片。
- 动效仅用于 `color`、`background`、`border`、`opacity`、`transform`，时长控制在 120–180ms；不要动画宽高或位置。
- 同时支持亮/暗主题。新增固定颜色前先确认主题变量不能满足。

## 8. 提交前检查

1. 运行 `git diff --check`，确认没有空白错误。
2. 对修改文件执行 `pnpm exec eslint <files>`。
3. 修改 Vue、样式或构建配置时执行 `pnpm build`。
4. 变更 `AiCrudPage`、字典、文件或低代码协议时，至少确认相关页面的默认值、回显、空态与窄屏行为。
5. 不提交 `.env.local`、密钥、Token、真实用户数据或无关构建产物。

## 9. Vue 编码规范

### 9.1 SFC 组织顺序

单文件组件按以下顺序组织，保持同一类组件易读、易审查：

```vue
<template>
  <!-- 页面结构或组件结构 -->
</template>

<script setup>
// 1. 第三方与项目 import
// 2. defineOptions / defineProps / defineEmits
// 3. refs 与静态常量
// 4. computed 与 watch
// 5. 数据加载、事件处理、辅助函数
// 6. 生命周期
</script>

<style scoped>
/* 当前组件样式 */
</style>
```

- Import 按「第三方 → `@/` 项目路径 → 相对路径」分组；删除未使用 import。
- 组件默认使用 `defineOptions({ name: '...' })`，名称与组件文件一致。
- Props 必须声明类型、默认值和必要时的 validator；对象、数组默认值使用工厂函数。
- Emits 只暴露必要事件，事件名描述已经发生的业务结果，例如 `submit-success`、`selection-change`。
- 不在模板中写复杂计算、数组过滤或深层取值；提取为 `computed`、`resolveXxx` 或 `isXxx` 函数。
- `v-for` 必须使用稳定的 `:key`，禁止用数组下标作为可排序、可编辑列表的 key。
- 异步请求必须处理 loading、异常和 finally；用户可见的失败使用 `window.$message` 或页面内错误态说明。

### 9.2 状态边界

| 状态类型                 | 放置位置                 | 示例                            |
| ------------------------ | ------------------------ | ------------------------------- |
| 单个控件、弹窗、筛选值   | 当前组件 `ref`           | `modalVisible`、`selectedOrgId` |
| 基于当前状态推导的展示值 | `computed`               | `filteredRows`、`canSubmit`     |
| 可复用请求/交互逻辑      | `src/composables/useXxx` | `useDict`、`usePermission`      |
| 登录态、主题、跨页会话   | Pinia Store              | `useUserStore`、`useAppStore`   |
| 不随渲染变化的映射/常量  | 模块顶层 `const`         | `USER_STATUS_DICT`              |

- 组件不能直接修改父组件传入的对象或数组；通过事件上抛，或先复制后提交。
- `watch` 只处理副作用（重新请求、同步外部值、清理资源），不能替代本应使用的 `computed`。
- 组件卸载后仍可能返回的异步请求，需要避免继续写失效状态；轮询、事件监听和定时器必须在卸载时清理。
- 不把接口返回对象无差别地扩散到全局 Store；先明确数据归属和失效策略。

### 9.3 模板与可访问性

- 有业务语义的容器使用 `header`、`main`、`section`、`aside`、`nav`、`article`，不要只堆叠 `div`。
- 非按钮元素不可承担点击行为；需要点击时用 `button`，或补齐 `role`、`tabindex`、键盘 Enter/Space 行为。
- 图标按钮必须带 `title` 或 `aria-label`；图片必须说明 `alt`，装饰图片才可空 alt。
- 对话框、抽屉和全屏工作台打开后，焦点应落在可操作区域；关闭后尽量回到触发元素。
- 长文本在列表、树节点、标签中要配置省略、完整标题或可展开查看，不能让列撑破页面。

## 10. 表单、表格与 CRUD 规范

### 10.1 Schema 与表单

- 查询 Schema、编辑 Schema 使用 `computed` 生成，特别是依赖字典、权限或上下文的 options。
- 字段 `field` 与后端 DTO 字段保持一致；展示 label 可以改中文，字段编码不随意改名。
- 新增、编辑、详情有差异时使用 `beforeRenderForm`、`beforeRenderDetail` 或字段权限配置，禁止复制三套表单。
- 详情优先配置完整 `editSchema`，保证字典、枚举和敏感字段按业务规则展示；`AiCrudPage` 会在遗漏 Schema 时按可见表格列生成只读兜底详情，防止空白弹窗，但不能替代正式 Schema。
- 提交前处理使用 `beforeSubmit`；返回 `false` 时明确阻止提交，异步逻辑必须 `await`。
- 复杂结构化编辑（基础信息、可排序明细、高级设置、预览）使用独立全屏工作台或同路由页面，不塞进侧边抽屉。
- 少字段、单一职责的编辑才使用 Modal 或 Drawer；关闭、取消不能污染未确认草稿。

### 10.2 表格

- 表格默认密度为 `medium`，仅高密度审计、日志或对比场景使用 `small`；不要把整站默认设为紧凑。
- `columns` 只承载列配置与必要渲染函数；超过三段逻辑的单元格渲染抽为小组件。
- 表格单元格只表达一种信息层级：用户、组织、应用等实体使用 `SystemTableCell` 的主标题 + 辅助标识；关联关系显示主值与可展开 `+N`；枚举状态才使用 `DictTag`；普通属性保持纯文字。不要在每个单元格叠加图标、色块和 Tag。
- 用户类实体以真实姓名或昵称为主标题，登录名以 `@username` 作为辅助标识；主标题区域可点击查看详情。性别等低频属性保留在详情或列设置，不挤占主列表。
- 操作列使用文字链接和项目语义色；常用操作直接显示，多个低频操作才放进“更多”。
- 分页接口统一传 `pageNum`、`pageSize`；远端排序、筛选参数必须与后端接口定义一致。
- 文件图片字段通过 `AuthImage` 渲染；下载链接通过 `getFileUrl(fileId)` 获取。
- 空态要区分“暂无数据”与“没有匹配结果”；筛选后为空时提供重置或调整筛选的明确入口。

### 10.3 删除与危险操作

- 删除、批量删除、重置密码、权限变更、状态流转必须二次确认，并明确对象和影响范围。
- 删除成功后刷新当前列表；分页最后一页被清空时回退到有效页。
- 按钮禁用状态不是安全边界，前端仍需按权限条件隐藏或禁用，后端必须继续校验权限。

## 11. 路由、权限与导航

- 路由页面放在 `src/views/<domain>/`，目录和路由业务域一致；动态路由由现有路由装配机制生成，不手写冲突路径。
- 新增菜单、按钮权限后，页面通过现有 `usePermission` 或权限指令判断展示；不能仅依赖前端隐藏来保护操作。
- 不为一次跳转滥用全局 Store。可由 URL 表达的筛选、记录 ID、页签等状态优先使用路由 query/params。
- 从列表打开独立设计器、预览或复杂工作台时，按既有交互选择独立页签或同路由工作区，并保证返回路径可用。
- `KeepAlive` 页面需要处理路由参数切换、激活后的刷新与资源释放，不能假设 `onMounted` 只会运行一次。

## 12. 请求、文件与安全

### 12.1 请求

- 所有请求通过 `@/utils/request` 或现有 API 模块；禁止在组件内新建 Axios 实例。
- API 模块函数以动词和资源命名，例如 `fetchUserPage`、`createUser`、`updateUser`、`removeUsers`。
- 明确约定请求参数位置：查询用 `params`，JSON 提交用 `data`；不要把对象序列化后拼进 URL。
- 不记录 Token、密码、手机号、身份证、银行卡、API Key 或完整服务端异常到控制台与埋点。
- 前端只做交互层校验和脱敏展示，权限、租户、数据范围、加密解密必须由后端最终控制。

### 12.2 文件

- 上传字段存储 `fileId`，表单与接口不要把临时 URL 当作持久业务值。
- 预览、下载、图片渲染统一复用项目文件工具和鉴权组件；不要自行拼接 OSS 地址或 Token。
- 批量导入、导出、下载属于阻塞性操作，应有局部 loading、防重复提交和可理解的失败反馈。

## 13. 样式工程规范

### 13.1 样式层次

1. 能用 Naive UI Props 解决的，不新增 CSS。
2. 能用 UnoCSS 表达的局部布局与间距，优先使用 UnoCSS。
3. 组件结构、主题兼容、复杂 hover/响应式规则使用当前 SFC 的 `<style scoped>`。
4. 跨业务域的视觉变量、重置和通用规则才进入 `src/styles/`。

- 不在多个页面复制同一组面板、树、工作台样式；先抽为公共组件。
- 不用 `!important` 覆盖正常组件样式；确需覆盖第三方内部节点时局部使用 `:deep()` 并写明原因。
- CSS 选择器以本组件根类为前缀，避免裸标签选择器污染后代组件。
- 禁止用负 margin、绝对定位修正正常布局；先检查 Grid/Flex、`min-width: 0`、`min-height: 0` 与溢出边界。

### 13.2 主题与响应式

- 亮暗主题都使用同一语义变量；新增深色模式特例必须和亮色规则同时提交。
- 布局断点优先使用现有 960px、768px 规则。窄屏由多列切为单列，工作台由左右切为上下。
- 页面外层不再叠加 `p-12` 等大留白；所有 Layout 内容区统一提供 8px 工作区边距，流程画布等 flush 页面保持 0。
- 表格、树和卡片容器必须配置可预期的溢出策略，禁止横向内容撑破视口。

## 14. 测试、排查与评审清单

### 最小验证矩阵

| 变更         | 必做验证                                      |
| ------------ | --------------------------------------------- |
| Vue、JS、CSS | 目标文件 ESLint + `pnpm build`                |
| CRUD 页面    | 列表、查询、重置、分页、增改删、空态          |
| 字典字段     | 异步加载、下拉回显、表格标签回显              |
| 文件字段     | 上传、编辑回显、鉴权预览、下载                |
| 主从工作台   | 左侧选择、收起、右侧刷新、窄屏堆叠            |
| 权限操作     | 无权限隐藏/禁用、接口失败提示、权限变更后刷新 |

### 代码评审

- 是否已有可复用组件、composable 或 API 模块，而不是复制实现？
- 是否正确处理 loading、空态、错误、重复点击和组件卸载？
- 是否保持了字典、租户、权限、文件和加密约定？
- 是否避免硬编码颜色、枚举、接口地址、密钥和用户敏感信息？
- 是否在亮暗主题和窄屏下保持可读、可滚动、可点击？
- 是否只修改任务相关文件，并保留用户已有的工作区改动？

## 15. 常用示例

### 字典字段

```vue
<script setup>
import { computed } from 'vue'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables/useDict'

const { dict } = useDict('sys_user_status')
const userStatusOptions = computed(() => dict.value.sys_user_status || [])
</script>

<template>
  <DictTag :options="userStatusOptions" :value="row.userStatus" />
</template>
```

### 配置化 CRUD 的路径占位符

```vue
<AiCrudPage
  :api-config="{
    list: 'get@/system/user/page',
    detail: 'get@/system/user/:id',
    delete: 'delete@/system/user/:id',
  }"
/>
```

不要使用 `/system/user/{id}`；组件只可靠识别冒号占位符。
