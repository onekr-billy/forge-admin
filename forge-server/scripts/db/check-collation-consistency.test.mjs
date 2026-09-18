import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(scriptDir, '../../..')
const scriptPath = path.join(scriptDir, 'check-collation-consistency.sh')
const source = readFileSync(scriptPath, 'utf8')
  .replace(/^SCRIPT_DIR=.*$/m, `SCRIPT_DIR=${JSON.stringify(scriptDir)}`)
  .replace(/^PROJECT_DIR=.*$/m, `PROJECT_DIR=${JSON.stringify(projectDir)}`)
const env = { ...process.env, BASH_ENV: '', ENV: '' }

// 仅在子 Shell 中模拟 rg 返回值，既不修改仓库文件，也不掩盖真实扫描测试。
function runMock({ guide = 1, sql = 1, required = 0, prefix = '', script = source } = {}) {
  const stub = `
rg() {
  if [[ "$*" == *'utf8mb4_(unicode_ci|general_ci)'* ]]; then
    if [[ " $* " == *' --glob '* ]]; then
      return ${sql}
    fi
    return ${guide}
  fi
  return ${required}
}
`
  return spawnSync('bash', ['--noprofile', '--norc', '-c', `${stub}\n${prefix}\n${script}`], {
    cwd: projectDir, encoding: 'utf8', env, timeout: 30000,
  })
}

function assertFailed(result) {
  assert.ifError(result.error)
  assert.equal(result.status, 1, result.stdout + result.stderr)
  assert.doesNotMatch(result.stdout, /check passed/)
}

test('真实仓库扫描通过且没有缺失目录警告，支持非仓库根目录调用', () => {
  const result = spawnSync('bash', [scriptPath], {
    cwd: scriptDir, encoding: 'utf8', env, timeout: 30000,
  })
  assert.ifError(result.error)
  assert.equal(result.status, 0, result.stdout + result.stderr)
  assert.equal(result.stderr, '')
  assert.match(result.stdout, /check passed/)
})

test('负向扫描返回 1 才代表未发现旧排序规则', () => {
  const result = runMock()
  assert.equal(result.status, 0, result.stdout + result.stderr)
  assert.match(result.stdout, /check passed/)
})

for (const target of ['guide', 'sql']) {
  test(`${target} 扫描匹配到旧排序规则时失败`, () => {
    assertFailed(runMock({ [target]: 0 }))
  })
  test(`${target} 扫描发生执行错误时不能误报通过`, () => {
    const result = runMock({ [target]: 2 })
    assertFailed(result)
    assert.match(result.stderr, /rg.*2/)
  })
}

for (const status of [1, 2]) {
  test(`必需模式检查返回 ${status} 时失败`, () => {
    const result = runMock({ required: status })
    assertFailed(result)
    if (status === 2) {
      assert.match(result.stderr, /rg.*2/)
    }
  })
}

test('缺少 rg 时在开始校验前失败', () => {
  const result = runMock({
    prefix: `command() {
      if [[ "$*" == '-v rg' ]]; then return 1; fi
      builtin command "$@"
    }`,
  })
  assertFailed(result)
  assert.match(result.stderr, /ripgrep|rg/)
})

test('必需扫描路径缺失时失败，不能静默跳过', () => {
  const missing = '$PROJECT_DIR/forge-server/__missing_collation_test_path__'
  const result = runMock({ script: source.replace('$PROJECT_DIR/forge-server/forge-framework', missing) })
  assertFailed(result)
  assert.match(result.stderr, /__missing_collation_test_path__/)
})

test('预检后真实 rg 遇到缺失路径仍必须失败', () => {
  const script = source.replace(
    '"${CONFIG_AND_GUIDE_PATHS[@]}"\n',
    '"${CONFIG_AND_GUIDE_PATHS[@]}" "$PROJECT_DIR/__missing_scan_test__.sql"\n',
  )
  assert.notEqual(script, source)
  const result = spawnSync('bash', ['--noprofile', '--norc', '-c', script], {
    cwd: projectDir, encoding: 'utf8', env, timeout: 30000,
  })
  assertFailed(result)
  assert.match(result.stderr, /__missing_scan_test__/)
  assert.match(result.stderr, /rg.*2/)
})
