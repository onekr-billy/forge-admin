# Tasks: lowcode-option-source-tree-load

## T1 运行时协议与纯函数
- [x] `option-source-runtime.js`：pageSize 钳制、扁平转树、loadMode 解析、displayFields 含 parentField
- [x] 单测覆盖扁平转树 / 限流 / displayFields

## T2 设计器属性面板
- [x] `ForgePropertyPanel`：树形组件动态源下展示「树结构怎么拼」「数据怎么加载」「每次最多取多少条」
- [x] 普通下拉/单选等动态源展示 pageSize
- [x] 切换动态源时为 treeSelect 写入默认 parentField / loadMode / structure / pageSize

## T3 AiFormItem 运行时
- [x] 全量：扁平+parentField → 树；嵌套 children 递归规范化
- [x] 懒加载：首屏根层 + on-load 拉下级；参数兼容 `/tree` 与查询源过滤
- [x] 回显：伴随字段 + 缺失节点临时注入；pageSize 统一钳制

## T4 验证
- [x] vitest：option-source-runtime（8）+ option-source-picker（4）通过
- [x] 记录 execution-log
