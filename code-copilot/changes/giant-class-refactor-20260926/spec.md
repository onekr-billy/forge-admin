# 后端巨型类分阶段拆分

## 背景与范围

用户列出的低代码、业务流程和 Flowable 巨型类约四万行，跨越运行配置、动态 CRUD、设计态、发布、流程编排、任务、事件及通知。一次性跨域搬迁风险过高；按稳定边界逐步提取协作者，保留现有公共 API、Spring 装配、事务边界、数据库协议和运行时 JSON 结构。

## 设计约束

- 入口 Service 保持 Facade/Orchestrator 职责；细分组件按编译、策略、状态、仓储或副作用单一职责命名。只引入能降低耦合的模式，不为模式增加空壳层。
- 不改变公开方法签名、现有构造器参数、Bean 名称、接口路径、状态码和异常文案；每次提取前后做契约测试。
- 不新增 Service 相互注入形成循环依赖。底层组件可由入口单向持有，跨域协调留在入口。
- 流程启动锁按租户和业务键互斥，并持续到事务完成；回调幂等、状态回写和表单权限保持原语义。
- 运行配置 JSON 的键、默认值、字典顺序、脱敏和加密规则保持兼容。
- 本变更不调整 SQL、Flyway、前端协议或业务状态机；若后续阶段需要更改，应单独扩展 Spec 并审查。
- Java 生产类目标不超过 1000 行；新增组件不得超限。存量巨型类按职责逐步缩小，阶段交付必须如实列出仍超限的类。

## 分阶段边界

1. 流程入口：从 `BusinessFlowService` 提取启动锁、状态修复、回调编排、表单上下文等独立组件，逐步收敛为 Facade。
2. 运行配置：从 `LowcodeRuntimeConfigBuilder` 提取字段元数据、树配置、主子表及 API 编译器，以序列化快照对比守护协议。
3. 动态 CRUD 与 Repository：分离查询计划、写入规则、字段安全和 SQL 拼装；优先保留 Mapper XML 的查询入口。
4. 设计态与发布：分离版本快照、Schema 迁移、DDL 执行和发布状态，保持回滚链路。
5. 业务流程编排、动作、触发器与 Flowable 服务/监听器：以状态机、事件路由和通知渠道为边界，分别建立回归基线后拆分。

每阶段独立编译、测试并记录；未完成阶段不得标记为已优化。

## 当前实施范围

首个切口提取 `BusinessFlowStartLockManager` 和 `RuntimeFieldMetadataCompiler`。第二个切口从运行配置入口提取 `RuntimePageRelationResolver`、`RuntimeTreeConfigBuilder` 与 `RuntimeChildTableCompiler`。第三个切口提取 `BusinessRuntimeConfigResolver` 与 `BusinessFlowStatusRepairService`，集中处理租户内运行配置选择、发布/草稿回退与流程状态字段修复；入口保留事务和 Flowable 事件编排。

本轮继续从 `LowcodeRuntimeConfigBuilder` 提取页面引用字段、子表字段、默认与自定义动作、关系查找配置、表单容器选项及树字段装饰等纯编译职责，并补完整主子表 JSON 契约。`FlowTaskNotifyListener` 仅迁出通知内容渲染，`FlowTaskEventListener` 仅迁出身份解析；`BusinessProcessOrchestrator` 仅迁出运行视图组装；`FlowMonitorServiceImpl` 仅迁出管理员视图、统计值和任务树组装。监听触发、通知投递、租户校验、批量查询、清理事务和状态机仍留原路径。

后续切口再从运行配置入口迁出搜索/编辑字段组件协议，并从低代码 DDL 入口迁出在线执行白名单。字段值映射、SQL 生成与 DDL 执行入口不变；涉及真实 DDL 的验收仍需独立服务/数据库环境。

