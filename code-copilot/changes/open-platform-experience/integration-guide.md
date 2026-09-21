# 开放平台使用与接入说明

## 开发位置

- 分支：`codex/open-platform-experience`
- 独立目录：`/Users/mini32g/Desktop/project/forge-admin-main`
- 原 H5 工作区不参与本次开发。未提交、推送或部署；原有 `.DS_Store` 修改不属于本次交付。

## 面向使用者的操作顺序

1. **能力目录**：决定“开放什么”。点击注册能力，选择流程操作、低代码应用或系统 REST 接口。
2. **配置来源**：优先选择已发布应用及页面；技术人员可展开直接选择对象、能力编码和版本。不可用来源会说明原因。
3. **确认发布**：核对来源、版本和允许字段。发布只创建能力版本，不会自动给外部系统授权。
4. **接入系统**：决定“谁来调用”。创建后妥善保存仅展示一次的凭据，点击“我已保存，继续授权”进入同一系统工作台。
5. **能力授权**：选择需要调用的能力及版本。流程、表单和本期 REST 能力要求真实用户委托；有客户端凭据不代表拥有用户业务权限。
6. **调用指南 / 在线测试 / 调用记录**：查看平台生成的地址、参数和认证示例，用已授权的测试身份验证。生产环境不要用真实业务单据进行破坏性测试。

菜单路径继续使用 `/open-platform/capability-catalog`、`/open-platform/capability-client`、`/open-platform/capability-invocation`。数据库迁移将内置“机器客户端”菜单改名为“接入系统”，不覆盖管理员自行设置的名称。

## 三类能力的边界

### 流程操作

- 业务审批复用已有启动、提交申请、同意、驳回协议，本次补充撤回。
- 提交申请按既有协议接收 `data`，由平台创建业务记录并送审；启动已有记录则传 `recordId`。
- 同意/驳回仍需指定任务，后端按真实委托人重新校验任务权限。
- 撤回传 `recordId` 与 `arguments.processInstanceId`，可附 `arguments.comment`。必须明确绑定本次实例，旧请求不能撤回后来重新发起的另一个实例。
- 撤回复用 `BusinessFlowService.withdrawDocumentFlow`，由原业务服务检查发起人、租户、当前实例与状态并执行回写，不直接修改 Flowable 表。
- 独立流程启动继续使用既有系统服务来源，不强制绑定业务对象。

### 低代码应用 / 表单

- 先选择应用，再选择发布快照中绑定了数据对象的页面或表单块；纯展示页面不是可执行写入能力。
- 通用填报服务为 `lowcode.form.create`，接收 `{"data": {...}}`，返回字符串 `recordId`。它只创建记录，不自动送审。
- 首期支持主库单表、标量业务字段。主子表、结构化字段和外部数据源需具备完整契约的专用业务动作，通用来源会显示不可用原因，不会忽略明细后保存。
- 发布时锁定允许字段、模型必填项及来源版本；系统字段、只读/隐藏/公式字段、租户与单据状态/归属字段不可外部填写。
- 执行用户需要 `ai:capability:system-service:invoke` 和 `ai:business:<objectCode>:add`，并同时满足客户端能力授权。
- 系统服务的字段契约固定在能力版本上；本期不支持按客户端再次裁剪表单字段。要提供不同字段集合，应发布分别受控的能力。
- 通过网关 `Idempotency-Key` 请求头传稳定键。主库插入及幂等回执在同一事务；同键同请求复用结果，同键不同用户/组织/版本/数据拒绝。
- 回执不保存表单内容，只保存摘要与记录标识。来源重新发布后需审核并发布能力新版本。

### 系统 REST 接口

接口由开发者显式纳管后才出现在选择器中，并非把所有管理接口自动公开。内置示例为字典选项查询。

```java
@OpenRestCapability(
    name = "查询字典选项",
    permission = "ai:capability:dictionary:read",
    description = "读取当前租户字典选项",
    version = "1"
)
@GetMapping("/type/{dictType}")
public RespInfo<?> getByType(@PathVariable("dictType") String dictType) {
    return RespInfo.success(dictDataService.selectDictDataByType(dictType));
}
```

纳管要求：

- 使用明确的路径/查询参数、JSON DTO 和同步 `RespInfo` 返回；不支持任意 `Map/Object`、Servlet 参数、流式或异步返回。
- 方法在可信能力身份上下文内执行，不通过 HTTP 重新调用；必须使用 `SessionHelper` 及原 Service 的租户/数据权限校验。
- 桥接保留声明的业务权限、JSON 字段白名单及默认 Bean Validation 校验。它不重放 MVC 拦截器、请求/响应 advice、原 HTTP 会话或自定义 validation group；依赖这些机制的接口不能直接纳管，应编写专用 `SystemServiceCapabilityDefinition`。
- 写接口仍需业务层事务与幂等设计，通用 REST 桥接不承诺跨任意业务资源的 exactly-once。
- 调用方不能填写目标 URL、Header、反射方法名或覆盖执行身份。`Long` 参数契约采用十进制字符串，避免前端精度丢失。
- 接口实现语义变化时增加注解 `version`；签名/输入契约/权限指纹变化会拒绝旧能力。之后发布能力新版本并人工确认客户端授权。
- 不要为未审查的敏感接口批量添加注解。本次新增权限资源不自动授予客户端或普通角色。

## 后续嵌入低代码应用

可复用 `CapabilityRegisterModal` 与 Pinia `useCapabilityRegistrationStore`，不复制整套注册页面：

```vue
<CapabilityRegisterModal
  v-model:show="registrationVisible"
  :allowed-types="allowedTypes"
  :initial-context="{ scenario: 'application', applicationCode }"
  @success="refreshCapabilities"
/>
```

`initialContext.pageId` 可选，使用 `publishedPageSources` 生成的页面/表单块来源标识。当前阶段尚未向应用设计器添加入口；应用/页面选择只用于定位发布对象，真正执行仍由后端发布快照及权限控制。

## 上线前检查

1. 在测试环境审核并执行 `V1.0.181`、`V1.0.182`。已有业务库禁止重跑全量初始化 SQL。
2. 确认 Flyway 历史成功，撤回字典、幂等回执表和两项权限资源存在，刷新客户端菜单与字典缓存。
3. 对权限/状态流转改动做人工审查。选择测试角色与客户端逐项授权，不直接给所有角色或接口开放权限。
4. 启动 Admin/Flow，验证真实用户登录、注册发布、授权、调用指南及网关调用；覆盖越权、跨租户、重复键和撤回后重新发起的情况。
5. 对表单回执执行真实 MySQL 事务/并发/失败恢复验证；目前单元测试使用模拟存储，不能代替数据库验收。

回滚应用时保留幂等回执与已有能力版本，先停用本次新增能力/授权；不要删除历史回执导致重试重复建单。菜单名如需回退，只恢复本次改为“接入系统”的内置资源，不改变资源 ID 与授权关系。
