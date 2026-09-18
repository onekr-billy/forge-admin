# Excel 导出水印 + 图片导入导出

## 一、Excel 导出文字水印

### 1.1 后端：水印配置扩展

在现有 `WatermarkConfig` 中新增 Excel 专用字段：

**文件**: `forge-starter-config/.../config/WatermarkConfig.java`
```java
// 新增字段
private Boolean excelWatermark = false;   // 是否给 Excel 导出加水印
private Boolean excelShowUsername = true;  // 水印是否包含操作人姓名
```

- `excelWatermark = false` 时不加水印（默认关闭，避免影响不需要水印的导出）
- `excelShowUsername = true` 时水印内容 = `content + " " + 操作人姓名`

### 1.2 后端：Excel 水印处理器

**新建文件**: `forge-starter-excel/.../core/ExcelWatermarkWriteHandler.java`

实现 `SheetWriteHandler`，在 `afterSheetCreate()` 中：
1. 用 `BufferedImage` + `Graphics2D` 绘制文字水印（读取 WatermarkConfig 的 fontSize、fontColor、opacity、rotate、gapX、gapY）
2. 将 BufferedImage 写入临时 PNG 文件
3. 通过 `XSSFSheet.addBackgroundPicture()` 设置为 Sheet 背景
4. 通过 EasyExcel 的 `WriteWorkbookHolder` 获取底层 SXSSFSheet → XSSFSheet 进行操作

注入 `ConfigManagerService` 读取水印配置，注入 `SessionHelper` 获取当前操作人姓名。

### 1.3 后端：DynamicExportEngine 接入水印

**文件**: `forge-starter-excel/.../core/DynamicExportEngine.java`

在 `exportToResponse()` 和 `exportToStreamInternal()` 的 `EasyExcel.write()` 链中：
```java
EasyExcel.write(outputStream)
    .head(headers)
    .registerWriteHandler(excelWatermarkWriteHandler)  // 新增
    .sheet(sheetName)
    .doWrite(mappedData);
```

### 1.4 前端：配置中心水印 Tab 增加 Excel 开关

**文件**: `forge-admin-ui/src/views/system/config-center.vue`

在"水印配置"Tab 中新增两个配置项：
- `excelWatermark`：Switch 开关，"Excel 导出水印"
- `excelShowUsername`：Switch 开关，"水印包含操作人姓名"

### 1.5 Flyway 迁移

更新 `sys_config_group` 表中 watermark 分组的 JSON 默认值，增加 `excelWatermark` 和 `excelShowUsername` 字段（无需 DDL，JSON 配置自动扩展）。

---

## 二、Excel 导入导出支持图片（多张）

### 2.1 数据库：列配置增加图片类型

**Flyway 脚本**: `V1.0.160__add_excel_image_column_support.sql`

```sql
-- sys_excel_column_config 增加列类型字段
ALTER TABLE sys_excel_column_config
    ADD COLUMN IF NOT EXISTS column_type VARCHAR(20) DEFAULT 'TEXT'
        COMMENT '列类型：TEXT/IMAGE' AFTER validation_message;

-- sys_excel_column_config 增加图片列配置
ALTER TABLE sys_excel_column_config
    ADD COLUMN IF NOT EXISTS image_width INT DEFAULT 120
        COMMENT '图片列导出宽度(像素)' AFTER column_type,
    ADD COLUMN IF NOT EXISTS image_height INT DEFAULT 90
        COMMENT '图片列导出高度(像素)' AFTER image_width,
    ADD COLUMN IF NOT EXISTS image_max_count INT DEFAULT 5
        COMMENT '图片列最大图片数量' AFTER image_height;
```

### 2.2 后端：实体和模型扩展

**文件**: `SysExcelColumnConfig.java` 和 `ExcelColumnConfig.java`

新增字段：
- `columnType`：列类型（`TEXT` / `IMAGE`），默认 `TEXT`
- `imageWidth`：导出图片宽度（像素），默认 120
- `imageHeight`：导出图片高度（像素），默认 90
- `imageMaxCount`：最大图片数量，默认 5