第十五个切口从 `FlowModelServiceImpl` 迁出 BPMN 文本兼容处理、流程 Key 替换和部署前结构/执行节点校验到无状态 `FlowModelBpmnPreflight`。模型服务仍保持创建、更新、导入、复制与部署的原调用顺序、发布事务、租户边界和版本治理。预检不改变 BPMN 节点配置归属、候选人解析或原 XML 保存语义；通过定向测试守护缺失节点、悬空连线、未配置审批人、不支持的执行节点/属性及网关分支拒绝路径。

第十六个切口从 `LowcodeRuntimeConfigBuilder` 迁出设计器列表布局块、搜索/表格字段设置、表单规则和画布元素读取到无状态 `RuntimeDesignerLayoutReader`。运行配置入口仍负责字段解析、选项覆盖与最终序列化，不更改设计态持久化协议或运行时 JSON 字段。

第十七个切口继续迁出 form-create 规则到运行时字段设置的转换，由 `RuntimeFormRuleSettingResolver` 处理组件标识、必填开关和验证规则、样式及网格跨度。原入口保留表单字段组装和网格列数解析；网格列数使用延迟回调保持原计算时机。

第十八个切口把编辑字段 JSON 编译迁入 `RuntimeEditFieldCompiler`，基础组件属性白名单、对齐及系统字段判定迁入共享 `RuntimeFieldPresentationSupport`。原入口仅选择字段和设计器设置，主子表仍复用同一编辑字段编译器；必填、公式只读、引用名称伴随列和字段约束的运行协议不变。

第十九个切口把列表列 JSON 编译迁入 `RuntimeTableColumnCompiler`，负责字典、引用、开关等渲染配置与列宽、固定列、点击动作、子表标题前缀。它继续复用原字段元数据判定；入口仍选择列字段并构造操作列。

第二十个切口把运行字段目录和设计器显式选列解析迁入 `RuntimeFieldCatalogResolver`，集中处理模型/子表字段映射、列表网格与旧页面回退、停用字段过滤及托管流程状态列补齐。`LowcodeRuntimeConfigBuilder.buildManagedFlowStatusColumn` 的公开签名保持不变，显式隐藏判定仍沿原运行时字段设置路径执行。

第二十一个切口从 `BusinessFlowService` 迁出节点表单绑定及主/子表字段权限兼容归一化到 `BusinessFlowNodeFormNormalizer`，将共用 JSON/标量读取迁入 `BusinessFlowJsonReader`。入口继续拥有流程配置保存、任务表单、权限执行、事务与状态机；仅把无状态解析规则集中，不改变 BPMN 节点配置所有权或回调语义。

第二十二个切口继续迁出审批表单控件类型兼容推断与字段目录编译到 `BusinessFlowTaskFormControlTypes`、`BusinessFlowFormFieldCatalog`。应用页和业务对象表单仍由原入口选择资产；新组件只读取设计器 schema，按原顺序提取主表字段、递归子表列和预览标签。

第二十三个切口把代码应用 Provider 表单资产与绑定元数据的合并、非公开字段过滤、资产移除标记及预览编译迁入 `BusinessCodeAppFormAssetMerger`。入口仍负责租户绑定读取、Provider 调用和运行时审批权限执行；只迁移无状态资产装配规则。

本轮后 `FlowTaskNotifyListener`、`FlowTaskEventListener`、`BusinessProcessOrchestrator`、`FlowMonitorServiceImpl`、`LowcodeDdlService`、`FlowModelServiceImpl` 和 `LowcodeRuntimeConfigBuilder` 均低于 1000 行；`BusinessFlowService` 为 8082 行，仍需继续按阶段拆分。其他目标类未完成，不视为已优化。

## 验收

- 所有修改后的模块可编译，新增及相关原有定向测试通过。
- 启动锁事务完成前互斥、异常时释放；字段元数据输出与原逻辑一致。
- `git diff --check` 无问题；不触碰用户已有的 `.DS_Store` 改动。
