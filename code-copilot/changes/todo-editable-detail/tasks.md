# 待办中的可编辑明细 Tasks

> 变更：`todo-editable-detail`  
> 执行方式：按 SDD 任务顺序实施；每个 Task 完成后先运行对应增量测试，再更新执行日志。

## T1. 建立权限协议和字段目录测试基线

**目标**：在不改变旧流程行为的前提下，定义主表/子表权限 v2 的归一化结果。

**文件**：

- 修改：`forge-admin-ui/src/components/flow-designer/panel/FormPermissionConfig.vue`
- 修改：`forge-admin-ui/src/components/flow-designer/converter/user-task-parser.js`
- 修改：`forge-admin-ui/src/components/flow-designer/converter/user-task-writer.js`
- 测试：对应 `__tests__/FormPermissionConfig.spec.js`、权限 parser/writer 测试

- [x] 写旧数组、v2 对象、缺省子表权限和重复 child field 的解析/去重测试基线。
- [x] 实现旧数组兼容解析，统一输出 `fields`、`children` 和 `version`。
- [x] 扩展设计器字段目录为主表字段和子表字段分组，并提供新增/修改/删除操作配置。
- [x] 写回 BPMN 时保留旧字段权限，v2 权限以稳定的 `scope/childKey/childField` 保存。
- [x] 运行前端目标测试，确认旧权限 XML round-trip 不变。

## T2. 后端业务字段目录增加子表字段

**目标**：待办上下文和流程设计器都能得到稳定的子表字段目录。

**文件**：

- 修改：`forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessFlowService.java`
- 视需要修改：业务表单字段目录 VO/内部 record 类型
- 测试：`BusinessFlowService` 相关字段目录/上下文测试

- [x] 为每个 `masterDetailConfig.children[].fields` 生成 `scope=child`、`childKey`、`childField` 和展示标签。
- [x] 对同名字段使用 `childKey + childField` 作为唯一权限键，不把不同子表字段混为一项。
- [x] 无节点子表权限时返回只读兼容配置；不可读字段不进入运行态数据。
- [x] 历史/已办上下文在权限投影后递归强制只读。
- [ ] 增加后端单元测试覆盖主表字段、多个子表、同名字段、隐藏字段和历史只读（受既有测试源码编译错误阻断）。

## T3. 实现动态 CRUD 任务专用主子表保存

**目标**：提供服务端安全 merge 保存能力，禁止通过待办接口绕过字段和行权限。

**文件**：

- 修改：`forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/service/DynamicCrudService.java`
- 视需要修改：动态 CRUD repository/Mapper XML
- 测试：`DynamicCrudService` 任务保存单元测试和 repository 契约测试

- [ ] 先写未授权主字段、未授权子字段、跨主记录行、未授权新增/删除的失败测试（受既有测试源码编译错误阻断）。
- [x] 定义任务保存输入的 main/children 解析和旧扁平 data 兼容规则。
- [x] 实现已有子表行归属校验、子表字段 merge、新增外键补齐和 `_deleted` 删除校验。
- [x] 复用现有字段类型、加密、结构化字段、必填和唯一性校验，不复制普通 CRUD 的 SQL 规则。
- [x] 明确 replace 配置下任务保存仍使用 merge 语义，返回最新业务记录。
- [ ] 增加更新时间/版本条件校验，发现并发修改时返回业务异常（当前 DTO/协议没有版本快照字段，留待后续增量）。
- [ ] 在事务测试中验证主表、子表任一失败时整体回滚（受既有测试源码编译错误阻断）。

## T4. 接入 BusinessFlowService 和 DTO 保存链路

**目标**：待办保存接口重新解析节点权限并调用任务专用主子表保存。

**文件**：

- 修改：`forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/service/businessapp/BusinessFlowService.java`
- 修改：`forge-server/forge-framework/forge-plugin-parent/forge-plugin-generator/src/main/java/com/mdframe/forge/plugin/generator/dto/businessapp/BusinessTaskFormSaveDTO.java`（仅需显式字段时）
- 测试：`BusinessFlowService` 保存权限和任务访问契约测试

- [x] 保持 `taskId/businessKey/processInstanceId/recordId` 等固定身份字段在 DTO 中。
- [x] 保存前校验当前用户已签收任务和当前节点仍可办理。
- [x] 从 BPMN 当前节点读取权限，禁止使用前端传入的权限标记。
- [x] 将 `data.main`、`data.children` 过滤后传入任务专用保存方法。
- [x] 对旧扁平 data 走主表兼容分支。
- [x] 保存成功后构造最新 `recordData`，供前端同时回填主表和子表。
- [x] 测试/代码路径统一覆盖 approve/reject/return 前保存；失效任务仍由任务访问校验拒绝。

## T5. ChildTableEditor 接入权限投影

**目标**：普通 CRUD 行为不变，待办模式支持字段级可编辑和行操作权限。

**文件**：

- 修改：`forge-admin-ui/src/components/page-templates/ChildTableEditor.vue`
- 测试：`forge-admin-ui/src/components/page-templates/__tests__/ChildTableEditor.spec.js`

