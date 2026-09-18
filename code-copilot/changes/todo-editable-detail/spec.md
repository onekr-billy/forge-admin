# 待办中的可编辑明细 Spec

> 变更名：`todo-editable-detail`  
> 优先级：P1（平台能力，不阻断当前 KPI 平铺槽位方案）  
> 状态：`implemented-with-validation-limitations`
> 实施范围：第一期路线 A——低代码业务对象主子表在待办中的受控编辑  
> 排除范围：路线 B 动态表单数组字段，本期只保留兼容设计，不进入实现

## 1. 背景与问题

Forge 已支持低代码业务对象主子表进入 Flowable 流程，但待办上下文中的 `ChildTableEditor` 被固定为只读，且待办保存接口只更新主表字段。节点字段权限目录也只覆盖主表字段，因此审批人无法在待办中修正采购数量、报销明细或考评行数据，只能驳回后重新提交。

现有普通 CRUD 已具备主子表新增、修改、删除、`_deleted` 标记和必填校验能力。本变更把这些能力接入流程节点权限和待办保存链路，同时保持普通 CRUD、老流程和已办/历史场景行为不变。

## 2. 目标

1. 流程设计器的业务对象字段权限目录同时展示主表字段和子表字段。
2. 节点可分别控制子表可见、子表字段可编辑、子表新增、修改、删除。
3. 当前已签收待办可在明细行中编辑允许字段，并按权限新增或删除行。
4. 明细修改、增加、删除结果在审批动作前安全保存到业务对象，下一节点能读取最新数据。
5. 服务端重新解析当前节点权限并校验主子表行归属，前端篡改权限或跨单据行 ID 不能越权。
6. 老流程没有子表权限配置时继续只读；已办、历史任务继续完整展示但不可编辑。

## 3. 非目标

1. 本期不实现动态表单 `AiForm` 的数组/明细字段类型，不改 `formCreateToAiSchema` 的数组协议。
2. 本期不把动态表单数组自动回写低代码业务对象；该能力另立路线 B 变更。
3. 本期不修改普通业务对象 CRUD 的默认主子表保存语义。
4. 本期不改变 Flowable 节点权限的存储位置，仍以真实流程设计器写入的 BPMN 节点配置为准。

## 4. 设计

### 4.1 权限协议

节点权限配置支持旧数组格式和新增 v2 对象格式。旧数组只包含主表字段时，按旧逻辑解析；v2 使用 `fields` 和 `children` 分离字段权限和子表行操作权限：

```json
{
  "version": 2,
  "fields": [
    {
      "scope": "main",
      "field": "amount",
      "readable": true,
      "writable": true,
      "required": false
    },
    {
      "scope": "child",
      "childKey": "items",
      "childField": "quantity",
      "label": "数量",
      "readable": true,
      "writable": true,
      "required": true
    }
  ],
  "children": [
    {
      "childKey": "items",
      "readable": true,
      "allowCreate": false,
      "allowUpdate": true,
      "allowDelete": false
    }
  ]
}
```

兼容和默认规则：

- 旧格式数组继续表示主表字段权限。
- 子表没有显式权限时默认 `readable=true、allowCreate=false、allowUpdate=false、allowDelete=false`，字段全部只读。
- 子表字段只有 `writable=true` 且子表 `allowUpdate=true` 时已有行可编辑。
- `readable=false` 的字段不下发到待办上下文；保存时服务端从数据库保留原值。
- 新增行的主表外键、租户字段、审计字段由服务端补齐，客户端不能指定。

### 4.2 待办保存协议

`BusinessTaskFormSaveDTO` 保留固定任务身份字段，`data` 仍作为低代码动态记录体，但支持以下结构；旧的扁平 `data` 继续视为主表字段：

```json
{
  "data": {
    "main": {
      "amount": 1000
    },
    "children": {
      "items": [
        {"id": 101, "quantity": 5},
        {"quantity": 2},
        {"id": 102, "_deleted": true}
      ]
    }
  }
}
```

后端保存采用任务专用 merge 语义：

- 有 `id` 的行必须属于当前主记录，否则拒绝。
- 无 `id` 的行仅在 `allowCreate=true` 时允许新增。
- `_deleted=true` 仅在 `allowDelete=true` 时允许删除。
- 修改已有行前读取数据库原行，只替换当前节点允许写入的字段，禁止用部分 payload 覆盖只读字段。
- 不直接按普通 CRUD 的 replace payload 删除未提交明细。
- 主表和子表在同一事务内保存，并返回最新任务表单上下文。

