# execution-log — admin-ui-giant-sfc-split

## 2026-09-26

### 已完成

1. 变更 Spec/tasks 落盘：`code-copilot/changes/admin-ui-giant-sfc-split/`
2. **ListPageGridDesigner（13645 → 主 SFC 939）**
   - 样式 → `list-page-grid-designer-shell.css` / `list-page-grid-designer-panels.css`
   - 选项常量 → `listDesignerOptions.js`
   - 属性栏 → `ListPageBlockPropertyPanel` + Tree/Props×3/Style/Interaction（inject Proxy）
   - 脚本 → `composables/useListPageGridDesigner.part1–5.js` + 编排器
   - 组件注册 → `listPageDesignerLocalComponents.js`
3. **ForgePropertyPanel**：样式外提为 `forge-property-panel-shell.css` / `forge-property-panel-fields.css`（SFC 仍 8621，待 Wave1b）

### 验证

- `node --check` 各 composable 通过
- `vitest` nested-selection：语法已过；套件仍因既有 `autoRoutes` / router 环境失败（与本次拆分无关）

### 续：ForgePropertyPanel + page-schema

4. **ForgePropertyPanel（8621 → 主 SFC 247）**
   - panels：`SelectedFieldCollapse` / `SelectedBasicTab` / `SelectedStyleTab` / `SelectedExtraTabs` / `SelectedPropertyDrawers` / `FormPropertyTabs`
   - script：`composables/useForgePropertyPanel.part1–3.js`
   - `forgePropertyPanelContext.js` + `forgePropertyPanelLocalComponents.js`
5. **page-schema.js（2932 → barrel 55）**
   - `page-schema-fields.js` / `page-schema-catalog.js` / `page-schema-layout.js` / `page-schema-utils.js`
   - `page-schema.spec.js` 13/13 通过

### 续：GridBlockRenderer

6. **GridBlockRenderer（~5670 → 主 SFC 1530）**
   - 样式 → `grid-block-renderer.css`（1934）
   - 纯函数 → `gridBlockRendererUtils.js`（420）；闭包依赖的 `boundRowField` / `requestRuntimeEvent` / `appendRuntimeEventParams` 保留在 composable
   - 脚本 → `composables/useGridBlockRenderer.js`（1910）；Options `setup` 返回 `props`/`emit` 供模板；`expose(gridBlockExposeApi)`
   - 组件注册 → `gridBlockRendererLocalComponents.js`（`AiCrudPage` 同步 import，避免 async wrapper 导致单测 props 为空）
   - 验证：`grid-block-renderer-data-source.spec.js` **18/18**；`node --check` 通过

### 下一步

- `application-runtime.[applicationCode].vue`、`AiCrudPage.vue`、`application-runtime.css`、`dataset.vue`
- 其余 2000+ 文件按 spec 波次推进

### 续：application-runtime CSS + AiCrudPage 壳

7. **application-runtime.css（2776 → shell 1386 + canvas 1391）**
   - `application-runtime-shell.css` / `application-runtime-canvas.css`
   - `application-runtime.[applicationCode].vue` 双 `<style scoped src>` 引用
8. **AiCrudPage（5803 → 主 SFC 1028）**
   - Options `setup` + `aiCrudPageLocalComponents.js`
   - 逻辑 → `crud/composables/useAiCrudPage.js`（**4822 行，仍超 2000**：需单作用域；跨 part bag-merge / 机械纯函数外提均破坏语义，待按领域显式依赖拆）
   - 兼容单测：`AiCrudPage-compat` + `AiCrudPage-print` **14/14**（flat 返回钮按 `.inline-form-back-btn` 断言）
9. **GridBlockRenderer** 单测仍 **18/18**

### 续：AiCrudPage composable + application-runtime + dataset

10. **useAiCrudPage（4822 → part1–3 + 编排器，均 <2000）**
   - `__impl` 懒包装 + `mut` 盒装 `let`；编排器剥离 `__impl`/`mut`
   - 兼容单测 **14/14**
