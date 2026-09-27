# admin-ui 巨型文件拆分（硬性 < 2000 行）

## 目标

`forge-admin-ui` 内任意源码文件（`.vue` / `.js` / `.css`）**不得超过 2000 行**。  
本变更只做结构拆分：**不改业务协议、不改对外 props/emit、不改运行时行为**。

## 硬性约束（对齐 AGENTS.md）

1. 单文件 > 2000 行禁止提交；拆完验收 `wc -l` 全部 < 2000。
2. 跨面板状态走 **Pinia**（已有 `listDesignerStore`；表单侧补 `formDesignerStore`），禁止加深 props/emit 链。
3. Vue 3 **不用 Options mixin**；逻辑抽 **composables**（`useXxx.js`），纯函数抽 `utils` / `*-options.js`。
4. 样式：SFC 内超长 `<style>` 外提到同目录 `.css`，或按面板拆到子组件 scoped style。
5. 每次只拆一个边界，保持可编译；优先复用已有测试，必要时补最小回归。
6. 禁止借拆分改接口协议或「顺手重构业务」。

## 拆分手法（统一套路）

| 类型 | 做法 |
|------|------|
| 常量/选项 | `xxxOptions.js` / `xxxConstants.js` |
| 纯函数 | `xxxUtils.js`，配套 `__tests__` |
| 交互状态逻辑 | `composables/useXxx.js` 或扩 Pinia store |
| 模板分区 | 同目录子组件（palette / canvas / property panels） |
| 属性面板 collapse | 一区一个 `panels/XxxPanel.vue` |
| 样式 | `Foo.css` 或随子组件带走 |

## 波次与优先级

### Wave 0 — 低风险机械外提（先减行数）

| 文件 | 行数 | 动作 |
|------|-----:|------|
| `ListPageGridDesigner.vue` style | ~3433 | → `ListPageGridDesigner.css` |
| `ForgePropertyPanel.vue` style | ~2839 | → `ForgePropertyPanel.css` |
| `application-runtime.css` | 2776 | 按区块切 `runtime-shell.css` / `runtime-canvas.css` / … |
| `page-schema.js` | 2932 | 按 schema 域切模块再 barrel 导出 |

### Wave 1 — P0 设计器（最大两个）

| 文件 | 行数 | 目标结构 |
|------|-----:|----------|
| `ListPageGridDesigner.vue` | 13645 | 壳 <800：`ListPagePalette` + `ListPageCanvas` + `ListPageBlockPropertyPanel` + drawers/modals；属性面板再按 props/style/interaction 与 blockType 拆 |
| `ForgePropertyPanel.vue` | 11458 | 已有 `panels/`：把 collapse 区迁入独立 Panel；脚本迁 composable/`formDesignerStore` |

### Wave 2 — P1 运行时 / CRUD / 栅格渲染

| 文件 | 行数 | 建议边界 |
|------|-----:|----------|
| `application-runtime.[applicationCode].vue` | 7408 | 已有 `runtime-modules/`：继续抽 load/edit/preview composable + 子视图 |
| `AiCrudPage.vue` | 5803 | 搜索区 / 表格区 / 弹窗 / hooks → 子组件 + composables |
| `GridBlockRenderer.vue` | 5670 | 按 blockType 拆 renderer；复用 `runtime-tree-table` / crud props |
| `dataset.vue` | 6720 | 列表/编辑/预览分面板 |

### Wave 3 — 流程 & AI

`flow/design.vue`(5398)、`ai/lowcode-apps.vue`(4006)、`ai/agent.vue`(3845)、`bpmn/NodePropertiesPanel.vue`(3306)、`AiFormItem.vue`(3226)、其余 flow/ai 页。

### Wave 4 — 应用中心设计器 & 系统页

`BusinessRelationDesigner`、`BusinessFieldPropertyPanel`、`BusinessListDesigner`、`login/index`、`menu`、`BusinessFormDesigner`、`ForgeFormCanvasNode`、`ExtensionEditorDrawer`、`role`、`StructuredListPageDesigner`、`flow/model`、`object-designer`、`ForgeFormDesigner`、`BusinessActionDesigner`、知识库/CRUD 生成器等（全部压到 <2000）。

## 验收

```bash
cd forge-admin-ui
find src -type f \( -name '*.vue' -o -name '*.js' -o -name '*.css' \) \
  ! -path '*/node_modules/*' ! -path '*/dist/*' \
  | while read f; do n=$(wc -l < "$f"); [ "$n" -ge 2000 ] && echo "$n $f"; done
# 期望：无输出
pnpm -C forge-admin-ui test --run  # 相关单测
pnpm -C forge-admin-ui build       # 阶段收尾
```

## 非目标

- 后端 Java 巨型类（另立变更；见既有 `giant-class-audit.md`）
- 视觉/交互改版、协议字段增减
