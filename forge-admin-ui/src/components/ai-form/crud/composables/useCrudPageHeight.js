import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

/** 页面高度适配；监听与探测状态按组件实例隔离。 */
export function useCrudPageHeight({ props, showInlineFormWorkspacePane }) {
  /*
   * 页面高度自愈。
   *
   * 表格依赖一条完整的 flex 高度链：
   * .ai-crud-page(height:100%) -> .ai-crud-main(flex:1) -> .ai-crud-table(flex:1,min-height:0)
   * -> .ai-table-wrapper(height:100%) -> n-data-table(flex-height)。
   * 链上任意一层高度是 auto（放进 n-tab-pane、n-card 内容区、未写高度的 div 等），
   * height:100% 就退化成 auto，表格体高度塌陷并被 overflow:hidden 裁掉，
   * 表现为「接口有数据但页面看不见行」。
   *
   * 这里挂载后探测父容器高度是否由内容决定：是则说明链路断在父级，
   * 用视口剩余空间接管自身高度让链路重新闭合，而不要求每个调用页面手工补父容器样式。
   */
  /** 页面底部留白，避免顶出外层滚动条 */
  const HEIGHT_FALLBACK_BOTTOM_GAP = 16
  /** 视口剩余空间低于此值时不再强行拉高，交给外层滚动 */
  const HEIGHT_FALLBACK_MIN_HEIGHT = 320
  /** 探测父容器高度变化的容差，规避亚像素误差 */
  const HEIGHT_PROBE_TOLERANCE = 8
  /** 这些容器自带确定高度或需要内容驱动高度，不接管：弹窗、抽屉、低代码栅格 */
  const HEIGHT_FALLBACK_EXCLUDE_SELECTOR = '.n-modal, .n-drawer-content, .ai-crud-preview'

  const crudRootRef = ref(null)
  const fallbackPageHeight = ref(0)
  let heightResizeObserver = null
  let heightMeasureRaf = null
  let heightProbeDone = false

  const pageHeightStyle = computed(() => (
    fallbackPageHeight.value > 0 ? { height: `${fallbackPageHeight.value}px` } : null
  ))

  /**
   * 探测父容器高度是否由内容决定。
   * 把自身临时隐藏后父容器明显变矮，说明父容器没有确定高度（height 为 auto），
   * 此时子元素的 height:100% 会退化成 auto，高度链断裂。
   * 父容器若由外层 flex 或显式高度撑开，隐藏子元素不会改变其高度。
   */
  function isParentHeightContentDriven(el) {
    const parent = el.parentElement
    if (!parent)
      return false
    const heightWithSelf = parent.clientHeight
    el.style.setProperty('display', 'none')
    const heightWithoutSelf = parent.clientHeight
    el.style.removeProperty('display')
    return heightWithoutSelf < heightWithSelf - HEIGHT_PROBE_TOLERANCE
  }

  function measurePageHeight() {
    const el = crudRootRef.value
    // formOnly 是文档流布局。显式 maxHeight 只约束表格，页签/内嵌表单仍需要页面高度接管，否则会被 overflow:hidden 裁掉且无法滚动。
    if (!el || props.formOnly)
      return
    if (props.maxHeight !== undefined && !showInlineFormWorkspacePane.value)
      return
    // 隐藏状态（如未激活的 n-tab-pane）测不到真实位置，等可见后由 ResizeObserver 再触发
    if (!el.offsetParent)
      return
    if (el.closest(HEIGHT_FALLBACK_EXCLUDE_SELECTOR))
      return

    const expected = Math.round(
      window.innerHeight - el.getBoundingClientRect().top - HEIGHT_FALLBACK_BOTTOM_GAP,
    )
    if (expected < HEIGHT_FALLBACK_MIN_HEIGHT)
      return

    // 已接管后只跟随视口更新数值，不再重复探测：
    // 接管带来的高度变化会让探测结论反转，与 ResizeObserver 相互触发形成抖动
    if (fallbackPageHeight.value > 0) {
      fallbackPageHeight.value = expected
      return
    }
    if (heightProbeDone)
      return

    heightProbeDone = true
    if (isParentHeightContentDriven(el))
      fallbackPageHeight.value = expected
  }

  function scheduleMeasurePageHeight() {
    if (heightMeasureRaf)
      cancelAnimationFrame(heightMeasureRaf)
    heightMeasureRaf = requestAnimationFrame(() => {
      heightMeasureRaf = null
      measurePageHeight()
    })
  }

  watch(showInlineFormWorkspacePane, () => {
    heightProbeDone = false
    scheduleMeasurePageHeight()
  })

  onMounted(() => {
    nextTick(scheduleMeasurePageHeight)
    if (typeof ResizeObserver !== 'undefined' && crudRootRef.value) {
      heightResizeObserver = new ResizeObserver(scheduleMeasurePageHeight)
      heightResizeObserver.observe(crudRootRef.value)
    }
    window.addEventListener('resize', scheduleMeasurePageHeight)
  })

  onBeforeUnmount(() => {
    if (heightMeasureRaf) {
      cancelAnimationFrame(heightMeasureRaf)
      heightMeasureRaf = null
    }
    heightResizeObserver?.disconnect()
    heightResizeObserver = null
    window.removeEventListener('resize', scheduleMeasurePageHeight)
  })

  return { crudRootRef, pageHeightStyle }
}