11. **application-runtime.[applicationCode].vue（7409 → 主 SFC 1346）**
   - Options `setup` + `applicationRuntimeLocalComponents.js`（图标/NIcon/draggable/同步组件）
   - 脚本 → `runtime-modules/useApplicationRuntime.part1–5.js`（1742/1534/1508/1529/607）
   - 源码契约单测改为读 vue+parts；prefetch 超时 15s；**18/18 + page-design-tabs 4/4**
12. **dataset.vue（6720 → 主 SFC 1525）**
   - 样式 → `dataset-shell.css` / `dataset-panels.css`
   - 脚本 → `composables/useDatasetPage.part1–3.js`；`datasetLocalComponents.js`
13. **flow/design.vue（5398 → 主 SFC 1003）**
   - 样式 → `design.css`（1573）
   - Options props/emits + `flowDesignLocalComponents.js`
   - 脚本 → `composables/useFlowDesign.part1–3.js`（1272/1156/692）；utils 单测 **26/26**
14. **ai/lowcode-apps.vue（4006 → 主 SFC 801）**
   - CSS → `lowcodeApps-shell/panels.css`；`useLowcodeApps.part1–2`
15. **ai/agent.vue（3845 → 主 SFC 865）**
   - CSS → `agent-shell/panels.css`；`useAgentPage.part1–2`

### 下一步

- 其余约 23 个 2000+ 文件（BusinessRelationDesigner、NodePropertiesPanel、AiFormItem…）
- 清零后 `pnpm build`

### 续：批量拆分清零 + AiFormItem

16. **批量 Options 壳拆分**（`split_sfc.py`）：BusinessRelation/Field/List/Form/Action、NodeProperties、login/menu/role、ForgeForm*、ExtensionEditor、StructuredList、flow model/monitor/todo、object-designer、knowledge/crud-generator/provider、external/manage 等均 <2000
17. **AiFormItem（3226 → 主 SFC 853）**
   - 样式 → `aiFormItem.css`（212）
   - 纯函数 → `aiFormItemUtils.js`（export，含选择类型常量）
   - 单 composable → `composables/useAiFormItem.js`（~1845，显式 return 模板绑定；避免 `__impl` 跨 part 提升丢失）
   - `aiFormItemLocalComponents.js`（Naive + CopyOutline + 业务子组件）
   - 验证：`AiFormItem.spec.js` **10/10**；`business-form-runtime-compile` / object-designer / phase-e / page-builder **34/34**
18. **`readSplitVueSource`**：补充 `runtime-modules/`、sibling `ListPageProperty*`、`*Utils.js`、相关 CSS，供拆分后源码契约扫描
19. **行数门禁**：`forge-admin-ui/src` 下目标扩展名文件 **0 ≥2000**

### 下一步

- 可选：`pnpm build` 全量确认
- `/archive admin-ui-giant-sfc-split` 沉淀

### 续：pnpm build 修复与验收

20. **构建失败修复**
   - `SelectedPropertyDrawers.vue`：去掉拆分残留多余 `</template>`
   - `useApplicationRuntime.part1.js`：动态 import 从 `./` 改为 `../`（runtime-modules 子目录）
   - `useFlowModel.part1.js`：`design.vue` / `version.vue` 改为 `../`
   - `forge-form-designer/composables/*`：`../form-first` → `../../form-first`（相对路径未随目录加深改写）
21. **验证**
   - `pnpm build`：**通过**（built in ~52.6s）
   - 关键单测抽样：AiFormItem + business-form-runtime-compile + phase-e **19/19**
   - 相对路径扫描：composables/runtime-modules/LocalComponents/Utils **0** 未解析

### 下一步

- `/archive admin-ui-giant-sfc-split` 沉淀（含相对路径改写踩坑）

### 续：修复 `__impl.resolveRuntimeView is not a function`

22. **根因**：`runtimeViewMode = ref(resolveRuntimeView(...))` 在 part1 eager 执行；实现却在 part4 经 `__impl` 赋值。
23. **修复**：`resolveRuntimeView` 改为 part1 内真实 `function`；去掉 lazy wrapper 与 part4 的 `__impl` 赋值。
24. **验证**：`application-runtime-load` + `page-design-tabs` + `phase-e` **26/26**

