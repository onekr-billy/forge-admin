const DANGEROUS_TAGS = new Set([
  'script',
  'iframe',
  'object',
  'embed',
  'link',
  'meta',
  'style',
  'form',
  'base',
  'svg',
  'math',
  'video',
  'audio',
  'source',
])

export function escapeHtml(text) {
  return String(text ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

export function sanitizeHtml(html) {
  const raw = String(html ?? '')
  if (!raw)
    return ''
  if (typeof document === 'undefined')
    return sanitizeHtmlFallback(raw)

  const template = document.createElement('template')
  template.innerHTML = raw
  stripNode(template.content)
  return template.innerHTML
}

function stripNode(root) {
  const nodes = [...root.childNodes]
  for (const node of nodes) {
    if (node.nodeType === 1) {
      const tag = node.tagName.toLowerCase()
      if (DANGEROUS_TAGS.has(tag)) {
        node.remove()
        continue
      }
      for (const attr of [...node.attributes]) {
        const name = attr.name.toLowerCase()
        const value = String(attr.value || '')
        if (name.startsWith('on') || name === 'srcdoc' || /javascript:|data:text\/html/i.test(value))
          node.removeAttribute(attr.name)
      }
      stripNode(node)
    }
    else if (node.nodeType === 8) {
      node.remove()
    }
  }
}

function sanitizeHtmlFallback(html) {
  return String(html)
    .replace(/<\s*(script|iframe|object|embed|link|meta|style|form|base|svg|math)\b[^>]*>[\s\S]*?<\s*\/\s*\1\s*>/gi, '')
    .replace(/<\s*(script|iframe|object|embed|link|meta|style|form|base|svg|math)\b[^>]*>/gi, '')
    .replace(/\son[a-z]+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, '')
    .replace(/(href|src)\s*=\s*("\s*javascript:[^"]*"|'\s*javascript:[^']*')/gi, '$1="#"')
}
