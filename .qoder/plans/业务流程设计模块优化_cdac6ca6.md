
# 业务流程设计模块优化方案

## 问题根因分析

### FLOW_MODEL_UNDECLARED
`useBusinessProcessDesigner.js` 的 `exportSchema()` (L252-L254) 调用 `cloneBusinessProcessSchema` 只做 normalize 不重新计算 dependencies。保存时 schema 中 `dependencies.flowModels` 未同步节点新引用的 flowModelKey，后端 `validateApproval` (L671-L672) 检查 `requireDeclared` 失败。

### FLOW_MODEL_UNAVAILABLE
`resolveDesignableFlowModels()` (L129-L131) 调用 `loadFlowModelCatalog(false)` 返回全部模型（含草稿），但校验时 `resolveFlowModelCatalog()` (L206-L208) 调用 `loadFlowModelCatalog(true)` 只认已发布模型。用户在 dropdown 选了草稿模型，保存/发布时报错。

### APPROVAL_FLOW_STATUS_REQUIRED
用户添加 APPROVAL 节点后，需手动点击"一键添加流程状态字段"按钮调用 `ensureBusinessFlowStatusField` API。如果用户不知道需要这一步，保存/发布时 formMode=BUSINESS_OBJECT_FORM 但 statusField 未绑定。

---

## Task 1: 修复 FLOW_MODEL_UNDECLARED — exportSchema 自动同步 dependencies

**文件**: `forge-admin-ui/src/components/business-process-designer/useBusinessProcessDesigner.js`

**变更**: `exportSchema()` (L252-L254) 从 `cloneBusinessProcessSchema` 改为先同步 dependencies 再 clone：

```js
function exportSchema() {
  return synchronizeBusinessProcessDependencies(cloneBusinessProcessSchema(schema.value))
}
```

`synchronizeBusinessProcessDependencies` 已存在于 `business-process-schema.js` (L147)，会遍历所有节点重新计算 dependencies.flowModels/formAssets/businessActions 等，确保保存时依赖列表与节点引用一致。

---

## Task 2: 修复 FLOW_MODEL_UNAVAILABLE — 下拉只显示已发布模型

### 2a. 前端过滤

**文件**: `forge-admin-ui/src/components/business-process-designer/ActionAndApprovalNodeConfig.vue`

**变更**: `designableFlowModels` computed (L39-L42) 增加 `deployed` 过滤条件，只展示已发布/已部署的模型：

```js
const designableFlowModels = computed(() => (props.flowModels || []).filter((item) => {
  const designerType = String(item.designerType || '').toLowerCase()
  const deployed = item.deployed === true || Boolean(item.deploymentId)
  return designerType !== 'business' && Boolean(modelKey(item)) && deployed
}))
```

同时更新"新建并设计"按钮旁的提示文案，引导用户先发布模型再选择。

### 2b. 后端保持一致

后端 `resolveDesignableFlowModels()` 保持不变（仍返回全部用于编辑场景），但 `resolveAvailableFlowModels()` 可考虑也过滤到已发布。此处修改可选，因为前端已过滤。

---

## Task 3: 消除 APPROVAL_FLOW_STATUS_REQUIRED — flowStatus 自动生成

### 3a. 前端自动触发 ensureFlowStatusField

**文件**: `forge-admin-ui/src/components/business-process-designer/ActionAndApprovalNodeConfig.vue`

**变更**: 在 `ensureDefaultApprovalBindings()` (L261-L289) 中，当检测到 `!hasIndependentFlowStatus` 且节点 formMode 为 BUSINESS_OBJECT_FORM 时，自动调用 `ensureFlowStatusField()`：

```js
async function ensureDefaultApprovalBindings() {
  if (props.node?.type !== 'APPROVAL') return
  const patch = {}
  // ... 现有 formAsset 逻辑 ...

  // 自动触发 flowStatus 字段创建（无需用户手动点击）
  if (!hasIndependentFlowStatus.value && props.objectId) {
    await ensureFlowStatusField()
    // ensureFlowStatusField 会触发 refreshFields，进而重新触发本函数
    return
  }
  if (!usesIndependentFlowStatus.value && suggestedStatusField.value) {
    patch.statusField = suggestedStatusField.value
  }
  // ... 现有 titleTemplate 逻辑 ...
}
```

同步修改 `ensureFlowStatusField()` (L305-L327)，在成功创建字段后自动 `patchConfig({ statusField: 'flowStatus' })` 并 emit refreshFields，让后续 ensureDefaultApprovalBindings 自动绑定。

### 3b. 后端兜底 — 保存时自动补齐

**文件**: `forge-server/.../service/businessprocess/BusinessProcessService.java`

在 `saveSchema` 方法中，保存前检查所有 APPROVAL 节点：如果 formMode=BUSINESS_OBJECT_FORM 但 statusField 缺失，自动调用 `ensureBusinessFlowStatusField` 逻辑并回填 statusField 到 schema。

**新增文件**: 在 BusinessProcessService 中新增私有方法 `autoEnsureFlowStatusFields(schema, objectId)` 遍历 APPROVAL 节点并补齐。

### 3c. 前端 schema 校验放宽

**文件**: `forge-admin-ui/src/components/business-process-designer/business-process-schema.js`

`validateBusinessProcessGraph` 中 APPROVAL_FLOW_STATUS_REQUIRED 校验 (L222-L232) 改为 warning 级别（`severity: 'warning'`），不再阻塞保存，只作为提示。后端校验保持不变（由后端兜底自动补齐）。

---

## Task 4: UI 视觉重新设计

参考主流低代码平台（钉钉宜搭、飞书审批、Retool Workflow）重新设计，遵循 DESIGN.md 设计原则。

### 4a. 节点面板 (node-palette) 重设计