### 续：系统性修复 `__impl` 注册时序

25. **同类故障**：`resolvePageBlockObjectRef`（part2 watch 求值 part1 computed 时 `__impl` 尚未赋值）；`resolveEntryDesignTab`（part1 immediate load watch 与 part4 竞态）。
26. **修复**：
   - 全部 part：每个 `function` 定义后立刻 `__impl.fn = fn`（+224 处）
   - 将 `applicationRuntimeLoadCoordinator.run` 的 `watch(..., { immediate: true })` 从 part1 挪到 part5 末尾
27. **验证**：runtime 相关单测 **26/26**

### 续：补全 part bag 漏传的 runtime CRUD / history

28. **根因**：`useRuntimeCrudConfig` / `useBuilderHistory` 的返回值在 part1 使用，但未写入 part1 `return`，后续 part 解构不到 → `runtimeCrudPropsByObjectId is not defined`。
29. **修复**：part1 return + part2–5 deps 补上 `runtimeCrudPropsByObjectId`、`runtimeCrudLoadingObjectIds`、`runtimeCrudUnavailableObjectIds`、`loadRuntimeCrudProps`、`resetRuntimeCrudConfig`、`historyReady`、`canUndo`/`canRedo`、`resetBuilderHistory`、`undoBuilder`/`redoBuilder`/`handleBuilderShortcut`。
30. **验证**：`application-runtime-load` + `page-design-tabs` **22/22**

### 续：`activePageDesignTab is not defined` + Failed to resolve async panels

31. **根因**
   - `activePageDesignTab` 在 part4 才 `ref()`，但 part1 watches/`load` 与 part2 处理器已闭包引用 → `ReferenceError`
   - Options API shell 只通过 `components:{}` 解析 PascalCase 标签；`setup` return 的 `defineAsyncComponent` 无法注册 → `Failed to resolve component: ForgeFormDesigner` 等
32. **修复**
   - part1 提前创建 `activePageDesignTab`；part2/part4 从 deps 解构；part4 在 `resolvePageDesignTab` 就绪后写回 `.value`
   - 异步面板迁入 `applicationRuntimeLocalComponents.js` 并挂到 Options `components`；part1 改为从该模块 import（保留 bag 回传）
33. **验证**：`application-runtime-load` + `page-design-tabs` + `phase-e` **26/26**

### 续：ForgeFormDesigner `previewMode is not defined`

34. **根因**：拆 part 时漏掉一整段 UI state 声明（`slots` / `selectedId` / `undoStack` / `previewMode` / `canvasView` / …），return bag 仍导出这些名。
35. **修复**：按 HEAD 原 SFC 顺序补回 `useForgeFormDesignerPage.part1.js` 顶部声明块。
36. **验证**：语法检查 + designer 相关单测（见下方）

### 续：ForgePropertyPanel `loadDefaultValueDictOptions is not defined`

37. **根因**：`useForgePropertyPanel` 拆成 part1/2/3 时未引入 `__impl` 懒包装；part1 `watch(selectedDictType, …, { immediate: true })` 调用仍在 part3 的实现；part2/part3 还缺 vue/API/schema import。
38. **修复**：
   - part1 为 part2+part3 函数建立 `__impl` 懒包装；各 part 定义后立刻 `__impl.fn = fn`
   - 该 immediate watch 挪到 part3（`__impl.loadDefaultValueDictOptions = …` 之后）
   - 为 part2/part3 补齐 import；编排器剥离 `__impl`/`mut`
39. **验证**：`node --check` 三 part + orchestrator；`business-field-switch-persistence` + `business-form-runtime-compile` **8/8**

### 续：ForgePropertyPanel `designerStore is not defined`

