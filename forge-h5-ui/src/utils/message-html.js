const ALLOWED_TAGS = new Set([
  'a', 'b', 'blockquote', 'br', 'code', 'div', 'em', 'h1', 'h2', 'h3',
  'h4', 'h5', 'h6', 'i', 'li', 'ol', 'p', 'pre', 'span', 'strong', 'u', 'ul',
])
const DROP_TAGS = new Set(['script', 'style', 'iframe', 'object', 'embed', 'form', 'svg', 'math', 'img', 'video', 'audio'])

function escapeHtml(value) {
  return String(value || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function safeHref(value) {
  const href = String(value || '').trim()
  return /^(https?:\/\/|\/[^/])/i.test(href) ? href : ''
}

function cleanChildren(root) {
  for (const node of [...root.childNodes]) {
    if (node.nodeType === 8) { node.remove(); continue }
    if (node.nodeType !== 1) continue
    const tag = node.tagName.toLowerCase()
    if (DROP_TAGS.has(tag)) { node.remove(); continue }
    if (!ALLOWED_TAGS.has(tag)) {
      cleanChildren(node)
      node.replaceWith(...node.childNodes)
      continue
    }
    for (const attribute of [...node.attributes]) {
      if (tag === 'a' && attribute.name.toLowerCase() === 'href') {
        const href = safeHref(attribute.value)
        if (href) { node.setAttribute('href', href); continue }
      }
      node.removeAttribute(attribute.name)
    }
    cleanChildren(node)
  }
}

export function sanitizeMessageHtml(value) {
  const source = String(value || '')
  if (!source) return ''
  if (typeof document === 'undefined') {
    const plain = source
      .replace(/<\s*(script|style|iframe|object|embed|form|svg|math)\b[^>]*>[\s\S]*?<\s*\/\s*\1\s*>/gi, '')
      .replace(/<[^>]*>/g, ' ')
    return escapeHtml(plain)
  }
  const template = document.createElement('template')
  template.innerHTML = source
  cleanChildren(template.content)
  return template.innerHTML
}
