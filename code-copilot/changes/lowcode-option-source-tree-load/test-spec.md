# Test Spec: lowcode-option-source-tree-load

## 自动化

| ID | 用例 | 方式 |
|----|------|------|
| AT1 | buildQuerySourceDisplayFields 含 parentField | vitest |
| AT2 | buildTreeFromFlatRows 父子挂载与孤儿入根 | vitest |
| AT3 | clampOptionPageSize 边界 | vitest |
| AT4 | resolveOptionLoadMode / shouldBuildTreeOptions | vitest |

## 手工（可选）

| ID | 步骤 | 期望 |
|----|------|------|
| MT1 | 树选择 + 业务对象配 parentId，全量 | 层级正确 |
| MT2 | 切懒加载，展开节点 | 再请求下级 |
| MT3 | 下拉 pageSize=5 | 最多 5 选项 |
| MT4 | 编辑回显有 Name | 不闪 id |
