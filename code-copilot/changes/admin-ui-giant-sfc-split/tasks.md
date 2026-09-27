# tasks — admin-ui 巨型文件拆分

## Wave 0

- [ ] T0.1 `ListPageGridDesigner.vue` style → `ListPageGridDesigner.css`
- [ ] T0.2 `ForgePropertyPanel.vue` style → `ForgePropertyPanel.css`
- [ ] T0.3 `application-runtime.css` 切分至各 <2000
- [ ] T0.4 `page-schema.js` 按域切分 + 保持对外导出

## Wave 1 — ListPageGridDesigner

- [ ] T1.1 抽出 `listDesignerOptions.js`（选项常量）
- [ ] T1.2 抽出 `ListPagePalettePanel.vue`
- [ ] T1.3 抽出 `ListPageCanvasPanel.vue`（工具栏 + 画布 + 缩放）
- [ ] T1.4 抽出 `ListPageBlockPropertyPanel.vue`（右侧属性整栏）
- [ ] T1.5 属性栏再拆：`props` / `style` / `interaction` 及 AiCrud/Tree/Search 子面板
- [ ] T1.6 抽出 drawers/modals 子组件
- [ ] T1.7 composables：`useListCanvasDrag`、`useListBlockPatch`（按需）
- [ ] T1.8 验收：主文件 + 子文件均 <2000，相关单测通过

## Wave 1b — ForgePropertyPanel

- [ ] T1b.1 collapse 区迁入 `panels/*Panel.vue`（字段/按钮/位置/排版/外观/权限…）
- [ ] T1b.2 脚本迁 composables / 扩 formDesignerStore
- [ ] T1b.3 验收 <2000

## Wave 2–4

- [ ] 按 spec 表逐文件拆分并验收（每完成一个文件勾选并记入 execution-log）

## 当前进度

- [x] T0.1 `ListPageGridDesigner` 样式外提并切分 <2000
- [x] T0.2 `ForgePropertyPanel` 样式外提并切分 <2000
- [x] T1.1 `listDesignerOptions.js`
- [x] T1.4–T1.5 属性栏拆为 Tree/Props×3/Style/Interaction + inject 上下文
- [x] T1.7 script 拆为 `composables/useListPageGridDesigner.part1–5.js`；主 SFC **939 行**
- [x] T1b ForgePropertyPanel：主 SFC **247 行**；panels + composables part1–3 均 <2000
- [x] T0.4 `page-schema.js` → barrel + fields/catalog/layout/utils；单测 13/13
- [x] Wave2 `GridBlockRenderer`：主 SFC **1530**；`grid-block-renderer.css` 1934；utils 420；`useGridBlockRenderer.js` 1910；localComponents；data-source 单测 **18/18**
- [x] T0.3 `application-runtime.css` → shell + canvas
- [x] Wave2 `AiCrudPage` 主 SFC **1028**；`useAiCrudPage.part1–3` 均 <2000；单测 14/14
- [x] Wave2 `application-runtime` 主 SFC **1346**；`useApplicationRuntime.part1–5` 均 <2000
- [x] Wave2 `dataset.vue` 主 SFC **1525**；shell/panels CSS + `useDatasetPage.part1–3`
- [x] Wave3 `flow/design.vue` 主 SFC **1003**；`useFlowDesign.part1–3` + `design.css`
- [x] Wave3 `ai/lowcode-apps.vue` **801**；`ai/agent.vue` **865**
- [x] Wave4 批量拆分其余 2000+（BusinessRelation/Field/List/Form/Action、NodeProperties、login/menu/role、ForgeForm*、ExtensionEditor、StructuredList、flow model/monitor/todo、object-designer、knowledge/crud-generator/provider、external/manage 等）
- [x] `AiFormItem.vue`：**3226 → 主 SFC 853**；`aiFormItem.css` + `aiFormItemUtils.js`（纯函数 export）+ 单文件 `useAiFormItem.js`（1845，含 setup return）；单测 **10/10**
- [x] 行数门禁：`src` 下 `*.vue|*.js|*.ts|*.css|*.scss` **0** 个 ≥2000
- [x] 源码契约：`readSplitVueSource` 支持 `runtime-modules/` / sibling panels / utils；相关合约单测 **34/34**
- [x] `pnpm build` 通过（并修复拆分引入的相对路径 / 多余 `</template>`）
- 可选收尾：`/archive admin-ui-giant-sfc-split`
