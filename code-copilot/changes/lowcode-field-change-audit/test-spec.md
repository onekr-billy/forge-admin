# 低代码字段级数据变更审计 Test Spec

> change: `lowcode-field-change-audit`
> 复用 `spec.md` 第 8.5 节验收表。本轮先覆盖可在无真实库环境下执行的纯逻辑与编译验证。

## 本轮必须执行

| 编号 | 级别 | 方式 | 期望 |
| --- | --- | --- | --- |
| AC02/AC03 | P0 | `DataAuditDiffEngineTest` | null/空串/0/false、数字精度、JSON、多选按 4.3 节比较 |
| AC04 | P0 | `DataAuditDiffEngineTest` | 同值不产生差异 |
| AC18 | P0 | `DataAuditValueProtectorTest` | 凭据 CHANGE_ONLY，敏感值加密信封，脱敏投影不含原值 |
| AC13 | P0 | `DataAuditWriteContextTest` | 客户端 actor/tenant/source/beforeData 被丢弃 |
| AC16/AC17 | P0 | `DataAuditQueryAuthTest` | RECORD/AUDIT 模式授权分离，无范围默认无数据 |
| 编译 | P0 | `mvn -pl forge-plugin-generator -am test -DskipTests` 不适用；使用 `-Penable-tests` 跑新增单测 | 新增测试通过 |
| 前端 lint | P1 | 变更相关文件 eslint | 无新增错误 |

## 体验重构增量验收

| 编号 | 级别 | 方式 | 期望 |
| --- | --- | --- | --- |
| UX01 | P0 | `DataAuditPolicyServiceTest` | 已启用策略可停用；历史策略行保留；原因要求、详情展示和策略版本正确更新 |
| UX02 | P0 | `DataAuditRecordMetaBuilderTest` | 停用对象仍返回历史可用性和详情展示配置，修订号不被清零 |
| UX03 | P0 | 前端审计工具单测 | 策略控制详情入口；停用且有历史时仍可见；无策略对象保持兼容 |
| UX04 | P1 | Mapper 契约/单测 | 记录关键字与字段选择值能够过滤事件，查询仍受授权对象集合限制 |
| UX05 | P1 | 后端单测 | 仅把获授权对象所在应用的数据页面组装为筛选项，并从对象模型自动生成字段选项 |
| UX06 | P1 | 前端 Vitest | 应用切换后只显示所属页面，页面切换后只显示绑定对象字段，查询参数只提交 objectId/fieldCode |
| UX07 | P1 | 前端组件/浏览器 | 业务详情不再出现字段编码输入框，记录以时间倒序时间轴展示并可按需展开差异 |
| UX05 | P1 | 浏览器验收 | 配置页可理解并可启停；详情可见历史；管理员查询不要求手填对象、用户等内部 ID |
| UX06 | P0 | `DataAuditRecordMetaBuilderTest` + 后端编译 | 服务重启后的首次详情读取会先加载策略；普通、主子表和关联查询详情均挂载审计元数据 |
| UX07 | P1 | `DataAuditRecordLabelResolverTest` | 新事件使用单据编号和对象显示字段生成可读记录快照，删除读取旧值，敏感字段不进入标签 |

## 本轮标记待验（需真实 MySQL / 登录态）

AC01、AC05–AC15、AC19–AC30 中依赖真实事务、流程回调、Flyway 升级库、浏览器的项。不把编译成功写成这些项已通过。

## 超级管理员审计范围增量验收

| 编号 | 级别 | 方式 | 期望 |
| --- | --- | --- | --- |
| SCOPE01 | P0 | `DataAuditScopeServiceTest` | 超级管理员无显式角色范围时获得当前租户全部已配置策略对象，包含已停用策略的历史范围 |
| SCOPE02 | P0 | `DataAuditScopeServiceTest` | 普通用户无角色审计范围时仍返回空集合 |
| SCOPE03 | P0 | `DataAuditScopeServiceTest` | 超级管理员管理员查询携带配置对象进入事件 Mapper，不再提前返回空页 |
| SCOPE04 | P1 | 重启 Admin 后浏览器/API 验收 | 应用下拉出现已配置对象所属应用，管理员页显示已落库事件；重新执行删除后出现对应历史 |

