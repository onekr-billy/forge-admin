import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const childTableEditorSource = readFileSync(resolve('src/components/page-templates/ChildTableEditor.vue'), 'utf8')
const aiFormItemSource = readFileSync(resolve('src/components/ai-form/AiFormItem.vue'), 'utf8')

describe('child table editor runtime cells', () => {
  it('does not fall class through onto fragment-rooted AiFormItem', () => {
    expect(childTableEditorSource).toContain('class="child-runtime-cell"')
    expect(childTableEditorSource).not.toMatch(/<AiFormItem[\s\S]*?class="child-runtime-cell"/)
    expect(childTableEditorSource).toMatch(/<div v-if="useRuntimeCell\(field, child\)" class="child-runtime-cell">/)
  })

  it('keeps editor value identity when parent echoes the same child rows', () => {
    expect(childTableEditorSource).toContain('function isSameEditorValue')
    expect(childTableEditorSource).toContain('if (isSameEditorValue(localValue.value, next))')
    expect(childTableEditorSource).toContain('previousRows[index]?.__rowKey')
  })

  it('sanitizes controlled props for every inline child-table control', () => {
    expect(childTableEditorSource).not.toContain('v-bind="field.props"')
    expect(childTableEditorSource).toContain('v-bind="resolveInputProps(field)"')
    expect(childTableEditorSource).toContain('child.allowCreate !== false')
    expect(childTableEditorSource).toContain('function isCellReadonly(child, row, field = {})')
    expect(childTableEditorSource).toContain('hasPersistedRowId(row) ? !canUpdateRows(child) : !canCreateRows(child)')
    expect(childTableEditorSource).toContain('child.modelCode || child.relationKey || child.key || child.tableName')
  })

  it('keeps optionSource when building AiFormItem runtime cell field', () => {
    expect(childTableEditorSource).toContain('function mergeChildFieldRuntimeProps')
    expect(childTableEditorSource).toContain('禁止在这里套 resolveControlProps')
    expect(childTableEditorSource).toContain("'switch'")
    expect(childTableEditorSource).toContain('min-width: 160px')
    expect(childTableEditorSource).toContain('preloadChildQuerySourceOptions')
    expect(childTableEditorSource).toContain('allowOptionSourceFetch: true')
    expect(childTableEditorSource).toContain('只要配了动态选项源，一律走 AiFormItem')
    expect(childTableEditorSource).toContain('[forge-child-select] ChildTableEditor setup v4')
    expect(childTableEditorSource).toContain('[forge-child-select] module imported v4')
  })

  it('coerces string optionSource.params before QUERY_SOURCE execute', () => {
    expect(aiFormItemSource).toContain('params 可能是 JSON 字符串')
    expect(aiFormItemSource).toContain('if (typeof next.params === \'string\')')
    expect(aiFormItemSource).toContain('sourceType === \'BUSINESS_OBJECT\'')
  })

  it('routes user/org aliases through AiFormItem runtime cells', () => {
    expect(childTableEditorSource).toContain('isUserSelectLikeField(field)')
    expect(childTableEditorSource).toContain('isOrgSelectLikeField(field)')
    expect(childTableEditorSource).toContain("type = 'userSelect'")
    expect(childTableEditorSource).toContain("type = 'orgTreeSelect'")
  })
})
