import { Teleport, defineComponent, h } from 'vue'

/**
 * 将已治理的运行时 CSS 挂到 document.head。
 * 使用渲染函数创建 style，避免 Vue 模板编译器忽略副作用标签。
 */
export default defineComponent({
  name: 'RuntimeScopedStyles',
  props: {
    styles: { type: Array, default: () => [] },
  },
  setup(props) {
    return () => h(Teleport, { to: 'head' }, (props.styles || [])
      .filter(item => String(item?.css || '').trim())
      .map((item, index) => h('style', {
        key: String(item.id || index),
        type: 'text/css',
        'data-forge-extension-style': String(item.id || index),
      }, String(item.css))))
  },
})
