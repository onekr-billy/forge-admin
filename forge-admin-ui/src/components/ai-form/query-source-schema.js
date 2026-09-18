/**
 * 受管查询源 inputSchema 解析（纯函数，设计器多处复用）
 *
 * 兼容三种 schema 形态：
 * 1. 数据集 paramSchemaJson 直接存 JSON 数组：[{paramName, label, dataType, required, ...}]
 * 2. 嵌套形态：{ inputSchema: [...] }
 * 3. 外部接口 JSON Schema：{ properties: {...}, required: [...] }
 *
 * 统一归一化为 [{ name, label, type, required }]，调用方不再关心来源格式差异。
 * FieldEventRulesEditor（字段自动查询）与 ForgePropertyPanel（下拉选项来源）共用本实现。
 */

/**
 * @param {string|Array|object} inputSchemaJson 后端 metadata.inputSchemaJson
 * @returns {Array<{name: string, label: string, type: string, required: boolean}>} 归一化后的参数定义列表
 */
export function parseQuerySourceInputSchema(inputSchemaJson) {
  if (!inputSchemaJson)
    return []
  try {
    const schema = typeof inputSchemaJson === 'string' ? JSON.parse(inputSchemaJson) : inputSchemaJson
    // 数据集 paramSchemaJson 直接存的是 JSON 数组：[{paramName, label, dataType, required, ...}]
    if (Array.isArray(schema)) {
      return schema.filter(p => p && (p.paramName || p.name)).map(p => ({
        name: p.paramName || p.name,
        label: p.label || p.paramName || p.name,
        type: p.dataType || p.type || 'string',
        required: p.required === true,
      }))
    }
    if (Array.isArray(schema?.inputSchema)) {
      return schema.inputSchema.filter(p => p && p.name).map(p => ({
        name: p.name,
        label: p.label || p.name,
        type: p.type || 'string',
        required: p.required === true,
      }))
    }
    if (schema?.properties && typeof schema.properties === 'object') {
      return Object.entries(schema.properties).map(([name, def]) => ({
        name,
        label: def?.label || def?.title || name,
        type: def?.type || 'string',
        required: Array.isArray(schema.required) && schema.required.includes(name),
      }))
    }
    return []
  }
  catch {
    return []
  }
}