- [x] 补充 readonly、field writable、allowCreate、allowDelete、`_deleted` 的组件测试基线。
- [x] 将子表级权限归一化为组件可用的 `readonly/allowCreate/allowUpdate/allowDelete`。
- [x] 只在 `allowUpdate=true` 且字段 `writable=true` 时启用已有行输入。
- [x] 新增行时只渲染可写字段，删除操作只标记允许删除的行。
- [x] 保留普通 CRUD 传入配置时的原行为和已有 `getValue/validate` API。
- [x] 保留当前单元格 wrapper、行 key 和输入焦点修复，避免回写后输入框失焦。

## T6. todo.vue 接入主子表编辑和审批前保存

**目标**：待办页面在当前任务中正确渲染、校验、暂存和回填主子表。

**文件**：

- 修改：`forge-admin-ui/src/views/flow/todo.vue`
- 测试：新增 `todo` 相关纯函数/组件测试，复用现有 flow 测试工具

- [x] 移除固定 `readonly`，将历史只读和当前节点子表权限区分处理。
- [x] 为 ChildTableEditor 增加 ref 或统一 `getValue/validate` 调用，构造 `{main, children}` payload。
- [x] `businessFormHasWritableFields` 同时计算主表字段、子表字段和行操作权限。
- [x] 保存成功后用服务端返回的最新 `recordData` 回填主表和子表，不能只更新主表。
- [x] 审批前暂存覆盖 approve、reject、rejectToStart、return 四条路径。
- [x] 前端测试覆盖无权限只读、单字段可写、增删按钮和保存 payload 的静态/组件基线。

## T7. 服务端和前端回归验证

**目标**：完成 P1 验收矩阵并留下可复跑证据。

**文件**：

- 修改：`code-copilot/changes/todo-editable-detail/test-spec.md`
- 修改：`code-copilot/changes/todo-editable-detail/execution-log.md`
- 修改：`code-copilot/changes/todo-editable-detail/tasks.md`

- [x] 读取当前变更测试规格和执行日志，按增量范围运行前端目标测试、前端构建和后端模块编译。
- [ ] 验证旧流程无子表权限、单字段写权限、增删权限、跨单据行和 replace/merge 不丢数据的后端自动化场景（测试编译基线阻断）。
- [x] 记录失败命令的根因或明确跳过原因，不把未运行的真实服务 E2E 写成通过。
- [x] 更新 Spec 状态、Tasks 勾选状态和执行日志。

## T8. 建立 AiForm 数组协议和字段目录

**目标**：form-create `group/tableForm` 保留数组父字段和行字段，不再被摊平成普通字段。

- [x] 为 `formCreateToAiSchema` 增加 group、tableForm、旧 children/新 props.rule 的转换测试。
- [x] 定义 `type=array + itemSchema + arrayConfig` 运行时协议。
- [x] 前后端表单字段目录输出数组父字段与 `scope=array` 行字段。
- [x] 字段目录去重使用 `arrayKey + itemField`，避免不同数组同名列冲突。

## T9. 扩展节点数组权限协议

**目标**：真实流程设计器可配置数组行字段与新增、修改、删除权限，BPMN 可稳定 round-trip。

- [x] `flow-field-permissions` 兼容 v1/v2，并在数组权限存在时输出 v3。
- [x] `FormPermissionConfig` 增加数组行操作区，保持现有子表区行为不变。
- [x] parser/writer 增加 `formArrayPermissions`，覆盖 XML round-trip 测试。
- [x] 老动态表单只配置父字段时保持兼容，不自动开放新增/删除。

## T10. 实现 AiForm 数组明细运行时

**目标**：待办可编辑数组行，已办/历史只读，父表单提交前完成逐行校验。

- [x] 新增 `AiFormArrayField`，支持卡片/表格模式、稳定行 key、增删行和空态。
- [x] AiForm 注册数组子校验器，并在 `validate()` 中等待所有行表单完成。
- [x] 数组父权限、行字段权限和行操作权限共同决定运行态可编辑性。
- [x] todo/readonly panel 传递完整权限 bundle，提交和历史回显保留对象数组。
- [x] 增加组件测试覆盖只读、单列可写、新增/删除和嵌套必填。

## T11. 动态数组服务端安全校验与回归

**目标**：Flow 服务按当前节点 schema/权限校验动态数组变量，拒绝前端越权。

- [x] 审批前从当前 BPMN 节点重新读取 form schema 与权限。
- [x] 拒绝不可写父字段、不可写行字段、未授权新增/删除和非对象数组元素。
- [x] 校验失败发生在 `taskService.complete` 前，不写流程变量、不完成任务。
- [x] 增加纯权限校验和后端字段目录单元测试；当前主机无 JDK/Maven，已记录可复跑命令和阻断原因。
- [x] 运行前端定向测试、ESLint、生产构建和 `git diff --check`；后端编译/测试因当前主机无 JDK/Maven 未执行。
