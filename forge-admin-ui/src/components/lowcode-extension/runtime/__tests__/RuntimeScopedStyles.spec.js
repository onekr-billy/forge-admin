import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import RuntimeScopedStyles from '../RuntimeScopedStyles'

describe('RuntimeScopedStyles', () => {
  afterEach(() => {
    document.head
      .querySelectorAll('[data-forge-extension-style]')
      .forEach(element => element.remove())
  })

  it('mounts runtime css in the document head and removes stale styles reactively', async () => {
    const wrapper = mount(RuntimeScopedStyles, {
      props: {
        styles: [
          { id: 'extension-1', css: '[data-forge-page="page_1"] { color: red; }' },
          { id: 'empty', css: '   ' },
        ],
      },
    })

    const first = document.head.querySelector('[data-forge-extension-style="extension-1"]')
    expect(first?.textContent).toBe('[data-forge-page="page_1"] { color: red; }')
    expect(document.head.querySelector('[data-forge-extension-style="empty"]')).toBeNull()

    await wrapper.setProps({
      styles: [{ id: 'extension-2', css: '[data-forge-page="page_2"] { color: blue; }' }],
    })

    expect(document.head.querySelector('[data-forge-extension-style="extension-1"]')).toBeNull()
    expect(document.head.querySelector('[data-forge-extension-style="extension-2"]')?.textContent)
      .toBe('[data-forge-page="page_2"] { color: blue; }')

    wrapper.unmount()
    expect(document.head.querySelector('[data-forge-extension-style]')).toBeNull()
  })
})
