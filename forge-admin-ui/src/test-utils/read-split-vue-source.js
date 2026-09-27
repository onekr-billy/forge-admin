import { readdirSync, readFileSync, existsSync } from 'node:fs'
import { dirname, join, resolve, basename } from 'node:path'
import { fileURLToPath } from 'node:url'

function appendMatchingComposableFiles(parts, dir, composablePrefix) {
  if (!existsSync(dir))
    return
  const files = readdirSync(dir)
    .filter((name) => {
      if (!composablePrefix) {
        return /^use.+\.(part\d+\.)?(js|ts)$/.test(name)
      }
      return name === `${composablePrefix}.js`
        || name === `${composablePrefix}.ts`
        || new RegExp(`^${composablePrefix}\\.part\\d+\\.(js|ts)$`).test(name)
    })
    .sort()
  for (const name of files) {
    parts.push(readFileSync(join(dir, name), 'utf8'))
  }
}

/**
 * Join a Vue SFC with its composable parts / sibling split artifacts for source-contract tests.
 * @param {string} vuePath Absolute or cwd-relative path to the .vue file
 * @param {string} [composablePrefix] e.g. useLoginPage — auto-detect from composables/ if omitted
 */
export function readSplitVueSource(vuePath, composablePrefix) {
  const abs = resolve(vuePath)
  const dir = dirname(abs)
  const base = basename(abs, '.vue')
  const parts = [readFileSync(abs, 'utf8')]

  appendMatchingComposableFiles(parts, join(dir, 'composables'), composablePrefix)
  // application-runtime keeps parts under runtime-modules/
  appendMatchingComposableFiles(parts, join(dir, 'runtime-modules'), composablePrefix)
  // AiCrudPage keeps parts under crud/composables/
  appendMatchingComposableFiles(parts, join(dir, 'crud', 'composables'), composablePrefix)

  try {
    for (const name of readdirSync(dir)) {
      // Local component registries, pure utils, extracted CSS next to the SFC
      if (
        name.endsWith('LocalComponents.js')
        || name.endsWith('Utils.js')
        || (name.startsWith(base) && (name.endsWith('.css') || name.endsWith('.scss')))
        || (name.startsWith(base.replace(/([A-Z])/g, m => m.toLowerCase())) && name.endsWith('.css'))
      ) {
        parts.push(readFileSync(join(dir, name), 'utf8'))
        continue
      }
      // kebab css shells: list-page-grid-designer-*.css, aiFormItem.css, etc.
      const kebab = base
        .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
        .replace(/([A-Z])([A-Z][a-z])/g, '$1-$2')
        .toLowerCase()
      if (name.startsWith(kebab) && (name.endsWith('.css') || name.endsWith('.scss'))) {
        parts.push(readFileSync(join(dir, name), 'utf8'))
        continue
      }
      // Sibling property/panel SFCs extracted from the same designer (ListPageProperty*.vue)
      if (
        name.endsWith('.vue')
        && name !== basename(abs)
        && (
          name.startsWith(`${base}Property`)
          || name.startsWith('ListPageProperty')
          || name.startsWith('ListPageBlock')
          || name.startsWith(`${base}Panel`)
        )
      ) {
        parts.push(readFileSync(join(dir, name), 'utf8'))
      }
    }
  }
  catch {
    // ignore
  }

  return parts.join('\n')
}

export function readSourceFromUrl(relativeUrl, importMetaUrl) {
  const base = dirname(fileURLToPath(importMetaUrl))
  return readFileSync(join(base, relativeUrl), 'utf8')
}
