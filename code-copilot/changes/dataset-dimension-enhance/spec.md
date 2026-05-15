# 数据集字段与维度管理增强 Spec

## 背景

现有数据集字段配置只支持基础字段元数据、是否可筛选、是否可展示、字典类型与简单脱敏。实际报表和分析页面需要更稳定的字段语义配置，包括字段显示名称、标准类型、字段角色、日期格式、计量单位、脱敏规则，以及维度值翻译。

## 目标

1. 数据集字段配置支持编辑显示名称、标准类型、字段角色，页面不再暴露“可筛选”“可展示”两个配置项。
2. 数据集字段扩展属性支持日期展示格式、数据计量单位、脱敏级别与脱敏规则。
3. 新增维度管理模块，支持手工维护维度字典数据，也支持绑定数据连接并通过 SQL 同步维度数据。
4. 数据集维度字段支持绑定维度，运行时查询结果自动将字段原始值翻译成维度展示值，同时保留原始值。

## 设计

### 数据模型

- 扩展 `ai_report_data_dataset_field`
  - `date_format`: 日期/日期时间展示格式
  - `data_unit`: 数据计量单位
  - `dimension_id`: 绑定维度 ID
- 新增 `ai_report_data_dimension`
  - 维度主表，支持 `MANUAL` 和 `SQL` 两种来源
  - SQL 来源配置 `connection_id`、`sql_text`、`value_column`、`label_column`
- 新增 `ai_report_data_dimension_item`
  - 维度值表，存储 value/label/status/sort/extra_json

### 后端接口

- 新增 `/data/dimension` 标准 CRUD
- 新增 `/data/dimension/list` 用于数据集字段绑定下拉
- 新增 `/data/dimension/{id}/items` 查询与保存维度项
- 新增 `/data/dimension/{id}/sync` 通过 SQL 同步维度项
- 数据集字段详情、元数据、运行时查询补充扩展字段与维度信息

### 前端页面

- 新增 `forge-admin-ui/src/views/data/dimension.vue`
- 新增 `forge-admin-ui/src/api/data/dimension.ts`
- 数据集字段弹窗改为字段配置弹窗，支持字段显示名、标准类型、字段角色、日期格式、单位、脱敏、维度绑定

### 运行时翻译

运行时查询如果字段绑定维度：

- `row[fieldName]` 输出翻译后的展示值
- `row[fieldNameRaw]` 保留原始值
- 字段元数据返回 `dimensionId`、`dimensionCode`、`dimensionName`

## 非目标

- 不实现维度树、级联维度、多语言翻译。
- 不改变数据集参数配置模型。
- 不删除数据库中的 `query_enabled`、`display_enabled` 字段，保持兼容，但页面不再提供配置入口。
