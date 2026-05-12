# 报表动态数据参数优化
> status: apply
> created: 2026-05-11
> complexity: 🟡中等

## 1. 背景与目标
`forge-report-ui` 动态数据配置在选择外部接口时全量加载接口列表，接口数量大时会卡顿。内部接口与外部接口的请求参数也缺少统一、可视化的动态参数机制。

目标：
- 外部接口选择改为「外部系统 -> 外部接口」两级联动，接口使用分页搜索，避免全量查询。
- 内部接口与外部接口共用一套动态参数配置，支持当前登录人上下文与画布筛选组件值。
- 参数配置在界面中选择完成，无需用户手写代码。

## 2. 代码现状（Research Findings）
### 2.1 相关入口与链路
- `forge-report-ui/src/views/chart/ContentConfigurations/components/ChartData/components/ChartDataRequest/components/RequestTargetConfig/index.vue`：动态数据请求配置面板，当前外部接口下拉在 `onMounted` 和 `focus` 时调用 `getExternalApiListApi()`。
- `forge-report-ui/src/api/http.ts`：`customizeHttp()` 统一执行动态请求；内部接口组装 `Params/Header/Body`，外部接口进入 `externalProxyRequest()`。
- `forge-report-ui/src/store/modules/chartEditStore/chartEditStore.d.ts`：`RequestConfigType` 已包含 `requestSource`、`externalApiId`、`externalRequestParams`。

### 2.2 现有实现
- `forge-report-ui/src/api/external/api.ts` 仅封装 `/forge-report-api/external/api/list`，会返回全量可用接口。
- 后端 `ExternalApiController.list(systemId)` 支持按系统列接口，但仍可能返回系统下全量接口。
- 后端 `ExternalApiController.page(ExternalApiQuery)` 已支持 `systemId/apiName/apiCode/apiStatus/pageNum/pageSize`，可直接用于远程搜索。
- 输入类筛选组件通过 `useChartInteract()` 修改目标接口参数，但组件当前值不稳定回写到组件配置，无法作为统一动态参数源可靠读取。

### 2.3 发现与风险
- 不改后端即可复用已有分页接口，减少影响面。
- 动态参数解析发生在前端请求前，需避免破坏原有 `javascript:` 参数能力。
- 当前登录人上下文需通过 `/forge-report-api/auth/userInfo` 获取并缓存，避免每次请求重复拉取。

## 3. 功能点
- [x] 外部系统列表加载，系统选择后清空接口选择。
- [x] 外部接口下拉按系统分页搜索，支持接口名称/编码关键字。
- [x] 新增统一动态参数配置模型 `dynamicRequestParams`。
- [x] 新增动态参数解析工具，支持上下文、组件值、固定值。
- [x] 内部接口请求前把动态参数合并到 Params/Header/Body。
- [x] 外部接口代理请求前把动态参数合并到代理参数。
- [x] 输入/选择/日期/分页/Tab 筛选组件回写当前值，供动态参数读取。

## 4. 业务规则
- 选择外部接口前必须先选择外部系统。
- 外部接口搜索默认只查询启用接口，单次最多返回 50 条。
- 动态参数禁用或参数名为空时不参与请求。
- 组件值源只允许读取画布组件的公开值，不执行用户代码。

## 5. 数据变更
| 操作 | 表名 | 字段/索引 | 说明 |
|------|------|-----------|------|
| 无 | - | - | 本次仅扩展前端保存的报表 JSON 配置 |

## 6. 接口变更
| 操作 | 接口 | 方法 | 变更内容 |
|------|------|------|----------|
| 复用 | `/forge-report-api/external/system/list` | GET | 获取外部系统 |
| 复用 | `/forge-report-api/external/api/page` | GET | 按系统与关键字分页查询接口 |
| 复用 | `/forge-report-api/auth/userInfo` | GET | 获取当前登录人上下文 |

## 7. 影响范围
- `forge-report-ui` 动态数据配置面板
- 图表运行时动态请求
- 公共数据池请求
- 画布输入类筛选组件

## 8. 风险与关注点
- 不涉及资金、状态流转、权限变更。
- 旧报表没有 `dynamicRequestParams` 时按空数组兼容。
- 外部接口旧配置只有 `externalApiId`、没有 `externalSystemId` 时，下拉不会主动全量回显，仍可按原 ID 请求。

## 8.5 测试策略
- **测试范围**：TypeScript 类型检查与生产构建。
- **覆盖率目标**：本次不新增单测，覆盖核心编译链路。
- **独立 Test Spec**：否。

## 9. 待澄清
- 无。

## 10. 技术决策
- 不新增后端接口，复用 `external/api/page` 做接口选择远程搜索。
- 动态参数源语法统一为结构化配置，而不是字符串表达式，降低配置错误率。
- 当前登录人上下文采用前端缓存，兼顾实时性和请求性能。

## 11. 执行日志
| Task | 状态 | 实际改动文件 | 备注 |
|------|------|--------------|------|
| Task 1 | done | `forge-report-ui/src/api/external/api.ts`, `RequestTargetConfig/index.vue` | 外部系统/接口两级联动与分页搜索 |
| Task 2 | done | `chartEditStore.d.ts`, `publicConfig.ts`, `requestDynamicParams.ts`, `http.ts` | 动态参数类型、默认值、解析与请求合并 |
| Task 3 | done | `InputsInput/InputsSelect/InputsDate/InputsTab/InputsPagination` | 筛选组件当前值回写 |
| Task 4 | done | - | `pnpm build` 通过；变更文件 ESLint 无错误 |

## 12. 审查结论
通过构建与变更文件 ESLint 检查，未发现阻塞问题。Vite 输出的 Rollup 循环依赖、CSS `:deep()`、lottie `eval` 均为既有项目警告。

## 13. 确认记录（HARD-GATE）
- **确认时间**：2026-05-11
- **确认人**：需求方本轮直接提出优化需求，按默认执行模式推进
