const assert = require('node:assert/strict')
const path = require('node:path')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')

const groupId = '90071992547409931'
const groups = [{ id: groupId, groupName: '项目资料', groupType: 'default', fileCount: 12 }, { id: '90071992547409932', groupName: '品牌素材', groupType: 'image', fileCount: 12 }]
const examples = [
  ['品牌视觉参考.png', 'image/png', 'png'],
  ['产品使用手册.pdf', 'application/pdf', 'pdf'],
  ['项目归档资料.zip', 'application/zip', 'zip'],
  ['季度项目计划.xlsx', 'application/vnd.ms-excel', 'xlsx'],
  ['首页界面.png', 'image/png', 'png'],
  ['会议纪要.docx', 'application/msword', 'docx'],
]
const records = Array.from({ length: 24 }, (_, index) => ({
  id: String(101 + index), fileId: `test-file-${index}`, originalName: `${index < 6 ? '' : `${index + 1}-`}${examples[index % 6][0]}`,
  mimeType: examples[index % 6][1], extension: examples[index % 6][2], fileSize: 127520 + index * 50300,
  storageType: index % 3 ? 'local' : 'oss', uploadTime: '2026-09-17 10:24:00',
  groupId: index % 2 ? groups[1].id : groupId, businessType: index % 2 ? 'common' : 'project',
}))
const imageData = `data:image/svg+xml,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="480" height="300"><rect width="480" height="300" fill="#e9eef2"/><rect x="40" y="40" width="400" height="220" rx="4" fill="#fff"/><rect x="65" y="66" width="130" height="14" rx="3" fill="#9baebd"/><rect x="65" y="100" width="350" height="100" rx="3" fill="#dae4eb"/><rect x="65" y="216" width="210" height="9" rx="3" fill="#c7d3dc"/></svg>')}`
const calls = []
let failNextList = false
let delayImages = false

async function mockApi(route) {
  const req = route.request()
  const url = new URL(req.url())
  const api = url.pathname.replace('/verification-api', '')
  const params = Object.fromEntries(url.searchParams)
  calls.push({ api, method: req.method(), params, body: req.postData() })
  let data = []
  if (api.includes('/dict/data/type/')) {
    data = api.endsWith('sys_file_storage_type')
      ? [{ dictLabel: '本地存储', dictValue: 'local' }, { dictLabel: '对象存储', dictValue: 'oss' }]
      : [{ dictLabel: '默认', dictValue: 'default' }, { dictLabel: '图片', dictValue: 'image' }]
  }
  else if (api.endsWith('/metadata/statistics')) {
    data = { total: records.length, imageCount: 8, documentCount: 16 }
  }
  else if (api.endsWith('/group/list')) {
    data = groups
  }
  else if (api.endsWith('/storage/config/options')) {
    data = [{ id: '1001', configName: '本地存储', storageType: 'local', isDefault: true }, { id: '1002', configName: '对象存储', storageType: 'oss' }]
  }
  else if (api.endsWith('/metadata/page')) {
    assert(params.pageNum && params.pageSize, '分页必须传递 pageNum/pageSize')
    assert(!params.page && !params.size, '禁止旧分页参数')
    if (failNextList) {
      failNextList = false
      return route.fulfill({ json: { code: 500, message: '模拟文件服务异常' } })
    }
    const filtered = records.filter(file => (!params.originalName || file.originalName.includes(params.originalName))
      && (!params.groupId || file.groupId === params.groupId)
      && (!params.mimeType || file.mimeType.startsWith(params.mimeType))
      && (!params.storageType || file.storageType === params.storageType)
      && (!params.businessType || file.businessType === params.businessType))
    const offset = (Number(params.pageNum) - 1) * Number(params.pageSize)
    data = { records: filtered.slice(offset, offset + Number(params.pageSize)), total: filtered.length }
    if (delayImages && params.mimeType === 'image/')
      await new Promise(resolve => setTimeout(resolve, 450))
  }
  else if (api.includes('/api/file/url/')) {
    data = imageData
  }
  else if (api.endsWith('/metadata/rename')) {
    const file = records.find(item => item.fileId === params.fileId)
    assert(file)
    file.originalName = params.originalName
  }
  else if (api.endsWith('/file/metadata') && req.method() === 'PUT') {
    const body = req.postDataJSON()
    assert.equal(body.groupId, groups[1].id)
    records.find(item => item.id === body.id).groupId = body.groupId
  }
  else if (api.endsWith('/file/group') && req.method() === 'POST') {
    const body = req.postDataJSON()
    groups.push({ id: '90071992547409933', ...body, fileCount: 0 })
  }
  return route.fulfill({ json: { code: 200, data } })
}