### 4.3 前端运行时

`todo.vue` 去掉 `ChildTableEditor` 的固定 `readonly`，改由后端返回的 `childrenConfig` 权限投影决定：

- 历史/已办上下文强制 readonly。
- 当前待办只启用 `writable=true` 且 `allowUpdate=true` 的输入控件。
- 新增、删除按钮分别由 `allowCreate`、`allowDelete` 控制。
- `businessFormHasWritableFields` 同时计算主表字段、子表字段和子表行操作权限。
- 审批前暂存和同意、驳回、驳回至发起人、退回动作共用同一份主子表 payload。

### 4.4 服务端权限边界

`BusinessFlowService.saveTaskFormContext` 必须从当前任务和 BPMN 节点重新获取权限，不能信任前端字段权限。权限过滤和主子表写入由 `DynamicCrudService` 的任务专用方法完成。保存过程必须继续经过租户、数据权限、运行配置状态和当前任务签收校验。

### 4.5 流程配置来源

权限配置继续由真实流程设计器的 `FormPermissionConfig.vue` 写入 `formFieldPermissions`，不新增 App Center 私有配置入口。BPMN 节点已有配置优先，业务绑定 `nodeForms` 只作为兼容回退。

## 5. 影响范围

### 前端

- `forge-admin-ui/src/views/flow/todo.vue`
- `forge-admin-ui/src/components/page-templates/ChildTableEditor.vue`
- `forge-admin-ui/src/components/flow-designer/panel/FormPermissionConfig.vue`
- `forge-admin-ui/src/components/flow-designer/panel/ApproverConfig.vue`
- `forge-admin-ui/src/components/flow-designer/converter/user-task-parser.js`
- `forge-admin-ui/src/components/flow-designer/converter/user-task-writer.js`
- 业务表单字段目录和相关测试

### 后端

- `BusinessFlowService.java`
- `BusinessTaskFormSaveDTO.java`（仅在需要增加显式主子表字段时调整，保留动态记录体兼容）
- `DynamicCrudService.java`
- 动态 CRUD 子表 Mapper/Repository 复用或新增任务保存辅助方法
- 对应服务测试和契约测试

### 数据库

本期不新增表或字段。节点权限使用已有 BPMN 扩展属性；如后续需要审计明细变更，再独立增加迁移。

## 6. 安全与并发

- 服务端拒绝未授权字段、未授权新增/删除和跨主记录行 ID。
- 不向前端暴露不可读子表字段；保存时从数据库合并原值。
- 任务必须是当前用户已签收且仍处于可办理状态。
- 保存结果记录任务身份和业务记录关联，禁止通过替换 `businessKey` 绕过校验。
- 第一期沿用当前任务签收校验、租户/数据权限和事务边界；更新时间/版本快照字段尚未纳入保存协议，乐观并发拒绝留待后续增量。
- 普通 CRUD、业务代码表单和历史只读路径不改变。

## 7. 验收标准

1. 老流程没有子表权限：待办明细仍只读，普通 CRUD 不受影响。
2. 节点只允许编辑一个子表字段：该字段可输入，其他字段仍只读。
3. `allowCreate/allowDelete` 关闭时，界面无对应操作且后端拒绝伪造请求。
4. 子表修改、新增、删除后，暂存接口返回的上下文包含最新主子表数据。
5. 同意、驳回、驳回至发起人、退回前的保存结果均可被业务对象和下一节点读取。
6. 跨单据行 ID、不可读字段、不可写字段和未签收任务请求均失败。
7. replace 配置下只提交部分字段不会丢失只读字段或未提交明细。
8. 已办、历史任务展示完整明细且不能编辑。

## 8. 发布和回滚

- 本期无需数据库迁移，发布顺序为后端权限解析/保存能力、前端权限目录和待办编辑、最后开启节点配置入口。
- 未配置 v2 权限的流程按旧只读子表规则运行，因此可直接回滚前端开关而不破坏存量流程。
- 若发现主子表保存异常，关闭子表可编辑配置即可恢复只读待办；主表保存和普通 CRUD 不受影响。

## 9. 开放问题

1. 是否需要在审批日志中展示每一行明细的字段变更前后值；本期先保留现有操作日志能力，不阻断主功能。
2. 是否需要“保存并办理”原子接口；本期沿用保存后执行 `task-action`，后续按实际并发和失败反馈决定是否增加。
3. 动态表单数组字段和业务对象回写另立路线 B Spec。
