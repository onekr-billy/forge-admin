import { LINKAGE_SCHEMA_VERSION } from './linkageSchema'

export function resolveFormFieldLinkages(formSchema = {}, legacyLinkageSchema = {}) {
  const governed = formSchema?.settings?.governance?.fieldLinkages
  const source = Array.isArray(governed)
    ? governed
    : Array.isArray(legacyLinkageSchema?.rules) ? legacyLinkageSchema.rules : []
  return clone(source)
}

export function applyFieldLinkagesToFormSchema(formSchema = {}, rules = []) {
  const source = clone(formSchema || {})
  return {
    ...source,
    settings: {
      ...(source.settings || {}),
      governance: {
        ...(source.settings?.governance || {}),
        fieldLinkages: clone(Array.isArray(rules) ? rules : []),
      },
    },
  }
}

export function buildLegacyLinkageSchema(formSchema = {}, legacyLinkageSchema = {}) {
  return {
    schemaVersion: legacyLinkageSchema?.schemaVersion || LINKAGE_SCHEMA_VERSION,
    settings: clone(legacyLinkageSchema?.settings || {}),
    rules: resolveFormFieldLinkages(formSchema, legacyLinkageSchema),
  }
}

function clone(value) {
  return JSON.parse(JSON.stringify(value ?? null))
}