**文件**: `BusinessProcessDesigner.vue` 样式区域 (L738-L838)

- 节点卡片改为带图标的横向卡片，左侧圆形色块图标（替代竖条），右侧标题+描述
- 增加 hover 微动效（translateX 2px + border 高亮）
- 增加节点类型对应的 SVG 图标（条件菱形、动作闪电、审批盾牌、子流程嵌套方块）
- 底部 boundary 提示改为带 info 图标的柔和提示条

### 4b. 画布区域 (canvas-pane) 升级

**文件**: `BusinessProcessCanvas.vue` 样式 (L177-L185)

- 画布背景改为点阵网格背景（radial-gradient dot pattern），提升低代码画布专业感
- 拖拽指示器增强（蓝色虚线边框 + 半透明蓝色填充）

**文件**: `BusinessProcessNodeRenderer.vue` 样式

- 节点卡片增加轻微阴影（`box-shadow: 0 1px 3px rgba(0,0,0,0.08)`）
- 选中态改为蓝色外发光（`box-shadow: 0 0 0 2px var(--primary-color)`）
- 节点类型色条从左侧竖条改为顶部色带
- 开始/结束节点改为圆角胶囊形

### 4c. 问题面板 (issue-pane) 重设计

**文件**: `BusinessProcessDesigner.vue` 样式区域 (L844-L975)

- 问题项从红色边框改为左侧 4px 色条指示严重级别（error=红、warning=橙）
- 空状态增加 check 图标 + 绿色调"流程结构完整"提示
- 折叠态图标改为竖排文字 + 角标数字气泡

### 4d. 工具栏 (toolbar) 优化

**文件**: `BusinessProcessDesigner.vue` 样式区域 (L654-L728)

- 按钮组分组（撤销/重做 | 复制/删除 | 检查/保存），组间加分隔线
- 保存状态指示器增加圆点动画（saving 时 pulse 动画）

### 4e. 主页面 header 和整体布局

**文件**: `business-process.[processId].vue` 样式区域 (L715-L851)

- header 改为更紧凑的 2 行布局：第一行返回按钮+标题+发布按钮，第二行显示对象编码和流程编码
- 主内容区增加 8px 内边距和微妙阴影

### 4f. 审批节点配置面板重设计

**文件**: `ActionAndApprovalNodeConfig.vue` 样式区域 (L709-L1028)

- 配置区改为分组卡片布局（"审批流程"一组、"表单配置"一组、"状态回写"一组）
- 流程预览卡片增加渐入动画
- flow-status-provision 黄色提示块改为带步骤指引的操作卡片（改为自动触发后此块消失）

---

## Task 5: 审批流程模型查询交互优化

### 5a. 下拉选项增强

**文件**: `ActionAndApprovalNodeConfig.vue`

- `flowModelOptions` computed (L44-L48) 增加版本号显示：`{label} v{version}`
- 增加 NSelect 的 `render-option` slot，为每个选项增加模型 Key 和版本号副标题

### 5b. 空状态引导

当 `flowModelOptions` 为空时，在审批模型选择区域下方显示引导提示：

```
暂无已发布的审批模型。请先点击「新建并设计」创建审批模型，在流程设计器中完成配置并部署后，即可在此处选择引用。
```

### 5c. 新建并设计后自动刷新

`createAndDesign()` (L171-L230) 创建完成后自动调用 `emit('refreshFlowModel')`（已有），但需确保新模型部署后刷新时能出现在列表中（Task 2 的已发布过滤要求用户先部署新模型）。

在创建成功回调中增加提示：`审批模型已创建，请在流程设计器中完成配置并部署后，即可在列表中选择。`

---

## Task 6: 后端 flowStatus 自动补齐（保存时兜底）

**文件**: `forge-server/.../service/businessprocess/BusinessProcessService.java`

在 `saveSchema` 方法中，`validate` 之前新增步骤：

1. 遍历 schema 中所有 APPROVAL 节点
2. 对 formMode=BUSINESS_OBJECT_FORM 且 statusField 为空的节点：
   a. 调用 `ensureBusinessFlowStatusField(objectId)` 确保字段存在
   b. 将 `statusField: "flowStatus"` 写回 schema node config
3. 同步更新 dependencies（后端也做一次 ensureDependenciesSync）

**注意**: 需要注入对应的 Service（如 `BusinessObjectFieldService` 或等效）来执行字段创建。

---

## 实施顺序

| 阶段 | 内容 | 风险 |
|------|------|------|
| Phase 1 | Task 1 (exportSchema sync) | 低 - 纯前端改动，影响范围小 |
| Phase 2 | Task 3a (前端自动 ensureFlowStatus) | 中 - 异步调用需要处理竞态 |
| Phase 3 | Task 2 (已发布模型过滤) | 低 - 纯过滤逻辑 |
| Phase 4 | Task 5 (下拉增强+引导) | 低 - 纯 UI 增强 |
| Phase 5 | Task 6 (后端兜底) | 中 - 需要后端 Service 调用 |
| Phase 6 | Task 4 (UI 重设计) | 低 - 纯样式变更 |
| Phase 7 | E2E 验证 | 验证全流程 |

## 验证方案

1. 新建业务流程 → 添加 APPROVAL 节点 → 保存：不应出现 FLOW_MODEL_UNDECLARED
2. 新建审批模型 → 部署 → 在 APPROVAL 节点下拉选择 → 保存：不应出现 FLOW_MODEL_UNAVAILABLE
3. 添加 APPROVAL 节点（formMode=BUSINESS_OBJECT_FORM）→ 保存：flowStatus 字段应自动创建，不应出现 APPROVAL_FLOW_STATUS_REQUIRED
4. UI 截图对比：节点面板、画布、问题面板、审批配置面板视觉一致性
