export const pageWidgetComponentKeys = [
  'rich-text',
  'transfer',
  'watermark',
  'vue-component',
  'html-tag',
  'markdown',
  'barcode',
  'qrcode',
  'calendar',
  'code',
  'countdown',
  'descriptions',
  'announcement',
  'list',
  'log',
  'number-animation',
  'breadcrumb',
  'menu',
  'pagination',
  'split',
]

export function isPageWidgetComponentKey(componentKey = '') {
  return pageWidgetComponentKeys.includes(componentKey)
}

export const pageWidgetCatalog = [
  {
    blockType: 'rich-text',
    componentKey: 'rich-text',
    group: 'content',
    title: '富文本框',
    label: '富文本框',
    desc: '带工具栏的富文本编辑器',
    defaultW: 8,
    defaultH: 6,
  },
  {
    blockType: 'transfer',
    componentKey: 'transfer',
    group: 'data',
    title: '穿梭框',
    label: '穿梭框',
    desc: '支持静态选项和远程接口选项',
    defaultW: 8,
    defaultH: 6,
  },
  {
    blockType: 'watermark',
    componentKey: 'watermark',
    group: 'extra',
    title: '水印',
    label: '水印',
    desc: '区域水印背景',
    defaultW: 6,
    defaultH: 4,
  },
  {
    blockType: 'vue-component',
    componentKey: 'vue-component',
    group: 'advanced',
    title: 'Vue 组件',
    label: 'Vue 组件',
    desc: '配置模板、脚本、样式和 props',
    defaultW: 8,
    defaultH: 6,
  },
  {
    blockType: 'html-tag',
    componentKey: 'html-tag',
    group: 'content',
    title: 'HTML 标签',
    label: 'HTML 标签',
    desc: '配置标签、属性和安全 HTML',
    defaultW: 6,
    defaultH: 5,
  },
  {
    blockType: 'markdown',
    componentKey: 'markdown',
    group: 'content',
    title: 'Markdown',
    label: 'Markdown',
    desc: 'Markdown 源码与预览',
    defaultW: 8,
    defaultH: 6,
  },
  {
    blockType: 'barcode',
    componentKey: 'barcode',
    group: 'media',
    title: '条形码',
    label: '条形码',
    desc: '条形码 (jsbarcode)',
    defaultW: 4,
    defaultH: 3,
  },
  {
    blockType: 'qrcode',
    componentKey: 'qrcode',
    group: 'media',
    title: '二维码',
    label: '二维码',
    desc: 'qrcode-vue3 二维码',
    defaultW: 4,
    defaultH: 4,
  },
  {
    blockType: 'calendar',
    componentKey: 'calendar',
    group: 'data',
    title: '日历',
    label: '日历',
    desc: 'Naive UI 日历',
    defaultW: 8,
    defaultH: 8,
  },
  {
    blockType: 'code',
    componentKey: 'code',
    group: 'content',
    title: '代码',
    label: '代码',
    desc: 'Naive UI 代码块',
    defaultW: 8,
    defaultH: 5,
  },
  {
    blockType: 'countdown',
    componentKey: 'countdown',
    group: 'data',
    title: '倒计时',
    label: '倒计时',
    desc: 'Naive UI 倒计时',
    defaultW: 4,
    defaultH: 3,
  },
  {
    blockType: 'descriptions',
    componentKey: 'descriptions',
    group: 'data',
    title: '描述',
    label: '描述',
    desc: 'Naive UI 描述信息',
    defaultW: 8,
    defaultH: 4,
  },
  {
    blockType: 'announcement',
    componentKey: 'announcement',
    group: 'content',
    title: '公示',
    label: '公示',
    desc: 'Naive UI 信息公示栏',
    defaultW: 6,
    defaultH: 3,
  },
  {
    blockType: 'list',
    componentKey: 'list',
    group: 'data',
    title: '列表',
    label: '列表',
    desc: 'Naive UI 列表',
    defaultW: 7,
    defaultH: 5,
  },
  {
    blockType: 'log',
    componentKey: 'log',
    group: 'data',
    title: '日志',
    label: '日志',
    desc: 'Naive UI 日志',
    defaultW: 8,
    defaultH: 5,
  },
  {
    blockType: 'number-animation',
    componentKey: 'number-animation',
    group: 'data',
    title: '数值动画',
    label: '数值动画',
    desc: 'Naive UI 数值动画',
    defaultW: 4,
    defaultH: 3,
  },
  {
    blockType: 'breadcrumb',
    componentKey: 'breadcrumb',
    group: 'navigation',
    title: '面包屑',
    label: '面包屑',
    desc: 'Naive UI 面包屑',
    defaultW: 6,
    defaultH: 2,
  },
  {
    blockType: 'menu',
    componentKey: 'menu',
    group: 'navigation',
    title: '菜单',
    label: '菜单',
    desc: 'Naive UI 菜单',
    defaultW: 5,
    defaultH: 6,
  },
  {
    blockType: 'pagination',
    componentKey: 'pagination',
    group: 'navigation',
    title: '分页',
    label: '分页',
    desc: 'Naive UI 分页',
    defaultW: 6,
    defaultH: 2,
  },
  {
    blockType: 'split',
    componentKey: 'split',
    group: 'layout',
    title: '面板分隔',
    label: '面板分隔',
    desc: 'Naive UI Split 面板分隔',
    defaultW: 8,
    defaultH: 5,
    container: true,
  },
]

