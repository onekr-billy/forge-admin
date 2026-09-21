import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getCapabilityCredential } from '../capabilityCredentialSession'

export function useCapabilityOnlineTest(props) {
  const router = useRouter()

  const authMode = ref(null)

  const credential = ref('')

  const subjectTokenMode = ref('OIDC')

  const subjectToken = ref('')

  const userAssertionSubject = ref('')

  const userAssertionPhone = ref('')

  const userAssertionOrgId = ref('')

  const userAssertionPrivateKey = ref('')

  const requestBody = ref('{}')

  const testing = ref(false)

  const testReport = ref(null)

  const credentialAutoFilled = ref(false)

  const authOptions = computed(() => (props.guide?.availableAuthModes || []).map(mode => ({
    label: mode === 'OAUTH' ? 'OAuth 2.1' : 'AppId + HMAC-SHA256',
    value: mode,
  })))

  const requiresSubjectToken = computed(() => (
    authMode.value === 'OAUTH' && props.guide?.tokenExchangeRequired
  ))

  const credentialLabel = computed(() => (
    authMode.value === 'HMAC' ? '签名密钥 Signing Key' : 'OAuth Client Secret'
  ))

  const credentialPlaceholder = computed(() => (
    authMode.value === 'HMAC'
      ? '粘贴创建或轮换客户端时保存的签名密钥'
      : '粘贴创建或轮换客户端时保存的 Client Secret'
  ))

  watch(() => props.guide, (guide) => {
    authMode.value = guide?.availableAuthModes?.[0] || null
    subjectTokenMode.value = guide?.userAssertionEnabled ? 'USER_ASSERTION' : 'OIDC'
    subjectToken.value = ''
    userAssertionSubject.value = ''
    userAssertionPhone.value = ''
    userAssertionOrgId.value = ''
    userAssertionPrivateKey.value = ''
    testReport.value = null
    resetBody()
    applySessionCredential(guide)
  }, { immediate: true })

  watch(authMode, () => {
    subjectTokenMode.value = props.guide?.userAssertionEnabled ? 'USER_ASSERTION' : 'OIDC'
    subjectToken.value = ''
    userAssertionSubject.value = ''
    userAssertionPhone.value = ''
    userAssertionOrgId.value = ''
    userAssertionPrivateKey.value = ''
    testReport.value = null
    applySessionCredential(props.guide)
  })

  watch(subjectTokenMode, () => {
    subjectToken.value = ''
    userAssertionSubject.value = ''
    userAssertionPhone.value = ''
    userAssertionOrgId.value = ''
    userAssertionPrivateKey.value = ''
    testReport.value = null
    applySessionCredential(props.guide)
  })

  function resetBody() {
    requestBody.value = JSON.stringify(props.guide?.requestExample || {}, null, 2)
  }

  function handleTest() {
    if (!validateTestInput())
      return
    if (props.guide.behavior === 'READ_ONLY') {
      executeTest()
      return
    }
    window.$dialog.warning({
      title: '确认执行有副作用的能力',
      content: '该能力可能启动流程、修改业务数据或触发外部动作。本次测试会真实执行，并自动携带一次性 Idempotency-Key。是否继续？',
      positiveText: '确认执行',
      negativeText: '取消',
      onPositiveClick: executeTest,
    })
  }

  function validateTestInput() {
    if (!props.guide?.ready) {
      window.$message.error('当前调用条件未就绪，请先处理阻断项')
      return false
    }
    if (!credential.value.trim()) {
      window.$message.error(`请输入${credentialLabel.value}`)
      return false
    }
    if (requiresSubjectToken.value) {
      if (subjectTokenMode.value === 'OIDC' && !subjectToken.value.trim()) {
        window.$message.error('请提供受信 OIDC subject_token')
        return false
      }
      if (subjectTokenMode.value === 'USER_ASSERTION') {
        if (!props.guide?.userAssertionEnabled || !props.guide?.userAssertionKeyId) {
          window.$message.error('当前客户端尚未启用用户断言密钥')
          return false
        }
        if (!userAssertionSubject.value.trim()) {
          window.$message.error('请输入已预绑定的外围用户标识')
          return false
        }
        if (props.guide?.userAssertionMappingMode === 'VERIFIED_PHONE'
          && userAssertionPhone.value.trim()
          && !/^\+?\d{6,20}$/.test(userAssertionPhone.value.trim())) {
          window.$message.error('手机号必须为 6 至 20 位数字，可带国际区号 +')
          return false
        }
        if (!userAssertionPrivateKey.value.includes('-----BEGIN PRIVATE KEY-----')) {
          window.$message.error('请粘贴有效的 PKCS#8 PEM 私钥')
          return false
        }
        if (userAssertionOrgId.value.trim() && !/^[1-9]\d*$/.test(userAssertionOrgId.value.trim())) {
          window.$message.error('Forge 组织 ID 必须是正整数')
          return false
        }
      }
    }
    try {
      const payload = JSON.parse(requestBody.value)
      if (!payload || Array.isArray(payload) || typeof payload !== 'object')
        throw new Error('请求 Body 必须是 JSON 对象')
      if (props.guide?.sourceType === 'FLOW_ACTION')
        validateFlowActionPayload(payload)
    }
    catch (error) {
      window.$message.error(error?.message || '请求 Body 不是合法 JSON')
      return false
    }
    return true
  }

  function validateFlowActionPayload(payload) {
    if (props.guide?.actionCode === 'SUBMIT') {
      const data = payload.data
      if (!data || Array.isArray(data) || typeof data !== 'object')
        throw new Error('SUBMIT 的 data 必须是包含申请字段的 JSON 对象')
      if ('recordId' in payload)
        throw new Error('SUBMIT 会自动创建业务记录，请不要传 recordId')
      return
    }
    if (typeof payload.recordId !== 'string' || !/^[1-9]\d{0,18}$/.test(payload.recordId.trim())) {
      throw new Error('recordId 必须替换为已经保存、且当前委托用户可见的真实记录 ID')
    }
    if (props.guide?.actionCode === 'START') {
      const argumentsValue = payload.arguments
      if (!argumentsValue || Array.isArray(argumentsValue) || typeof argumentsValue !== 'object')
        throw new Error('arguments 必须是 JSON 对象')
      if (Object.keys(argumentsValue).length)
        throw new Error('START 的 arguments 必须保持为空对象 {}')
    }
  }

  async function executeTest() {
    testing.value = true
    testReport.value = null
    const startedAt = new Date()
    try {
      const report = authMode.value === 'HMAC'
        ? await executeHmac()
        : await executeOAuth()
      testReport.value = {
        ...report,
        authMode: authMode.value,
        userIdentityMode: requiresSubjectToken.value ? subjectTokenMode.value : null,
        capabilityCode: props.guide.capabilityCode,
        clientId: props.guide.clientId,
        startedAt: formatDate(startedAt),
        durationMs: Date.now() - startedAt.getTime(),
      }
      if (testReport.value.success)
        window.$message.success('能力调用成功，可以下载完整测试报文')
      else
        window.$message.error(testReport.value.error || '能力调用失败，请查看返回报文和 requestId')
    }
    catch (error) {
      testReport.value = {
        success: false,
        error: error?.message || '网络请求失败',
        authMode: authMode.value,
        userIdentityMode: requiresSubjectToken.value ? subjectTokenMode.value : null,
        capabilityCode: props.guide.capabilityCode,
        clientId: props.guide.clientId,
        startedAt: formatDate(startedAt),
        durationMs: Date.now() - startedAt.getTime(),
        tokenExchange: null,
        invocation: null,
      }
      window.$message.error(testReport.value.error)
    }
    finally {
      testing.value = false
    }
  }

  async function executeOAuth() {
    const params = new URLSearchParams()
    params.set('grant_type', props.guide.tokenExchangeRequired
      ? 'urn:ietf:params:oauth:grant-type:token-exchange'
      : 'client_credentials')
    if (props.guide.tokenExchangeRequired) {
      const clientAssertion = subjectTokenMode.value === 'USER_ASSERTION'
      params.set('subject_token', clientAssertion
        ? await createUserAssertionJwt()
        : subjectToken.value.trim())
      params.set('subject_token_type', clientAssertion
        ? props.guide.userAssertionSubjectTokenType
        : 'urn:ietf:params:oauth:token-type:jwt')
      params.set('requested_token_type', 'urn:ietf:params:oauth:token-type:access_token')
    }
    params.set('resource', props.guide.openapiResource)
    params.set('scope', `capability:invoke:${props.guide.capabilityCode}`)

    const tokenStartedAt = Date.now()
    const tokenResponse = await fetch(backendProxyUrl(props.guide.tokenUrl), {
      method: 'POST',
      credentials: 'omit',
      headers: {
        'Authorization': `Basic ${basicCredentials(props.guide.clientId, credential.value)}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: params.toString(),
    })
    const tokenText = await tokenResponse.text()
    const tokenPayload = parseBody(tokenText)
    const tokenExchange = exchangeReport(
      {
        method: 'POST',
        url: props.guide.tokenUrl,
        headers: {
          'Authorization': 'Basic <REDACTED>',
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: redactTokenForm(params),
        rawBody: redactTokenFormString(params),
      },
      tokenResponse,
      redactSensitive(tokenPayload),
      Date.now() - tokenStartedAt,
    )
    const accessToken = tokenPayload?.access_token
    if (!tokenResponse.ok || !accessToken) {
      return {
        success: false,
        error: `获取访问令牌失败，HTTP ${tokenResponse.status}`,
        tokenExchange,
        invocation: null,
      }
    }

    const invocation = await invokeGateway({
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    }, {
      'Authorization': 'Bearer <REDACTED>',
      'Content-Type': 'application/json',
    })
    const success = invocation.response.status >= 200 && invocation.response.status < 300
    return {
      success,
      error: success ? null : gatewayErrorMessage(invocation),
      tokenExchange,
      invocation,
    }
  }

  async function createUserAssertionJwt() {
    if (!globalThis.crypto?.subtle)
      throw new Error('当前浏览器环境不支持 RSA 签名，请使用 HTTPS 或 localhost')
    const issuedAt = Math.floor(Date.now() / 1000)
    const configuredTtl = Number(props.guide?.userAssertionMaxTtlSeconds || 120)
    const ttlSeconds = Math.min(120, Math.max(30, configuredTtl))
    const claims = {
      iss: props.guide.userAssertionIssuer,
      aud: props.guide.userAssertionAudience,
      client_id: String(props.guide.clientId),
      sub: userAssertionSubject.value.trim(),
      iat: issuedAt,
      exp: issuedAt + ttlSeconds,
      jti: globalThis.crypto.randomUUID?.() || fallbackNonce(),
    }
    if (props.guide?.userAssertionMappingMode === 'VERIFIED_PHONE'
      && userAssertionPhone.value.trim()) {
      claims.phone_number = userAssertionPhone.value.trim()
    }
    if (userAssertionOrgId.value.trim())
      claims.forge_org_id = userAssertionOrgId.value.trim()
    const header = {
      alg: 'RS256',
      typ: 'JWT',
      kid: props.guide.userAssertionKeyId,
    }
    const signingInput = `${base64UrlText(JSON.stringify(header))}.${base64UrlText(JSON.stringify(claims))}`
    const privateKey = await globalThis.crypto.subtle.importKey(
      'pkcs8',
      pemPrivateKeyBytes(userAssertionPrivateKey.value),
      { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
      false,
      ['sign'],
    )
    const signature = await globalThis.crypto.subtle.sign(
      'RSASSA-PKCS1-v1_5',
      privateKey,
      new TextEncoder().encode(signingInput),
    )
    return `${signingInput}.${base64UrlBytes(signature)}`
  }

  async function executeHmac() {
    if (!globalThis.crypto?.subtle)
      throw new Error('当前浏览器环境不支持 Web Crypto，请使用 HTTPS 或 localhost')
    const timestamp = String(Date.now())
    const nonce = globalThis.crypto.randomUUID?.() || fallbackNonce()
    const bodyHash = await sha256Hex(requestBody.value)
    const path = new URL(props.guide.invokeUrl).pathname
    const canonical = [
      String(props.guide.clientId),
      timestamp,
      nonce,
      'POST',
      path,
      bodyHash,
    ].join('\n')
    const signature = await hmacSha256Hex(credential.value, canonical)
    const invocation = await invokeGateway({
      'X-Forge-App-Id': String(props.guide.clientId),
      'X-Forge-Timestamp': timestamp,
      'X-Forge-Nonce': nonce,
      'X-Forge-Signature': signature,
      'Content-Type': 'application/json',
    }, {
      'X-Forge-App-Id': String(props.guide.clientId),
      'X-Forge-Timestamp': timestamp,
      'X-Forge-Nonce': nonce,
      'X-Forge-Signature': '<REDACTED>',
      'Content-Type': 'application/json',
    })
    const success = invocation.response.status >= 200 && invocation.response.status < 300
    return {
      success,
      error: success ? null : gatewayErrorMessage(invocation),
      tokenExchange: null,
      invocation,
    }
  }

  async function invokeGateway(actualHeaders, reportHeaders) {
    const idempotencyKey = props.guide.behavior === 'READ_ONLY'
      ? null
      : (globalThis.crypto?.randomUUID?.() || fallbackNonce())
    const requestHeaders = { ...actualHeaders }
    const safeHeaders = { ...reportHeaders }
    if (idempotencyKey) {
      requestHeaders['Idempotency-Key'] = idempotencyKey
      safeHeaders['Idempotency-Key'] = idempotencyKey
    }
    const startedAt = Date.now()
    const response = await fetch(backendProxyUrl(props.guide.invokeUrl), {
      method: 'POST',
      credentials: 'omit',
      headers: requestHeaders,
      body: requestBody.value,
    })
    const responseText = await response.text()
    const report = exchangeReport({
      method: 'POST',
      url: props.guide.invokeUrl,
      headers: safeHeaders,
      body: JSON.parse(requestBody.value),
      rawBody: requestBody.value,
    }, response, redactSensitive(parseBody(responseText)), Date.now() - startedAt)
    report.success = response.ok
    return report
  }

  function exchangeReport(request, response, body, durationMs) {
    return {
      request,
      response: {
        status: response.status,
        statusText: response.statusText,
        headers: redactHeaders(response.headers),
        body,
        rawBody: typeof body === 'string' ? body : JSON.stringify(body),
      },
      durationMs,
    }
  }

  function gatewayErrorMessage(invocation) {
    const status = invocation?.response?.status
    const body = invocation?.response?.body
    const code = body && typeof body === 'object' ? body.code : null
    const message = body && typeof body === 'object' ? body.message : null
    if (message)
      return `${message}${code ? `（${code}）` : ''}`
    return `能力调用失败，HTTP ${status || '-'}`
  }

  function redactTokenForm(params) {
    const safe = {}
    for (const [key, value] of params.entries())
      safe[key] = key === 'subject_token' ? '<REDACTED>' : value
    return safe
  }

  function redactTokenFormString(params) {
    const safe = new URLSearchParams(params)
    if (safe.has('subject_token'))
      safe.set('subject_token', '<REDACTED>')
    return safe.toString()
  }

  function redactSensitive(value) {
    if (Array.isArray(value))
      return value.map(redactSensitive)
    if (!value || typeof value !== 'object')
      return value
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [
      key,
      /authorization|token|secret|password|signing.?key|signature/i.test(key)
        ? '<REDACTED>'
        : redactSensitive(item),
    ]))
  }

  function redactHeaders(headers) {
    const result = {}
    headers.forEach((value, key) => {
      result[key] = /authorization|cookie|token|secret|signature/i.test(key)
        ? '<REDACTED>'
        : value
    })
    return result
  }

  function parseBody(text) {
    if (!text)
      return null
    try {
      return JSON.parse(text)
    }
    catch {
      return text
    }
  }

  function backendProxyUrl(absoluteUrl) {
    const parsed = new URL(absoluteUrl, window.location.origin)
    const prefix = String(import.meta.env.VITE_REQUEST_PREFIX || '').replace(/\/$/, '')
    return `${prefix}${parsed.pathname}${parsed.search}`
  }

  function basicCredentials(clientId, secret) {
    const bytes = new TextEncoder().encode(`${clientId}:${secret}`)
    let binary = ''
    for (const byte of bytes)
      binary += String.fromCharCode(byte)
    return btoa(binary)
  }

  function pemPrivateKeyBytes(pem) {
    const normalized = pem
      .replace('-----BEGIN PRIVATE KEY-----', '')
      .replace('-----END PRIVATE KEY-----', '')
      .replace(/\s/g, '')
    let binary
    try {
      binary = atob(normalized)
    }
    catch {
      throw new Error('用户断言私钥不是有效的 PKCS#8 PEM')
    }
    const bytes = new Uint8Array(binary.length)
    for (let index = 0; index < binary.length; index += 1)
      bytes[index] = binary.charCodeAt(index)
    return bytes.buffer
  }

  function base64UrlText(value) {
    return base64UrlBytes(new TextEncoder().encode(value))
  }

  function base64UrlBytes(value) {
    const bytes = value instanceof Uint8Array ? value : new Uint8Array(value)
    let binary = ''
    for (const byte of bytes)
      binary += String.fromCharCode(byte)
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  }

  async function sha256Hex(value) {
    const digest = await globalThis.crypto.subtle.digest(
      'SHA-256',
      new TextEncoder().encode(value),
    )
    return bytesToHex(digest)
  }

  async function hmacSha256Hex(key, value) {
    const cryptoKey = await globalThis.crypto.subtle.importKey(
      'raw',
      new TextEncoder().encode(key),
      { name: 'HMAC', hash: 'SHA-256' },
      false,
      ['sign'],
    )
    const signature = await globalThis.crypto.subtle.sign(
      'HMAC',
      cryptoKey,
      new TextEncoder().encode(value),
    )
    return bytesToHex(signature)
  }

  function bytesToHex(buffer) {
    return [...new Uint8Array(buffer)]
      .map(value => value.toString(16).padStart(2, '0'))
      .join('')
  }

  function fallbackNonce() {
    return `${Date.now()}-${Math.random().toString(16).slice(2)}`
  }

  function exchangeText(exchange) {
    return exchange ? JSON.stringify(exchange, null, 2) : '未发起请求'
  }

  function applySessionCredential(guide) {
    const session = getCapabilityCredential(guide?.clientId)
    const nextCredential = authMode.value === 'HMAC'
      ? session?.signingKey
      : session?.clientSecret
    credential.value = nextCredential || ''
    userAssertionPrivateKey.value = session?.privateKeyPem || ''
    const needsPrivateKey = authMode.value === 'OAUTH'
      && guide?.tokenExchangeRequired
      && subjectTokenMode.value === 'USER_ASSERTION'
    credentialAutoFilled.value = !!nextCredential
      && (!needsPrivateKey || !!session?.privateKeyPem)
  }

  function goClientWorkbench() {
    router.push({ path: '/open-platform/capability-client', query: { clientId: props.guide?.clientId } })
  }

  function downloadTestReport() {
    if (!testReport.value)
      return
    downloadText(
      JSON.stringify({
        securityNotice: '本报文已自动脱敏，不包含 Client Secret、Signing Key、用户断言私钥或可用 Token。',
        ...testReport.value,
      }, null, 2),
      `${fileStem()}-test-report.json`,
      'application/json;charset=UTF-8',
    )
  }

  function downloadIntegrationExample() {
    if (!props.guide)
      return
    const guide = props.guide
    const sections = [
      `# ${guide.capabilityName} 外围系统接入示例`,
      '',
      '> 安全提示：示例和测试报文均已脱敏，请从密钥管理系统注入真实凭据。',
      '',
      '## 接口信息',
      '',
      `- 能力编码：\`${guide.capabilityCode}\``,
      `- 能力版本：\`${guide.version || '-'}\``,
      `- 调用地址：\`${guide.invokeUrl}\``,
      `- Token 地址：\`${guide.tokenUrl}\``,
      `- OAuth Resource：\`${guide.openapiResource}\``,
      `- 客户端 ID / AppId：\`${guide.clientId}\``,
      `- 用户身份方案：\`${guide.userAssertionEnabled ? '客户端 RS256 用户断言' : '受信 OIDC JWT'}\``,
      ...(guide.userAssertionEnabled
        ? [
            `- 用户断言 kid：\`${guide.userAssertionKeyId}\``,
            `- 用户断言 Issuer：\`${guide.userAssertionIssuer}\``,
            `- 用户断言 Audience：\`${guide.userAssertionAudience}\``,
            `- Subject Token Type：\`${guide.userAssertionSubjectTokenType}\``,
          ]
        : []),
      ...(guide.requestNotes?.length
        ? [
            '',
            '## 请求前提',
            '',
            ...guide.requestNotes.map(note => `- ${note}`),
          ]
        : []),
      '',
      '## 请求 Body',
      '',
      '```json',
      JSON.stringify(guide.requestExample || {}, null, 2),
      '```',
    ]
    appendFieldTable(sections, '请求参数', guide.requestFields)
    appendFieldTable(sections, '返回参数', guide.responseFields)
    if (guide.responseNotes?.length) {
      sections.push('', '### 返回说明', '', ...guide.responseNotes.map(note => `- ${note}`))
    }
    if (guide.businessRules?.length) {
      sections.push('', '## 业务校验', '', ...guide.businessRules.map((rule, index) => `${index + 1}. ${rule}`))
    }
    const currentCurl = authMode.value === 'HMAC' ? guide.hmacExample : guide.oauthExample
    const currentJava = authMode.value === 'HMAC'
      ? guide.hmacJavaExample
      : subjectTokenMode.value === 'USER_ASSERTION' && guide.userAssertionJavaExample
        ? guide.userAssertionJavaExample
        : guide.oauthJavaExample
    appendCodeSection(sections, 'Curl 示例', 'bash', currentCurl)
    appendCodeSection(sections, 'Java 17 示例', 'java', currentJava)
    if (testReport.value)
      appendCodeSection(sections, '最近一次测试报文（已脱敏）', 'json', JSON.stringify(testReport.value, null, 2))
    downloadText(
      sections.join('\n'),
      `${fileStem()}-integration-example.md`,
      'text/markdown;charset=UTF-8',
    )
  }

  function appendFieldTable(sections, title, fields = []) {
    sections.push('', `## ${title}`, '')
    if (!fields.length) {
      sections.push('当前版本未声明字段。')
      return
    }
    sections.push('| 中文名称 | 字段编码 | 类型 | 必填 | 含义与约束 | 示例 |')
    sections.push('| --- | --- | --- | --- | --- | --- |')
    fields.forEach((field) => {
      sections.push(`| ${markdownCell(field.fieldLabel)} | \`${markdownCell(field.fieldCode)}\` | ${markdownCell(field.type)} | ${field.required ? '是' : '否'} | ${markdownCell(field.description)} | ${markdownCell(formatMarkdownExample(field.example))} |`)
    })
  }

  function markdownCell(value) {
    return String(value ?? '-').replace(/\|/g, '\\|').replace(/\r?\n/g, '<br>')
  }

  function formatMarkdownExample(value) {
    if (value == null)
      return '-'
    return typeof value === 'string' ? value : JSON.stringify(value)
  }

  function appendCodeSection(sections, title, language, content) {
    if (!content)
      return
    sections.push('', `## ${title}`, '', `\`\`\`${language}`, content, '\`\`\`')
  }

  function downloadText(content, filename, type) {
    const url = URL.createObjectURL(new Blob([content], { type }))
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  }

  function fileStem() {
    return `${props.guide?.capabilityCode || 'capability'}-${props.guide?.version || 'latest'}`
  }

  function formatDate(date) {
    return new Intl.DateTimeFormat('zh-CN', {
      dateStyle: 'medium',
      timeStyle: 'medium',
      hour12: false,
    }).format(date)
  }

  return {
    router,
    authMode,
    credential,
    subjectTokenMode,
    subjectToken,
    userAssertionSubject,
    userAssertionPhone,
    userAssertionOrgId,
    userAssertionPrivateKey,
    requestBody,
    testing,
    testReport,
    credentialAutoFilled,
    authOptions,
    requiresSubjectToken,
    credentialLabel,
    credentialPlaceholder,
    resetBody,
    handleTest,
    validateTestInput,
    validateFlowActionPayload,
    executeTest,
    executeOAuth,
    createUserAssertionJwt,
    executeHmac,
    invokeGateway,
    exchangeReport,
    gatewayErrorMessage,
    redactTokenForm,
    redactTokenFormString,
    redactSensitive,
    redactHeaders,
    parseBody,
    backendProxyUrl,
    basicCredentials,
    pemPrivateKeyBytes,
    base64UrlText,
    base64UrlBytes,
    sha256Hex,
    hmacSha256Hex,
    bytesToHex,
    fallbackNonce,
    exchangeText,
    applySessionCredential,
    goClientWorkbench,
    downloadTestReport,
    downloadIntegrationExample,
    appendFieldTable,
    markdownCell,
    formatMarkdownExample,
    appendCodeSection,
    downloadText,
    fileStem,
    formatDate,
  }
}