async function run() {
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  const context = await browser.newContext({ viewport: { width: 1280, height: 840 }, permissions: ['clipboard-read', 'clipboard-write'], reducedMotion: 'reduce' })
  const page = await context.newPage()
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') console.log('CONSOLE', message.text().slice(0, 200)) })
  await page.route('**/verification-api/**', mockApi)
  await page.route('**/api/file/upload', async route => {
    calls.push({ api: '/api/file/upload', method: 'POST', body: route.request().postData() })
    await route.fulfill({ json: { code: 200, data: { fileId: 'uploaded-test-file' } } })
  })
  const screenshot = async (name) => {
    await page.waitForTimeout(250)
    await page.screenshot({ path: path.join(__dirname, `${name}.png`), fullPage: true })
  }
  const ready = async () => page.waitForFunction(() => document.querySelector('.file-content')?.getAttribute('aria-busy') === 'false')
  const lastList = () => calls.filter(call => call.api.endsWith('/metadata/page')).at(-1)
  const more = name => page.getByRole('button', { name: `${name}的更多操作`, exact: true })
  try {
    await page.goto('http://127.0.0.1:5187', { waitUntil: 'networkidle' })
    await ready()
    await page.getByRole('button', { name: '品牌视觉参考.png', exact: true }).waitFor()
    assert.equal(await page.locator('.n-data-table-tr').count(), 21)
    assert.equal(await page.getByRole('checkbox').count(), 0)
    await screenshot('list-light')
    console.log('PASS initial list, counts and compact toolbar')

    await page.locator('.n-pagination-item').filter({ hasText: /^2$/ }).click()
    await ready()
    assert.equal(lastList().params.pageNum, '2')
    await page.getByRole('button', { name: /^图片/ }).click()
    await ready()
    assert.equal(lastList().params.pageNum, '1')
    assert.equal(lastList().params.mimeType, 'image/')
    await page.getByRole('button', { name: '网格视图', exact: true }).click()
    assert.equal(await page.locator('.file-card').count(), 8)
    await screenshot('grid-images')
    await page.getByRole('button', { name: /^全部文件/ }).click()
    await ready()
    await page.getByPlaceholder('搜索文件名').fill('产品')
    await page.getByRole('button', { name: '搜索', exact: true }).click()
    await ready()
    assert.equal(await page.locator('.file-card').count(), 4)
    await page.getByRole('button', { name: '列表视图', exact: true }).click()
    assert.equal(await page.locator('.n-data-table-tr').count(), 5)
    await page.getByRole('button', { name: '清除筛选', exact: true }).click()
    await ready()
    console.log('PASS pagination, category, search and view consistency')

    await page.getByRole('button', { name: '筛选', exact: true }).click()
    await page.getByPlaceholder('输入业务类型').fill('project')
    await page.getByRole('button', { name: '应用筛选' }).click()
    await ready()
    assert.equal(lastList().params.businessType, 'project')
    await page.getByRole('button', { name: '清除筛选', exact: true }).click()
    await ready()
    await page.getByPlaceholder('搜索文件名').fill('不存在的文件')
    await page.getByPlaceholder('搜索文件名').press('Enter')
    await ready()
    await page.getByText('没有匹配的文件', { exact: true }).waitFor()
    await screenshot('empty-search')
    await page.getByRole('button', { name: '清除筛选', exact: true }).click()
    await ready()

    await more('品牌视觉参考.png').click()
    await page.getByText('重命名', { exact: true }).click()
    await page.getByPlaceholder('请输入文件名').fill('品牌视觉参考-新版.png')
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await ready()
    await more('品牌视觉参考-新版.png').waitFor()
    await more('品牌视觉参考-新版.png').click()
    await page.getByText('复制链接', { exact: true }).click()
    assert.equal(await page.evaluate(() => navigator.clipboard.readText()), imageData)
    await more('品牌视觉参考-新版.png').click()
    await page.getByText('移动到分组', { exact: true }).click()
    await page.locator('.move-group').filter({ hasText: '品牌素材' }).click()
    await page.getByRole('button', { name: '移动', exact: true }).click()
    await ready()
    await more('品牌视觉参考-新版.png').click()
    await page.getByText('删除', { exact: true }).click()
    await page.getByRole('button', { name: '取消', exact: true }).click()
    assert.equal(calls.filter(call => call.method === 'DELETE').length, 0)
    console.log('PASS rename, copy, move and delete cancellation')

    await page.getByRole('button', { name: '品牌视觉参考-新版.png', exact: true }).click()
    await page.locator('.preview-image img').waitFor()
    await screenshot('preview-image')
    await page.keyboard.press('Escape')
    const downloadPromise = page.waitForEvent('download')
    await page.getByRole('button', { name: '下载', exact: true }).first().click()
    assert.equal((await downloadPromise).suggestedFilename(), '品牌视觉参考-新版.png')
    await page.getByRole('button', { name: '管理分组', exact: true }).click()
    await page.getByPlaceholder('分组名称').fill('验收分组')
    await page.getByRole('button', { name: '添加', exact: true }).click()
    await page.getByText('验收分组', { exact: true }).first().waitFor()
    await screenshot('groups')
    await page.keyboard.press('Escape')
    await page.getByRole('button', { name: /^项目资料/ }).click()
    await ready()
    assert.equal(lastList().params.groupId, groupId)
    await page.locator('input[type="file"]').setInputFiles({ name: 'verification.txt', mimeType: 'text/plain', buffer: Buffer.from('file-list verification') })
    await page.getByText('“verification.txt”上传成功', { exact: true }).waitFor()
    const upload = calls.find(call => call.api === '/api/file/upload')
    assert(upload.body.includes(groupId))
    assert(upload.body.includes('local'))
    console.log('PASS preview, download, groups, string group IDs and upload payload')

    await page.getByRole('button', { name: /^全部文件/ }).click()
    await ready()
    failNextList = true
    await page.getByRole('button', { name: '刷新文件' }).click()
    await page.getByText('文件加载失败', { exact: true }).waitFor()
    await page.getByRole('button', { name: '重新加载' }).click()
    await ready()
    delayImages = true
    await page.getByRole('button', { name: /^图片/ }).click()
    await page.getByRole('button', { name: /^文档/ }).click()
    await ready()
    await page.waitForTimeout(550)
    assert.equal(await page.locator('.workspace-header h1').innerText(), '文档')
    assert.equal(await page.locator('.file-title').filter({ hasText: '.png' }).count(), 0)
    delayImages = false
    await page.getByRole('button', { name: /^全部文件/ }).click()
    await ready()
    await page.evaluate(() => window.$message.destroyAll())
    await page.getByRole('button', { name: '网格视图' }).click()
    await screenshot('grid-light')
    await page.getByRole('button', { name: '切换主题' }).click()
    await screenshot('grid-dark')
    await page.getByRole('button', { name: '列表视图' }).click()
    await screenshot('list-dark')
    await page.getByRole('button', { name: '切换主题' }).click()
    await page.setViewportSize({ width: 390, height: 844 })
    await screenshot('list-mobile')
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), '页面不能横向溢出')
    const footer = await page.locator('.workspace-footer').boundingBox()
    assert(footer.y + footer.height <= 844, '分页必须在可见区域')
    await page.getByRole('button', { name: '网格视图' }).click()
    await screenshot('grid-mobile')
    assert(await page.locator('.grid-loading').evaluate(el => el.scrollHeight > el.clientHeight), '网格必须独立滚动')
    await page.getByRole('button', { name: '管理分组' }).click()
    await screenshot('groups-mobile')
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    assert.deepEqual(errors, [])
    console.log(JSON.stringify({ status: 'passed', browserErrors: errors, apiCalls: calls.length, screenshots: 11 }))
  }
  catch (error) {
    await screenshot('failure')
    console.error((await page.locator('body').innerText()).slice(0, 3500))
    console.error('BROWSER ERRORS', errors)
    throw error
  }
  finally {
    await browser.close()
  }
}
run().catch(error => { console.error(error); process.exitCode = 1 })