export function resolvePageWidgetMeta(componentKey = '') {
  return pageWidgetCatalog.find(item => item.componentKey === componentKey || item.blockType === componentKey) || null
}

export function createPageWidgetDefaultProps(componentKey = '') {
  if (componentKey === 'rich-text') {
    return {
      title: '富文本标题',
      content: '<h3>富文本内容</h3><p>支持加粗、斜体、标题、列表、引用、链接和源码编辑。</p>',
      placeholder: '请输入富文本内容',
      editorMode: 'visual',
      toolbarMode: 'default',
      editorModeName: 'default',
      excludeToolbarKeys: [],
      menuConfigText: '{}',
      readonly: false,
      minHeight: 180,
      backgroundColor: 'transparent',
      bordered: false,
      dataBinding: createWidgetDataBinding('content', { contentField: 'content', titleField: 'title' }),
    }
  }
  if (componentKey === 'transfer') {
    return {
      title: '穿梭框',
      sourceTitle: '可选项',
      targetTitle: '已选项',
      filterable: true,
      virtualScroll: false,
      disabled: false,
      dataSourceType: 'static',
      options: [
        { label: '选项一', value: 'option1' },
        { label: '选项二', value: 'option2' },
        { label: '选项三', value: 'option3' },
      ],
      value: ['option1'],
      optionSource: {
        api: '',
        method: 'get',
        paramsText: '{}',
        recordsField: 'records',
        labelField: 'label',
        valueField: 'value',
        disabledField: 'disabled',
      },
    }
  }
  if (componentKey === 'watermark') {
    return {
      content: '内部资料',
      previewText: '水印覆盖区域',
      cross: false,
      debug: false,
      fontSize: 14,
      fontFamily: '',
      fontStyle: 'normal',
      fontVariant: '',
      fontWeight: 400,
      fontColor: 'rgba(128, 128, 128, .3)',
      fullscreen: false,
      globalRotate: 0,
      lineHeight: 14,
      height: 32,
      image: '',
      imageHeight: undefined,
      imageOpacity: 1,
      imageWidth: undefined,
      rotate: 0,
      selectable: true,
      textAlign: 'left',
      width: 32,
      xGap: 0,
      xOffset: 0,
      yGap: 0,
      yOffset: 0,
      zIndex: 10,
      dataBinding: createWidgetDataBinding('content', { contentField: 'content' }),
    }
  }
  if (componentKey === 'vue-component') {
    return {
      componentName: 'CustomBusinessWidget',
      title: 'Vue 组件',
      description: '可维护 Vue SFC 风格代码。默认安全预览仅渲染 template，不执行 script。',
      templateCode: '<section class="custom-widget"><h3>{{ title }}</h3><p>{{ description }}</p></section>',
      scriptCode: 'export default {\n  props: {\n    title: String,\n    description: String\n  }\n}',
      styleCode: '.custom-widget {\n  padding: 16px;\n  border-radius: 12px;\n  background: #eff6ff;\n  color: #1e3a8a;\n}',
      propsJson: '{\n  "title": "业务组件",\n  "description": "这里展示组件 props 驱动的内容"\n}',
      previewMode: 'safe-template',
      safeMode: true,
    }
  }
  if (componentKey === 'html-tag') {
    return {
      tagName: 'section',
      textContent: 'HTML 标签内容',
      htmlContent: '<strong>HTML 内容</strong><p>支持安全标签和属性。</p>',
      renderMode: 'html',
      attributesText: '{\n  "class": "html-panel",\n  "aria-label": "说明区块"\n}',
      semanticRole: 'region',
      allowHtml: true,
      sanitize: true,
      dataBinding: createWidgetDataBinding('content', { contentField: 'htmlContent', titleField: 'textContent' }),
    }
  }
  if (componentKey === 'markdown') {
    return {
      title: 'Markdown 文档',
      content: '## Markdown 标题\n\n- 支持列表\n- 支持 **加粗** 和 *斜体*\n\n> 这是一段引用\n\n```js\nconst hello = "Forge"\n```',
      showTitle: true,
      previewMode: 'split',
      height: 320,
      breaks: true,
      sanitize: true,
      codeTheme: 'light',
      disabledMenus: [],
      dataBinding: createWidgetDataBinding('content', { contentField: 'content', titleField: 'title' }),
    }
  }
  if (componentKey === 'barcode') {
    return {
      title: '条形码',
      value: 'FORGE-2026-0001',
      format: 'CODE128',
      showText: true,
      barWidth: 2,
      barHeight: 72,
      fontSize: 14,
      margin: 8,
      lineColor: '#0f172a',
      background: 'transparent',
      dataBinding: createWidgetDataBinding('value', { valueField: 'value', titleField: 'title' }),
    }
  }
  if (componentKey === 'qrcode') {
    return {
      title: '二维码',
      value: 'https://forge.local',
      size: 132,
      margin: 0,
      foreground: '#0f172a',
      background: 'transparent',
      cornerColor: '#0f172a',
      errorCorrectionLevel: 'Q',
      dotsType: 'square',
      cornersSquareType: 'square',
      cornersDotType: 'square',
      showText: true,
      dataBinding: createWidgetDataBinding('value', { valueField: 'value', titleField: 'title' }),
    }
  }
  if (componentKey === 'calendar') {
    return {
      title: '日历',
      value: Date.now(),
      size: 'medium',
      showTitle: true,
      dataBinding: createWidgetDataBinding('value', { valueField: 'date' }),
    }
  }
  if (componentKey === 'code') {
    return {
      title: '代码',
      code: 'const message = "Forge Admin"\nconsole.log(message)',
      language: 'javascript',
      showLineNumbers: true,
      wordWrap: true,
      trim: true,
      dataBinding: createWidgetDataBinding('content', { contentField: 'code' }),
    }
  }
  if (componentKey === 'countdown') {
    return {
      title: '倒计时',
      duration: 3600000,
      active: true,
      precision: 0,
      separator: ':',
      dataBinding: createWidgetDataBinding('value', { valueField: 'duration' }),
    }
  }
  if (componentKey === 'descriptions') {
    return {
      title: '描述',
      column: 2,
      bordered: false,
      labelPlacement: 'left',
      size: 'small',
      itemsText: '[\n  { "label": "业务对象", "value": "订单管理" },\n  { "label": "状态", "value": "启用" },\n  { "label": "负责人", "value": "管理员" },\n  { "label": "更新时间", "value": "2026-06-25" }\n]',
      dataBinding: createWidgetDataBinding('items', { labelField: 'label', valueField: 'value' }),
    }
  }
  if (componentKey === 'announcement') {
    return {
      title: '公示标题',
      content: '这里展示公告、公示、提示或业务说明内容。',
      type: 'info',
      bordered: false,
      showIcon: true,
      closable: false,
      dataBinding: createWidgetDataBinding('content', { contentField: 'content', titleField: 'title' }),
    }
  }
  if (componentKey === 'list') {
    return {
      title: '列表',
      bordered: false,
      hoverable: true,
      size: 'small',
      itemsText: '[\n  { "title": "审批待办", "description": "等待业务负责人处理", "meta": "刚刚" },\n  { "title": "数据同步", "description": "订单数据已完成同步", "meta": "10 分钟前" },\n  { "title": "系统通知", "description": "低代码页面配置已更新", "meta": "今天" }\n]',
      dataBinding: createWidgetDataBinding('items', { titleField: 'title', descriptionField: 'description', metaField: 'meta' }),
    }
  }
  if (componentKey === 'log') {
    return {
      title: '日志',
      log: '[10:00:01] 页面配置加载完成\n[10:00:03] 查询接口 GET /page\n[10:00:05] 渲染完成',
      rows: 6,
      fontSize: 12,
      trim: true,
      language: 'log',
      dataBinding: createWidgetDataBinding('content', { contentField: 'log' }),
    }
  }
  if (componentKey === 'number-animation') {
    return {
      title: '数值动画',
      from: 0,
      to: 12836,
      precision: 0,
      duration: 1200,
      prefix: '',
      suffix: ' 条',
      color: '#2563eb',
      dataBinding: createWidgetDataBinding('value', { valueField: 'value' }),
    }
  }
  if (componentKey === 'breadcrumb') {
    return {
      itemsText: '[\n  { "label": "首页" },\n  { "label": "应用中心" },\n  { "label": "订单管理" }\n]',
      dataBinding: createWidgetDataBinding('items', { labelField: 'label' }),
    }
  }
  if (componentKey === 'menu') {
    return {
      mode: 'vertical',
      value: 'overview',
      collapsed: false,
      optionsText: '[\n  { "label": "概览", "key": "overview" },\n  { "label": "订单列表", "key": "orders" },\n  { "label": "数据统计", "key": "stats" }\n]',
      dataBinding: createWidgetDataBinding('items', { labelField: 'label', valueField: 'key', childrenField: 'children' }),
    }
  }
  if (componentKey === 'pagination') {
    return {
      page: 1,
      pageSize: 10,
      itemCount: 128,
      showSizePicker: true,
      simple: false,
      dataBinding: createWidgetDataBinding('total', { totalField: 'total' }),
    }
  }
  if (componentKey === 'split') {
    return {
      direction: 'horizontal',
      defaultSize: 0.38,
      min: 0.2,
      max: 0.8,
      pane1Title: '左侧面板',
      pane1Content: '这里放置筛选、导航或说明内容。',
      pane2Title: '右侧面板',
      pane2Content: '这里放置列表、详情或主要业务内容。',
    }
  }
  return {}
}

