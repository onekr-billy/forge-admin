import { describe, expect, it } from 'vitest'
import {
  buildApplicationPortalNavigationNodes,
  filterNavigationNodesByClient,
  resolveNodeClientCodes,
} from '../components/portal/portal-navigation-runtime'

describe('portal navigation client projection', () => {
  const nodes = [
    { id: 'group', type: 'group', title: '业务分组', mountTarget: 'ADMIN' },
    { id: 'admin', type: 'page', title: '管理端', parentId: 'group', mountTarget: 'ADMIN' },
    { id: 'mobile', type: 'page', title: '移动端', parentId: 'group', mountTarget: 'MOBILE' },
    { id: 'both', type: 'page', title: '两端', mountTarget: 'BOTH' },
    { id: 'empty', type: 'group', title: '空分组', mountTarget: 'BOTH' },
  ]

  it('maps mount targets to isolated client codes', () => {
    expect(resolveNodeClientCodes({ mountTarget: 'ADMIN' })).toEqual(['pc'])
    expect(resolveNodeClientCodes({ mountTarget: 'MOBILE' })).toEqual(['h5'])
    expect(resolveNodeClientCodes({ mountTarget: 'BOTH' })).toEqual(['pc', 'h5'])
  })

  it('does not mix mobile pages into the management portal', () => {
    expect(filterNavigationNodesByClient(nodes, 'pc').map(node => node.id)).toEqual(['group', 'admin', 'both'])
  })

  it('keeps a legacy/default group as the mobile page parent', () => {
    expect(filterNavigationNodesByClient(nodes, 'h5').map(node => node.id)).toEqual(['group', 'mobile', 'both'])
  })

  it('removes groups with no page for the selected client', () => {
    const result = filterNavigationNodesByClient([
      ...nodes,
      { id: 'admin-only-group', type: 'group', mountTarget: 'ADMIN' },
    ], 'h5')
    expect(result.some(node => node.id === 'admin-only-group')).toBe(false)
  })

  it('keeps the built-in workflow pages fixed before user pages in the pc portal', () => {
    const result = buildApplicationPortalNavigationNodes(nodes, 'pc')
    const renderedOrder = [...result]
      .sort((left, right) => Number(left.sort || 0) - Number(right.sort || 0))

    expect(result.slice(0, 6).map(node => node.id)).toEqual([
      'system:workbench',
      'system:todo',
      'system:done',
      'system:sent',
      'system:cc',
      'system:messages',
    ])
    expect(result.slice(0, 6).map(node => node.systemView)).toEqual([
      'workbench',
      'todo',
      'done',
      'sent',
      'cc',
      'messages',
    ])
    expect(result.map(node => node.id)).toEqual([
      'system:workbench',
      'system:todo',
      'system:done',
      'system:sent',
      'system:cc',
      'system:messages',
      'group',
      'admin',
      'both',
    ])
    expect(renderedOrder.slice(0, 6).map(node => node.id)).toEqual([
      'system:workbench',
      'system:todo',
      'system:done',
      'system:sent',
      'system:cc',
      'system:messages',
    ])
  })

  it('does not inject desktop workflow pages into the h5 portal', () => {
    const result = buildApplicationPortalNavigationNodes(nodes, 'h5')

    expect(result.map(node => node.id)).toEqual(['group', 'mobile', 'both'])
    expect(result.some(node => node.id.startsWith('system:'))).toBe(false)
  })
})
