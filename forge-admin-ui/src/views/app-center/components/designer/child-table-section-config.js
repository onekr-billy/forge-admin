export function upsertChildTableSectionConfig(source = {}, input = {}) {
  const pageSchema = cloneValue(source.pageSchema || {})
  const formDesignerSchema = cloneValue(source.formDesignerSchema || {})
  const config = normalizeChildTableConfig(input)
  if (!config.relationKey || !config.modelCode)
    return { pageSchema, formDesignerSchema }

  config.primaryModelCode = firstNonBlank(
    config.primaryModelCode,
    pageSchema.primaryModelCode,
    pageSchema.modelRefs?.find(ref => ref?.primary)?.modelCode,
  )
  pageSchema.layoutType = 'master-detail-crud'
  const modelRefs = Array.isArray(pageSchema.modelRefs) ? pageSchema.modelRefs : []
  const modelRefIndex = modelRefs.findIndex(ref => matchesRelation(ref, config))
  const currentModelRef = modelRefIndex >= 0 ? modelRefs[modelRefIndex] : {}
  const nextModelRef = {
    ...currentModelRef,
    modelCode: config.modelCode,
    modelName: config.modelName,
    tableName: config.tableName,
    primary: false,
    relations: resolveModelRefRelations(currentModelRef, config),
    fields: config.fields.map(field => normalizeModelRefField(field, config)),
    props: {
      ...(currentModelRef.props || {}),
      relationKey: config.relationKey,
      relationName: config.title,
      tabTitle: config.title,
      saveMode: 'CASCADE',
      inlineCreateEnabled: true,
      inlineEditEnabled: true,
      showInDetail: true,
    },
  }
  pageSchema.modelRefs = upsertAt(modelRefs, modelRefIndex, nextModelRef)

  const options = pageSchema.options && typeof pageSchema.options === 'object' ? pageSchema.options : {}
  const masterDetailConfig = options.masterDetailConfig && typeof options.masterDetailConfig === 'object'
    ? options.masterDetailConfig
    : {}
  const children = Array.isArray(masterDetailConfig.children) ? masterDetailConfig.children : []
  const childIndex = children.findIndex(child => matchesRelation(child, config))
  const currentChild = childIndex >= 0 ? children[childIndex] : {}
  const nextChild = {
    ...currentChild,
    key: config.relationKey,
    relationKey: config.relationKey,
    modelCode: config.modelCode,
    modelName: config.modelName,
    tableName: config.tableName,
    fields: config.fields.map(field => normalizeRuntimeChildField(field)),
    saveMode: 'CASCADE',
    showInCreate: true,
    showInEdit: true,
    showInDetail: true,
    inlineCreateEnabled: true,
    inlineEditEnabled: true,
    rowActions: Array.isArray(currentChild.rowActions) ? currentChild.rowActions : [],
    toolbarActions: Array.isArray(currentChild.toolbarActions) ? currentChild.toolbarActions : [],
  }
  pageSchema.options = {
    ...options,
    masterDetailConfig: {
      ...masterDetailConfig,
      children: upsertAt(children, childIndex, nextChild),
    },
  }

  const pageSections = Array.isArray(formDesignerSchema.pageSections) ? formDesignerSchema.pageSections : []
  const sectionIndex = pageSections.findIndex(section => matchesRelation(section, config))
  const currentSection = sectionIndex >= 0 ? pageSections[sectionIndex] : {}
  const sectionId = currentSection.sectionId || currentSection.sectionKey || `child_${safeKey(config.relationKey)}`
  const nextSection = {
    ...currentSection,
    sectionId,
    sectionKey: sectionId,
    sectionType: 'child_table',
    type: 'child_table',
    title: config.title,
    displayMode: config.displayMode,
    relationKey: config.relationKey,
    visibleInModes: Array.isArray(currentSection.visibleInModes)
      ? currentSection.visibleInModes
      : ['create', 'edit', 'detail'],
    inlineCreateEnabled: currentSection.inlineCreateEnabled !== false,
    inlineEditEnabled: currentSection.inlineEditEnabled !== false,
  }
  formDesignerSchema.pageSections = upsertAt(pageSections, sectionIndex, nextSection)

  return { pageSchema, formDesignerSchema }
}