export function createWidgetDataBinding(target = 'items', overrides = {}) {
  return {
    enabled: false,
    target,
    sourceType: 'static',
    contextPath: '',
    api: '',
    method: 'get',
    paramsText: '{}',
    dataPath: 'data',
    labelField: 'label',
    valueField: 'value',
    titleField: 'title',
    descriptionField: 'description',
    metaField: 'meta',
    keyField: 'key',
    childrenField: 'children',
    contentField: 'content',
    totalField: 'total',
    ...overrides,
  }
}

export function createPageWidgetComponent(componentKey = '', options = {}) {
  const meta = resolvePageWidgetMeta(componentKey) || {}
  const id = options.id || `cmp_${componentKey || 'widget'}_${Date.now()}`
  const gridColumns = Math.max(1, Math.min(24, Number(options.gridColumns || 2)))
  return {
    id,
    componentKey,
    label: options.label || meta.label || meta.title || componentKey,
    props: {
      ...createPageWidgetDefaultProps(componentKey),
      ...(options.props || {}),
    },
    fieldBinding: {
      mode: 'virtual',
    },
    layout: {
      span: Math.min(gridColumns, Math.max(1, Number(options.span || gridColumns))),
      align: 'left',
    },
    visibility: {
      hidden: false,
      readonly: false,
    },
    children: [],
  }
}

export function safeJsonParseObject(value = '', fallback = {}) {
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value || '{}') : value
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : fallback
  }
  catch {
    return fallback
  }
}

export function safeHtml(value = '') {
  return String(value || '')
    .replace(/<script\b[^>]*>/gi, '&lt;script&gt;')
    .replace(/<\/script>/gi, '&lt;/script&gt;')
    .replace(/\son\w+="[^"]*"/gi, '')
    .replace(/\son\w+='[^']*'/gi, '')
    .replace(/javascript:/gi, '')
}

export function interpolateTemplate(template = '', data = {}) {
  return String(template || '').replace(/\{\{\s*([\w.]+)\s*\}\}/g, (_, key) => {
    const value = String(key).split('.').reduce((next, part) => next?.[part], data)
    return value === undefined || value === null ? '' : String(value)
  })
}
