# 独立打印中心接入

## 管理端配置

“打印中心”是与“应用中心”平级的独立一级目录，包含三个菜单：

- “业务数据源”：登记并维护 SERVICE / DATASET 来源。
- “打印模板”：创建、设计和发布来源对应的打印模板。
- “场景绑定”：将已发布版本绑定到列表、详情或流程场景。

配置顺序为：先新建业务来源，再创建、设计并发布模板，最后为发布版本绑定业务场景。

- `SERVICE`：`Provider 编码`必须与后端 `PrintBusinessDataProvider.code()` 一致。
- `DATASET`：只能选择已发布、已启用且当前用户有查询权限的数据集；`recordIdParam`声明记录 ID 对应的数据集参数。
- 来源新建后默认停用。模板发布、场景绑定完成后再启用。
- 独立来源的绑定固定发布版本；模板重新发布后，在绑定面板中手动升级版本。

参数协议示例：

```json
{
  "version": { "type": "integer", "required": true, "min": 1 },
  "language": { "type": "string", "enum": ["zh-CN", "en-US"] }
}
```

## 业务页面调用

页面只需要保存稳定的来源编码。组件会按当前租户向后端解析来源身份，再复用统一的模板选择、预览、PDF 和打印事件链路。

```vue
<script setup>
import BusinessPrintButton from '@/components/print/runtime/BusinessPrintButton.vue'

defineProps({ row: { type: Object, required: true } })
</script>

<template>
  <BusinessPrintButton
    source-code="purchase_order"
    :record-id="row.id"
    scene="DETAIL"
    :params="{ version: row.version }"
  />
</template>
```

需要自定义按钮或弹层时，可以复用 composable：

```vue
<script setup>
import { NModal } from 'naive-ui'
import PrintTemplatePicker from '@/components/print/runtime/PrintTemplatePicker.vue'
import { useBusinessPrint } from '@/components/print/runtime/useBusinessPrint'

const { visible, record, open, close } = useBusinessPrint({
  sourceCode: 'purchase_order',
  scene: 'LIST',
})

async function printRow(row) {
  await open({ recordId: row.id, params: { version: row.version } })
}
</script>

<template>
  <NModal :show="visible" preset="card" @update:show="value => !value && close()">
    <PrintTemplatePicker v-if="record" :record="record" @back="close" />
  </NModal>
</template>
```

`params` 只用于向后端 Provider 传递参数协议中声明的标量值。它不携带页面正文，也不能覆盖租户、用户、模板或流程身份；业务数据始终由后端重新查询和鉴权。
