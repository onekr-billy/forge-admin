import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

function readDesignerSource() {
  return fs.readFileSync(
    path.resolve(process.cwd(), 'src/views/app-center/components/designer/BusinessFormDesigner.vue'),
    'utf8',
  )
}

function readSourceBlock(source, startMarker, endMarker) {
  return source.slice(source.indexOf(startMarker), source.indexOf(endMarker))
}

describe('business form runtime compilation contract', () => {
  it('compiles barcode scanner fields into runtime field settings and layout', () => {
    const source = readDesignerSource()
    const fieldComponentKeys = readSourceBlock(source, 'const FORM_FIELD_COMPONENT_KEYS', 'const DICT_FIELD_TYPES')
    const componentDefaults = readSourceBlock(source, 'const COMPONENT_FIELD_DEFAULTS', 'const message =')

    expect(fieldComponentKeys).toContain('\'barcodeScanner\'')
    expect(componentDefaults).toContain('barcodeScanner:')
  })

  it('preserves the static hidden baseline for runtime visibility rules', () => {
    const source = readDesignerSource()
    const compiler = readSourceBlock(source, 'function buildRuntimeFormFieldSetting', 'function collectRuntimeFieldComponents')

    expect(compiler).toContain('setting.hidden = Boolean(component.visibility.hidden)')
    expect(compiler).toContain('setting.formVisible = !component.visibility.hidden')
  })
})
