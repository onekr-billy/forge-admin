import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('page type selector object code validation', () => {
  const source = readFileSync(resolve('src/views/app-center/components/designer/PageTypeSelector.vue'), 'utf8')
  const apiSource = readFileSync(resolve('src/api/business-app.js'), 'utf8')

  it('checks object code availability through the backend before creating a data page', () => {
    expect(source).toContain('businessObjectCodeAvailable')
    expect(apiSource).toContain("'/ai/business/object/code-available'")
    expect(source).toContain('对象编码已存在，请换一个编码')
    expect(source).toContain('trigger: [\'blur\']')
  })

  it('keeps a visible checking state while the async validator is running', () => {
    expect(source).toContain('objectCodeChecking')
    expect(source).toContain(':loading="objectCodeChecking"')
  })
})
