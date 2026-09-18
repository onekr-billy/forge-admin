import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const serverDir = path.resolve(scriptDir, '../..')
const source = readFileSync(path.join(scriptDir, 'init-db.sh'), 'utf8')
  .replace(/^SCRIPT_DIR=.*$/m, `SCRIPT_DIR=${JSON.stringify(scriptDir)}`)
  .replace(/^FORGE_DIR=.*$/m, `FORGE_DIR=${JSON.stringify(serverDir)}`)
const sqlPath = '$FORGE_DIR/db/全量初始化SQL.sql'

// MySQL 调用全部由当前子 Shell 的函数接管，不执行任何真实数据库操作。
function runInit(script = source, args = [], mysqlStatus = 0) {
  return spawnSync('bash', ['--noprofile', '--norc', '-c', `
mysql() { printf 'MYSQL_STUB_CALLED\\n'; return ${mysqlStatus}; }
${script}
`, 'init-db-test', ...args], {
    encoding: 'utf8', timeout: 30000,
    env: { ...process.env, BASH_ENV: '', ENV: '' },
  })
}

function assertRejectedBeforeMysql(result) {
  assert.ifError(result.error)
  assert.equal(result.status, 1, result.stdout + result.stderr)
  assert.doesNotMatch(result.stdout, /MYSQL_STUB_CALLED|initialization completed/)
  assert.match(result.stderr, /初始化 SQL/)
}

test('全量 SQL 缺失时在调用 MySQL 前失败', () => {
  assertRejectedBeforeMysql(runInit(source.replaceAll(sqlPath, '$FORGE_DIR/__missing_init_test__.sql')))
})

test('全量 SQL 为空时在调用 MySQL 前失败', () => {
  assertRejectedBeforeMysql(runInit(source.replaceAll(sqlPath, '/dev/null')))
})

test('显式跳过全量初始化时不要求该文件存在', () => {
  const result = runInit(source.replaceAll(sqlPath, '$FORGE_DIR/__missing_init_test__.sql'), ['--skip-admin-init'])
  assert.ifError(result.error)
  assert.equal(result.status, 0, result.stdout + result.stderr)
  assert.match(result.stdout, /initialization completed/)
  assert.doesNotMatch(result.stdout, /Running .*__missing_init_test__/)
})

test('默认流程会将仓库中真实全量 SQL 交给 MySQL 桩', () => {
  const result = runInit()
  assert.ifError(result.error)
  assert.equal(result.status, 0, result.stdout + result.stderr)
  assert.match(result.stdout, /Running .*db\/全量初始化SQL\.sql/)
  assert.match(result.stdout, /MYSQL_STUB_CALLED/)
})

test('MySQL 执行失败时不输出初始化成功', () => {
  const result = runInit(source, [], 2)
  assert.equal(result.status, 2, result.stdout + result.stderr)
  assert.doesNotMatch(result.stdout, /initialization completed/)
})

test('帮助使用当前 SQL 路径且不连接数据库', () => {
  const result = runInit(source, ['--help'])
  assert.equal(result.status, 0, result.stdout + result.stderr)
  assert.match(result.stdout, /db\/全量初始化SQL\.sql/)
  assert.doesNotMatch(result.stdout, /MYSQL_STUB_CALLED|forge-admin-server\/sql/)
})