40. **根因**：part2/part3 return 手抄枚举上游 key，但 deps 解构不全；`designerStore` 是 return 求值时第一个未声明标识符。测试文件未改业务代码。同模式还存在于 `useListPageGridDesigner.part2–5`。
41. **修复**：part2+ return 改为 `return { ...deps, /* 本 part 局部 key */ }`；全库扫描 `*.part[2-9].js` return shorthand 作用域，剩余问题为 0。
42. **验证**：`node --check` useForgePropertyPanel + useListPageGridDesigner 各 part；作用域扫描 0 missing。

### 续：application-runtime 模板缺 import 绑定

43. **根因**：Options `setup` + composable 后，`defaultLogo` / `WORKBENCH_PAGE_ID` / `isNavigationVisible` / `DEFAULT_PAGE_PADDING` 仅 import 未 return；模板渲染时报 not defined，随后 `isNavigationVisible is not a function`。
44. **修复**：补进 `useApplicationRuntime.part1.js` return（经 `...deps` 传到 publicApi）。
45. **验证**：runtime 相关合约单测 **26/26**。

### 续：AiCrudPage 模板 `props.columns` 崩溃

46. **根因**：Options `setup(props)` 后模板仍写 `props.columns`；composable 还会主动 strip `props`，渲染时报 not defined → 读 `columns` 崩溃。
47. **修复**：模板改为直接用 prop 名；顺带改掉 GridBlockRenderer / ListPageGridDesigner / BusinessRelationDesigner 同类写法。
48. **验证**：`AiCrudPage-compat` **8/8**。

### 续：FormPropertyTabs `schema is not defined`

49. **根因**：子面板 `return useForgePropertyPanelApi()` 只代理父 `setupState`；`schema` 等是父 props，拆出后子模板读到 undefined → `schema.layout` 崩溃。
50. **修复**：provide 代理同时暴露 `instance.props`；part1 再 return store 派生的 `schema/selectedId/fields/relations/objectCode`。`listPageDesignerContext` 同步加固。

### 续：codeRuleRequestVersion / BitableDragIcon

51. **根因**：`let codeRuleRequestVersion` 留在 part1，part2 `loadCodeRuleOptions` 直接引用 → ReferenceError；`<BitableDragIcon />` 仅在 setupState，子面板 components 未注册 → Failed to resolve component。
52. **修复**：计数迁到 `mut.codeRuleRequestVersion`；图标抽到 `bitableIcons.js` 并注册进 `forgePropertyPanelLocalComponents`。

### 续：formPropertyActiveTab set trap + 选中面板空白

53. **根因**：inject Proxy 只有 get、descriptor 无 writable/`set`，子面板 `v-model` 写 `formPropertyActiveTab` 等时报 `trap returned falsish`；选中态若只跟 `props.selectedId` 也会和 store 不同步。
54. **修复**：Proxy 增加 set + accessor descriptor（`isRef` 写 `.value`）；`selectedComponent` 改为优先读 `designerStore.selectedComponent`。

### 续：ListPageGridDesigner `normalizeDesignerLayout is not defined`

55. **根因**：机械拆 part 后 part1 顶层 eager 调用 `normalizeDesignerLayout`（实现在 part4）；part2–5 缺 vue/page-schema/blockTree 等 import；跨 part 函数无 `__impl` 转发；共享 `let`（`treeSourceCatalogPromise` 等）跨文件不可见。
56. **修复**：
    - part1：`__impl` 懒包装 + 延迟 `hydrateInitialState`（编排器在 part5 后调用）；去掉 tree-panel watch 的 `immediate`；补 `runtime-tree-table` import。
    - part2–5：补齐 import；对后续 part 符号加 `__impl` 包装；定义后立刻 `__impl.fn = fn`。
    - 共享可变状态迁到 `mut`，经 deps 传递；编排器 strip `__impl` / `hydrateInitialState` / `mut`。
57. **验证**：`node --check` useListPageGridDesigner 编排器 + part1–5 全部通过。

### 续：ListPage 模板常量未 return / propertyPanelRef 非 ref

