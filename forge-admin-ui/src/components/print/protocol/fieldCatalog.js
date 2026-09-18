/** Return field requirements with locations; table columns use collection-relative paths. */
export function collectFieldBindings(document) {
  const fields = []
  const binding = (value, location) => {
    if (value?.source === 'FIELD') {
      fields.push({ path: value.path, location, collection: false })
    }
  }
  const elements = (items, location) => items.forEach((item, i) => binding(item.binding, `${location}[${i}].binding`))
  elements(document.header.elements, 'header.elements')
  elements(document.footer.elements, 'footer.elements')
  document.body.forEach((section, i) => {
    const location = `body[${i}]`
    binding(section.binding, `${location}.binding`)
    elements(section.elements || [], `${location}.elements`)
    if (section.kind === 'TABLE') {
      fields.push({ path: section.collectionPath, location, collection: true })
      section.columns.forEach((column, j) => fields.push({ path: `${section.collectionPath}.${column.field}`, location: `${location}.columns[${j}]`, collection: false }))
      section.footer?.cells.forEach((cell, j) => binding(cell.binding, `${location}.footer.cells[${j}]`))
    }
  })
  return fields
}

export function validateFieldCatalog(document, catalog) {
  const allowed = new Map(catalog.map(field => [field.path, field]))
  return collectFieldBindings(document).flatMap((field) => {
    const entry = allowed.get(field.path)
    if (!entry || (field.collection && entry.type !== 'COLLECTION') || (!field.collection && entry.type === 'COLLECTION')) {
      return [{ path: field.location, field: field.path, code: 'FIELD_NOT_ALLOWED', message: `字段不可用：${field.path}` }]
    }
    return []
  })
}
