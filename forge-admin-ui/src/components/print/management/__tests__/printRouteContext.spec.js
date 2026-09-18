import { describe, expect, it } from 'vitest'
import { printRecordFromQuery, printSourceFromQuery } from '../printRouteContext'

const source = { applicationId: '9007199254740993', sourceType: 'LOWCODE', pageId: 'page_purchase', objectCode: 'purchase' }
describe('打印工作台页面身份', () => {
  it('保留页面标识和大整数应用 ID，不转为数字', () => {
    expect(printSourceFromQuery(source)).toEqual({ ...source, formKey: null })
    expect(printSourceFromQuery({ ...source, pageId: '9007199254740993' }).pageId).toBe('9007199254740993')
    expect(printRecordFromQuery({ ...source, recordId: 'saved_record', scene: 'DETAIL' }).source.pageId).toBe('page_purchase')
  })
  it.each(['', ' ', 'a/b', 'a\nb', 'a'.repeat(129), ['page_purchase']])('拒绝无效页面标识 %s', (pageId) => {
    expect(printSourceFromQuery({ ...source, pageId })).toBeNull()
  })
  it('代码表单身份保持独立', () => {
    expect(printSourceFromQuery({ ...source, sourceType: 'CODE', formKey: 'purchase_form' })).toEqual({ ...source, sourceType: 'CODE', pageId: null, formKey: 'purchase_form' })
  })
})