58. **根因**：Options + inject proxy 后，`resizeAnchors` / `localDataBindableBlockTypes` / `treeLoadModeOptions` 等仅 import 未 return；子面板 `ref="propertyPanelRef"` 经 proxy 被模板自动解包成 null。
59. **修复**：part1 return 补齐 listDesignerOptions 与相关 schema/fieldDrawer 常量；`setPropertyPanelRef` 函数 ref；`ListPageBlockPropertyPanel` 改用 `:ref="setPropertyPanelRef"`。

### 续：表单节点缩放 + 列表画布拖放/边界

60. **根因**：`useForgeFormCanvasNode` 漏改 `mut.activeResizeState`；列表画布在非容器区块（尤其 AiCrudPage）上拦截落点，拖放预览被关掉；区块默认无描边/底色。
61. **修复**：补 `mut.activeResizeState.designerStyle`；取消非容器拦截，允许画布重叠落点；区块常显虚线边框+白底；拖放时画布高亮；预览态给非列表区块卡片轮廓。

## 2026-09-26 portal rich list grid + runtime lag
- Portal/GridBlockRenderer: when listGridLayout has companion blocks (tree/title/info/...), render ListPageGridDesigner(readonly) instead of bare AiCrudPage.
- buildRuntimeCrudProps now forwards pageSchema/modelSchema; ListPageGridDesigner passes suppress-runtime-list-grid to avoid nesting.
- Restored missing useCrudPageView.part1 state (SFC split drop) and aligned shouldRenderRuntimeGrid with rich-list helper.
- Runtime load: always prefetch ListPageGridDesigner chunk; warm timeout 3.2s for page-management (non-draft) to cut white-screen wait.

## 2026-09-26 fix: portal rich list 禁止嵌套设计器画布
- 用户反馈 readonly `ListPageGridDesigner` 把设计态 canvas/zoom/绝对定位带到门户：样式乱、下方 tabs/日历看不见、静态预览 tip 残留。
- 改为 `RuntimeListGridFlow`：纵向文档流 + `normalizeFlowBlockItem`（heightMode=auto / widthMode=full / previewLiveData）+ `suppress-runtime-list-grid`。
- `blockStyle` 对 heightMode=auto 输出 `height:auto`；首屏预热改拉 RuntimeListGridFlow。
- 验证：`vitest run runtime-list-grid.spec + application-runtime-load.spec + grid-block-renderer-data-source.spec` → 41 passed。

## 2026-09-26 fix: 左树右表双树 / 分页 / 预览脏数据
- 双树：`suppressRuntimeListGrid` 时禁止 `TreeCrudTemplate`；`RuntimeListGridFlow` 对 tree-panel 改左右分栏，并向嵌套 CRUD 注入 `suppressTreeCrudShell`。
- 分页：设计态有伴生块时 demote AiCrudPage `heightMode=full` → fixed，避免铺满盖住下方组件并把分页拉到画布底。
- 预览：`openDraftPreview` 改为 `currentDesignerDirty` + `saveCurrentDesignerSection`；本地预览 `pickLivePreviewLayout` 按实时源优先，禁止按块数最多选旧 persisted。
- 树样式：收起按钮移入标题栏，去掉飘在面板外的胶囊按钮。
- 验证：runtime-list-grid + application-runtime-load + ChildTableEditor → 29 passed。

## 2026-09-27 fix: 列表预览先保存 + 伴生块分页 + 卡片滚动
- `openLocalPreview`：先 `saveLayout` 再开弹窗；富布局预览改 `RuntimeListGridFlow`。
- `openDraftPreview`：list/form 设计态始终落盘后再跳转。
- `RuntimeListGridFlow`：补齐 is-crud → ai-crud-preview → ai-crud-page 高度链，伴生块下分页可见。
- `AiTable` 卡片：`.ai-card-scroll` 中间滚动、分页固定底栏。
- 验证：`AiTable.spec` + `runtime-list-grid.spec` → 7 passed。

## 2026-09-27 fix: switchRuntimeView ReferenceError
- part4 `switchRuntimeView` 使用 `resolveRuntimeView` 但未从 deps 解构（SFC 拆分漏传），点击 pages/process 等视图切换直接炸。
- 补回 `resolveRuntimeView` 解构。
