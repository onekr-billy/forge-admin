# 任务拆分 — 报表动态数据参数优化
> 拆分顺序：数据模型 → 接口协议 → 底层实现 → 上层编排 → 入口层

## 前置条件
- [x] 已确认后端存在 `/external/system/list`、`/external/api/page`、`/auth/userInfo`。

## Task 1: 外部接口两级联动
- [x] 完成
- **目标**: 外部接口配置从全量接口下拉改为先选系统，再按系统远程搜索接口。
- **涉及文件**:
  - `forge-report-ui/src/api/external/api.ts` — 新增外部系统列表和接口分页查询封装。
  - `forge-report-ui/src/store/modules/chartEditStore/chartEditStore.d.ts` — 增加 `externalSystemId`。
  - `forge-report-ui/src/packages/public/publicConfig.ts` — 增加默认 `externalSystemId`。
  - `forge-report-ui/src/views/chart/ContentConfigurations/components/ChartData/components/ChartDataRequest/components/RequestTargetConfig/index.vue` — 改造选择器。

## Task 2: 统一动态参数模型与解析
- [x] 完成
- **目标**: 内部/外部请求共用动态参数绑定模型，支持登录人、筛选组件、固定值。
- **涉及文件**:
  - `forge-report-ui/src/store/modules/chartEditStore/chartEditStore.d.ts` — 增加 `DynamicRequestParamBinding` 等类型。
  - `forge-report-ui/src/packages/public/publicConfig.ts` — 增加默认 `dynamicRequestParams`。
  - `forge-report-ui/src/utils/requestDynamicParams.ts` — 新增动态参数解析工具。
  - `forge-report-ui/src/api/http.ts` — 请求前应用动态参数。

## Task 3: 可视化配置入口
- [x] 完成
- **目标**: 在动态数据请求面板增加结构化参数配置弹窗。
- **涉及文件**:
  - `forge-report-ui/src/views/chart/ContentConfigurations/components/ChartData/components/ChartDataRequest/components/RequestTargetConfig/index.vue` — 增加参数绑定表单、上下文字段和筛选组件选项。

## Task 4: 筛选组件当前值回写
- [x] 完成
- **目标**: 确保运行时可读取画布筛选组件的最新值。
- **涉及文件**:
  - `forge-report-ui/src/packages/components/Informations/Inputs/InputsInput/index.vue`
  - `forge-report-ui/src/packages/components/Informations/Inputs/InputsSelect/index.vue`
  - `forge-report-ui/src/packages/components/Informations/Inputs/InputsDate/index.vue`
  - `forge-report-ui/src/packages/components/Informations/Inputs/InputsTab/index.vue`
  - `forge-report-ui/src/packages/components/Informations/Inputs/InputsPagination/index.vue`

## Task 5: 验证
- [x] 完成
- **目标**: 确认前端构建通过。
- **命令**:
  ```bash
  source ~/.nvm/nvm.sh && nvm use v20.19.0 && cd forge-report-ui && pnpm build
  ```
