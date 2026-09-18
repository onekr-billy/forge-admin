import { computed } from 'vue'
import { previewFormula } from '@/api/formula'
import { isNumberFieldType } from '../../field-type-utils'

export function flattenRuntimeFormFields(nodes = []) {
  const result = []
  const walk = (items = []) => {
    ;(Array.isArray(items) ? items : []).forEach((node) => {
      if (!node || typeof node !== 'object')
        return
      if (node.nodeType && node.nodeType !== 'field') {
        walk(node.children || [])
        return
      }
      if (node.field)
        result.push(node)
    })
  }
  walk(nodes)
  return result
}

/** 公式运行态；回填调用方的 formData ref，定时器和请求序号按实例隔离。 */
export function useCrudFormula({ props, formData, isDetailMode, formOnlySubmitted, modalVisible, inlineWorkspaceVisible }) {
  const formulaRuntimeTimers = new Map()
  const formulaRuntimeSequences = new Map()

  const runtimeFormulaFields = computed(() => {
    return flattenRuntimeFormFields(props.editSchema)
      .map(field => normalizeRuntimeFormulaField(field))
      .filter(Boolean)
  })

  const runtimeFormFieldMap = computed(() => {
    const result = new Map()
    flattenRuntimeFormFields(props.editSchema).forEach((field) => {
      if (field?.field)
        result.set(field.field, field)
    })
    return result
  })

  const runtimeFormulaCalculationEnabled = computed(() => {
    if (isDetailMode.value || formOnlySubmitted.value)
      return false
    return props.formOnly || modalVisible.value || inlineWorkspaceVisible.value
  })

  const runtimeFormulaSignature = computed(() => {
    if (!runtimeFormulaCalculationEnabled.value || !runtimeFormulaFields.value.length)
      return ''
    return JSON.stringify(runtimeFormulaFields.value.map((field) => {
      const dependsOn = getRuntimeFormulaDependsOn(field.config, field.field)
      return {
        field: field.field,
        expression: field.config.expression || '',
        condition: field.config.condition || null,
        values: dependsOn.map(dep => [dep, formData.value?.[dep]]),
      }
    }))
  })

  function normalizeRuntimeFormulaField(field) {
    if (!field?.field)
      return null
    const config = normalizeRuntimeFormulaConfig(field.formulaConfig)
    if (!config)
      return null
    const type = String(config.type || 'CALC').toUpperCase()
    if (type === 'AGGREGATE')
      return null
    if (type === 'CONDITIONAL') {
      const expression = config.condition?.expression || config.expression
      if (!expression)
        return null
      return {
        field: field.field,
        config: {
          ...config,
          type,
          expression,
          condition: {
            ...(config.condition || {}),
            expression,
          },
        },
      }
    }
    if (!config.expression)
      return null
    return {
      field: field.field,
      config: {
        ...config,
        type,
      },
    }
  }

  function normalizeRuntimeFormulaConfig(raw) {
    if (!raw)
      return null
    if (typeof raw === 'string') {
      try {
        return JSON.parse(raw)
      }
      catch {
        return null
      }
    }
    if (typeof raw !== 'object')
      return null
    return { ...raw }
  }

  function getRuntimeFormulaDependsOn(config = {}, targetField = '') {
    const result = []
    appendRuntimeDependsOn(result, config.dependsOn)
    appendRuntimeExpressionVariables(result, config.expression)
    if (String(config.type || '').toUpperCase() === 'CONDITIONAL')
      appendRuntimeExpressionVariables(result, config.condition?.expression)
    return result.filter(field => field !== targetField)
  }

  function appendRuntimeDependsOn(result, dependsOn) {
    ;(Array.isArray(dependsOn) ? dependsOn : []).forEach(value => appendRuntimeFormulaVariable(result, value))
  }

  function appendRuntimeExpressionVariables(result, expression) {
    extractRuntimeFormulaVariables(expression).forEach(value => appendRuntimeFormulaVariable(result, value))
  }

  function appendRuntimeFormulaVariable(result, value) {
    const field = String(value || '').trim()
    if (field && !result.includes(field))
      result.push(field)
  }

  function extractRuntimeFormulaVariables(expression) {
    const text = stripRuntimeFormulaStringLiterals(expression)
    if (!text)
      return []
    const variables = []
    const pattern = /[a-z_]\w*/gi
    let match = pattern.exec(text)
    while (match) {
      const token = match[0]
      const previous = text[match.index - 1] || ''
      const nextIndex = match.index + token.length
      const nextText = text.slice(nextIndex).trimStart()
      if (!isRuntimeFormulaReservedToken(token) && previous !== '.' && !nextText.startsWith('(') && !variables.includes(token))
        variables.push(token)
      match = pattern.exec(text)
    }
    return variables
  }

  function stripRuntimeFormulaStringLiterals(expression) {
    return String(expression || '').replace(/'[^']*'|"[^"]*"/g, ' ')
  }

  function isRuntimeFormulaReservedToken(token) {
    return [
      'true',
      'false',
      'null',
      'nil',
      'and',
      'or',
      'not',
      'if',
      'else',
      'return',
      'math',
      'string',
      'seq',
      'date',
    ].includes(String(token || '').toLowerCase())
  }

  function scheduleRuntimeFormulaCalculation(formulaField, delay = 180) {
    if (!formulaField?.field || !runtimeFormulaCalculationEnabled.value)
      return
    const field = formulaField.field
    if (formulaRuntimeTimers.has(field))
      clearTimeout(formulaRuntimeTimers.get(field))
    formulaRuntimeTimers.set(field, setTimeout(() => {
      formulaRuntimeTimers.delete(field)
      calculateRuntimeFormula(formulaField)
    }, delay))
  }

  function clearRuntimeFormulaTimers() {
    formulaRuntimeTimers.forEach(timer => clearTimeout(timer))
    formulaRuntimeTimers.clear()
  }

  function refreshRuntimeFormulas(delay = 0) {
    if (!runtimeFormulaCalculationEnabled.value)
      return
    runtimeFormulaFields.value.forEach(field => scheduleRuntimeFormulaCalculation(field, delay))
  }

  async function calculateRuntimeFormula(formulaField) {
    if (!formulaField?.field || !runtimeFormulaCalculationEnabled.value)
      return
    const dependsOn = getRuntimeFormulaDependsOn(formulaField.config, formulaField.field)
    if (dependsOn.some(dep => isEmptyRuntimeFormulaValue(formData.value?.[dep]))) {
      patchRuntimeFormulaValue(formulaField.field, null)
      return
    }
    const sequence = (formulaRuntimeSequences.get(formulaField.field) || 0) + 1
    formulaRuntimeSequences.set(formulaField.field, sequence)
    try {
      const response = await previewFormula(buildRuntimeFormulaPreviewPayload(formulaField))
      if (!runtimeFormulaCalculationEnabled.value || formulaRuntimeSequences.get(formulaField.field) !== sequence)
        return
      const result = response?.data ?? response
      if (result?.success) {
        patchRuntimeFormulaValue(formulaField.field, result.result)
      }
      else if (result?.errorMessage) {
        console.warn(`[AiCrudPage] 公式计算失败: ${formulaField.field}`, result.errorMessage)
      }
    }
    catch (error) {
      if (runtimeFormulaCalculationEnabled.value && formulaRuntimeSequences.get(formulaField.field) === sequence)
        console.warn(`[AiCrudPage] 公式计算请求失败: ${formulaField.field}`, error)
    }
  }

  function buildRuntimeFormulaPreviewPayload(formulaField) {
    const config = formulaField.config || {}
    const type = String(config.type || 'CALC').toUpperCase()
    const payload = {
      expression: config.expression,
      type,
      dependsOn: getRuntimeFormulaDependsOn(config, formulaField.field),
      sampleValues: buildRuntimeFormulaSampleValues(formulaField),
    }
    if (type === 'CONDITIONAL') {
      const condition = config.condition || {}
      payload.condition = {
        expression: condition.expression || config.expression,
        trueValue: condition.trueValue ?? '',
        falseValue: condition.falseValue ?? '',
      }
      payload.expression = payload.condition.expression
    }
    return payload
  }

  function buildRuntimeFormulaSampleValues(formulaField) {
    const values = { ...(formData.value || {}) }
    getRuntimeFormulaDependsOn(formulaField.config, formulaField.field).forEach((field) => {
      values[field] = normalizeRuntimeFormulaSampleValue(
        values[field],
        runtimeFormFieldMap.value.get(field),
        formulaField.config,
        field,
      )
    })
    return values
  }

  function normalizeRuntimeFormulaSampleValue(value, field = {}, config = {}, fieldName = '') {
    if (isEmptyRuntimeFormulaValue(value))
      return value
    if (typeof value !== 'string')
      return value
    if (!isRuntimeNumericField(field) && !isRuntimeNumericFormulaOperand(config, fieldName))
      return value
    const text = value.trim()
    if (!/^-?\d+(?:\.\d+)?$/.test(text))
      return value
    const number = Number(text)
    return Number.isFinite(number) ? number : value
  }

  function isRuntimeNumericField(field = {}) {
    const type = String(field.type || field.componentType || '').toLowerCase()
    const dataType = String(field.dataType || field.fieldDataType || field.props?.dataType || '').toLowerCase()
    return isNumberFieldType(type)
      || ['int', 'integer', 'bigint', 'decimal', 'double', 'float', 'number'].includes(dataType)
  }

  function isRuntimeNumericFormulaOperand(config = {}, fieldName = '') {
    if (!fieldName)
      return false
    if (isRuntimeNumericLikeFieldName(fieldName))
      return true
    if (isRuntimeArithmeticOperand(config.expression, fieldName))
      return true
    return String(config.type || '').toUpperCase() === 'CONDITIONAL'
      && isRuntimeArithmeticOperand(config.condition?.expression, fieldName)
  }

  function isRuntimeNumericLikeFieldName(fieldName = '') {
    const value = String(fieldName || '').toLowerCase()
    return [
      'qty',
      'quantity',
      'count',
      'num',
      'number',
      'price',
      'amount',
      'money',
      'fee',
      'cost',
      'total',
      'rate',
      'ratio',
      'percent',
      'discount',
      'tax',
      'score',
      'weight',
      'area',
      'volume',
    ].some(keyword => value.includes(keyword))
  }

  function isRuntimeArithmeticOperand(expression, fieldName = '') {
    const text = stripRuntimeFormulaStringLiterals(expression)
    if (!text || !fieldName)
      return false
    const tokenPattern = new RegExp(`(^|[^\\w.])${escapeRuntimeFormulaRegex(fieldName)}(?=$|[^\\w])`, 'g')
    const arithmeticOperators = new Set(['*', '/', '%', '-'])
    let match = tokenPattern.exec(text)
    while (match) {
      const tokenStart = match.index + match[1].length
      const tokenEnd = tokenStart + fieldName.length
      const previous = findRuntimeFormulaAdjacentOperator(text, tokenStart, -1)
      const next = findRuntimeFormulaAdjacentOperator(text, tokenEnd, 1)
      if (arithmeticOperators.has(previous) || arithmeticOperators.has(next))
        return true
      match = tokenPattern.exec(text)
    }
    return false
  }

  function findRuntimeFormulaAdjacentOperator(text = '', startIndex = 0, direction = 1) {
    let index = startIndex + (direction < 0 ? -1 : 0)
    while (index >= 0 && index < text.length) {
      const char = text[index]
      if (!/\s/.test(char))
        return char
      index += direction < 0 ? -1 : 1
    }
    return ''
  }

  function escapeRuntimeFormulaRegex(value = '') {
    return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  }

  function patchRuntimeFormulaValue(field, value) {
    if (!field)
      return
    if (Object.is(formData.value?.[field], value))
      return
    formData.value = {
      ...(formData.value || {}),
      [field]: value,
    }
  }

  function isEmptyRuntimeFormulaValue(value) {
    if (Array.isArray(value))
      return value.length === 0
    return value === null || value === undefined || value === ''
  }

  return {
    runtimeFormulaFields,
    runtimeFormulaCalculationEnabled,
    runtimeFormulaSignature,
    scheduleRuntimeFormulaCalculation,
    refreshRuntimeFormulas,
    clearRuntimeFormulaTimers,
  }
}
