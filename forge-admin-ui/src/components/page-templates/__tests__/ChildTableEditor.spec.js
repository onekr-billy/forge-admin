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

  it('lets AiFormItem inherit class/style onto n-form-item instead of a fragment root', () => {
    expect(aiFormItemSource).toContain('defineOptions({ inheritAttrs: false })')
    expect(aiFormItemSource).toMatch(/<n-form-item[\s\S]*v-bind="\$attrs"/)
  })
})
