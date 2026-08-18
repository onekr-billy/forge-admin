import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(process.cwd(), relativePath), 'utf8')
}

describe('application designer phase E layout contract', () => {
  it('removes the duplicate embedded object navigation without affecting standalone mode', () => {
    const source = readSource('src/views/app-center/components/designer/BusinessObjectDesignerShell.vue')

    expect(source).toContain('\'nav-hidden\': !showDesignerNavigation')
    expect(source).toContain('v-if="showDesignerNavigation" class="designer-nav"')
    expect(source).not.toContain('closureSteps')
    expect(source).not.toContain('closure-steps')
    expect(source).toContain('.designer-shell.embedded.nav-hidden .designer-workbench')
  })

  it('uses the resource tree as the only editing sidebar and delegates page creation', () => {
    const runtimeSource = readSource('src/views/app-center/application-runtime.[applicationCode].vue')
    const treeSource = readSource('src/views/app-center/components/ApplicationDesignerResourceTree.vue')

    expect(runtimeSource).toContain('@create-page="createQuickNode(\'page\')"')
    expect(runtimeSource).toContain('<aside v-if="!editing" class="runtime-navigation')
    expect(treeSource).toContain('emit(\'createPage\')')
  })

  it('renders the real application extension panel for the enhancement resource', () => {
    const source = readSource('src/views/app-center/application-runtime.[applicationCode].vue')

    expect(source).toContain('activeDesignerResource?.kind === \'automation-enhancements\'')
    expect(source).toContain('<ApplicationExtensionsPanel')
    expect(source).toContain(':initial-extensions="workspaceExtensions"')
  })

  it('routes the legacy enhancement guide to the real designer resource', () => {
    const source = readSource('src/views/app-center/application.[applicationCode].vue')

    expect(source).toContain('designerSection: \'automation-enhancements\'')
    expect(source).toContain('const directResource = section === \'automation-enhancements\'')
    expect(source).toContain('directResource ? { designResource: section }')
  })
})
