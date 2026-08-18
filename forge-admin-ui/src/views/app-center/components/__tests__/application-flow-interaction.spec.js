import { describe, expect, it } from 'vitest'
import {
  buildFlowNodePermissionRows,
  buildPageSectionOptions,
  normalizeFlowUserTasks,
} from '../application-flow-interaction'

describe('application flow interaction helpers', () => {
  it('normalizes user tasks without converting identifiers to numbers', () => {
    expect(normalizeFlowUserTasks([
      {
        taskDefKey: 'finance_review',
        taskName: '财务复核',
        candidateGroups: ['finance'],
      },
    ])).toEqual([
      {
        taskDefKey: 'finance_review',
        taskName: '财务复核',
        formKey: '',
        assignee: '',
        candidateUsers: [],
        candidateGroups: ['finance'],
      },
    ])
  })

  it('merges parsed tasks with saved permissions and preserves stale rows', () => {
    const rows = buildFlowNodePermissionRows(
      [{ taskDefKey: 'finance_review', taskName: '财务复核', candidateGroups: ['finance'] }],
      [
        { nodeKey: 'finance_review', visibleSectionIds: ['base'], readonlySectionIds: ['payment'] },
        { nodeKey: 'legacy_node', visibleSectionIds: ['legacy'], readonlySectionIds: [] },
      ],
    )

    expect(rows).toEqual([
      expect.objectContaining({
        nodeKey: 'finance_review',
        nodeName: '财务复核',
        visibleSectionIds: ['base'],
        readonlySectionIds: ['payment'],
        stale: false,
      }),
      expect.objectContaining({ nodeKey: 'legacy_node', stale: true }),
    ])
  })

  it('marks configured section ids that no longer exist', () => {
    expect(buildPageSectionOptions(
      [{ sectionId: 'base', title: '基础信息' }],
      ['base', 'removed'],
    )).toEqual([
      { label: '基础信息', value: 'base', invalid: false },
      { label: 'removed（已失效）', value: 'removed', invalid: true },
    ])
  })
})
