# 统一 UI Document 渲染协议

> 变更名：`task-form-document-protocol`  
> 状态：`in-progress`  
> 创建日期：2026-09-24

## 1. 问题与目标

### 问题

设计态（`formDesignerSchema`）、审批 PC、低代码运行页、H5 各自组装 schema，布局与权限协议不一致，导致「设计出来的页面」和「渲染出来的页面」容易漂移，H5 适配成本高。

### 目标

1. **一份轻量运行契约 `uiDocument`**：PC / H5 / 审批 / 业务页共用；端差异只在 Renderer。
2. **设计态与运行态分层**：设计器继续编辑完整 `formDesignerSchema`；运行前 **编译** 为 `uiDocument`（所见即所得）。
3. **加量兼容**：旧 `fields` / `editSchema` / `editFormLayout` / `fieldPermissions` 暂不删除。
4. **轻量、高适配、可扩展**：必填形状极小；扩展走可选 `props` / `span` / `extensions`。

### 非目标

- 不重写流程设计器节点配置 UI。
- 本轮不改 H5 渲染代码（协议先统一，H5 下一阶段消费）。
- 不改 Flyway / 业务表白结构。

## 2. 协议分层

```
formDesignerSchema（设计态，重）
        │ compile（FE / BE 同构规则）
        ▼
uiDocument v1（运行态契约，轻）
        │ resolve + fields overlay
        ▼
AiForm schema / 未来 H5 Renderer
```

| 层 | 职责 |
|----|------|
| 设计态 | 完整组件树、校验、治理、多表单 |
| `uiDocument` | 可渲染树：`sections` + `components` + 轻量权限标记 |
| `fields` / `recordData` | 字段元数据、值、可写范围 overlay |

### `uiDocument` v1

```json
{
  "version": "1",
  "uiType": "business-object | lowcode-form | task-form",
  "formKey": "order_form",
  "sections": [{ "sectionId", "title", "fields", "..." }],
  "components": [{ "type", "field?", "label", "editable", "visible", "span?", "children?", "props?" }],
  "actions": [],
  "extensions": {}
}
```

扩展原则：新能力只加可选字段；渲染端不认识即可忽略。

## 3. 分阶段

### Phase 1 — 后端审批协议加量 ✅

- `TaskFormUiDocumentCompiler`；VO 加 `protocolVersion` / `uiDocument`。

### Phase PC-A / PC-B — 审批 PC 消费 ✅

- `resolveAiFormSchemaFromUiDocument`：sections 序 / components 树；`fields` 仍是 SoT。

### Phase Unify — 设计/运行协议统一（本轮）

- FE 共享模块 `@/protocols/ui-document`（`compile` + `resolve`，对齐后端编译器）。
- 低代码运行：`buildRuntimeCrudProps` / `crud-page` / 预览 / `hydrateRuntimeFormLayout` 统一走 uiDocument。
- 审批页继续用后端下发的 uiDocument；兼容 re-export 不变。
- **仍不改 H5 代码**；运行 props 已挂 `uiDocument` 供后续 H5 直接消费。

### Phase H5 — 延后

- 审批 H5 + 业务页 H5 共读 `uiDocument`。

### Phase 4 — 收敛

- 文档化；评估废弃纯拍扁 / 双 layout 路径。

## 4. 验收

### 已完成

- [x] 审批 `task-form-context` 返回 `protocolVersion=1` + `uiDocument`
- [x] 审批 PC 消费 uiDocument（回退安全）
- [x] FE `compileUiDocument` 与后端形状对齐（含 span 等轻量扩展）
- [x] 低代码运行 / 预览 / 应用页 hydrate 优先 uiDocument
- [x] 无设计器 schema 时行为与改造前一致
- [x] 不改动 H5 代码

### 待业务验证

- [ ] 审批暂存 / 同意驳回
- [ ] 低代码设计含 card/row 布局的表单：预览与运行页布局一致
