function list(value) {
  return Array.isArray(value) ? value : []
}

function formSchema(source = {}) {
  return source?.formDesignerSchema || source?.designerOptions?.formDesignerSchema || {}
}

function countComponents(components = []) {
  return list(components).reduce((total, component) => {
    const nested = list(component?.children)
    return total + 1 + countComponents(nested)
  }, 0)
}

function countActions(source = {}) {
  return list(source?.designerOptions?.actions).length
}

export function requiresSeedTakeoverConfirmation(designer = {}) {
  const options = designer?.designerOptions || {}
  return Boolean(options.seedKey) && options.seedTakeover?.accepted !== true
}

export function buildSeedTakeoverSummary(designer = {}, draft = {}) {
  const previousForm = formSchema(designer)
  const nextForm = formSchema(draft)
  const changes = [
    `表单组件 ${countComponents(previousForm.components)} -> ${countComponents(nextForm.components)}`,
    `页面分区 ${list(previousForm.pageSections).length} -> ${list(nextForm.pageSections).length}`,
    `业务动作 ${countActions(designer)} -> ${countActions(draft)}`,
  ]
  return changes.join('，')
}

export function markSeedTakeoverAccepted(designerOptions = {}, acceptedAt = new Date().toISOString()) {
  return {
    ...designerOptions,
    seedTakeover: {
      accepted: true,
      acceptedAt,
    },
  }
}
