import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { pageWidgetComponentKeys } from '@/components/lowcode-builder/shared/page-widget-schema'
import {
  FIELD_COMPONENT_DEFAULTS,
  FIELD_COMPONENT_PALETTE_GROUPS,
  FORM_FIELD_COMPONENT_KEYS,
  STRUCTURED_VALUE_COMPONENT_KEYS,
} from '../form-first/fieldComponentCatalog'

function readDesignerSource() {
  return fs.readFileSync(
    path.resolve(process.cwd(), 'src/views/app-center/components/designer/BusinessFormDesigner.vue'),
    'utf8',
  )
}

function readRuntimeFieldSource() {
  return fs.readFileSync(
    path.resolve(process.cwd(), 'src/components/ai-form/AiFormItem.vue'),
    'utf8',
  )
}

describe('business form runtime compilation contract', () => {
  it('defines a field model for every component displayed on the left shelf', () => {
    const shelfKeys = FIELD_COMPONENT_PALETTE_GROUPS.flatMap(group => group.items.map(item => item.componentKey))

    expect(shelfKeys).toHaveLength(33)
    expect(new Set(shelfKeys).size).toBe(shelfKeys.length)
    shelfKeys.forEach((componentKey) => {
      expect(FORM_FIELD_COMPONENT_KEYS.has(componentKey), componentKey).toBe(true)
      expect(FIELD_COMPONENT_DEFAULTS[componentKey], componentKey).toBeDefined()
      expect(FIELD_COMPONENT_DEFAULTS[componentKey].componentType, componentKey).toBeTruthy()
    })
  })

  it('uses stable numeric, date and structured-value storage contracts', () => {
    expect(FIELD_COMPONENT_DEFAULTS.slider).toMatchObject({ fieldType: 'NUMBER', dataType: 'int', componentType: 'slider' })
    expect(FIELD_COMPONENT_DEFAULTS.rate).toMatchObject({ fieldType: 'NUMBER', dataType: 'decimal', precision: 1 })
    expect(FIELD_COMPONENT_DEFAULTS.year).toMatchObject({ dataType: 'varchar', length: 4, componentType: 'year' })
    expect(FIELD_COMPONENT_DEFAULTS.daterange).toMatchObject({ dataType: 'text', componentType: 'daterange' })
    expect(FIELD_COMPONENT_DEFAULTS.transfer).toMatchObject({ fieldType: 'MULTI_SELECT', dataType: 'text' })
    expect(Array.from(STRUCTURED_VALUE_COMPONENT_KEYS).sort()).toEqual([
      'checkbox',
      'daterange',
      'datetimerange',
      'timerange',
      'transfer',
    ])
  })

  it('has an explicit runtime renderer for every field component on the left shelf', () => {
    const runtimeSource = readRuntimeFieldSource()
    const directTypes = FIELD_COMPONENT_PALETTE_GROUPS
      .flatMap(group => group.items.map(item => item.componentKey))
      .filter(componentKey => !['number', 'money', 'userSelect', 'orgTreeSelect'].includes(componentKey))

    directTypes.forEach((componentKey) => {
      expect(runtimeSource, componentKey).toContain(`field.type === '${componentKey}'`)
    })
    expect(runtimeSource).toContain('v-else-if="isNumberFieldType(field.type)"')
    expect(runtimeSource).toContain('v-else-if="isUserSelectField(field)"')
    expect(runtimeSource).toContain('v-else-if="isOrgTreeSelectField(field)"')
  })

  it('compiles and renders every standalone page widget', () => {
    const designerSource = readDesignerSource()
    const runtimeSource = fs.readFileSync(
      path.resolve(process.cwd(), 'src/components/ai-form/AiFormLayoutNodes.vue'),
      'utf8',
    )
    const standaloneWidgetKeys = pageWidgetComponentKeys.filter(componentKey => componentKey !== 'transfer')

    expect(standaloneWidgetKeys).toHaveLength(19)
    expect(designerSource).toContain('nodeType: \'widget\'')
    expect(designerSource).toContain('isPageWidgetComponentKey(componentKey)')
    expect(runtimeSource).toContain('<PageWidgetRenderer')
    expect(runtimeSource).toContain('isPageWidgetComponentKey(resolveWidgetKey(node))')
  })

  it('preserves the static hidden baseline for runtime visibility rules', () => {
    const source = readDesignerSource()
    const compiler = source.slice(
      source.indexOf('function buildRuntimeFormFieldSetting'),
      source.indexOf('function collectRuntimeFieldComponents'),
    )

    expect(compiler).toContain('setting.hidden = Boolean(component.visibility.hidden)')
    expect(compiler).toContain('setting.formVisible = !component.visibility.hidden')
  })
})