export function resolveChildTableSectionEditConfig(source = {}, section = {}) {
  const pageSchema = source.pageSchema || {}
  const relationKey = firstNonBlank(section.relationKey, section.key)
  const children = Array.isArray(pageSchema.options?.masterDetailConfig?.children)
    ? pageSchema.options.masterDetailConfig.children
    : []
  const child = children.find(item => relationKeyOf(item) === relationKey) || {}
  const modelRefs = Array.isArray(pageSchema.modelRefs) ? pageSchema.modelRefs : []
  const modelRef = modelRefs.find((item) => {
    const itemRelationKey = relationKeyOf(item)
    if (itemRelationKey)
      return itemRelationKey === relationKey
    return Boolean(child.modelCode && item.modelCode === child.modelCode)
  }) || {}
  const fields = Array.isArray(child.fields) && child.fields.length
    ? child.fields
    : (Array.isArray(modelRef.fields) ? modelRef.fields : [])
  return {
    relationKey,
    title: firstNonBlank(section.title, child.modelName, modelRef.modelName, relationKey, '子表分区'),
    displayMode: ['inline_grid', 'card_list', 'bottom_sheet'].includes(section.displayMode)
      ? section.displayMode
      : 'inline_grid',
    modelCode: firstNonBlank(child.modelCode, modelRef.modelCode, section.modelCode),
    modelName: firstNonBlank(child.modelName, modelRef.modelName),
    tableName: firstNonBlank(child.tableName, modelRef.tableName),
    fieldCodes: fields.map(resolveFieldCode).filter(Boolean),
  }
}

export function removeChildTableSectionConfig(source = {}, target = {}) {
  const pageSchema = cloneValue(source.pageSchema || {})
  const formDesignerSchema = cloneValue(source.formDesignerSchema || {})
  const relationKey = firstNonBlank(target.relationKey, target.key)
  const sectionId = firstNonBlank(target.sectionId, target.sectionKey)
  const pageSections = Array.isArray(formDesignerSchema.pageSections) ? formDesignerSchema.pageSections : []
  const removedSections = pageSections.filter(section => matchesRemovalTarget(section, relationKey, sectionId))
  formDesignerSchema.pageSections = pageSections.filter(section => !matchesRemovalTarget(section, relationKey, sectionId))

  const options = pageSchema.options && typeof pageSchema.options === 'object' ? pageSchema.options : {}
  const masterDetailConfig = options.masterDetailConfig && typeof options.masterDetailConfig === 'object'
    ? options.masterDetailConfig
    : {}
  const children = Array.isArray(masterDetailConfig.children) ? masterDetailConfig.children : []
  const removedChildren = children.filter(child => matchesRemovalTarget(child, relationKey))
  const remainingChildren = children.filter(child => !matchesRemovalTarget(child, relationKey))
  pageSchema.options = {
    ...options,
    masterDetailConfig: {
      ...masterDetailConfig,
      children: remainingChildren,
    },
  }

  const removedModelCodes = new Set([
    ...removedChildren.map(child => child?.modelCode),
    ...removedSections.map(section => section?.modelCode),
  ].map(value => firstNonBlank(value)).filter(Boolean))
  const remainingRelationKeys = new Set([
    ...remainingChildren.map(relationKeyOf),
    ...formDesignerSchema.pageSections.map(relationKeyOf),
  ].filter(Boolean))
  const remainingModelCodes = new Set(remainingChildren.map(child => firstNonBlank(child?.modelCode)).filter(Boolean))
  const modelRefs = Array.isArray(pageSchema.modelRefs) ? pageSchema.modelRefs : []
  pageSchema.modelRefs = modelRefs.filter((modelRef) => {
    if (modelRef?.primary)
      return true
    const modelRefRelationKey = relationKeyOf(modelRef)
    if (modelRefRelationKey) {
      if (modelRefRelationKey !== relationKey)
        return true
      return remainingRelationKeys.has(modelRefRelationKey)
    }
    const modelCode = firstNonBlank(modelRef?.modelCode)
    return !removedModelCodes.has(modelCode) || remainingModelCodes.has(modelCode)
  })

  return { pageSchema, formDesignerSchema }
}

export function resolveChildTableRelationKey(relation = {}) {
  const config = parseRelationConfig(relation.relationConfig || relation.config || relation.props)
  return firstNonBlank(
    relation.relationKey,
    relation.key,
    config.relationKey,
    config.collectionKey,
    relation.targetObjectCode,
    relation.modelCode,
  )
}

