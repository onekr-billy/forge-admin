import { useAuthStore } from '@/store'
import { hasPermission } from '@/utils/access-control'

function parsePermissionBinding(binding = {}) {
  if (typeof binding.value === 'string' || typeof binding.value === 'number') {
    return {
      permissions: [binding.value],
      mode: 'any',
      strategy: binding.modifiers?.disable ? 'disable' : 'hide',
    }
  }

  if (Array.isArray(binding.value)) {
    return {
      permissions: binding.value,
      mode: 'any',
      strategy: binding.modifiers?.disable ? 'disable' : 'hide',
    }
  }

  const value = binding.value || {}
  return {
    permissions: Array.isArray(value.permissions) ? value.permissions : value.permission ? [value.permission] : [],
    mode: value.mode || 'any',
    strategy: value.strategy || (binding.modifiers?.disable ? 'disable' : 'hide'),
  }
}

function applyVisibility(el, allowed, strategy) {
  if (allowed) {
    if (el.__permissionDisplay !== undefined) {
      el.style.display = el.__permissionDisplay
    } else {
      el.style.display = ''
    }
    el.style.pointerEvents = ''
    el.style.opacity = ''
    el.setAttribute('aria-hidden', 'false')
    el.removeAttribute('aria-disabled')
    return
  }

  if (strategy === 'disable') {
    el.style.pointerEvents = 'none'
    el.style.opacity = '0.45'
    el.setAttribute('aria-disabled', 'true')
    return
  }

  el.style.display = 'none'
  el.setAttribute('aria-hidden', 'true')
}

function syncPermission(el, binding) {
  const authStore = useAuthStore()
  const config = parsePermissionBinding(binding)
  const allowed = hasPermission(authStore.permissions, config.permissions, config.mode)
  applyVisibility(el, allowed, config.strategy)
}

const permissionDirective = {
  mounted(el, binding) {
    el.__permissionDisplay = el.style.display
    syncPermission(el, binding)
  },
  updated(el, binding) {
    syncPermission(el, binding)
  },
  unmounted(el) {
    if (el.__permissionDisplay !== undefined) {
      el.style.display = el.__permissionDisplay
    }
    delete el.__permissionDisplay
  },
}

export default permissionDirective

