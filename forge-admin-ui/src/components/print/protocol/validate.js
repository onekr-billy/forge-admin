import { ELEMENT_TYPES, FORMAT_TYPES, PRINT_LIMITS, PRINT_PROTOCOL, PRINT_SCHEMA_VERSION, PrintError, SECTION_KINDS } from './types'
import { paperGeometry } from './units'

const DANGEROUS_KEYS = new Set(['__proto__', 'prototype', 'constructor'])
const IDENTIFIER = /^[\w-]{1,80}$/
const COLOR = /^#[\da-f]{3}(?:[\da-f]{3})?$/i
const STYLE_KEYS = ['fontFamily', 'fontSizePt', 'fontWeight', 'fontStyle', 'textAlign', 'lineHeight', 'color', 'backgroundColor', 'borderColor', 'borderWidthMm', 'paddingMm', 'textDecoration']
const SAFE_SYSTEM_PATHS = new Set(['system.generatedAt', 'system.pageNumber', 'system.totalPages'])

export function isSafeFieldPath(path) {
  return typeof path === 'string' && path.length <= 300
    && /^[a-z_$][\w$]*(?:\.[a-z_$][\w$]*)*$/i.test(path)
    && path.split('.').every(segment => !DANGEROUS_KEYS.has(segment))
}

export function isSafeImageReference(value) {
  return typeof value === 'string'
    && (/^[\w-]{1,128}$/.test(value)
      || (/^data:image\/(?:png|jpeg|webp);base64,[a-z\d+/]+=*$/i.test(value) && value.length <= PRINT_LIMITS.inlineImageBytes * 4 / 3))
}