### 2.3 后端：图片导出处理器

**新建文件**: `forge-starter-excel/.../core/ExcelImageWriteHandler.java`

实现 `CellWriteHandler`，在 `afterCellDispose()` 中：
1. 检测当前列的 `columnType == "IMAGE"`
2. 单元格值为逗号分隔的 fileId 列表
3. 对每个 fileId：调用 `FileManager` 获取文件输入流
4. 使用 `DrawingPatriarch.createPicture()` 在单元格内嵌入图片
5. 图片尺寸按配置的 imageWidth/imageHeight 缩放
6. 多张图片在同一单元格内垂直排列

注入 `FileManager`（通过 `ApplicationContext.getBean()`）获取文件流。

### 2.4 后端：DynamicExportEngine 接入图片处理

在 `exportToResponse()` 中：
1. 检测 columnConfigs 中是否有 `columnType == "IMAGE"` 的列
2. 如果有，注册 `ExcelImageWriteHandler` 并传入图片列配置
3. 图片列的数据映射：将 fileId 列表直接作为逗号分隔字符串写入单元格值

### 2.5 后端：图片导入处理

**文件**: `ExcelImportServiceImpl.java` / `GenericRowDataListener.java`

导入时图片列处理：
1. 使用 EasyExcel 的 `ReadListener` 的 `extra()` 方法获取图片数据（EasyExcel 4.x 支持）
2. 或使用 `AnalysisEventListener` 的 `invokeHeadMap` + 自定义 `ReadSheet` 获取嵌入图片
3. 将图片上传到 `FileManager`，获取 fileId
4. 将 fileId 以逗号分隔存入对应字段

**注意**: EasyExcel 对导入图片的支持有限，实际实现可能需要使用 POI 底层 API 直接读取 `XSSFDrawing` 中的图片。

### 2.6 前端：列配置页面增加图片类型

**文件**: `forge-admin-ui/src/views/system/excel-column-config.vue`

在列配置表单中：
1. 新增"列类型"下拉（TEXT / IMAGE）
2. 当列类型为 IMAGE 时，显示图片相关配置：
   - 图片宽度（数字输入）
   - 图片高度（数字输入）
   - 最大图片数量（数字输入）
3. 图片列的"字典类型"、"校验规则"等字段隐藏（不适用）

---

## 三、实施顺序

| 步骤 | 内容 | 涉及文件 |
|------|------|---------|
| 1 | Flyway 迁移脚本 | `V1.0.160__*.sql` |
| 2 | WatermarkConfig 增加 excel 字段 | `WatermarkConfig.java` |
| 3 | ExcelWatermarkWriteHandler | 新建 |
| 4 | DynamicExportEngine 接入水印 | `DynamicExportEngine.java` |
| 5 | 前端配置中心增加 Excel 水印开关 | `config-center.vue` |
| 6 | SysExcelColumnConfig + ExcelColumnConfig 增加图片字段 | 两个实体类 |
| 7 | ExcelImageWriteHandler | 新建 |
| 8 | DynamicExportEngine 接入图片导出 | `DynamicExportEngine.java` |
| 9 | 图片导入处理 | `ExcelImportServiceImpl.java` |
| 10 | 前端列配置页面增加图片类型 | `excel-column-config.vue` |

## 四、风险和注意事项

- **水印性能**: 每 Sheet 生成一张水印图片，大数据量导出时需关注内存。使用 `BufferedImage` 缓存避免重复绘制。
- **图片导入复杂度**: EasyExcel 原生不支持读取嵌入图片，需要 POI 底层 API，实现复杂度较高。建议先完成图片导出，导入作为第二阶段。
- **图片导出性能**: 大量图片导出时需关注文件下载和图片嵌入的性能。建议使用线程池并行下载图片。
- **兼容性**: 水印背景图使用 XSSFSheet API，需确认 EasyExcel 版本兼容性。
