import { paperGeometry } from '../protocol/units'
import { findSurface, newPrintId } from './commands'
import { cloneDocument } from './history'
import { renewStaticTableIds } from './staticTable'

const DEFINITION_KEYS = ['key', 'label', 'icon', 'factory']
const COMPONENT_KEY = /^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*$/
const ICON_KEY = /^[a-z][a-z0-9-]*$/

function plainObject(value) {
  return value && typeof value === 'object' && !Array.isArray(value) && [Object.prototype, null].includes(Object.getPrototypeOf(value))
}

function assertJsonValue(value, seen = new WeakSet()) {
  if (value === null || ['string', 'number', 'boolean'].includes(typeof value)) {
    if (typeof value === 'number' && !Number.isFinite(value))
      throw new TypeError('业务打印组件包含非法数字')
    return
  }
  if (typeof value !== 'object' || value === undefined)
    throw new TypeError('业务打印组件只能输出纯 JSON 协议片段')
  if (seen.has(value))
    throw new TypeError('业务打印组件不能输出循环引用')
  seen.add(value)
  if (Array.isArray(value)) {
    value.forEach(item => assertJsonValue(item, seen))
    return
  }
  if (!plainObject(value))
    throw new TypeError('业务打印组件只能输出普通 JSON 对象')
  Object.values(value).forEach(item => assertJsonValue(item, seen))
}

function frozenContext(context) {
  const copy = cloneDocument(context)
  const freeze = (value) => {
    if (value && typeof value === 'object' && !Object.isFrozen(value)) {
      Object.values(value).forEach(freeze)
      Object.freeze(value)
    }
    return value
  }
  return freeze(copy)
}

function renewElementIds(element) {
  return renewStaticTableIds({ ...element, id: newPrintId() }, newPrintId)
}

function renewSectionIds(section) {
  section.id = newPrintId()
  section.elements = section.elements?.map(renewElementIds)
  section.columns = section.columns?.map(column => ({ ...column, id: newPrintId() }))
  return section
}

export class PrintComponentRegistry {
  #items = new Map()

  register(definition) {
    if (!plainObject(definition) || Object.keys(definition).some(key => !DEFINITION_KEYS.includes(key)))
      throw new TypeError('业务打印组件定义只允许 key、label、icon 和 factory')
    if (typeof definition.key !== 'string' || definition.key.length > 80 || !COMPONENT_KEY.test(definition.key))
      throw new TypeError('业务打印组件 key 无效')
    if (typeof definition.label !== 'string' || !definition.label.trim() || definition.label.length > 50)
      throw new TypeError('业务打印组件标签无效')
    if (typeof definition.icon !== 'string' || definition.icon.length > 40 || !ICON_KEY.test(definition.icon))
      throw new TypeError('业务打印组件图标无效')
    if (typeof definition.factory !== 'function' || definition.factory.constructor?.name === 'AsyncFunction')
      throw new TypeError('业务打印组件必须使用同步工厂')
    if (this.#items.has(definition.key))
      throw new Error(`业务打印组件已注册：${definition.key}`)
    this.#items.set(definition.key, Object.freeze({ ...definition, label: definition.label.trim() }))
    return definition.key
  }

  list() {
    return [...this.#items.values()].map(({ key, label, icon }) => ({ key, label, icon }))
  }

  expand(key, context = {}) {
    const definition = this.#items.get(key)
    if (!definition)
      throw new Error(`业务打印组件未注册：${key}`)
    const fragment = definition.factory(frozenContext(context))
    if (fragment && typeof fragment.then === 'function')
      throw new TypeError('业务打印组件工厂不能返回 Promise')
    assertJsonValue(fragment)
    if (!plainObject(fragment) || !['ELEMENT', 'SECTION'].includes(fragment.kind))
      throw new TypeError('业务打印组件必须返回 ELEMENT 或 SECTION 协议片段')
    const keys = fragment.kind === 'ELEMENT' ? ['kind', 'element'] : ['kind', 'section']
    if (Object.keys(fragment).some(key => !keys.includes(key)) || !plainObject(fragment[keys[1]]))
      throw new TypeError('业务打印组件片段结构无效')
    return cloneDocument(fragment)
  }
}

export const printComponentRegistry = new PrintComponentRegistry()

export function registerBusinessPrintComponent(definition) {
  return printComponentRegistry.register(definition)
}

export function insertRegisteredPrintComponent(store, key, position, registry = printComponentRegistry) {
  let surfaceId = store.surfaceId
  let elementId = ''
  let sectionId = ''
  const ok = store.execute((document) => {
    const geometry = paperGeometry(document)
    const fragment = registry.expand(key, { catalog: store.catalog, contentWidthMm: geometry.contentWidthMm })
    if (fragment.kind === 'SECTION') {
      const section = renewSectionIds(fragment.section)
      const activeId = store.surfaceId.startsWith('section:') ? store.surfaceId.slice('section:'.length) : ''
      const activeIndex = document.body.findIndex(item => item.id === activeId)
      document.body.splice(activeIndex < 0 ? document.body.length : activeIndex + 1, 0, section)
      sectionId = section.id
      return
    }
    const element = renewElementIds(fragment.element)
    let surface = findSurface(document, surfaceId)
    if (!surface?.elements) {
      surface = { id: newPrintId(), kind: 'FIXED', heightMm: 40, elements: [] }
      document.body.push(surface)
      surfaceId = surface.id
    }
    if (position) {
      element.xMm = Math.max(0, Math.min(position.xMm, geometry.contentWidthMm - element.widthMm))
      element.yMm = Math.max(0, position.yMm)
    }
    surface.heightMm = Math.max(surface.heightMm, element.yMm + element.heightMm)
    surface.elements.push(element)
    elementId = element.id
  })
  if (!ok)
    return false
  if (sectionId) {
    store.selectSurface(sectionId)
  }
  else {
    store.selectSurface(surfaceId)
    store.selectedIds = [elementId]
  }
  return true
}
