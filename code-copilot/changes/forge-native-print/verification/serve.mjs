import { createRequire } from 'node:module'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const root = path.dirname(fileURLToPath(import.meta.url))
const repo = path.resolve(root, '../../../..')
const ui = path.join(repo, 'forge-admin-ui')
const require = createRequire(path.join(ui, 'package.json'))
const { createServer, build } = await import(pathToFileURL(require.resolve('vite')).href)
const { default: vue } = await import(pathToFileURL(require.resolve('@vitejs/plugin-vue')).href)
const config = {
  configFile: false,
  root,
  cacheDir: '/private/tmp/forge-print-verification-cache',
  plugins: [vue()],
  resolve: { alias: { '@': path.join(ui, 'src'), pinia: path.join(ui, 'node_modules/pinia/dist/pinia.mjs'), vue: path.join(ui, 'node_modules/vue/dist/vue.runtime.esm-bundler.js'), 'naive-ui': path.join(ui, 'node_modules/naive-ui/es/index.mjs') } },
  server: { host: '127.0.0.1', port: 4318, strictPort: true, fs: { allow: [repo] } },
  build: { outDir: '/private/tmp/forge-print-verification-dist', emptyOutDir: true },
}
if (process.argv.includes('--build')) {
  await build(config)
}
else {
  const server = await createServer(config)
  await server.listen()
  server.printUrls()
}