## 字段容量与时间轴增量验收

| 编号 | 级别 | 方式 | 期望 |
| --- | --- | --- | --- |
| VALUE01 | P0 | `LowcodeFieldValueValidatorTest` | varchar/char 超长、结构化值序列化后超长在写库前返回字段名称和容量提示 |
| VALUE02 | P0 | `LowcodeFieldValueValidatorTest` | int/tinyint/bigint 拒绝非整数和越界值，decimal 严格校验总位数与小数位 |
| VALUE03 | P0 | `LowcodeFieldValueValidatorTest` | basicProps.min/max 生效，且不能放宽数据库物理范围；bigint MONEY 按元输入、分存储校验 |
| VALUE04 | P0 | `LowcodeRuntimeConfigBuilderTest` | 数字运行组件得到数据库安全 min/max/precision，文本得到 maxlength，自定义范围与物理范围取交集 |
| VALUE05 | P1 | `LowcodeSchemaValidatorTest` | 发布时拒绝非法字符长度、decimal 精度以及 min 大于 max |
| AUDIT_UI01 | P1 | 前端 Vitest | 主表变化优先、默认显示前 4 项、不同事件显示对应前后值表头 |
| AUDIT_UI02 | P1 | 组件/浏览器 | 详情时间轴自动加载字段差异，无需逐条点击“查看变化” |

## 主表差异与大量历史增量验收

| 编号 | 级别 | 方式 | 期望 |
| --- | --- | --- | --- |
| AUDIT_DETAIL01 | P0 | `DataAuditControllerPermissionTest` | 记录权限、列表权限或详情权限均可进入事件及字段接口，服务层继续执行访问模式授权 |
| AUDIT_DETAIL02 | P1 | 前端 Vitest | 主表与子表字段分组计数准确，主表始终排在子表之前 |
| AUDIT_DETAIL03 | P1 | 目标 ESLint + 生产构建 | 时间轴每页 10 条、页数切换和字段加载失败提示无编译或静态检查错误 |
| AUDIT_DETAIL04 | P1 | 登录态浏览器验收 | 只有记录权限的用户可见主表字段前后值；大量历史按时间倒序分页且切页无旧记录残留 |

## 主表字段采集完整性增量验收

| 编号 | 级别 | 方式 | 期望 |
| --- | --- | --- | --- |
| AUDIT_CAPTURE01 | P0 | `DataAuditFieldResolverTest` | `modelSchema` 非空但遗漏 `field_number` 时，真实快照中的变化仍生成主表字段差异 |
| AUDIT_CAPTURE02 | P0 | `DataAuditFieldResolverTest` | 编辑表单中的中文名称和组件类型被保留，驼峰编码正确读取下划线物理列 |
| AUDIT_CAPTURE03 | P0 | `DataAuditFieldResolverTest` | 过期列映射按真实快照列纠正，敏感配置继续保留，系统字段不进入差异集合 |
| AUDIT_CAPTURE04 | P1 | 重启 Admin 后登录态浏览器验收 | 新建一次同时修改主表和子表的记录，时间轴同时显示主表字段前后值与子表摘要 |

## 事务提交阶段快照增量验收

| 编号 | 级别 | 方式 | 期望 |
| --- | --- | --- | --- |
| AUDIT_TX01 | P0 | `DataAuditCaptureServiceTest` | 实际 open/Holder/beforeCommit 链路在运行上下文退出后仍生成主表两字段及子表删除摘要，前后值、中文名、事件关联准确 |
| AUDIT_TX02 | P0 | 同上 | 主表单独修改也生成证据，同值保存无事件，同事务多次写入使用首次前值和最终值 |
| AUDIT_TX03 | P0 | 同上 | 写入时的数据源和自定义主键用于最终回读，自增生成主键后仍能读取，读取完成后还原调用方上下文 |
| AUDIT_TX04 | P1 | 用户环境联调 | 重启 Admin 后新修改主表及删除子行，同一 event_id 下查询到 relation_key 为空的主表字段及非空的子表摘要 |

本轮模拟事务同步生命周期和 Repository 数据返回，不连接数据库、不启动服务；不把以上单测当作跨库原子性或真实 MySQL 隔离级别验证。
