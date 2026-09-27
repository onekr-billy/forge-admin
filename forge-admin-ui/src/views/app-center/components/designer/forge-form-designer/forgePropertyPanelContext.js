import { getCurrentInstance, inject, isRef, provide } from 'vue'

export const FORGE_PROPERTY_PANEL_KEY = Symbol('forgePropertyPanel')

/**
 * Child panels `return useForgePropertyPanelApi()` and bind parent setupState + props.
 * Must support v-model writes (set trap + writable/accessor descriptors); a get-only
 * proxy makes Vue treat bindings as readonly and throws:
 *   'set' on proxy: trap returned falsish for property '…'
 */
function createSetupStateProxy(instance) {
  const resolve = (prop) => {
    const state = instance.setupState || {}
    if (Object.prototype.hasOwnProperty.call(state, prop))
      return { found: true, value: state[prop], source: 'setupState' }
    const props = instance.props || {}
    if (Object.prototype.hasOwnProperty.call(props, prop))
      return { found: true, value: props[prop], source: 'props' }
    return { found: false, value: undefined, source: null }
  }

  const assign = (prop, value) => {
    const hit = resolve(prop)
    if (!hit.found)
      return false
    if (hit.source === 'props')
      return false
    const current = hit.value
    if (isRef(current)) {
      current.value = value
      return true
    }
    instance.setupState[prop] = value
    return true
  }

  return new Proxy({}, {
    get(_target, prop) {
      if (prop === '__v_isRef' || prop === '__v_isReadonly' || prop === 'constructor')
        return undefined
      return resolve(prop).value
    },
    set(_target, prop, value) {
      return assign(prop, value)
    },
    has(_target, prop) {
      return resolve(prop).found
    },
    ownKeys() {
      const keys = new Set([
        ...Reflect.ownKeys(instance.setupState || {}),
        ...Reflect.ownKeys(instance.props || {}),
      ])
      return [...keys]
    },
    getOwnPropertyDescriptor(_target, prop) {
      const hit = resolve(prop)
      if (!hit.found)
        return undefined
      return {
        configurable: true,
        enumerable: true,
        get: () => resolve(prop).value,
        set: value => assign(prop, value),
      }
    },
  })
}

export function provideForgePropertyPanelApi() {
  const instance = getCurrentInstance()
  if (!instance)
    throw new Error('provideForgePropertyPanelApi must run inside setup()')
  const api = createSetupStateProxy(instance)
  provide(FORGE_PROPERTY_PANEL_KEY, api)
  return api
}

export function useForgePropertyPanelApi() {
  const api = inject(FORGE_PROPERTY_PANEL_KEY, null)
  if (!api)
    throw new Error('useForgePropertyPanelApi() requires ForgePropertyPanel parent')
  return api
}