/** Validate without mutating, coercing or silently discarding unsupported data. */
export function validatePrintDocument(document) {
  const issues = []
  const ids = new Set()
  let elementCount = 0
  const issue = (path, code, message) => issues.push({ path, code, message })
  const number = (value, path, min = 0, max = PRINT_LIMITS.paperSizeMm) => {
    if (typeof value !== 'number' || !Number.isFinite(value) || value < min || value > max) {
      issue(path, 'INVALID_NUMBER', `数值须在 ${min} 至 ${max} 之间`)
      return false
    }
    return true
  }
  const choice = (value, values, path) => {
    if (!values.includes(value)) {
      issue(path, 'UNSUPPORTED_VALUE', '不支持此配置值')
    }
  }
  const object = (value, keys, path) => {
    if (!value || typeof value !== 'object' || Array.isArray(value) || ![Object.prototype, null].includes(Object.getPrototypeOf(value))) {
      issue(path, 'INVALID_OBJECT', '必须是普通 JSON 对象')
      return false
    }
    for (const key of Object.keys(value)) {
      if (!keys.includes(key)) {
        issue(`${path ? `${path}.` : ''}${key}`, 'UNKNOWN_PROPERTY', '不支持此属性')
      }
    }
    return true
  }
  const array = (value, path, max) => {
    if (!Array.isArray(value) || value.length > max) {
      issue(path, 'INVALID_ARRAY', `必须是最多 ${max} 项的数组`)
      return false
    }
    return true
  }
  const identifier = (value, path) => {
    if (typeof value !== 'string' || !IDENTIFIER.test(value)) {
      issue(path, 'INVALID_ID', '元素标识无效')
    }
    else if (ids.has(value)) {
      issue(path, 'DUPLICATE_ID', '元素标识重复')
    }
    else {
      ids.add(value)
    }
  }
  function style(value, path) {
    if (value === undefined || !object(value, STYLE_KEYS, path)) {
      return
    }
    for (const [key, item] of Object.entries(value)) {
      const location = `${path}.${key}`
      if (['color', 'backgroundColor', 'borderColor'].includes(key) && !COLOR.test(item)) {
        issue(location, 'INVALID_COLOR', '颜色须使用十六进制格式')
      }
      if (key === 'fontFamily' && (typeof item !== 'string' || !/^[\p{L}\p{N} ,_-]{1,100}$/u.test(item))) {
        issue(location, 'INVALID_FONT', '字体名称无效')
      }
      if (key === 'fontSizePt') {
        number(item, location, 6, 144)
      }
      if (key === 'lineHeight') {
        number(item, location, 1, 4)
      }
      if (key === 'paddingMm') {
        number(item, location, 0, 20)
      }
      if (key === 'borderWidthMm') {
        number(item, location, 0, 3)
      }
      if (key === 'fontWeight') {
        choice(item, [400, 700], location)
      }
      if (key === 'fontStyle') {
        choice(item, ['normal', 'italic'], location)
      }
      if (key === 'textAlign') {
        choice(item, ['left', 'center', 'right'], location)
      }
      if (key === 'textDecoration') {
        choice(item, ['none', 'underline'], location)
      }
    }
  }
  function format(value, path) {
    if (value === undefined || !object(value, ['type', 'scale', 'emptyText', 'trueText', 'falseText', 'datePattern'], path)) {
      return
    }
    choice(value.type, FORMAT_TYPES, `${path}.type`)
    if (value.scale !== undefined) {
      number(value.scale, `${path}.scale`, 0, 6)
      if (!Number.isInteger(value.scale)) {
        issue(`${path}.scale`, 'INVALID_SCALE', '精度必须为整数')
      }
    }
    for (const key of ['emptyText', 'trueText', 'falseText']) {
      if (value[key] !== undefined && (typeof value[key] !== 'string' || value[key].length > 100)) {
        issue(`${path}.${key}`, 'INVALID_TEXT', '格式文案过长或类型不正确')
      }
    }
    if (value.datePattern !== undefined) {
      choice(value.datePattern, ['YYYY-MM-DD', 'YYYY-MM-DD HH:mm', 'YYYY-MM-DD HH:mm:ss'], `${path}.datePattern`)
    }
  }
  function binding(value, path, image = false, fixedText = false) {
    if (!object(value, ['source', 'path', 'value'], path)) {
      return
    }
    choice(value.source, ['CONSTANT', 'FIELD', 'SYSTEM'], `${path}.source`)
    if (value.source === 'CONSTANT') {
      if (value.path !== undefined) {
        issue(path, 'CONFLICTING_BINDING', '固定值不能同时声明字段路径')
      }
      if (value.value !== null && !['string', 'number', 'boolean'].includes(typeof value.value)) {
        issue(`${path}.value`, 'INVALID_CONSTANT', '固定值必须是文本、数字、布尔值或 null')
      }
      if (typeof value.value === 'number' && !Number.isFinite(value.value)) {
        issue(path, 'INVALID_CONSTANT', '数字必须有限')
      }
      if (typeof value.value === 'string' && value.value.length > PRINT_LIMITS.textLength) {
        issue(path, 'TEXT_TOO_LONG', '文本超过限制')
      }
      if (image && value.value && !isSafeImageReference(value.value)) {
        issue(path, 'INVALID_RESOURCE', '图片须引用受控文件或支持的内联图片')
      }
    }
    else if (!isSafeFieldPath(value.path) || (value.source === 'SYSTEM' && !SAFE_SYSTEM_PATHS.has(value.path))) {
      issue(`${path}.path`, 'INVALID_FIELD_PATH', '字段路径无效')
    }
    if (value.source !== 'CONSTANT' && value.value !== undefined) {
      issue(path, 'CONFLICTING_BINDING', '字段绑定不能同时声明固定值')
    }
    if (value.source === 'FIELD' && value.path?.startsWith('system.')) {
      issue(path, 'INVALID_FIELD_PATH', '系统变量须使用 SYSTEM 绑定')
    }
    if (!fixedText && value.source === 'SYSTEM' && ['system.pageNumber', 'system.totalPages'].includes(value.path)) {
      issue(path, 'INVALID_PAGE_NUMBER_BINDING', '页码须绑定固定文本或使用页码元素')
    }
  }
  function element(value, path, width, height) {
    if (!object(value, ['id', 'type', 'xMm', 'yMm', 'widthMm', 'heightMm', 'binding', 'format', 'style', 'barcodeFormat', 'pageNumberFormat'], path)) {
      return
    }
    identifier(value.id, `${path}.id`)
    elementCount++
    choice(value.type, ELEMENT_TYPES, `${path}.type`)
    const dimensions = ['xMm', 'yMm', 'widthMm', 'heightMm'].map(key => number(value[key], `${path}.${key}`, key.startsWith('width') || key.startsWith('height') ? 0.1 : 0))
    if (dimensions.every(Boolean) && (value.xMm + value.widthMm > width + 0.001 || value.yMm + value.heightMm > height + 0.001)) {
      issue(path, 'OUT_OF_BOUNDS', '元素超出所属区块')
    }
    if (['TEXT', 'IMAGE', 'BARCODE', 'QRCODE'].includes(value.type)) {
      binding(value.binding, `${path}.binding`, value.type === 'IMAGE', value.type === 'TEXT')
    }
    if (value.barcodeFormat !== undefined) {
      choice(value.barcodeFormat, ['CODE128', 'CODE39', 'EAN13', 'EAN8', 'ITF14'], `${path}.barcodeFormat`)
    }
    if (value.pageNumberFormat !== undefined) {
      choice(value.pageNumberFormat, ['CURRENT', 'CURRENT_TOTAL'], `${path}.pageNumberFormat`)
    }
    style(value.style, `${path}.style`)
    format(value.format, `${path}.format`)
  }
  function band(value, path, width) {
    if (!object(value, ['heightMm', 'repeat', 'elements'], path)) {
      return
    }
    number(value.heightMm, `${path}.heightMm`)
    choice(value.repeat, [true, false], `${path}.repeat`)
    if (array(value.elements, `${path}.elements`, PRINT_LIMITS.elements)) {
      value.elements.forEach((item, index) => element(item, `${path}.elements[${index}]`, width, value.heightMm))
    }
  }
  function table(value, path, width) {
    if (!isSafeFieldPath(value.collectionPath)) {
      issue(`${path}.collectionPath`, 'INVALID_FIELD_PATH', '明细路径无效')
    }
    choice(value.repeatHeader, [true, false], `${path}.repeatHeader`)
    if (!array(value.columns, `${path}.columns`, PRINT_LIMITS.columns)) {
      return
    }
    if (!value.columns.length) {
      issue(path, 'EMPTY_COLUMNS', '表格至少需要一列')
    }
    let columnWidth = 0
    value.columns.forEach((column, index) => {
      const location = `${path}.columns[${index}]`
      if (!object(column, ['id', 'field', 'title', 'widthMm', 'format', 'style'], location)) {
        return
      }
      identifier(column.id, `${location}.id`)
      if (!isSafeFieldPath(column.field)) {
        issue(location, 'INVALID_FIELD_PATH', '明细字段无效')
      }
      if (typeof column.title !== 'string' || column.title.length > 200) {
        issue(location, 'INVALID_TITLE', '列标题无效')
      }
      if (number(column.widthMm, `${location}.widthMm`, 1)) {
        columnWidth += column.widthMm
      }
      format(column.format, `${location}.format`)
      style(column.style, `${location}.style`)
    })
    if (columnWidth > width + 0.001) {
      issue(path, 'OUT_OF_BOUNDS', '表格列宽超过纸张正文宽度')
    }
    if (value.headerRows !== undefined && array(value.headerRows, `${path}.headerRows`, 10)) {
      value.headerRows.forEach((row, index) => cells(row, `${path}.headerRows[${index}]`, value.columns.length, false))
    }
    if (value.footer !== undefined) {
      cells(value.footer, `${path}.footer`, value.columns.length, true)
    }
    if (value.emptyText !== undefined && (typeof value.emptyText !== 'string' || value.emptyText.length > 500)) {
      issue(`${path}.emptyText`, 'INVALID_TEXT', '空明细文案须为不超过 500 字的文本')
    }
  }
  function cells(row, path, columns, hasBinding) {
    if (!object(row, ['cells'], path) || !array(row.cells, `${path}.cells`, PRINT_LIMITS.columns)) {
      return
    }
    let spans = 0
    row.cells.forEach((cell, index) => {
      const location = `${path}.cells[${index}]`
      if (!object(cell, hasBinding ? ['binding', 'span', 'format', 'style'] : ['text', 'span', 'style'], location)) {
        return
      }
      if (!Number.isInteger(cell.span) || cell.span < 1 || cell.span > columns) {
        issue(location, 'INVALID_SPAN', '单元格跨度无效')
      }
      spans += cell.span
      if (hasBinding) {
        binding(cell.binding, `${location}.binding`)
        format(cell.format, `${location}.format`)
      }
      else if (typeof cell.text !== 'string' || cell.text.length > 500) {
        issue(location, 'INVALID_TEXT', '表头文案无效')
      }
      style(cell.style, `${location}.style`)
    })
    if (spans !== columns) {
      issue(path, 'INVALID_SPAN', '表头/表尾跨度必须覆盖所有列')
    }
  }

  try {
    const json = JSON.stringify(document, (key, value) => {
      if (DANGEROUS_KEYS.has(key) || ['function', 'symbol', 'bigint'].includes(typeof value)) {
        throw new Error('非法 JSON 值')
      }
      return value
    })
    if (!json || new TextEncoder().encode(json).byteLength > PRINT_LIMITS.documentBytes) {
      throw new Error('模板体积超过限制')
    }
  }
  catch {
    return [{ code: 'INVALID_JSON', path: '', message: '模板必须是大小受限、无循环引用的 JSON' }]
  }
  if (!object(document, ['protocol', 'schemaVersion', 'paper', 'header', 'body', 'footer', 'resources'], '')) {
    return issues
  }
  choice(document.protocol, [PRINT_PROTOCOL], 'protocol')
  choice(document.schemaVersion, [PRINT_SCHEMA_VERSION], 'schemaVersion')
  const { paper } = document
  if (!object(paper, ['widthMm', 'heightMm', 'orientation', 'marginMm'], 'paper')) {
    return issues
  }
  number(paper.widthMm, 'paper.widthMm', 10)
  number(paper.heightMm, 'paper.heightMm', 10)
  choice(paper.orientation, ['PORTRAIT', 'LANDSCAPE'], 'paper.orientation')
  if (!object(paper.marginMm, ['top', 'right', 'bottom', 'left'], 'paper.marginMm')) {
    return issues
  }
  for (const key of ['top', 'right', 'bottom', 'left']) {
    number(paper.marginMm[key], `paper.marginMm.${key}`)
  }
  const horizontal = paper.orientation === 'LANDSCAPE' ? Math.max(paper.widthMm, paper.heightMm) : Math.min(paper.widthMm, paper.heightMm)
  const width = horizontal - paper.marginMm.left - paper.marginMm.right
  band(document.header, 'header', width)
  band(document.footer, 'footer', width)
  if (document.header && document.footer) {
    const geometry = paperGeometry(document)
    if (!(geometry.contentHeightMm > 0 && geometry.contentWidthMm > 0)) {
      issue('paper', 'NO_PRINTABLE_AREA', '页边距、页眉和页脚未留下可打印正文')
    }
  }
  if (array(document.body, 'body', PRINT_LIMITS.sections)) {
    document.body.forEach((section, index) => {
      const location = `body[${index}]`
      if (!object(section, ['id', 'kind', 'heightMm', 'elements', 'binding', 'format', 'style', 'gapAfterMm', 'keepWithNext', 'collectionPath', 'columns', 'headerRows', 'repeatHeader', 'footer', 'emptyText'], location)) {
        return
      }
      identifier(section.id, `${location}.id`)
      choice(section.kind, SECTION_KINDS, `${location}.kind`)
      if (section.gapAfterMm !== undefined) {
        number(section.gapAfterMm, `${location}.gapAfterMm`, 0, 100)
      }
      if (section.keepWithNext !== undefined) {
        choice(section.keepWithNext, [true, false], `${location}.keepWithNext`)
      }
      style(section.style, `${location}.style`)
      if (section.kind === 'FIXED') {
        number(section.heightMm, `${location}.heightMm`, 0.1)
        if (array(section.elements, `${location}.elements`, PRINT_LIMITS.elements)) {
          section.elements.forEach((item, elementIndex) => element(item, `${location}.elements[${elementIndex}]`, width, section.heightMm))
        }
      }
      if (section.kind === 'TEXT') {
        binding(section.binding, `${location}.binding`)
        format(section.format, `${location}.format`)
      }
      if (section.kind === 'TABLE') {
        table(section, location, width)
      }
    })
  }
  if (elementCount > PRINT_LIMITS.elements) {
    issue('body', 'TOO_MANY_ELEMENTS', '元素总数超过限制')
  }
  if (array(document.resources, 'resources', 100)) {
    document.resources.forEach((resource, index) => {
      const location = `resources[${index}]`
      if (!object(resource, ['id', 'fileId'], location)) {
        return
      }
      identifier(resource.id, `${location}.id`)
      if (typeof resource.fileId !== 'string' || !/^[\w-]{1,128}$/.test(resource.fileId)) {
        issue(location, 'INVALID_RESOURCE', '资源必须使用文件标识')
      }
    })
  }
  return issues
}

export function assertPrintDocument(document) {
  const issues = validatePrintDocument(document)
  if (issues.length) {
    throw new PrintError('INVALID_TEMPLATE', issues[0].message, issues[0].path, issues)
  }
  return document
}
