# 低代码选择类数据源：树映射、加载策略与回显
> status: completed
> created: 2026-09-23
> complexity: 🟡中

## 1. 背景与目标

表单里的 **树形选择**、**下拉** 等组件在选用动态数据源（业务对象 / 查询源 / 手写接口）时，当前只能配「值字段 / 显示字段」，缺少：

1. **树结构映射**：父级字段、节点标识、子节点字段 —— 用户无法表达「扁平表如何拼成树」；
2. **加载策略**：全量一次拉 vs 展开再加载下级；
3. **数据量上限**：防止组织树、字典大表一次拉爆页面；
4. **回显**：值存 id、标签走伴随字段（xxxName），加载中禁止闪 id（已有基础，需与树懒加载兼容）。

本变更为 **平台级** 能力：配置落在 `optionSource`，运行时由 `AiFormItem` + `option-source-runtime` 统一消费，不绑定具体业务表。

## 2. 用户可理解的配置模型

属性面板在「选项来源」下方，按业务语言分组（避免技术黑话堆砌）：

| 配置项 | 业务文案 | 含义 | 适用 |
|--------|----------|------|------|
| valueField | 选中后保存的值 | 写入表单主列 | 全部选择类 |
| labelField | 树上/下拉看到的文字 | 展示名 | 全部选择类 |
| parentField | 这条数据挂在谁下面 | 扁平表拼树用的父级列 | 仅树形 |
| childrenField | 接口里下级列表的字段名 | 已是嵌套树时用，默认 `children` | 仅树形，高级 |
| rootParentValue | 顶级节点的父级取值 | 空 / 0 / null 等，默认空 | 仅树形，高级 |
| loadMode | 数据怎么加载 | `full` 一次全部 / `lazy` 展开再加载 | 仅树形 |
| pageSize | 每次最多取多少条 | 限流，默认下拉 50、树全量 200 | 动态源 |

提示文案原则：

- 全量：适合节点不多（建议不超过配置上限）；
- 懒加载：适合组织树、大型分类；会把「父级字段 = 当前节点值」传给数据源，**数据源需支持按该字段过滤**（`/tree` 接口传 `loadMode=lazy&parentValue=`）。

## 3. optionSource 协议扩展

在现有字段上增量，不破坏旧配置：

```json
{
  "type": "QUERY_SOURCE",
  "sourceType": "BUSINESS_OBJECT",
  "sourceKey": "org",
  "valueField": "id",
  "labelField": "orgName",
  "parentField": "parentId",
  "childrenField": "children",
  "rootParentValue": "",
  "loadMode": "full",
  "pageSize": 200,
  "structure": "tree"
}
```

- `structure: "tree"`：显式声明按树渲染；树形组件选动态源时由设计器写入。
- `type` 仍可为 `QUERY_SOURCE` / `REMOTE` / `CURRENT_CHILDREN`；运行时用 `structure` / `parentField` / 组件类型判断是否建树，**不**把 `type` 强改成 `tree`（避免打断查询源执行分支）。
- 已有 `type: "tree"` + `/ai/crud/{key}/tree` 保持兼容。

## 4. 运行时行为

### 4.1 全量（full）

1. 按 `pageSize`（钳制上限 1000）请求；
2. 行映射为 `{ value, label, key, ... }`；
3. 若已有嵌套 `children` → 递归规范化；
4. 否则若配置了 `parentField` → **扁平转树**；
5. 否则作为一层根节点列表。

### 4.2 懒加载（lazy）

1. 首次只请求根层：`parentValue = rootParentValue`（或空），`loadMode=lazy`；
2. 节点默认 `isLeaf: false`（除非接口给出 `isLeaf` / `hasChildren=false`）；
3. `n-tree-select` 的 `on-load` 再请求该节点子层，写入 `option.children`；
4. 编辑回显：优先伴随字段 `labelValueField`；远程未就绪时用不显示占位符，禁止 id 闪现；若当前树层找不到选中节点，注入一条仅含 value+label 的临时节点保证展示。

### 4.3 下拉 / 单选等

- 暴露 `pageSize`（默认 50），请求时钳制；
- `buildQuerySourceDisplayFields` 在树场景额外带上 `parentField`，保证拼树字段被投影出来。

## 5. 非目标

- 不改造后端树表 `DynamicCrudService.selectTree` 核心算法（已支持 full/lazy）；
- 不做远程关键字搜索分页无限滚动（可后续）；
- 不改变静态 options 编辑体验。

## 6. 验收场景

1. 树形选择 + 业务对象（扁平 parentId）：配置父级字段后预览/运行呈树，选中保存 id + Name 伴随字段。
2. 同一数据源切「展开再加载」：首次仅根节点，展开后出下级。
3. 普通下拉 pageSize=10：接口只取 10 条。
4. 编辑打开：有伴随名称时不闪 id；无伴随时加载完成后显示正确 label。
