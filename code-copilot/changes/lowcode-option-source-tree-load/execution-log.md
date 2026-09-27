# Execution Log: lowcode-option-source-tree-load

## 2026-09-23（续）字段存储类型纠偏

- 根因：treeSelect / SELECT 默认 `varchar(64|128)`，且未跟随目标值字段真实类型
- 修正原则：本表存储类型 = 目标对象「值字段」的 dataType（int 就 int，bigint 就 bigint）
- 实现：查询源元数据返回模型字段 dataType/length；设计器选值字段后强制同步；去掉 id 名启发式
- 验证：vitest option-source-runtime 9 passed；generator test-compile SUCCESS