function normalizeChildTableConfig(input = {}) {
  const relation = input.relation && typeof input.relation === 'object' ? input.relation : {}
  const relationKey = firstNonBlank(input.relationKey, resolveChildTableRelationKey(relation))
  const modelCode = firstNonBlank(input.modelCode, relation.targetObjectCode, relation.modelCode)
  return {
    ...input,
    relation,
    relationKey,
    modelCode,
    modelName: firstNonBlank(input.modelName, relation.targetObjectName, relation.modelName, modelCode),
    tableName: firstNonBlank(input.tableName, relation.targetTableName, relation.tableName),
    title: firstNonBlank(input.title, relation.relationName, relation.targetObjectName, modelCode, '子表分区'),
    displayMode: ['inline_grid', 'card_list', 'bottom_sheet'].includes(input.displayMode)
      ? input.displayMode
      : 'inline_grid',
    fields: Array.isArray(input.fields) ? input.fields.filter(resolveFieldCode) : [],
  }
}

function resolveModelRefRelations(currentModelRef = {}, config = {}) {
  if (Array.isArray(currentModelRef.relations) && currentModelRef.relations.length)
    return currentModelRef.relations
  const relation = config.relation || {}
  if (!Object.keys(relation).length)
    return []
  const sourceField = relation.sourceField || relation.sourceFieldCode || ''
  const targetField = relation.targetField || relation.targetFieldCode || ''
  if (config.primaryModelCode && relation.targetObjectCode === config.modelCode) {
    return [{
      ...relation,
      relationType: relation.relationType || 'DETAIL',
      sourceObjectCode: config.modelCode,
      targetObjectCode: config.primaryModelCode,
      sourceField: targetField,
      targetField: sourceField,
    }]
  }
  return [{
    ...relation,
    relationType: relation.relationType || 'DETAIL',
    targetObjectCode: relation.targetObjectCode || config.primaryModelCode || config.modelCode,
    sourceField,
    targetField,
  }]
}

function normalizeModelRefField(field = {}, config = {}) {
  const fieldCode = resolveFieldCode(field)
  return {
    ...field,
    field: fieldCode,
    fieldCode,
    sourceField: field.sourceField || fieldCode,
    fieldRef: field.fieldRef || `${safeKey(config.modelCode)}__${fieldCode}`,
    modelCode: config.modelCode,
    modelName: config.modelName,
    label: field.label || field.fieldName || fieldCode,
    rawLabel: field.rawLabel || field.label || field.fieldName || fieldCode,
  }
}

function normalizeRuntimeChildField(field = {}) {
  const fieldCode = resolveFieldCode(field)
  const componentType = field.componentType || field.type || 'input'
  return {
    ...field,
    field: fieldCode,
    fieldCode,
    sourceField: field.sourceField || fieldCode,
    label: field.label || field.fieldName || fieldCode,
    type: componentType,
    componentType,
  }
}

function matchesRelation(item = {}, config = {}) {
  const itemRelationKey = firstNonBlank(item.relationKey, item.key, item.props?.relationKey)
  if (itemRelationKey)
    return itemRelationKey === config.relationKey
  return Boolean(item.modelCode && item.modelCode === config.modelCode)
}

function matchesRemovalTarget(item = {}, relationKey = '', sectionId = '') {
  if (sectionId && firstNonBlank(item.sectionId, item.sectionKey) === sectionId)
    return true
  return Boolean(relationKey && relationKeyOf(item) === relationKey)
}

function relationKeyOf(item = {}) {
  return firstNonBlank(item.relationKey, item.key, item.props?.relationKey)
}

function upsertAt(items = [], index = -1, value) {
  if (index < 0)
    return [...items, value]
  return items.map((item, itemIndex) => itemIndex === index ? value : item)
}

function resolveFieldCode(field = {}) {
  return firstNonBlank(field.fieldCode, field.sourceField, field.field)
}

function parseRelationConfig(value) {
  if (value && typeof value === 'object')
    return value
  if (!value)
    return {}
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' ? parsed : {}
  }
  catch {
    return {}
  }
}

export function safeKey(value = '') {
  return String(value || '')
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/\W+/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '')
    .toLowerCase() || 'child'
}

function firstNonBlank(...values) {
  for (const value of values) {
    const text = String(value ?? '').trim()
    if (text)
      return text
  }
  return ''
}

function cloneValue(value) {
  if (Array.isArray(value))
    return value.map(cloneValue)
  if (!value || typeof value !== 'object')
    return value
  return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, cloneValue(item)]))
}
