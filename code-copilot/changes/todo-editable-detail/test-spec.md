# 待办中的可编辑明细 Test Spec

> 变更：`todo-editable-detail`  
> 当前状态：路线 A、路线 B 已实现；待真实服务 E2E

## 1. 测试范围

本变更覆盖路线 A（低代码业务对象主子表）和路线 B（动态表单 `group/tableForm` 对象数组）。路线 B 重点验证 schema 转换、节点权限、嵌套校验、流程变量保存和服务端越权拒绝。

## 2. 前端测试

### 2.1 权限协议和设计器

- 旧 `formFieldPermissions` 数组能解析、写回且字段含义不变。
- v2 主表/子表字段权限能 round-trip。
- 多个子表存在同名字段时按 `childKey + childField` 区分。
- 缺少子表权限时默认只读。
- 子表新增、修改、删除操作权限在节点配置中可单独保存。

建议命令：

```bash
source ~/.nvm/nvm.sh && nvm use v20.19.0
cd forge-admin-ui
pnpm --ignore-workspace exec vitest run \
  src/components/flow-designer/panel/__tests__/FormPermissionConfig.spec.js \
  src/components/flow-designer/converter/__tests__/user-task-parser-permissions.spec.js \
  src/components/page-templates/__tests__/ChildTableEditor.spec.js
```

### 2.2 待办交互

- 当前待办仅允许配置的子表字段可输入。
- 已办/历史所有子表控件只读。
- `allowCreate=false` 时没有新增入口；`allowDelete=false` 时没有删除入口。
- 保存 payload 同时包含 `main` 和 `children`。
- 服务端回填后主表和子表数据均更新。
- 输入、回写和校验不会导致子表输入框失焦。

### 2.3 动态数组明细

- `group.props.rule`、`group.children`、`tableForm.props.columns[].rule` 均转换为一个数组父字段。
- 不同数组内同名行字段按 `arrayKey + itemField` 区分。
- 父字段只读、数组 `allowUpdate=false`、行字段 `writable=false` 任一成立时对应输入禁用。
- `allowCreate/allowDelete` 分别控制新增和删除入口；已办/历史强制关闭全部操作。
- 外层 `AiForm.validate()` 能发现第 N 行的必填错误。
- 同意提交变量保留对象数组，不产生摊平的伪顶层字段。

UI 改动至少运行：

```bash
source ~/.nvm/nvm.sh && nvm use v20.19.0
cd forge-admin-ui
pnpm --ignore-workspace build
```

## 3. 后端测试

### 3.1 权限过滤

- 未配置子表权限时子表只读。
- `readable=false` 字段不返回。
- `writable=false` 字段不能被保存。
- 未签收任务、其他任务和其他业务记录不能保存。

### 3.2 主子表保存

- 已有行部分字段修改保留只读字段。
- 新增行要求 `allowCreate=true`，并由服务端补齐父 ID 和租户上下文。
- 删除行要求 `allowDelete=true`。
- 跨主记录行 ID 被拒绝。
- replace 配置下提交部分 children 不删除未提交行。
- 主表或子表失败时事务整体回滚。
- 并发更新时间/版本不一致时拒绝覆盖。

### 3.3 动态数组变量

- 服务端从当前 BPMN 重新读取权限，不接受客户端权限标记。
- 不可写数组父字段或行字段发生变化时拒绝。
- 新增/删除行分别要求 `allowCreate/allowDelete=true`。
- 数组元素不是对象、超过 schema `max` 或低于 `min` 时拒绝。
- 校验失败时任务仍为待办，原流程变量不变。

建议命令：

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/Cellar/openjdk@17/17.0.13/libexec/openjdk.jdk/Contents/Home/bin:$PATH \
mvn -pl forge-framework/forge-plugin-parent/forge-plugin-generator -am test \
  -Dtest=BusinessFlowService*Test,DynamicCrudService*Test \
  -DfailIfNoTests=false

mvn -pl forge-framework/forge-plugin-parent/forge-plugin-flow -am test \
  -Dtest=DynamicFormArrayPermissionValidatorTest,FlowFormServiceImplFieldCatalogTest \
  -DfailIfNoTests=false
```

## 4. 服务级验证

本轮不自动启动真实 Admin/Flow 服务、不改动用户数据库或运行中流程。用户若提供可用开发环境，可按以下路径补验：

1. 登录并签收一条带主子表的待办。
2. 修改一个有权限字段，确认保存接口返回最新 `recordData`。
3. 尝试提交不可写字段、跨单据行 ID、未授权删除，确认接口拒绝。
4. 同意或驳回后，在下一节点/业务详情确认明细已更新。

## 5. 通过标准

- 前端目标测试和构建通过。
- 后端目标测试通过，或失败有明确环境/代码根因。
- 旧流程只读兼容和跨租户/跨记录安全边界有自动化证据。
- `git diff --check` 无错误。

## 6. 本轮增量验证（2026-09-18）

已执行：

- 前端 Vitest：`FormPermissionConfig.spec.js`、`user-task-parser-permissions.spec.js`、`DingFlowDesigner.spec.js`、`ChildTableEditor.spec.js`，4 个文件、37 tests passed。
- 前端定向 ESLint：0 errors、0 warnings。
- 前端生产构建：`pnpm --ignore-workspace build`，`✓ built`。
- 后端主代码：JDK 17 下 `forge-plugin-generator` reactor compile 通过。
- `git diff --check`：通过。

未通过/受阻：

- 后端开启测试编译后，仓库既有测试基线无法编译：`FormulaExecutionEngineTest.java` 存在 UTF-8 不可映射字符；`DynamicCrudRepositoryTest`、`DbAggregateDataProviderTest` 使用已变更构造器。错误发生在测试源码编译阶段，未进入本变更目标测试执行；本轮未修改这些无关测试。
- 未启动 Admin/Flow、未连接数据库，未执行真实待办 E2E；按照项目约定由用户在可用开发环境补验。

## 7. 路线 B 增量验证（2026-09-18）

已执行：

- 前端 Vitest：数组 schema、字段目录、v3 权限 round-trip、设计器自动绑定、AiForm 数组运行时、只读回显和路线 A 回归共 10 个文件、68 tests passed。
- 前端定向 ESLint：本轮涉及的 Vue/JS/测试文件 0 errors、0 warnings。
- 前端生产构建：项目本地 `vite build` 通过；仅保留仓库既有 Vite configLoader 和 CSS 注释警告。
- `git diff --check`：通过。

已补但当前主机未执行：

- 新增 `DynamicFormArrayPermissionValidatorTest`，覆盖父/行字段越权、未授权增删、顺序删除、对象数组类型、min/max、必填和历史未知字段保护。
- 新增 `FlowFormServiceImplFieldCatalogTest`，覆盖数组父字段、两个数组同名行字段的目录隔离。
- 当前主机没有 Java Runtime，且没有 `mvn` 命令，因此无法编译或运行新增后端测试；上面的 Flow 模块命令可在 JDK 17 + Maven 环境直接复跑。
- 未启动 Admin/Flow、未连接数据库，未执行真实动态数组待办 E2E。
