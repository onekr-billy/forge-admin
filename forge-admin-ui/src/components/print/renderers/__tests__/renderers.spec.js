import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PrintTable from '../PrintTable.vue'
import PrintText from '../PrintText.vue'

describe('safe paper rendering', () => {
  it('renders plain text with line preservation without interpreting markup', () => {
    const wrapper = mount(PrintText, { props: { node: { lines: ['<img src=x onerror=alert(1)>', '', '合计'], lineHeightMm: 5 } } })
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
    expect(wrapper.findAll('div')[2].attributes('style')).toContain('height: 5mm')
  })
  it('uses the measured row height and treats headers as plain text', () => {
    const wrapper = mount(PrintTable, { props: { node: { id: 'items', widthMm: 100, rows: [{ kind: 'header', heightMm: 9, cells: [{ widthMm: 100, text: '<b>名称</b>' }] }] } } })
    expect(wrapper.find('[role=row]').attributes('style')).toContain('height: 9mm')
    expect(wrapper.find('b').exists()).toBe(false)
    expect(wrapper.find('[role=columnheader]').text()).toBe('<b>名称</b>')
  })
  it('renders an authenticated signature image inside a table cell', () => {
    const wrapper = mount(PrintTable, { props: { node: { id: 'history', widthMm: 80, rows: [{ kind: 'data', heightMm: 20, cells: [{ widthMm: 80, type: 'IMAGE', text: '', src: 'blob:signature', imageHeightMm: 18 }] }] } } })
    expect(wrapper.get('img').attributes('src')).toBe('blob:signature')
    expect(wrapper.text()).not.toContain('blob:signature')
  })
})
