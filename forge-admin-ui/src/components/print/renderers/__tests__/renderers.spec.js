import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PrintPage from '../../runtime/PrintPage.vue'
import PrintImage from '../PrintImage.vue'
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
  it('uses the protocol image fitting mode', () => {
    const wrapper = mount(PrintImage, { props: { node: { src: 'data:image/png;base64,AA==', style: { objectFit: 'cover' } } } })
    expect(wrapper.get('img').attributes('style')).toContain('object-fit: cover')
  })
  it('renders protocol rotation and mirror transforms on the print page', () => {
    const element = { id: 'rotated', type: 'TEXT', xMm: 5, yMm: 5, widthMm: 30, heightMm: 8, rotationDeg: 90, flipX: true, lines: ['旋转文本'] }
    const page = {
      number: 1,
      header: { xMm: 0, yMm: 0, widthMm: 190, heightMm: 15, elements: [element] },
      footer: { xMm: 0, yMm: 277, widthMm: 190, heightMm: 10, elements: [] },
      fragments: [],
    }
    const wrapper = mount(PrintPage, { props: { page, geometry: { widthMm: 210, heightMm: 297 } } })
    expect(wrapper.get('[data-print-element="rotated"]').attributes('style')).toContain('rotate(90deg) scaleX(-1) scaleY(1)')
  })
})
