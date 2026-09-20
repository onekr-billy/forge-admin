import { validateFieldCatalog } from '../protocol/fieldCatalog'
import { PRINT_LIMITS, PrintError } from '../protocol/types'
import { paperGeometry } from '../protocol/units'
import { assertPrintDocument } from '../protocol/validate'
import { expandDataTablesForLayout } from './expandDataTables'
import { createPageCursor } from './pageGeometry'
import { fillPageNumbers } from './pageNumbers'
import { prepareElements, prepareSection } from './prepare'
import { paginateTable, tableMinimumHeight } from './tablePagination'
import { paginateText } from './textPagination'

function freeze(value) {
  if (value && typeof value === 'object' && !Object.isFrozen(value)) {
    Object.values(value).forEach(freeze)
    Object.freeze(value)
  }
  return value
}

function minimumHeight(section) {
  if (!section) {
    return 0
  }
  if (section.kind === 'TEXT') {
    return section.lineHeightMm + 2 * section.insetMm
  }
  return section.kind === 'TABLE' ? tableMinimumHeight(section) : section.heightMm
}

export function layoutPrintDocument(input, context, { measure, resources, catalog = [], templateVersion = null } = {}) {
  // Clone first so expand never mutates the editable template.
  const document = JSON.parse(JSON.stringify(input))
  expandDataTablesForLayout(document)
  assertPrintDocument(document)
  const issues = validateFieldCatalog(document, catalog)
  if (issues.length) {
    throw new PrintError('FIELD_NOT_ALLOWED', issues[0].message, issues[0].path, issues)
  }
  const geometry = paperGeometry(document)
  const header = prepareElements(document.header.elements, context, measure, resources)
  const footer = prepareElements(document.footer.elements, context, measure, resources)
  const sections = document.body.map(section => prepareSection(section, context, measure, geometry, resources, catalog))
  if (sections.reduce((sum, section) => sum + (section.sourceRowCount || 0), 0) > PRINT_LIMITS.rows) {
    throw new PrintError('ROW_LIMIT', `所有明细合计最多 ${PRINT_LIMITS.rows} 行`)
  }
  const cursor = createPageCursor(geometry)
  function requiredStart(index) {
    const section = sections[index]
    if (!section) {
      return 0
    }
    if (section.keepWithNext && index + 1 < sections.length) {
      return section.heightMm + (section.gapAfterMm || 0) + requiredStart(index + 1)
    }
    return minimumHeight(section)
  }
  sections.forEach((section, index) => {
    if (section.kind === 'PAGE_BREAK') {
      cursor.forceBreak()
      return
    }
    if (section.keepWithNext && index + 1 < sections.length) {
      cursor.ensure(requiredStart(index), section.id)
    }
    if (section.kind === 'TEXT') {
      paginateText(section, cursor)
    }
    else if (section.kind === 'TABLE') {
      paginateTable(section, cursor)
    }
    else {
      cursor.place(section)
    }
    cursor.gap(section.gapAfterMm || 0)
  })
  const total = cursor.pages.length
  cursor.pages.forEach((page, index) => {
    page.number = index + 1
    page.header = { xMm: geometry.bodyLeftMm, yMm: document.paper.marginMm.top, elements: document.header.repeat || index === 0 ? fillPageNumbers(header, index + 1, total, measure) : [] }
    page.footer = { xMm: geometry.bodyLeftMm, yMm: geometry.footerTopMm, elements: document.footer.repeat || index === total - 1 ? fillPageNumbers(footer, index + 1, total, measure) : [] }
    page.fragments = page.fragments.map(fragment => fragment.kind === 'FIXED' ? { ...fragment, elements: fillPageNumbers(fragment.elements, index + 1, total, measure) } : fragment)
  })
  return freeze({ geometry, pages: cursor.pages, warnings: [], templateVersion, generatedAt: context.system?.generatedAt ?? new Date().toISOString(), dataMode: 'CURRENT' })
}
